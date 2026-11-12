package io.github.amandhakar.documents.word

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.StringReader
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Size limits that keep a hostile or huge file from using all the phone's memory or time. */
data class DocxLimits(
    val maxXmlPartBytes: Int = 20 * 1024 * 1024,
    val maxImageBytes: Int = 5 * 1024 * 1024,
    val maxTotalImageBytes: Int = 25 * 1024 * 1024,
    val maxEntries: Int = 5000,
    /** Everything unzipped, including parts that are skipped: stops a "zip bomb". */
    val maxInflatedBytes: Long = 200L * 1024 * 1024,
    val maxBlocks: Int = 20000,
)

/**
 * Reads a Word .docx (a zip of XML files) into [DocxDocument]: paragraphs, headings, bold / italic / underline / size /
 * colour, bullet and numbered lists, tables and pictures. Nothing is written anywhere and nothing is run. Free of
 * Android calls so it can be unit tested.
 *
 * Not read (yet): headers and footers, footnotes, text boxes, fields, floating pictures' wrapping, comments, tracked
 * deletions (insertions are shown), and numbering that comes from a style instead of the paragraph itself.
 */
object DocxParser {
    private const val DOCUMENT = "word/document.xml"
    private const val STYLES = "word/styles.xml"
    private const val NUMBERING = "word/numbering.xml"
    private const val RELS = "word/_rels/document.xml.rels"
    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp")


    fun parse(input: InputStream, limits: DocxLimits = DocxLimits()): DocxDocument {
        val parts = readParts(input, limits)
        val documentXml = parts.xml[DOCUMENT] ?: throw DocxException("This isn't a Word document (word/document.xml is missing).")
        val styles = parts.xml[STYLES]?.let { Styles.parse(it) } ?: Styles(emptyMap())
        val numbering = parts.xml[NUMBERING]?.let { Numbering.parse(it) } ?: Numbering(emptyMap(), emptyMap())
        val rels = parts.xml[RELS]?.let { parseRels(it) } ?: emptyMap()
        val doc = dom(documentXml)
        val body = doc.documentElement.children("body").firstOrNull() ?: throw DocxException("The document has no body.")
        val reader = BodyReader(styles, numbering, rels, parts.media, limits)
        reader.readBlocks(body)
        return DocxDocument(reader.blocks, reader.truncated)
    }

    // ---- reading the zip ----

    private class Parts(val xml: Map<String, String>, val media: Map<String, ByteArray>)

