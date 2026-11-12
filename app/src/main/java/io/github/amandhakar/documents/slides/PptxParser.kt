package io.github.amandhakar.documents.slides

import io.github.amandhakar.documents.ooxml.OoxmlException
import io.github.amandhakar.documents.ooxml.OoxmlLimits
import io.github.amandhakar.documents.ooxml.OoxmlParts
import io.github.amandhakar.documents.ooxml.OoxmlZip
import io.github.amandhakar.documents.ooxml.RelationshipInfo
import io.github.amandhakar.documents.ooxml.attr
import io.github.amandhakar.documents.ooxml.kid
import io.github.amandhakar.documents.ooxml.kids
import io.github.amandhakar.documents.ooxml.parseRelationships
import io.github.amandhakar.documents.ooxml.parseXml
import io.github.amandhakar.documents.ooxml.relAttr
import io.github.amandhakar.documents.word.NumberLabels
import org.w3c.dom.Element
import java.io.InputStream

/** Limits for reading a presentation, on top of the zip limits. */
data class PptxLimits(
    val zip: OoxmlLimits = OoxmlLimits(),
    val maxSlides: Int = 500,
    val maxShapesPerSlide: Int = 2000,
)

private const val P = "http://schemas.openxmlformats.org/presentationml/2006/main"
private const val A = "http://schemas.openxmlformats.org/drawingml/2006/main"
private val COLOR_ELEMENTS = setOf("srgbClr", "schemeClr", "sysClr", "prstClr")

/**
 * Reads a PowerPoint .pptx (a zip of XML files) into [PptxDeck]: each slide's shapes with their positions, text with
 * its formatting (inherited from the layout, master and the presentation's defaults), pictures, tables, plain
 * shapes with fills, backgrounds in a single colour, and speaker notes. Nothing is written anywhere and nothing is
 * run. Free of Android calls so it can be unit tested.
 *
 * The slide size is the file's own; a file that doesn't state one gets the standard widescreen 16:9 (13.33 x 7.5 in).
 *
 * Shown as a labelled grey box (never dropped silently): charts, SmartArt, other embedded objects, pictures of a type
 * the phone can't show. Not drawn at all: connector lines, gradient and picture backgrounds (a plain colour is used),
 * rotation, shadows and effects, and animations and transitions. Shapes other than rectangles, rounded rectangles and
 * ellipses are drawn as rectangles.
 */
object PptxParser {
    private fun wantXml(name: String): Boolean =
        name.startsWith("ppt/") && (name.endsWith(".xml") || name.endsWith(".rels")) &&
            !name.startsWith("ppt/charts/") && !name.startsWith("ppt/diagrams/") && !name.startsWith("ppt/embeddings/")

    fun parse(input: InputStream, limits: PptxLimits = PptxLimits()): PptxDeck {
        val parts = OoxmlZip.read(input, limits.zip, ::wantXml) { it.startsWith("ppt/media/") }
        val presPath = "ppt/presentation.xml"
        val presXml = parts.xml[presPath] ?: throw OoxmlException("This isn't a PowerPoint file (ppt/presentation.xml is missing).")
        val pres = parseXml(presXml).documentElement
        val size = pres.kid(P, "sldSz")
        val width = size?.attr("cx")?.toLongOrNull()?.takeIf { it > 0 } ?: 12_192_000L
        val height = size?.attr("cy")?.toLongOrNull()?.takeIf { it > 0 } ?: 6_858_000L
        val presRels = parts.xml[OoxmlZip.relsPathOf(presPath)]?.let { parseRelationships(it, "ppt") } ?: emptyMap()
        val ids = pres.kid(P, "sldIdLst")?.kids(P, "sldId")?.mapNotNull { it.relAttr("id") } ?: emptyList()
        val paths = ids.mapNotNull { id -> presRels[id]?.path?.takeIf { parts.xml.containsKey(it) } }
        if (paths.isEmpty()) throw OoxmlException("This presentation has no slides.")
        val reader = DeckReader(parts, pres.kid(P, "defaultTextStyle"), limits)
        val slides = paths.take(limits.maxSlides).map { reader.readSlide(it) }
        return PptxDeck(width, height, slides, truncated = paths.size > limits.maxSlides)
    }
}

