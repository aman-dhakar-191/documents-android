package io.github.amandhakar.documents.convert

import io.github.amandhakar.documents.ooxml.XmlText
import io.github.amandhakar.documents.slides.PptxAlign
import io.github.amandhakar.documents.slides.PptxAnchor
import io.github.amandhakar.documents.slides.PptxDeck
import io.github.amandhakar.documents.slides.PptxGeometry
import io.github.amandhakar.documents.slides.PptxNotShown
import io.github.amandhakar.documents.slides.PptxParagraph
import io.github.amandhakar.documents.slides.PptxPicture
import io.github.amandhakar.documents.slides.PptxShape
import io.github.amandhakar.documents.slides.PptxSlide
import io.github.amandhakar.documents.slides.PptxTable
import io.github.amandhakar.documents.slides.PptxText
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

/**
 * Writes a [PptxDeck] as a PowerPoint .pptx (a zip of XML files) with no library: one blank layout and master, and per
 * slide its text boxes and shapes (position, fill, outline, paragraphs with size, bold, italic, underline, colour,
 * alignment and bullets), pictures (png, jpeg, gif) and a single-colour background. Tables, charts and labelled boxes
 * aren't written (the PDF conversion never makes any). Free of Android calls so it can be unit tested.
 */
object PptxWriter {
    private const val A = "http://schemas.openxmlformats.org/drawingml/2006/main"
    private const val P = "http://schemas.openxmlformats.org/presentationml/2006/main"
    private const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val NS = "xmlns:a=\"$A\" xmlns:r=\"$R\" xmlns:p=\"$P\""
    private const val XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
    private const val GROUP = "<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>" +
        "<p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>"

    private class Media(val bytes: ByteArray, val name: String)