    private fun readParts(input: InputStream, limits: DocxLimits): Parts {
        val xml = HashMap<String, String>()
        val media = HashMap<String, ByteArray>()
        var entries = 0
        var inflated = 0L
        var imageTotal = 0L
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++entries > limits.maxEntries) throw DocxException("This file has too many parts.")
                    val name = entry.name
                    val wanted = name == DOCUMENT || name == STYLES || name == NUMBERING || name == RELS
                    val isMedia = name.startsWith("word/media/") && name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS
                    val cap = when {
                        wanted -> limits.maxXmlPartBytes
                        isMedia -> limits.maxImageBytes
                        else -> 0
                    }
                    val buffer = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(16 * 1024)
                    var size = 0
                    var overCap = false
                    while (true) {
                        val n = zip.read(chunk)
                        if (n < 0) break
                        size += n
                        inflated += n
                        if (inflated > limits.maxInflatedBytes) throw DocxException("This file unpacks to more than the app can handle.")
                        if (size <= cap) buffer.write(chunk, 0, n) else overCap = true
                    }
                    when {
                        wanted && overCap -> throw DocxException("A part of this document is too big to open.")
                        wanted -> xml[name] = buffer.toString(Charsets.UTF_8.name())
                        isMedia && !overCap && imageTotal + size <= limits.maxTotalImageBytes -> {
                            imageTotal += size
                            media[name] = buffer.toByteArray()
                        }
                    }
                }
            }
        } catch (e: ZipException) {
            throw DocxException("This isn't a valid .docx file. It may be damaged or password-protected.")
        }
        if (xml.isEmpty() && media.isEmpty() && entries == 0) {
            throw DocxException("This isn't a valid .docx file. It may be damaged or password-protected.")
        }
        return Parts(xml, media)
    }

    private fun parseRels(xml: String): Map<String, String> {
        val result = HashMap<String, String>()
        val root = dom(xml).documentElement
        for (rel in root.childElements()) {
            if (rel.localName != "Relationship") continue
            if (rel.getAttribute("TargetMode").equals("External", ignoreCase = true)) continue
            val id = rel.getAttribute("Id")
            val target = rel.getAttribute("Target")
            if (id.isNotEmpty() && target.isNotEmpty()) result[id] = resolve(target)
        }
        return result
    }

    /** A relationship target is relative to word/: "media/a.png" -> "word/media/a.png"; "/word/x" stays absolute. */
    internal fun resolve(target: String): String {
        val parts = ArrayList<String>()
        val base = if (target.startsWith("/")) target.removePrefix("/") else "word/$target"
        for (p in base.split('/')) {
            when (p) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts += p
            }
        }
        return parts.joinToString("/")
    }

    // ---- styles and numbering ----

    internal class Styles(private val headingByStyleId: Map<String, Int>) {
        fun headingLevel(styleId: String?): Int =
            // A style the styles part names wins; otherwise the usual ids ("Heading2") still work, e.g. with no styles part.
            styleId?.let { headingByStyleId[it] ?: headingLevelOf(it, "") } ?: 0

        companion object {
            fun parse(xml: String): Styles {
                val map = HashMap<String, Int>()
                for (style in dom(xml).documentElement.children("style")) {
                    val id = style.wAttr("styleId") ?: continue
                    val name = style.valOf("name")?.lowercase().orEmpty()
                    val level = headingLevelOf(id, name)
                    if (level > 0) map[id] = level
                }
                return Styles(map)
            }
        }
    }

    internal class Level(val format: String, val start: Int)

    internal class Numbering(
        /** abstractNumId -> level -> format */
        private val abstracts: Map<String, Map<Int, Level>>,
        /** numId -> abstractNumId */
        private val nums: Map<String, String>,
    ) {
        fun level(numId: String, ilvl: Int): Level? = abstracts[nums[numId]]?.get(ilvl)

        companion object {
            fun parse(xml: String): Numbering {
                val root = dom(xml).documentElement
                val abstracts = HashMap<String, Map<Int, Level>>()
                for (a in root.children("abstractNum")) {
                    val id = a.wAttr("abstractNumId") ?: continue
                    val levels = HashMap<Int, Level>()
                    for (l in a.children("lvl")) {
                        val ilvl = l.wAttr("ilvl")?.toIntOrNull() ?: continue
                        levels[ilvl] = Level(l.valOf("numFmt") ?: "decimal", l.valOf("start")?.toIntOrNull() ?: 1)
                    }
                    abstracts[id] = levels
                }
                val nums = HashMap<String, String>()
                for (n in root.children("num")) {
                    val id = n.wAttr("numId") ?: continue
                    val abstractId = n.valOf("abstractNumId") ?: continue
                    nums[id] = abstractId
                }
                return Numbering(abstracts, nums)
            }
        }
    }

    // ---- the body ----

    private class BodyReader(
        private val styles: Styles,
        private val numbering: Numbering,
        private val rels: Map<String, String>,
        private val media: Map<String, ByteArray>,
        private val limits: DocxLimits,
    ) {
        val blocks = ArrayList<DocxBlock>()
        var truncated = false
        private val counter = ListCounter()

        fun readBlocks(container: Element) {
            readInto(container, blocks)
        }

        private fun readInto(container: Element, out: MutableList<DocxBlock>) {
            for (e in container.childElements()) {
                if (e.namespaceURI != W) continue
                if (out === blocks && blocks.size >= limits.maxBlocks) {
                    truncated = true
                    return
                }
                when (e.localName) {
                    "p" -> readParagraph(e, out)
                    "tbl" -> out += readTable(e)
                    "sdt" -> e.child("sdtContent")?.let { readInto(it, out) } // a content control: its content is the real thing
                }
            }
        }

        private fun readTable(tbl: Element): DocxTable {
            val rows = ArrayList<List<List<DocxBlock>>>()
            for (tr in tbl.children("tr")) {
                val cells = ArrayList<List<DocxBlock>>()
                for (tc in tr.children("tc")) {
                    val cell = ArrayList<DocxBlock>()
                    readInto(tc, cell)
                    cells += cell
                }
                rows += cells
            }
            return DocxTable(rows)
        }

        private fun readParagraph(p: Element, out: MutableList<DocxBlock>) {
            val pPr = p.child("pPr")
            val heading = styles.headingLevel(pPr?.valOf("pStyle"))
            val align = when (pPr?.valOf("jc")) {
                "center" -> DocxAlign.Center
                "right", "end" -> DocxAlign.End
                "both", "distribute" -> DocxAlign.Justify
                else -> DocxAlign.Start
            }
            var list: DocxListMark? = null
            val numPr = pPr?.child("numPr")
            val numId = numPr?.valOf("numId")
            if (numPr != null && numId != null && numId != "0") {
                val ilvl = numPr.valOf("ilvl")?.toIntOrNull()?.coerceIn(0, 8) ?: 0
                val level = numbering.level(numId, ilvl)
                val format = level?.format ?: "bullet"
                val label = if (format == "bullet") {
                    NumberLabels.bullet(ilvl)
                } else {
                    NumberLabels.label(format, counter.next(numId, ilvl, level?.start ?: 1), ilvl)
                }
                list = DocxListMark(ilvl, label)
            }
            val runs = ArrayList<DocxRun>()
            val images = ArrayList<DocxImage>()
            collectRuns(p, runs, images)
            out += DocxParagraph(mergeRuns(runs), heading, list, align)
            out += images
        }

        private fun collectRuns(parent: Element, runs: MutableList<DocxRun>, images: MutableList<DocxImage>) {
            for (e in parent.childElements()) {
                if (e.namespaceURI != W) continue
                when (e.localName) {
                    "r" -> readRun(e, runs, images)
                    // links, tracked insertions, smart tags and simple fields wrap more runs
                    "hyperlink", "ins", "smartTag", "fldSimple" -> collectRuns(e, runs, images)
                }
            }
        }

        private fun readRun(r: Element, runs: MutableList<DocxRun>, images: MutableList<DocxImage>) {
            val rPr = r.child("rPr")
            val size = rPr?.valOf("sz")?.toIntOrNull()
            val color = rPr?.valOf("color")?.takeIf { Regex("^[0-9A-Fa-f]{6}$").matches(it) }?.uppercase()
            val underline = rPr?.child("u")?.let { it.wAttr("val") != "none" } ?: false
            val text = StringBuilder()
            for (c in r.childElements()) {
                if (c.namespaceURI != W) continue
                when (c.localName) {
                    "t" -> text.append(c.textContent)
                    "tab" -> text.append('\t')
                    "br", "cr" -> if (c.wAttr("type") != "page") text.append('\n')
                    "noBreakHyphen" -> text.append('-')
                    "drawing", "pict" -> findImage(c)?.let { images += it }
                }
            }
            if (text.isNotEmpty()) {
                runs += DocxRun(
                    text = text.toString(),
                    bold = rPr?.child("b").isOn(),
                    italic = rPr?.child("i").isOn(),
                    underline = underline,
                    strike = rPr?.child("strike").isOn(),
                    sizeHalfPoints = size?.takeIf { it in 2..400 },
                    colorHex = color?.takeIf { it != "AUTO" },
                )
            }
        }

        /** The picture inside a drawing, found through its relationship id. Pictures we can't show are skipped. */
        private fun findImage(node: Element): DocxImage? {
            val stack = ArrayDeque<Element>()
            stack.add(node)
            while (stack.isNotEmpty()) {
                val e = stack.removeFirst()
                val id = e.rAttr("embed") ?: e.rAttr("id").takeIf { e.localName == "imagedata" }
                if (id != null) {
                    val path = rels[id]
                    val bytes = path?.let { media[it] }
                    if (path != null && bytes != null) return DocxImage(path, bytes)
                }
                stack.addAll(e.childElements())
            }
            return null
        }

        private fun mergeRuns(runs: List<DocxRun>): List<DocxRun> {
            val out = ArrayList<DocxRun>()
            for (run in runs) {
                val last = out.lastOrNull()
                if (last != null && last.copy(text = "") == run.copy(text = "")) {
                    out[out.size - 1] = last.copy(text = last.text + run.text)
                } else {
                    out += run
                }
            }
            return out
        }
    }
}