/** Theme colours: the scheme's named colours, and the master's mapping of names like tx1 / bg1 onto them. */
internal class ThemeColors(private val scheme: Map<String, String>, private val clrMap: Map<String, String>) {
    fun named(name: String): String? = scheme[clrMap[name] ?: name]

    /** The default text colour (theme "tx1"), black if the theme doesn't say. */
    fun text1(): String = named("tx1") ?: "000000"

    /** The colour inside [parent] (a solidFill, fillRef, ...), with lightness changes applied, or null. */
    fun colorOf(parent: Element?): String? {
        val c = parent?.kids()?.firstOrNull { it.namespaceURI == A && it.localName in COLOR_ELEMENTS } ?: return null
        val base = when (c.localName) {
            "srgbClr" -> c.attr("val")
            "sysClr" -> c.attr("lastClr") ?: if (c.attr("val") == "window") "FFFFFF" else "000000"
            "schemeClr" -> c.attr("val")?.let { named(it) }
            "prstClr" -> ColorMath.named(c.attr("val"))
            else -> null
        } ?: return null
        if (ColorMath.parse(base) == null) return null
        val mod = c.kid(A, "lumMod")?.attr("val")?.toIntOrNull()
        val off = c.kid(A, "lumOff")?.attr("val")?.toIntOrNull()
        return ColorMath.adjustLuminance(base.uppercase(), mod, off)
    }
}

private class SlideCtx(
    val layout: Element?,
    val master: Element?,
    val rels: Map<String, RelationshipInfo>,
    val colors: ThemeColors,
)

private class DeckReader(val parts: OoxmlParts, val defaultText: Element?, val limits: PptxLimits) {
    private val roots = HashMap<String, Element>()
    private val themes = HashMap<String, ThemeColors>()

    private fun root(path: String): Element? {
        roots[path]?.let { return it }
        val xml = parts.xml[path] ?: return null
        return parseXml(xml).documentElement.also { roots[path] = it }
    }

    private fun rels(path: String): Map<String, RelationshipInfo> =
        parts.xml[OoxmlZip.relsPathOf(path)]?.let { parseRelationships(it, OoxmlZip.folderOf(path)) } ?: emptyMap()

    private fun colorsFor(themePath: String?, master: Element?): ThemeColors {
        val key = "$themePath|${System.identityHashCode(master)}"
        themes[key]?.let { return it }
        val scheme = HashMap<String, String>()
        val clrScheme = themePath?.let { root(it) }?.kid(A, "themeElements")?.kid(A, "clrScheme")
        for (c in clrScheme?.kids().orEmpty()) {
            val inner = c.kids().firstOrNull() ?: continue
            val hex = if (inner.localName == "sysClr") inner.attr("lastClr") ?: inner.attr("val") else inner.attr("val")
            if (hex != null && ColorMath.parse(hex) != null) scheme[c.localName] = hex.uppercase()
        }
        val map = HashMap<String, String>()
        master?.kid(P, "clrMap")?.let { m ->
            val attrs = m.attributes
            for (i in 0 until attrs.length) map[attrs.item(i).nodeName] = attrs.item(i).nodeValue
        }
        // Without a mapping, the usual one.
        if (map.isEmpty()) map.putAll(mapOf("bg1" to "lt1", "tx1" to "dk1", "bg2" to "lt2", "tx2" to "dk2"))
        return ThemeColors(scheme, map).also { themes[key] = it }
    }