    fun write(deck: PptxDeck, out: OutputStream) {
        val media = ArrayList<Media>()
        val slideXml = ArrayList<String>()
        val slideImageRels = ArrayList<List<Pair<String, String>>>() // per slide: relationship id and picture file
        for (slide in deck.slides) {
            val rels = ArrayList<Pair<String, String>>()
            slideXml += slide(slide, media, rels)
            slideImageRels += rels
        }
        val count = deck.slides.size

        ZipOutputStream(out).use { z ->
            fun put(name: String, text: String) = put(z, name, text.toByteArray(Charsets.UTF_8))
            put("[Content_Types].xml", contentTypes(count, media))
            put("_rels/.rels", "$XML<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"$R/officeDocument\" Target=\"ppt/presentation.xml\"/></Relationships>")
            put("ppt/presentation.xml", "$XML<p:presentation $NS><p:sldMasterIdLst><p:sldMasterId id=\"2147483648\" r:id=\"rId1\"/></p:sldMasterIdLst>" +
                "<p:sldIdLst>" + (1..count).joinToString("") { "<p:sldId id=\"${255 + it}\" r:id=\"rId${it + 2}\"/>" } + "</p:sldIdLst>" +
                "<p:sldSz cx=\"${deck.widthEmu}\" cy=\"${deck.heightEmu}\"/><p:notesSz cx=\"6858000\" cy=\"9144000\"/></p:presentation>")
            put("ppt/_rels/presentation.xml.rels", "$XML<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"$R/slideMaster\" Target=\"slideMasters/slideMaster1.xml\"/>" +
                "<Relationship Id=\"rId2\" Type=\"$R/theme\" Target=\"theme/theme1.xml\"/>" +
                (1..count).joinToString("") { "<Relationship Id=\"rId${it + 2}\" Type=\"$R/slide\" Target=\"slides/slide$it.xml\"/>" } + "</Relationships>")
            put("ppt/slideMasters/slideMaster1.xml", "$XML<p:sldMaster $NS><p:cSld><p:bg><p:bgRef idx=\"1001\"><a:schemeClr val=\"bg1\"/></p:bgRef></p:bg>" +
                "<p:spTree>$GROUP</p:spTree></p:cSld>" +
                "<p:clrMap bg1=\"lt1\" tx1=\"dk1\" bg2=\"lt2\" tx2=\"dk2\" accent1=\"accent1\" accent2=\"accent2\" accent3=\"accent3\" accent4=\"accent4\" " +
                "accent5=\"accent5\" accent6=\"accent6\" hlink=\"hlink\" folHlink=\"folHlink\"/>" +
                "<p:sldLayoutIdLst><p:sldLayoutId id=\"2147483649\" r:id=\"rId1\"/></p:sldLayoutIdLst></p:sldMaster>")
            put("ppt/slideMasters/_rels/slideMaster1.xml.rels", "$XML<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"$R/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/>" +
                "<Relationship Id=\"rId2\" Type=\"$R/theme\" Target=\"../theme/theme1.xml\"/></Relationships>")
            put("ppt/slideLayouts/slideLayout1.xml", "$XML<p:sldLayout $NS type=\"blank\" preserve=\"1\"><p:cSld name=\"Blank\"><p:spTree>$GROUP</p:spTree></p:cSld>" +
                "<p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>")
            put("ppt/slideLayouts/_rels/slideLayout1.xml.rels", "$XML<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"$R/slideMaster\" Target=\"../slideMasters/slideMaster1.xml\"/></Relationships>")
            put("ppt/theme/theme1.xml", THEME)
            for (i in 0 until count) {
                put("ppt/slides/slide${i + 1}.xml", slideXml[i])
                put("ppt/slides/_rels/slide${i + 1}.xml.rels", "$XML<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"$R/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/>" +
                    slideImageRels[i].joinToString("") { (id, file) -> "<Relationship Id=\"$id\" Type=\"$R/image\" Target=\"../media/$file\"/>" } + "</Relationships>")
            }
            for (m in media) put(z, "ppt/media/${m.name}", m.bytes)
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

    private fun contentTypes(slides: Int, media: List<Media>): String {
        val defaults = media.map { it.name.substringAfterLast('.') }.distinct().joinToString("") { "<Default Extension=\"$it\" ContentType=\"image/$it\"/>" }
        return "$XML<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>$defaults" +
            "<Override PartName=\"/ppt/presentation.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/>" +
            "<Override PartName=\"/ppt/slideMasters/slideMaster1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml\"/>" +
            "<Override PartName=\"/ppt/slideLayouts/slideLayout1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml\"/>" +
            "<Override PartName=\"/ppt/theme/theme1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.theme+xml\"/>" +
            (1..slides).joinToString("") { "<Override PartName=\"/ppt/slides/slide$it.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>" } +
            "</Types>"
    }

    private fun slide(slide: PptxSlide, media: MutableList<Media>, rels: MutableList<Pair<String, String>>): String {
        val shapes = StringBuilder()
        var id = 2
        for (shape in slide.shapes) {
            when (shape) {
                is PptxText -> shapes.append(textShape(shape, id++))
                is PptxPicture -> {
                    val ext = extensionOf(shape.bytes) ?: continue
                    val file = "image${media.size + 1}.$ext"
                    media += Media(shape.bytes, file)
                    val rid = "rId${rels.size + 2}"
                    rels += rid to file
                    shapes.append(pictureShape(shape, id++, rid))
                }
                is PptxTable, is PptxNotShown -> Unit // not written: see the class comment
            }
        }
        val bg = if (slide.backgroundHex.uppercase() == "FFFFFF") "" else
            "<p:bg><p:bgPr><a:solidFill><a:srgbClr val=\"${slide.backgroundHex.uppercase()}\"/></a:solidFill><a:effectLst/></p:bgPr></p:bg>"
        return "$XML<p:sld $NS><p:cSld>$bg<p:spTree>$GROUP$shapes</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>"
    }

    private fun xfrm(s: PptxShape) = "<a:xfrm><a:off x=\"${s.box.x}\" y=\"${s.box.y}\"/><a:ext cx=\"${s.box.w}\" cy=\"${s.box.h}\"/></a:xfrm>"

    private fun pictureShape(p: PptxPicture, id: Int, rid: String) =
        "<p:pic><p:nvPicPr><p:cNvPr id=\"$id\" name=\"Picture $id\"/><p:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></p:cNvPicPr><p:nvPr/></p:nvPicPr>" +
            "<p:blipFill><a:blip r:embed=\"$rid\"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>" +
            "<p:spPr>${xfrm(p)}<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr></p:pic>"

    private fun hex(h: String?): String? = h?.takeIf { Regex("^[0-9A-Fa-f]{6}$").matches(it) }?.uppercase()

    private fun textShape(t: PptxText, id: Int): String {
        val geometry = when (t.geometry) {
            PptxGeometry.Ellipse -> "ellipse"
            PptxGeometry.RoundRect -> "roundRect"
            PptxGeometry.Rect -> "rect"
        }
        val fill = hex(t.fillHex)?.let { "<a:solidFill><a:srgbClr val=\"$it\"/></a:solidFill>" } ?: "<a:noFill/>"
        val line = hex(t.lineHex)?.let { "<a:ln w=\"12700\"><a:solidFill><a:srgbClr val=\"$it\"/></a:solidFill></a:ln>" } ?: ""
        val anchor = when (t.anchor) {
            PptxAnchor.Middle -> "ctr"
            PptxAnchor.Bottom -> "b"
            PptxAnchor.Top -> "t"
        }
        val paragraphs = if (t.paragraphs.isEmpty()) "<a:p><a:endParaRPr lang=\"en-US\"/></a:p>" else t.paragraphs.joinToString("") { paragraph(it) }
        return "<p:sp><p:nvSpPr><p:cNvPr id=\"$id\" name=\"Text $id\"/><p:cNvSpPr txBox=\"1\"/><p:nvPr/></p:nvSpPr>" +
            "<p:spPr>${xfrm(t)}<a:prstGeom prst=\"$geometry\"><a:avLst/></a:prstGeom>$fill$line</p:spPr>" +
            "<p:txBody><a:bodyPr wrap=\"square\" lIns=\"91440\" tIns=\"45720\" rIns=\"91440\" bIns=\"45720\" rtlCol=\"0\" anchor=\"$anchor\"><a:noAutofit/></a:bodyPr><a:lstStyle/>$paragraphs</p:txBody></p:sp>"
    }

    private fun paragraph(p: PptxParagraph): String {
        val algn = when (p.align) {
            PptxAlign.Center -> "ctr"
            PptxAlign.End -> "r"
            PptxAlign.Justify -> "just"
            PptxAlign.Start -> "l"
        }
        val indent = 342900 * p.level
        val bullet = p.bullet
        val pPr = if (bullet != null) {
            "<a:pPr marL=\"${indent + 342900}\" indent=\"-342900\" algn=\"$algn\"><a:buFont typeface=\"Arial\"/><a:buChar char=\"${XmlText.escape(bullet.take(1))}\"/></a:pPr>"
        } else {
            "<a:pPr marL=\"$indent\" indent=\"0\" algn=\"$algn\"><a:buNone/></a:pPr>"
        }
        if (p.runs.isEmpty()) return "<a:p>$pPr<a:endParaRPr lang=\"en-US\"/></a:p>"
        val runs = StringBuilder()
        for (run in p.runs) {
            val size = (run.sizePt * 100).roundToInt().coerceIn(100, 20000)
            val attrs = "lang=\"en-US\" sz=\"$size\"" + (if (run.bold) " b=\"1\"" else "") + (if (run.italic) " i=\"1\"" else "") + (if (run.underline) " u=\"sng\"" else "") + " dirty=\"0\""
            val rPr = "<a:rPr $attrs>" + (hex(run.colorHex)?.let { "<a:solidFill><a:srgbClr val=\"$it\"/></a:solidFill>" } ?: "") + "</a:rPr>"
            val parts = run.text.split('\n')
            parts.forEachIndexed { i, part ->
                if (i > 0) runs.append("<a:br>$rPr</a:br>")
                if (part.isNotEmpty()) runs.append("<a:r>$rPr<a:t>${XmlText.escape(part)}</a:t></a:r>")
            }
        }
        return "<a:p>$pPr$runs</a:p>"
    }

    private val THEME = "$XML<a:theme xmlns:a=\"$A\" name=\"Documents\"><a:themeElements>" +
        "<a:clrScheme name=\"Documents\"><a:dk1><a:srgbClr val=\"000000\"/></a:dk1><a:lt1><a:srgbClr val=\"FFFFFF\"/></a:lt1>" +
        "<a:dk2><a:srgbClr val=\"1F497D\"/></a:dk2><a:lt2><a:srgbClr val=\"EEECE1\"/></a:lt2>" +
        "<a:accent1><a:srgbClr val=\"4F81BD\"/></a:accent1><a:accent2><a:srgbClr val=\"C0504D\"/></a:accent2><a:accent3><a:srgbClr val=\"9BBB59\"/></a:accent3>" +
        "<a:accent4><a:srgbClr val=\"8064A2\"/></a:accent4><a:accent5><a:srgbClr val=\"4BACC6\"/></a:accent5><a:accent6><a:srgbClr val=\"F79646\"/></a:accent6>" +
        "<a:hlink><a:srgbClr val=\"0000FF\"/></a:hlink><a:folHlink><a:srgbClr val=\"800080\"/></a:folHlink></a:clrScheme>" +
        "<a:fontScheme name=\"Documents\"><a:majorFont><a:latin typeface=\"Arial\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:majorFont>" +
        "<a:minorFont><a:latin typeface=\"Arial\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:minorFont></a:fontScheme>" +
        "<a:fmtScheme name=\"Documents\"><a:fillStyleLst>" + "<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill>".repeat(3) + "</a:fillStyleLst>" +
        "<a:lnStyleLst>" + listOf(9525, 25400, 38100).joinToString("") { "<a:ln w=\"$it\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:ln>" } + "</a:lnStyleLst>" +
        "<a:effectStyleLst>" + "<a:effectStyle><a:effectLst/></a:effectStyle>".repeat(3) + "</a:effectStyleLst>" +
        "<a:bgFillStyleLst>" + "<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill>".repeat(3) + "</a:bgFillStyleLst></a:fmtScheme>" +
        "</a:themeElements></a:theme>"
}
