package io.github.amandhakar.documents.word

import io.github.amandhakar.documents.ooxml.OoxmlException
import io.github.amandhakar.documents.ooxml.OoxmlLimits
import io.github.amandhakar.documents.ooxml.OoxmlZip
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.StringReader
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
 * Not read (yet): headers and footers, footnotes, text boxes, fields, floating pictures' wrapping, comments, and tracked
 * deletions (insertions are shown).
 */
object DocxParser {
    private const val DOCUMENT = "word/document.xml"
    private const val STYLES = "word/styles.xml"
    private const val NUMBERING = "word/numbering.xml"
    private const val RELS = "word/_rels/document.xml.rels"
    private const val MAX_COLUMNS = 4
    private const val DEFAULT_GAP_TWIPS = 720


    fun parse(input: InputStream, limits: DocxLimits = DocxLimits()): DocxDocument {
        val parts = readParts(input, limits)
        val documentXml = parts.xml[DOCUMENT] ?: throw DocxException("This isn't a Word document (word/document.xml is missing).")
        val styles = parts.xml[STYLES]?.let { Styles.parse(it) } ?: Styles(emptyMap())
        val numbering = parts.xml[NUMBERING]?.let { Numbering.parse(it) } ?: Numbering(emptyMap(), emptyMap())
        val rels = parts.xml[RELS]?.let { parseRels(it) } ?: emptyMap()
        val doc = dom(documentXml)
        val body = doc.documentElement.children("body").firstOrNull() ?: throw DocxException("The document has no body.")
        // Every w:p in document order, so each paragraph read can say which one it is (editing finds it by this number).
        val ids = java.util.IdentityHashMap<Element, Int>()
        val all = doc.getElementsByTagNameNS(W, "p")
        for (i in 0 until all.length) ids[all.item(i) as Element] = i
        val reader = BodyReader(styles, numbering, rels, parts.media, limits, ids)
        reader.readBlocks(body)
        reader.addColumns(body.child("sectPr"))
        return DocxDocument(reader.blocks, reader.truncated, readPage(body.child("sectPr")))
    }

    /** The page of the document's last (or only) section. No section properties means A4. */
    private fun readPage(sect: Element?): PageSetup {
        if (sect == null) return PageSetup()
        val size = sect.child("pgSz")
        val mar = sect.child("pgMar")
        return PageSetup.fromTwips(
            size?.wAttr("w")?.toIntOrNull(), size?.wAttr("h")?.toIntOrNull(),
            mar?.wAttr("top")?.toIntOrNull(), mar?.wAttr("right")?.toIntOrNull(),
            mar?.wAttr("bottom")?.toIntOrNull(), mar?.wAttr("left")?.toIntOrNull(),
        )
    }

    // ---- reading the zip ----

    private class Parts(val xml: Map<String, String>, val media: Map<String, ByteArray>)

    private fun readParts(input: InputStream, limits: DocxLimits): Parts {
        val wanted = setOf(DOCUMENT, STYLES, NUMBERING, RELS)
        try {
            val parts = OoxmlZip.read(
                input,
                OoxmlLimits(
                    maxXmlPartBytes = limits.maxXmlPartBytes,
                    maxImageBytes = limits.maxImageBytes,
                    maxTotalImageBytes = limits.maxTotalImageBytes,
                    maxEntries = limits.maxEntries,
                    maxInflatedBytes = limits.maxInflatedBytes,
                ),
                wantXml = { it in wanted },
                wantMedia = { it.startsWith("word/media/") },
            )
            return Parts(parts.xml, parts.media)
        } catch (e: OoxmlException) {
            // The same reader serves PowerPoint; here the file is a .docx.
            throw DocxException(e.message.orEmpty().replace("valid Office file", "valid .docx file"))
        }
    }

    // ---- xml ----

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

    internal class StyleInfo(val id: String, val name: String, val basedOn: String?, val numId: String?, val ilvl: Int?)

    internal class Styles(private val byId: Map<String, StyleInfo>) {
        /** The style and the styles it is based on, nearest first (stops after 10 to survive a loop). */
        private fun chain(styleId: String?): List<StyleInfo> {
            val out = ArrayList<StyleInfo>()
            var current = styleId
            while (current != null && out.size < 10) {
                val style = byId[current] ?: break
                out += style
                current = style.basedOn
            }
            return out
        }