    fun readSlide(path: String): PptxSlide {
        val slide = root(path) ?: throw OoxmlException("A slide couldn't be read.")
        val rels = rels(path)
        val layoutPath = rels.values.firstOrNull { it.type == "slideLayout" }?.path
        val layout = layoutPath?.let { root(it) }
        val masterPath = layoutPath?.let { rels(it) }?.values?.firstOrNull { it.type == "slideMaster" }?.path
        val master = masterPath?.let { root(it) }
        val themePath = masterPath?.let { rels(it) }?.values?.firstOrNull { it.type == "theme" }?.path
        val colors = colorsFor(themePath, master)
        val ctx = SlideCtx(layout, master, rels, colors)
        val background = background(slide, colors) ?: background(layout, colors) ?: background(master, colors) ?: "FFFFFF"
        val shapes = ArrayList<PptxShape>()
        val tree = slide.kid(P, "cSld")?.kid(P, "spTree")
        if (tree != null) readTree(tree, ctx, { it }, shapes)
        return PptxSlide(shapes, background, notesOf(rels))
    }

    private fun background(root: Element?, colors: ThemeColors): String? {
        val bg = root?.kid(P, "cSld")?.kid(P, "bg") ?: return null
        return colors.colorOf(bg.kid(P, "bgPr")?.kid(A, "solidFill")) ?: colors.colorOf(bg.kid(P, "bgRef"))
    }

    private fun notesOf(rels: Map<String, RelationshipInfo>): String {
        val path = rels.values.firstOrNull { it.type == "notesSlide" }?.path ?: return ""
        val tree = root(path)?.kid(P, "cSld")?.kid(P, "spTree") ?: return ""
        val out = ArrayList<String>()
        for (sp in tree.kids(P, "sp")) {
            if (sp.kid(P, "nvSpPr")?.kid(P, "nvPr")?.kid(P, "ph")?.attr("type") != "body") continue
            for (p in sp.kid(P, "txBody")?.kids(A, "p").orEmpty()) {
                out += p.kids().filter { it.namespaceURI == A && (it.localName == "r" || it.localName == "fld") }
                    .joinToString("") { it.kid(A, "t")?.textContent.orEmpty() }
            }
        }
        return out.joinToString("\n").trim()
    }

    // ---- shapes ----

    private fun readTree(tree: Element, ctx: SlideCtx, map: (EmuBox) -> EmuBox, out: MutableList<PptxShape>) {
        for (e in tree.kids()) {
            if (e.namespaceURI != P) continue
            if (out.size >= limits.maxShapesPerSlide) return
            when (e.localName) {
                "sp" -> readSp(e, ctx, map)?.let { out += it }
                "pic" -> readPic(e, ctx, map)?.let { out += it }
                "graphicFrame" -> readFrame(e, ctx, map)?.let { out += it }
                "grpSp" -> readGroup(e, ctx, map, out)
            }
        }
    }

    private fun xfrmBox(xfrm: Element?): EmuBox? {
        val off = xfrm?.kid(A, "off") ?: return null
        val ext = xfrm.kid(A, "ext") ?: return null
        val w = ext.attr("cx")?.toLongOrNull() ?: return null
        val h = ext.attr("cy")?.toLongOrNull() ?: return null
        return EmuBox(off.attr("x")?.toLongOrNull() ?: 0L, off.attr("y")?.toLongOrNull() ?: 0L, w.coerceAtLeast(0), h.coerceAtLeast(0))
    }

    private fun readGroup(grp: Element, ctx: SlideCtx, parent: (EmuBox) -> EmuBox, out: MutableList<PptxShape>) {
        val xfrm = grp.kid(P, "grpSpPr")?.kid(A, "xfrm")
        val box = xfrmBox(xfrm)
        val childOff = xfrm?.kid(A, "chOff")
        val childExt = xfrm?.kid(A, "chExt")
        val cw = childExt?.attr("cx")?.toLongOrNull()
        val ch = childExt?.attr("cy")?.toLongOrNull()
        val map: (EmuBox) -> EmuBox = if (box == null || cw == null || ch == null || cw <= 0 || ch <= 0) {
            parent
        } else {
            val cx = childOff?.attr("x")?.toLongOrNull() ?: 0L
            val cy = childOff?.attr("y")?.toLongOrNull() ?: 0L
            val sx = box.w.toDouble() / cw
            val sy = box.h.toDouble() / ch
            { b -> parent(EmuBox(((b.x - cx) * sx).toLong() + box.x, ((b.y - cy) * sy).toLong() + box.y, (b.w * sx).toLong(), (b.h * sy).toLong())) }
        }
        readTree(grp, ctx, map, out)
    }

