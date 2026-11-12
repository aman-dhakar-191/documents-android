package io.github.amandhakar.documents.edit

import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import com.tom_roush.pdfbox.util.Vector
import java.io.ByteArrayInputStream

/** A rectangle in points measured from the top-left of the page (the same space as [PageLines]). */
data class PageBoxPt(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun containsStartOf(x: Float, baselineY: Float, slop: Float): Boolean =
        x >= left - slop && x < right + slop && baselineY >= top - slop && baselineY <= bottom + slop
}

/**
 * Removes characters from a page's real content (not just covers them). It only touches text shown directly by
 * the page's own `Tj` and `TJ` operators, and it keeps everything after a removed character where it was by moving
 * the pen on by the same distance. It refuses (by throwing [UnsupportedOperationException], leaving the page
 * unchanged) whenever it can't be sure what it is doing: rotated pages, vertical text, text inside forms, an
 * operator list that doesn't match what PdfBox ran, or a character it can't match up.
 * Blocking: call from a background thread.
 */
object TextEraser {
    /** One character PdfBox drew, with where it started and how far it advances. */
    private class Glyph(
        val code: Int,
        val font: PDFont,
        val x: Float,
        val baselineY: Float,
        val widthThousandths: Float,
        val charSpacingThousandths: Float,
        val wordSpacingThousandths: Float,
    ) {
        var remove = false
    }

    /**
     * Removes the characters of page [pageIndex] (0-based) of [doc] that start inside any of [boxes].
     * Returns how many characters were removed (0 when none matched; the page is then left unchanged).
     */
    fun erase(doc: PDDocument, pageIndex: Int, boxes: List<PageBoxPt>): Int {
        val page = doc.getPage(pageIndex)
        if (PdfCoordinates.normalizeRotation(page.rotation) != 0) {
            throw UnsupportedOperationException("rotated page")
        }
        val parser = PDFStreamParser(page)
        parser.parse()
        val tokens = parser.tokens
        val pageOperators = tokens.filterIsInstance<Operator>().map { it.name }

        val collector = GlyphCollector(page)
        collector.sortByPosition = false
        collector.startPage = pageIndex + 1
        collector.endPage = pageIndex + 1
        collector.getText(doc)
        if (collector.problem != null) throw UnsupportedOperationException(collector.problem)
        if (collector.topLevelOperators != pageOperators) {
            throw UnsupportedOperationException("content stream didn't match what was run")
        }

        for ((_, glyphs) in collector.glyphsByOperator) {
            for (g in glyphs) g.remove = boxes.any { it.containsStartOf(g.x, g.baselineY, SLOP) }
        }
        val operatorTokenIndex = tokens.indices.filter { tokens[it] is Operator }
        var removed = 0
        for ((operatorIndex, glyphs) in collector.glyphsByOperator) {
            if (glyphs.none { it.remove }) continue
            val tokenIndex = operatorTokenIndex[operatorIndex]
            val operator = tokens[tokenIndex] as Operator
            val operand = tokens.getOrNull(tokenIndex - 1)
            val array: COSArray = when {
                operator.name == "Tj" && operand is COSString -> COSArray().also { it.add(operand) }
                operator.name == "TJ" && operand is COSArray -> operand
                else -> throw UnsupportedOperationException("text shown by ${operator.name}")
            }
            val (newArray, count) = rewrite(array, glyphs)
            tokens[tokenIndex - 1] = newArray
            tokens[tokenIndex] = Operator.getOperator("TJ")
            removed += count
        }
        if (removed == 0) return 0

        val stream = PDStream(doc)
        stream.createOutputStream(COSName.FLATE_DECODE).use { ContentStreamWriter(it).writeTokens(tokens) }
        page.setContents(stream)
        return removed
    }

    private const val SLOP = 1f

    /** Rewrites one TJ array, dropping the characters marked for removal. Returns the new array and how many were dropped. */
    private fun rewrite(array: COSArray, glyphs: List<Glyph>): Pair<COSArray, Int> {
        val parts = mutableListOf<TjPart>()
        val perString = mutableListOf<List<TjGlyph>>()
        var next = 0
        var count = 0
        for (i in 0 until array.size()) {
            when (val element = array.get(i)) {
                is COSString -> {
                    val bytes = element.bytes
                    parts += TjPart.Bytes(bytes)
                    val list = mutableListOf<TjGlyph>()
                    val input = ByteArrayInputStream(bytes)
                    while (input.available() > 0) {
                        val before = input.available()
                        val glyph = glyphs.getOrNull(next)
                            ?: throw UnsupportedOperationException("more codes than drawn characters")
                        val code = glyph.font.readCode(input)
                        val length = before - input.available()
                        if (code != glyph.code) throw UnsupportedOperationException("character codes didn't match")
                        val space = if (code == 32 && length == 1) glyph.wordSpacingThousandths else 0f
                        val advance = glyph.widthThousandths + glyph.charSpacingThousandths + space
                        list += TjGlyph(length, glyph.remove, advance)
                        if (glyph.remove) count++
                        next++
                    }
                    perString += list
                }
                is com.tom_roush.pdfbox.cos.COSNumber -> parts += TjPart.Num(element.floatValue())
                else -> throw UnsupportedOperationException("unexpected item in a text array")
            }
        }
        if (next != glyphs.size) throw UnsupportedOperationException("fewer codes than drawn characters")
        val out = COSArray()
        for (part in TjRewrite.rewrite(parts, perString)) {
            when (part) {
                is TjPart.Bytes -> out.add(COSString(part.bytes))
                is TjPart.Num -> out.add(COSFloat(part.value))
            }
        }
        return out to count
    }

    /**
     * Runs the page the way a text extractor does, but notes every character with the top-level operator that drew it.
     * Anything drawn inside a form or an annotation is ignored for erasing (and so never edited).
     */
    private class GlyphCollector(page: PDPage) : PDFTextStripper() {
        private val cropLeft = page.cropBox.lowerLeftX
        private val cropTop = page.cropBox.upperRightY
        private var depth = 0
        private var currentOperator = -1

        val topLevelOperators = mutableListOf<String>()
        val glyphsByOperator = LinkedHashMap<Int, MutableList<Glyph>>()
        var problem: String? = null

        override fun processOperator(operator: Operator, operands: MutableList<COSBase>) {
            if (depth == 0) {
                currentOperator = topLevelOperators.size
                topLevelOperators += operator.name
            }
            super.processOperator(operator, operands)
        }

        override fun showForm(form: PDFormXObject) {
            depth++
            try {
                super.showForm(form)
            } finally {
                depth--
            }
        }

        override fun showGlyph(textRenderingMatrix: Matrix, font: PDFont, code: Int, unicode: String?, displacement: Vector) {
            super.showGlyph(textRenderingMatrix, font, code, unicode, displacement)
            if (depth != 0) return
            if (font.isVertical) {
                problem = "vertical text"
                return
            }
            val state = graphicsState.textState
            val size = state.fontSize
            if (size == 0f) {
                problem = "zero font size"
                return
            }
            glyphsByOperator.getOrPut(currentOperator) { mutableListOf() } += Glyph(
                code = code,
                font = font,
                x = textRenderingMatrix.translateX - cropLeft,
                baselineY = cropTop - textRenderingMatrix.translateY,
                widthThousandths = displacement.x * 1000f,
                charSpacingThousandths = state.characterSpacing * 1000f / size,
                wordSpacingThousandths = state.wordSpacing * 1000f / size,
            )
        }
    }
}