        fun headingLevel(styleId: String?): Int {
            if (styleId == null) return 0
            // A style the styles part names wins (also through "based on"); otherwise the usual ids ("Heading2") still work.
            return chain(styleId).firstNotNullOfOrNull { headingLevelOf(it.id, it.name).takeIf { l -> l > 0 } } ?: headingLevelOf(styleId, "")
        }

        /** The list a style puts its paragraphs in (numId and level, level null when the style doesn't say), or null. */
        fun numbering(styleId: String?): Pair<String, Int?>? {
            val style = chain(styleId).firstOrNull { it.numId != null } ?: return null
            return style.numId!! to style.ilvl
        }

        companion object {
            fun parse(xml: String): Styles {
                val map = HashMap<String, StyleInfo>()
                for (style in dom(xml).documentElement.children("style")) {
                    val id = style.wAttr("styleId") ?: continue
                    val numPr = style.child("pPr")?.child("numPr")
                    map[id] = StyleInfo(
                        id = id,
                        name = style.valOf("name")?.lowercase().orEmpty(),
                        basedOn = style.valOf("basedOn"),
                        numId = numPr?.valOf("numId"),
                        ilvl = numPr?.valOf("ilvl")?.toIntOrNull(),
                    )
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
        /** (abstractNumId, paragraph style id) -> the level that style is tied to, e.g. "List Bullet 2" -> 1 */
        private val styleLevels: Map<Pair<String, String>, Int> = emptyMap(),
    ) {
        fun level(numId: String, ilvl: Int): Level? = abstracts[nums[numId]]?.get(ilvl)

        /** The level a paragraph style is tied to in list [numId], or null. */
        fun levelForStyle(numId: String, styleId: String): Int? = nums[numId]?.let { styleLevels[it to styleId] }

        companion object {
            fun parse(xml: String): Numbering {
                val root = dom(xml).documentElement
                val abstracts = HashMap<String, Map<Int, Level>>()
                val styleLevels = HashMap<Pair<String, String>, Int>()
                for (a in root.children("abstractNum")) {
                    val id = a.wAttr("abstractNumId") ?: continue
                    val levels = HashMap<Int, Level>()
                    for (l in a.children("lvl")) {
                        val ilvl = l.wAttr("ilvl")?.toIntOrNull() ?: continue
                        levels[ilvl] = Level(l.valOf("numFmt") ?: "decimal", l.valOf("start")?.toIntOrNull() ?: 1)
                        l.valOf("pStyle")?.let { styleLevels[id to it] = ilvl }
                    }
                    abstracts[id] = levels
                }
                val nums = HashMap<String, String>()
                for (n in root.children("num")) {
                    val id = n.wAttr("numId") ?: continue
                    val abstractId = n.valOf("abstractNumId") ?: continue
                    nums[id] = abstractId
                }
                return Numbering(abstracts, nums, styleLevels)
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
        private val ids: java.util.IdentityHashMap<Element, Int>,
    ) {
        val blocks = ArrayList<DocxBlock>()
        var truncated = false
        private val counter = ListCounter()

        fun readBlocks(container: Element) {
            readInto(container, blocks)
        }

        /** Where each section ended (a block count) and what it said about columns, in order. */
        private class SectionEnd(val endIndex: Int, val columns: Int, val gapPt: Float, val continuous: Boolean)

        private val sections = ArrayList<SectionEnd>()

        private fun noteSection(sect: Element) {
            val cols = sect.child("cols")
            val count = (cols?.wAttr("num")?.toIntOrNull() ?: 1).coerceIn(1, MAX_COLUMNS)
            val gap = (cols?.wAttr("space")?.toIntOrNull() ?: DEFAULT_GAP_TWIPS).coerceIn(0, 1440) / 20f
            sections += SectionEnd(blocks.size, count, gap, sect.valOf("type") == "continuous")
        }

        /**
         * Puts a [DocxColumns] marker at the start of every section whose column count differs from the one before it.
         * [last] is the properties of the final section (the body's own `sectPr`), the others were noted on the way.
         */
        fun addColumns(last: Element?) {
            if (last != null) noteSection(last)
            var previous = 1
            var start = 0
            val inserts = ArrayList<Pair<Int, DocxColumns>>()
            for ((i, s) in sections.withIndex()) {
                if (s.columns != previous) inserts += start to DocxColumns(s.columns, s.gapPt, newPage = i > 0 && !s.continuous)
                previous = s.columns
                start = s.endIndex
            }
            // From the back, so earlier positions stay right.
            for ((index, marker) in inserts.asReversed()) blocks.add(index.coerceIn(0, blocks.size), marker)
        }

        private fun readInto(container: Element, out: MutableList<DocxBlock>) {
            for (e in container.childElements()) {
                if (e.namespaceURI != W) continue
                if (out === blocks && blocks.size >= limits.maxBlocks) {
                    truncated = true
                    return
                }
                when (e.localName) {
                    "p" -> {
                        readParagraph(e, out)
                        // A paragraph that carries section properties ends a section (only the body's own sections count).
                        if (out === blocks) e.child("pPr")?.child("sectPr")?.let { noteSection(it) }
                    }
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
            val styleId = pPr?.valOf("pStyle")
            val numPr = pPr?.child("numPr")
            var numId = numPr?.valOf("numId")
            var level = numPr?.valOf("ilvl")?.toIntOrNull()
            if (numId == null) {
                // No list on the paragraph itself: Word's own list styles ("List Bullet", "List Number") carry it.
                styles.numbering(styleId)?.let { (id, styleLevel) ->
                    numId = id
                    if (level == null) level = styleLevel
                }
            }
            val listId = numId
            if (listId != null && listId != "0") {
                val ilvl = (level ?: styleId?.let { numbering.levelForStyle(listId, it) } ?: 0).coerceIn(0, 8)
                val def = numbering.level(listId, ilvl)
                val format = def?.format ?: "bullet"
                val label = if (format == "bullet") {
                    NumberLabels.bullet(ilvl)
                } else {
                    NumberLabels.label(format, counter.next(listId, ilvl, def?.start ?: 1), ilvl)
                }
                list = DocxListMark(ilvl, label)
            }
            val runs = ArrayList<DocxRun>()
            val images = ArrayList<DocxImage>()
            val flags = RunFlags()
            collectRuns(p, runs, images, flags)
            val breakBefore = pPr?.child("pageBreakBefore").isOn()
            if (runs.isEmpty() && images.isNotEmpty() && list == null) {
                // A paragraph that only holds picture(s): the pictures are the content. An empty paragraph in front of
                // them would take up room and could push a page-sized picture onto the next page.
                images.forEachIndexed { i, image -> out += if (i == 0 && breakBefore) DocxImage(image.name, image.bytes, pageBreakBefore = true) else image }
                if (flags.pageBreak) out += DocxParagraph(emptyList(), pageBreakAfter = true)
            } else {
                out += DocxParagraph(
                    mergeRuns(runs), heading, list, align, pageBreakBefore = breakBefore, pageBreakAfter = flags.pageBreak,
                    id = ids[p] ?: -1, editable = !hasField(p), columnBreakAfter = flags.columnBreak,
                )
                out += images
            }
        }

        /** Things a run can say about the paragraph around it. */
        private class RunFlags {
            var pageBreak = false
            var columnBreak = false
        }

        /** True when the paragraph holds a field: editing its text could break the field. */
        private fun hasField(p: Element): Boolean =
            p.getElementsByTagNameNS(W, "fldChar").length > 0 || p.getElementsByTagNameNS(W, "instrText").length > 0 ||
                p.getElementsByTagNameNS(W, "fldSimple").length > 0

        private fun collectRuns(parent: Element, runs: MutableList<DocxRun>, images: MutableList<DocxImage>, flags: RunFlags) {
            for (e in parent.childElements()) {
                if (e.namespaceURI != W) continue
                when (e.localName) {
                    "r" -> readRun(e, runs, images, flags)
                    // links, tracked insertions, smart tags and simple fields wrap more runs
                    "hyperlink", "ins", "smartTag", "fldSimple" -> collectRuns(e, runs, images, flags)
                }
            }
        }

        private fun readRun(r: Element, runs: MutableList<DocxRun>, images: MutableList<DocxImage>, flags: RunFlags) {
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
                    "br", "cr" -> when (c.wAttr("type")) {
                        "page" -> flags.pageBreak = true
                        "column" -> flags.columnBreak = true
                        else -> text.append('\n')
                    }
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