    private fun readPic(pic: Element, ctx: SlideCtx, map: (EmuBox) -> EmuBox): PptxShape? {
        val box = xfrmBox(pic.kid(P, "spPr")?.kid(A, "xfrm"))?.let(map) ?: return null
        val rel = pic.kid(P, "blipFill")?.kid(A, "blip")?.relAttr("embed")?.let { ctx.rels[it] }
        val bytes = rel?.path?.let { parts.media[it] }
        return if (rel != null && bytes != null) PptxPicture(box, rel.path, bytes) else PptxNotShown(box, "Picture not shown")
    }

    private fun readFrame(frame: Element, ctx: SlideCtx, map: (EmuBox) -> EmuBox): PptxShape? {
        val box = xfrmBox(frame.kid(P, "xfrm"))?.let(map) ?: return null
        val data = frame.kid(A, "graphic")?.kid(A, "graphicData")
        val tbl = data?.kid(A, "tbl")
        if (tbl == null) {
            val uri = data?.attr("uri").orEmpty()
            val what = when {
                "chart" in uri -> "Chart"
                "diagram" in uri -> "SmartArt"
                else -> "Object"
            }
            return PptxNotShown(box, "$what not shown")
        }
        val widths = tbl.kid(A, "tblGrid")?.kids(A, "gridCol")?.map { it.attr("w")?.toLongOrNull() ?: 0L } ?: emptyList()
        val rows = tbl.kids(A, "tr").map { tr ->
            val cells = tr.kids(A, "tc").map { tc ->
                val paragraphs = readParagraphs(tc.kid(A, "txBody"), listOf(null, defaultText), null, 1f, ctx.colors)
                PptxTableCell(paragraphs, ctx.colors.colorOf(tc.kid(A, "tcPr")?.kid(A, "solidFill")))
            }
            PptxTableRow(tr.attr("h")?.toLongOrNull() ?: 0L, cells)
        }
        return PptxTable(box, widths, rows)
    }

    // ---- placeholders ----

    private fun phKey(type: String?): String = when (type) {
        null, "body", "obj", "subTitle" -> "body"
        "title", "ctrTitle" -> "title"
        else -> type
    }

    /** The placeholder in a layout or master that a slide's placeholder takes its position and text style from. */
    private fun findPh(root: Element?, type: String?, idx: String?, useIdx: Boolean): Element? {
        val tree = root?.kid(P, "cSld")?.kid(P, "spTree") ?: return null
        val phs = tree.kids(P, "sp").mapNotNull { sp ->
            sp.kid(P, "nvSpPr")?.kid(P, "nvPr")?.kid(P, "ph")?.let { sp to it }
        }
        if (useIdx && idx != null) phs.firstOrNull { it.second.attr("idx") == idx }?.let { return it.first }
        return phs.firstOrNull { phKey(it.second.attr("type")) == phKey(type) }?.first
    }

    private fun masterStyle(master: Element?, type: String?): Element? {
        val styles = master?.kid(P, "txStyles") ?: return null
        return when (phKey(type)) {
            "title" -> styles.kid(P, "titleStyle")
            "body" -> styles.kid(P, "bodyStyle")
            else -> styles.kid(P, "otherStyle")
        }
    }

