package io.github.amandhakar.documents.edit

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File

/** Finds the lines of text on a page with PdfBox-Android. Blocking: call from a background thread. */
object PdfTextLines {
    /**
     * The text lines of page [pageIndex] (0-based) of [input], with where each sits. Rotated pages give
     * `supported = false` and no lines. Throws [java.io.IOException] if the PDF can't be read.
     */
    fun read(context: Context, input: File, pageIndex: Int): PageLines {
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument.load(input, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            require(pageIndex in 0 until doc.numberOfPages) { "no such page" }
            val page = doc.getPage(pageIndex)
            val crop = page.cropBox
            if (PdfCoordinates.normalizeRotation(page.rotation) != 0) {
                return PageLines(crop.width, crop.height, emptyList(), supported = false)
            }
            val collector = WordCollector()
            collector.sortByPosition = true
            collector.startPage = pageIndex + 1
            collector.endPage = pageIndex + 1
            collector.getText(doc)
            return PageLines(crop.width, crop.height, LineGrouping.group(collector.words))
        }
    }

    /** The standard font closest to [font] (from its name and descriptor). */
    private fun styleOf(font: com.tom_roush.pdfbox.pdmodel.font.PDFont): FontStyleChoice {
        val d = font.fontDescriptor
        return FontMatch.choose(
            name = font.name,
            fixedPitch = d?.isFixedPitch ?: false,
            serif = d?.isSerif ?: false,
            forceBold = d?.isForceBold ?: false,
            italic = d?.isItalic ?: false,
            weight = d?.fontWeight ?: 0f,
        )
    }

    /** Collects every word PdfBox finds, with its position in points from the top-left of the page. */
    private class WordCollector : PDFTextStripper() {
        val words = mutableListOf<TextWord>()

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            if (textPositions.isEmpty()) return
            val first = textPositions.first()
            val last = textPositions.last()
            val size = first.fontSizeInPt
            val height = maxOf(textPositions.maxOf { it.heightDir }, size * 0.7f)
            words += TextWord(
                text = text,
                left = first.xDirAdj,
                baseline = first.yDirAdj,
                width = last.xDirAdj + last.widthDirAdj - first.xDirAdj,
                height = height,
                fontSize = size,
                font = styleOf(first.font),
            )
        }
    }
}
