package io.github.amandhakar.documents.convert

import io.github.amandhakar.documents.ooxml.XmlText
import io.github.amandhakar.documents.word.DocxAlign
import io.github.amandhakar.documents.word.DocxBlock
import io.github.amandhakar.documents.word.DocxDocument
import io.github.amandhakar.documents.word.DocxImage
import io.github.amandhakar.documents.word.DocxParagraph
import io.github.amandhakar.documents.word.DocxTable
import io.github.amandhakar.documents.word.ImageSize
import io.github.amandhakar.documents.word.PageLayout
import io.github.amandhakar.documents.word.PageSetup
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Writes a [DocxDocument] as a Word .docx (a zip of XML files) with no library: paragraphs with their runs (bold,
 * italic, underline, strike, size, colour), headings, alignment, page breaks, pictures (png, jpeg, gif) and the page
 * size and margins. List marks are written as plain text in front of the paragraph. Tables aren't written (the PDF
 * conversion never makes any). Free of Android calls so it can be unit tested.
 */
object DocxWriter {
    private const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val EMU_PER_PT = 12700L

    fun write(doc: DocxDocument, out: OutputStream) {
        val images = ArrayList<Pair<DocxImage, String>>() // picture and its file name inside the zip
        val body = StringBuilder()
        for (block in doc.blocks) block(block, doc.page, body, images)
        val documentXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<w:document xmlns:w=\"$W\" xmlns:r=\"$R\" xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" " +
            "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
            "<w:body>$body${sectPr(doc.page)}</w:body></w:document>"

        ZipOutputStream(out).use { z ->
            fun put(name: String, text: String) = put(z, name, text.toByteArray(Charsets.UTF_8))
            put("[Content_Types].xml", contentTypes(images))
            put("_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"$R/officeDocument\" Target=\"word/document.xml\"/></Relationships>")
            put("word/document.xml", documentXml)
            put("word/styles.xml", STYLES)
            put("word/_rels/document.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"$R/styles\" Target=\"styles.xml\"/>" +
                images.mapIndexed { i, (_, name) -> "<Relationship Id=\"rIdImg${i + 1}\" Type=\"$R/image\" Target=\"media/$name\"/>" }.joinToString("") +
                "</Relationships>")
            for ((image, name) in images) put(z, "word/media/$name", image.bytes)
        }
    }

    private fun put(z: ZipOutputStream, name: String, bytes: ByteArray) {
        z.putNextEntry(ZipEntry(name))
        z.write(bytes)
        z.closeEntry()
    }

    private fun extensionOf(bytes: ByteArray): String? = when {
        bytes.size > 4 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "png"
        bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "jpeg"
        bytes.size > 3 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() -> "gif"
        else -> null
    }

    private fun contentTypes(images: List<Pair<DocxImage, String>>): String {
        val extensions = images.map { it.second.substringAfterLast('.') }.distinct()
        val defaults = extensions.joinToString("") { "<Default Extension=\"$it\" ContentType=\"image/$it\"/>" }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>$defaults" +
            "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
            "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>" +
            "</Types>"
    }

    private fun twips(pt: Float) = (pt * 20).roundToInt()

    private fun sectPr(p: PageSetup) =
        "<w:sectPr><w:pgSz w:w=\"${twips(p.widthPt)}\" w:h=\"${twips(p.heightPt)}\"/>" +
            "<w:pgMar w:top=\"${twips(p.marginTopPt)}\" w:right=\"${twips(p.marginRightPt)}\" w:bottom=\"${twips(p.marginBottomPt)}\" " +
            "w:left=\"${twips(p.marginLeftPt)}\" w:header=\"720\" w:footer=\"720\" w:gutter=\"0\"/></w:sectPr>"

    private fun block(b: DocxBlock, page: PageSetup, out: StringBuilder, images: MutableList<Pair<DocxImage, String>>) {
        when (b) {
            is DocxParagraph -> paragraph(b, out)
            is DocxImage -> image(b, page, out, images)
            is DocxTable -> Unit // not written: see the class comment
        }
    }