    private fun readSp(sp: Element, ctx: SlideCtx, map: (EmuBox) -> EmuBox): PptxShape? {
        val ph = sp.kid(P, "nvSpPr")?.kid(P, "nvPr")?.kid(P, "ph")
        val phType = ph?.attr("type")
        if (phType == "dt" || phType == "ftr" || phType == "sldNum") return null
        val phIdx = ph?.attr("idx")
        val spPr = sp.kid(P, "spPr")
        val layoutPh = if (ph != null) findPh(ctx.layout, phType, phIdx, useIdx = true) else null
        val masterPh = if (ph != null) findPh(ctx.master, phType, phIdx, useIdx = false) else null
        val box = (
            xfrmBox(spPr?.kid(A, "xfrm"))
                ?: xfrmBox(layoutPh?.kid(P, "spPr")?.kid(A, "xfrm"))
                ?: xfrmBox(masterPh?.kid(P, "spPr")?.kid(A, "xfrm"))
            )?.let(map) ?: return null

        val tx = sp.kid(P, "txBody")
        val bodyPr = tx?.kid(A, "bodyPr")
        val fontScale = (bodyPr?.kid(A, "normAutofit")?.attr("fontScale")?.toIntOrNull() ?: 100000) / 100000f
        val sources = listOf(
            tx?.kid(A, "lstStyle"),
            layoutPh?.kid(P, "txBody")?.kid(A, "lstStyle"),
            masterPh?.kid(P, "txBody")?.kid(A, "lstStyle"),
            if (ph != null) masterStyle(ctx.master, phType) else null,
            defaultText,
        )
        val style = sp.kid(P, "style")
        // An autoshape's style gives its text a colour (usually white, to show on the shape's fill).
        val styleTextColor = if (ph == null) ctx.colors.colorOf(style?.kid(A, "fontRef")) else null
        val paragraphs = readParagraphs(tx, sources, styleTextColor, fontScale, ctx.colors)
        val fill = when {
            spPr?.kid(A, "noFill") != null -> null
            spPr?.kid(A, "solidFill") != null -> ctx.colors.colorOf(spPr.kid(A, "solidFill"))
            ph == null && style?.kid(A, "fillRef")?.attr("idx")?.toIntOrNull()?.let { it > 0 } == true -> ctx.colors.colorOf(style.kid(A, "fillRef"))
            else -> null
        }
        val ln = spPr?.kid(A, "ln")
        val line = when {
            ln?.kid(A, "noFill") != null -> null
            ln?.kid(A, "solidFill") != null -> ctx.colors.colorOf(ln.kid(A, "solidFill"))
            ph == null && style?.kid(A, "lnRef")?.attr("idx")?.toIntOrNull()?.let { it > 0 } == true -> ctx.colors.colorOf(style.kid(A, "lnRef"))
            else -> null
        }
        val hasText = paragraphs.any { it.text.isNotBlank() }
        if (!hasText && fill == null && line == null) return null
        val geometry = when (spPr?.kid(A, "prstGeom")?.attr("prst")) {
            "ellipse" -> PptxGeometry.Ellipse
            "roundRect" -> PptxGeometry.RoundRect
            else -> PptxGeometry.Rect
        }
        val anchor = listOf(bodyPr, layoutPh?.kid(P, "txBody")?.kid(A, "bodyPr"), masterPh?.kid(P, "txBody")?.kid(A, "bodyPr"))
            .firstNotNullOfOrNull { it?.attr("anchor") }
        return PptxText(
            box, paragraphs, fill, line, geometry,
            when (anchor) {
                "ctr" -> PptxAnchor.Middle
                "b" -> PptxAnchor.Bottom
                else -> PptxAnchor.Top
            },
        )
    }

    // ---- text ----