private const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
private const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

// ---- xml ----

internal fun dom(xml: String): org.w3c.dom.Document {
    // No DTDs, so no entity tricks ("billion laughs", reading files): a Word file never needs one.
    if (xml.contains("<!DOCTYPE") || xml.contains("<!ENTITY")) throw DocxException("This file contains something unsafe and wasn't opened.")
    val factory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = true
    factory.isExpandEntityReferences = false
    val builder = factory.newDocumentBuilder()
    builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
    try {
        return builder.parse(InputSource(StringReader(xml)))
    } catch (e: org.xml.sax.SAXException) {
        throw DocxException("The document's text couldn't be read. The file may be damaged.")
    }
}


internal fun Element.childElements(): List<Element> {
    val out = ArrayList<Element>()
    var n: Node? = firstChild
    while (n != null) {
        if (n is Element) out += n
        n = n.nextSibling
    }
    return out
}

/** Child elements in the Word namespace with this local name. */
internal fun Element.children(name: String): List<Element> = childElements().filter { it.localName == name && it.namespaceURI == W }

internal fun Element.child(name: String): Element? = children(name).firstOrNull()

/** The w:val attribute of this element's child [name], or null. */
internal fun Element.valOf(name: String): String? = child(name)?.wAttr("val")

internal fun Element.wAttr(name: String): String? =
    getAttributeNS(W, name).takeIf { it.isNotEmpty() } ?: getAttribute("w:$name").takeIf { it.isNotEmpty() }

internal fun Element.rAttr(name: String): String? = getAttributeNS(R, name).takeIf { it.isNotEmpty() }

/** True for toggles like <w:b/> or <w:b w:val="1"/>; false for <w:b w:val="false"/> or "0" / "off". */
internal fun Element?.isOn(): Boolean {
    if (this == null) return false
    val v = wAttr("val")?.lowercase() ?: return true
    return v != "false" && v != "0" && v != "off" && v != "none"
}


internal fun headingLevelOf(styleId: String, styleName: String): Int {
    if (styleName == "title") return 1
    val fromName = Regex("^heading (\\d)$").find(styleName)?.groupValues?.get(1)?.toInt()
    if (fromName != null && fromName in 1..9) return fromName
    val fromId = Regex("^[Hh]eading(\\d)$").find(styleId)?.groupValues?.get(1)?.toInt()
    return if (fromId != null && fromId in 1..9) fromId else 0
}