    private fun paragraph(p: DocxParagraph, out: StringBuilder) {
        out.append("<w:p><w:pPr>")
        if (p.headingLevel in 1..3) out.append("<w:pStyle w:val=\"Heading${p.headingLevel}\"/>")
        if (p.pageBreakBefore) out.append("<w:pageBreakBefore/>")
        p.list?.let { out.append("<w:ind w:left=\"${360 * (it.level + 1)}\" w:hanging=\"360\"/>") }
        when (p.align) {
            DocxAlign.Center -> out.append("<w:jc w:val=\"center\"/>")
            DocxAlign.End -> out.append("<w:jc w:val=\"right\"/>")
            DocxAlign.Justify -> out.append("<w:jc w:val=\"both\"/>")
            DocxAlign.Start -> Unit
        }
        out.append("</w:pPr>")
        p.list?.label?.takeIf { it.isNotEmpty() }?.let { out.append("<w:r><w:t xml:space=\"preserve\">${XmlText.escape(it)}</w:t><w:tab/></w:r>") }
        for (run in p.runs) {
            out.append("<w:r>")
            val props = StringBuilder()
            if (run.bold) props.append("<w:b/>")
            if (run.italic) props.append("<w:i/>")
            if (run.strike) props.append("<w:strike/>")
            run.colorHex?.takeIf { Regex("^[0-9A-Fa-f]{6}$").matches(it) }?.let { props.append("<w:color w:val=\"${it.uppercase()}\"/>") }
            run.sizeHalfPoints?.takeIf { it in 2..400 }?.let { props.append("<w:sz w:val=\"$it\"/><w:szCs w:val=\"$it\"/>") }
            if (run.underline) props.append("<w:u w:val=\"single\"/>")
            if (props.isNotEmpty()) out.append("<w:rPr>$props</w:rPr>")
            // Tabs and line breaks become their elements; everything else is text.
            val text = run.text
            var start = 0
            fun flush(end: Int) {
                if (end > start) out.append("<w:t xml:space=\"preserve\">${XmlText.escape(text.substring(start, end))}</w:t>")
            }
            for (i in text.indices) {
                when (text[i]) {
                    '\t' -> { flush(i); out.append("<w:tab/>"); start = i + 1 }
                    '\n' -> { flush(i); out.append("<w:br/>"); start = i + 1 }
                }
            }
            flush(text.length)
            out.append("</w:r>")
        }
        if (p.pageBreakAfter) out.append("<w:r><w:br w:type=\"page\"/></w:r>")
        out.append("</w:p>")
    }

    private fun image(image: DocxImage, page: PageSetup, out: StringBuilder, images: MutableList<Pair<DocxImage, String>>) {
        val extension = extensionOf(image.bytes) ?: return
        val (wPt, hPt) = PageLayout.imageBoxPt(image, page.contentWidthPt, page.contentHeightPt)
        val cx = (wPt * EMU_PER_PT).roundToLong()
        val cy = (hPt * EMU_PER_PT).roundToLong()
        images += image to "image${images.size + 1}.$extension"
        val index = images.size
        out.append(
            "<w:p><w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\"><wp:extent cx=\"$cx\" cy=\"$cy\"/>" +
                "<wp:docPr id=\"$index\" name=\"Picture $index\"/><wp:cNvGraphicFramePr/>" +
                "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:pic>" +
                "<pic:nvPicPr><pic:cNvPr id=\"$index\" name=\"Picture $index\"/><pic:cNvPicPr/></pic:nvPicPr>" +
                "<pic:blipFill><a:blip r:embed=\"rIdImg$index\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>" +
                "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$cx\" cy=\"$cy\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr>" +
                "</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>",
        )
    }

    private val STYLES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<w:styles xmlns:w=\"$W\">" +
        "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Arial\" w:hAnsi=\"Arial\" w:cs=\"Arial\"/><w:sz w:val=\"22\"/><w:szCs w:val=\"22\"/></w:rPr></w:rPrDefault>" +
        "<w:pPrDefault><w:pPr><w:spacing w:after=\"160\" w:line=\"259\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault></w:docDefaults>" +
        "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/><w:qFormat/></w:style>" +
        heading(1, 36) + heading(2, 28) + heading(3, 24) +
        "</w:styles>"

    private fun heading(level: Int, halfPoints: Int) =
        "<w:style w:type=\"paragraph\" w:styleId=\"Heading$level\"><w:name w:val=\"heading $level\"/><w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/><w:qFormat/>" +
            "<w:pPr><w:keepNext/><w:spacing w:before=\"240\" w:after=\"80\"/><w:outlineLvl w:val=\"${level - 1}\"/></w:pPr><w:rPr><w:b/><w:sz w:val=\"$halfPoints\"/><w:szCs w:val=\"$halfPoints\"/></w:rPr></w:style>"
}