    /** [sources] are lstStyle-like elements (holding lvl1pPr .. lvl9pPr), most specific first. */
    private fun readParagraphs(
        tx: Element?,
        sources: List<Element?>,
        defaultColor: String?,
        scale: Float,
        colors: ThemeColors,
    ): List<PptxParagraph> {
        if (tx == null) return emptyList()
        val counters = IntArray(9)
        val out = ArrayList<PptxParagraph>()
        for (p in tx.kids(A, "p")) {
            val pPr = p.kid(A, "pPr")
            val lvl = (pPr?.attr("lvl")?.toIntOrNull() ?: 0).coerceIn(0, 8)
            val lvlProps = sources.mapNotNull { it?.kid(A, "lvl${lvl + 1}pPr") }
            val pSources = listOfNotNull(pPr) + lvlProps
            val align = when (pSources.firstNotNullOfOrNull { it.attr("algn") }) {
                "ctr" -> PptxAlign.Center
                "r" -> PptxAlign.End
                "just", "dist" -> PptxAlign.Justify
                else -> PptxAlign.Start
            }
            var bullet: String? = null
            var numbered = false
            for (src in pSources) {
                if (src.kid(A, "buNone") != null) break
                val ch = src.kid(A, "buChar")
                val auto = src.kid(A, "buAutoNum")
                if (ch != null) {
                    bullet = ch.attr("char") ?: "•"
                    break
                }
                if (auto != null) {
                    counters[lvl]++
                    for (i in lvl + 1..8) counters[i] = 0
                    bullet = autoLabel(auto.attr("type"), counters[lvl], lvl)
                    numbered = true
                    break
                }
            }
            if (!numbered) for (i in lvl..8) counters[i] = 0
            val rSourcesBase = lvlProps.mapNotNull { it.kid(A, "defRPr") }
            val runs = ArrayList<PptxRun>()
            for (child in p.kids()) {
                if (child.namespaceURI != A) continue
                when (child.localName) {
                    "r", "fld" -> runs += run(child.kid(A, "t")?.textContent.orEmpty(), listOfNotNull(child.kid(A, "rPr")) + rSourcesBase, defaultColor, scale, colors)
                    "br" -> runs += run("\n", listOfNotNull(child.kid(A, "rPr")) + rSourcesBase, defaultColor, scale, colors)
                }
            }
            out += PptxParagraph(mergeRuns(runs.filter { it.text.isNotEmpty() }), align, lvl, bullet)
        }
        return out
    }

    private fun autoLabel(type: String?, n: Int, level: Int): String = when (type) {
        "arabicParenR" -> "$n)"
        "alphaLcPeriod" -> NumberLabels.label("lowerLetter", n, level)
        "alphaUcPeriod" -> NumberLabels.label("upperLetter", n, level)
        "alphaLcParenR" -> NumberLabels.letters(n, false) + ")"
        "romanLcPeriod" -> NumberLabels.label("lowerRoman", n, level)
        "romanUcPeriod" -> NumberLabels.label("upperRoman", n, level)
        else -> "$n."
    }

    private fun run(text: String, rSources: List<Element>, defaultColor: String?, scale: Float, colors: ThemeColors): PptxRun {
        fun flag(name: String): Boolean? = rSources.firstNotNullOfOrNull { it.attr(name) }?.let { it == "1" || it == "true" }
        val size = (rSources.firstNotNullOfOrNull { it.attr("sz")?.toIntOrNull() } ?: 1800).coerceIn(100, 20000) / 100f * scale
        val underline = rSources.firstNotNullOfOrNull { it.attr("u") }?.let { it != "none" } ?: false
        val color = rSources.firstNotNullOfOrNull { colors.colorOf(it.kid(A, "solidFill")) } ?: defaultColor ?: colors.text1()
        return PptxRun(text, flag("b") ?: false, flag("i") ?: false, underline, size, color)
    }

    private fun mergeRuns(runs: List<PptxRun>): List<PptxRun> {
        val out = ArrayList<PptxRun>()
        for (r in runs) {
            val last = out.lastOrNull()
            if (last != null && last.copy(text = "") == r.copy(text = "")) out[out.size - 1] = last.copy(text = last.text + r.text) else out += r
        }
        return out
    }
}
