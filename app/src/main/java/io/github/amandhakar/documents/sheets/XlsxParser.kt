package io.github.amandhakar.documents.sheets

import io.github.amandhakar.documents.ooxml.OoxmlException
import io.github.amandhakar.documents.ooxml.OoxmlLimits
import io.github.amandhakar.documents.ooxml.OoxmlZip
import io.github.amandhakar.documents.ooxml.attr
import io.github.amandhakar.documents.ooxml.kids
import io.github.amandhakar.documents.ooxml.parseRelationships
import io.github.amandhakar.documents.ooxml.parseXml
import io.github.amandhakar.documents.ooxml.relAttr
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

/** Size limits for a workbook, on top of the zip limits. */
data class SheetLimits(
    /** Cells read in the whole workbook; the rest of a bigger file is left out (the screen says so). */
    val maxCells: Int = 400_000,
    /** Columns shown: a stray cell far to the right doesn't make every row thousands of cells wide. */
    val maxColumns: Int = 256,
    val maxSharedStrings: Int = 1_000_000,
)

/**
 * Reads an Excel .xlsx file into [Workbook]: every worksheet in order (hidden ones too, marked), cell values as Excel
 * last saved them (formulas are not recalculated), number and date formats, merged cells, column widths and table
 * headers. Strict-format files (ISO 29500 Strict) are read too, by element names. Free of Android calls so it can be
 * unit tested.
 */
object XlsxParser {
    fun parse(input: InputStream, limits: OoxmlLimits = OoxmlLimits(), sheetLimits: SheetLimits = SheetLimits()): Workbook {
        val parts = OoxmlZip.read(
            input,
            limits,
            wantXml = { it.endsWith(".xml") || it.endsWith(".rels") },
            wantMedia = { false },
        )
        val xml = parts.xml
        val rootRels = xml["_rels/.rels"]?.let { parseRelationships(it, "") }
        val workbookPath = rootRels?.values?.firstOrNull { it.type == "officeDocument" }?.path
            ?: "xl/workbook.xml".takeIf { xml.containsKey(it) }
            ?: throw OoxmlException("This isn't an Excel workbook (.xlsx).")
        val workbookXml = xml[workbookPath] ?: throw OoxmlException("This isn't an Excel workbook (.xlsx).")
        val folder = OoxmlZip.folderOf(workbookPath)
        val rels = xml[OoxmlZip.relsPathOf(workbookPath)]?.let { parseRelationships(it, folder) } ?: emptyMap()

        val workbook = parseXml(workbookXml).documentElement
        if (workbook.localName != "workbook") throw OoxmlException("This isn't an Excel workbook (.xlsx).")
        val date1904 = workbook.kids().firstOrNull { it.localName == "workbookPr" }?.attr("date1904").let { it == "1" || it == "true" }
        val sharedStrings = rels.values.firstOrNull { it.type == "sharedStrings" }?.path?.let { xml[it] }
            ?.let { readSharedStrings(it, sheetLimits.maxSharedStrings) } ?: emptyList()
        val styleFormats = rels.values.firstOrNull { it.type == "styles" }?.path?.let { xml[it] }?.let { readStyles(it) } ?: emptyList()
        val calcChain = rels.values.firstOrNull { it.type == "calcChain" }?.path

        val sheets = ArrayList<SheetData>()
        var skipped = 0
        var budget = sheetLimits.maxCells
        val sheetList = workbook.kids().firstOrNull { it.localName == "sheets" }?.kids()?.filter { it.localName == "sheet" } ?: emptyList()
        for (entry in sheetList) {
            val name = entry.attr("name") ?: "Sheet ${sheets.size + 1}"
            val state = entry.attr("state")
            if (state == "veryHidden") continue
            val rid = entry.relAttr("id") ?: entry.attributes.let { a ->
                // Strict files use another namespace for r:id.
                (0 until a.length).map { a.item(it) }.firstOrNull { it.localName == "id" && it.namespaceURI != null }?.nodeValue
            }
            val rel = rid?.let { rels[it] }
            if (rel == null || rel.type != "worksheet") {
                skipped++
                continue
            }
            val sheetXml = xml[rel.path]
            if (sheetXml == null) {
                skipped++
                continue
            }
            val sheetRels = xml[OoxmlZip.relsPathOf(rel.path)]?.let { parseRelationships(it, OoxmlZip.folderOf(rel.path)) } ?: emptyMap()
            val tableHeaders = sheetRels.values.filter { it.type == "table" }.mapNotNull { xml[it.path]?.let(::tableHeader) }
            val hasPivot = sheetRels.values.any { it.type == "pivotTable" }
            val reader = SheetReader(sharedStrings, styleFormats, date1904, budget)
            sax(sheetXml, reader)
            budget -= reader.cells.size
            val cut = reader.truncated || reader.maxCol + 1 > sheetLimits.maxColumns
            val readOnly = when {
                reader.protected -> "This sheet is protected in Excel, so it can't be edited here."
                hasPivot -> "This sheet has a pivot table, so it can't be edited here."
                cut -> "This sheet is bigger than the app reads, so it can't be edited here."
                else -> null
            }
            val maxCol = (reader.maxCol + 1).coerceAtMost(sheetLimits.maxColumns)
            val cells = if (reader.maxCol + 1 > sheetLimits.maxColumns) reader.cells.filterKeys { CellRefs.colOf(it) < sheetLimits.maxColumns } else reader.cells
            sheets += SheetData(
                name = name,
                path = rel.path,
                cells = cells,
                rowCount = reader.maxRow + 1,
                columnCount = maxCol,
                columnWidths = reader.widths,
                merges = reader.merges,
                tableHeaders = tableHeaders,
                hidden = state == "hidden",
                truncated = cut,
                readOnly = readOnly,
            )
        }
        if (sheets.isEmpty() && skipped == 0) throw OoxmlException("This workbook has no sheets to show.")
        return Workbook(sheets, styleFormats, date1904, workbookPath, calcChain, skipped)
    }

    // ---- parts ----

    private fun sax(xml: String, handler: DefaultHandler) {
        if (xml.contains("<!DOCTYPE") || xml.contains("<!ENTITY")) throw OoxmlException("This file contains something unsafe and wasn't opened.")
        val factory = SAXParserFactory.newInstance()
        factory.isNamespaceAware = true
        try {
            val reader = factory.newSAXParser().xmlReader
            reader.setEntityResolver { _, _ -> InputSource(StringReader("")) }
            reader.contentHandler = handler
            reader.parse(InputSource(StringReader(xml)))
        } catch (e: StopReading) {
            // The cell limit was reached: what was read so far is kept.
        } catch (e: SAXException) {
            throw OoxmlException("The workbook couldn't be read. The file may be damaged.")
        }
    }

    private class StopReading : SAXException("limit")

    private fun readSharedStrings(xml: String, max: Int): List<String> {
        val out = ArrayList<String>()
        sax(xml, object : DefaultHandler() {
            val sb = StringBuilder()
            var inSi = false
            var inT = false
            var phonetic = 0

            override fun startElement(uri: String?, localName: String, qName: String?, attributes: Attributes?) {
                when (localName) {
                    "si" -> { inSi = true; sb.clear() }
                    "t" -> if (inSi && phonetic == 0) inT = true
                    "rPh" -> phonetic++
                }
            }

            override fun endElement(uri: String?, localName: String, qName: String?) {
                when (localName) {
                    "si" -> {
                        inSi = false
                        if (out.size >= max) throw StopReading()
                        out += sb.toString()
                    }
                    "t" -> inT = false
                    "rPh" -> phonetic--
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inT) sb.append(ch, start, length)
            }
        })
        return out
    }

    /** The number format of each cell style (the cellXfs list). */
    private fun readStyles(xml: String): List<NumberFormat> {
        val root = parseXml(xml).documentElement
        val custom = HashMap<Int, String>()
        root.kids().firstOrNull { it.localName == "numFmts" }?.kids()?.forEach { f ->
            val id = f.attr("numFmtId")?.toIntOrNull()
            val code = f.getAttribute("formatCode")
            if (id != null) custom[id] = code
        }
        val xfs = root.kids().firstOrNull { it.localName == "cellXfs" }?.kids()?.filter { it.localName == "xf" } ?: return emptyList()
        return xfs.map { xf ->
            val id = xf.attr("numFmtId")?.toIntOrNull() ?: 0
            NumberFormat(id, custom[id])
        }
    }

    private fun tableHeader(xml: String): CellRange? {
        val table = runCatching { parseXml(xml).documentElement }.getOrNull() ?: return null
        if (table.attr("headerRowCount") == "0") return null
        val range = table.attr("ref")?.let { CellRefs.parseRange(it) } ?: return null
        return CellRange(range.top, range.left, range.top, range.right)
    }

    /** Reads one worksheet's cells, column widths, merges and protection with SAX (sheets can be large). */
    private class SheetReader(
        val strings: List<String>,
        val formats: List<NumberFormat>,
        val date1904: Boolean,
        var budget: Int,
    ) : DefaultHandler() {
        val cells = HashMap<Long, SheetCell>()
        val widths = HashMap<Int, Float>()
        val merges = ArrayList<CellRange>()
        var maxRow = -1
        var maxCol = -1
        var protected = false
        var truncated = false

        private var row = -1
        private var col = -1
        private var type: String? = null
        private var style = 0
        private var formula: String? = null
        private var sharedMaster = false
        private var array = false
        private var value: StringBuilder? = null
        private var inline: StringBuilder? = null
        private var text: StringBuilder? = null
        private var inF = false
        private var inV = false
        private var inT = false
        private var phonetic = 0
        private val f = StringBuilder()

        override fun startElement(uri: String?, localName: String, qName: String?, a: Attributes) {
            when (localName) {
                "row" -> row = a.getValue("r")?.toIntOrNull()?.minus(1) ?: (row + 1)
                "c" -> {
                    if (budget <= 0) {
                        truncated = true
                        throw StopReading()
                    }
                    val ref = a.getValue("r")?.let { CellRefs.parse(it) }
                    if (ref != null) {
                        row = ref.first
                        col = ref.second
                    } else {
                        if (row < 0) row = 0
                        col++
                    }
                    type = a.getValue("t")
                    style = a.getValue("s")?.toIntOrNull() ?: 0
                    formula = null
                    sharedMaster = false
                    array = false
                    value = null
                    inline = null
                }
                "f" -> {
                    inF = true
                    f.clear()
                    val t = a.getValue("t")
                    sharedMaster = t == "shared" && a.getValue("ref") != null
                    array = t == "array" || t == "dataTable"
                }
                "v" -> { inV = true; value = StringBuilder() }
                "is" -> inline = StringBuilder()
                "t" -> if (inline != null && phonetic == 0) inT = true
                "rPh" -> phonetic++
                "col" -> {
                    val min = a.getValue("min")?.toIntOrNull()
                    val max = a.getValue("max")?.toIntOrNull()
                    val width = a.getValue("width")?.toFloatOrNull()
                    if (min != null && max != null && width != null && min >= 1) {
                        for (c in (min - 1) until minOf(max, 256)) widths[c] = if (a.getValue("hidden") == "1" || a.getValue("hidden") == "true") 0f else width
                    }
                }
                "mergeCell" -> a.getValue("ref")?.let { CellRefs.parseRange(it) }?.let { merges += it }
                "sheetProtection" -> a.getValue("sheet").let { if (it == "1" || it == "true") protected = true }
            }
        }

        override fun endElement(uri: String?, localName: String, qName: String?) {
            when (localName) {
                "f" -> { inF = false; formula = f.toString() }
                "v" -> inV = false
                "t" -> inT = false
                "rPh" -> phonetic--
                "c" -> finishCell()
                "row" -> col = -1
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            when {
                inF -> f.append(ch, start, length)
                inV -> value?.append(ch, start, length)
                inT -> inline?.append(ch, start, length)
            }
        }

        private fun finishCell() {
            if (row < 0 || col < 0) return
            val v = value?.toString()
            val fm = formula?.takeIf { it.isNotEmpty() || sharedMaster || array }
            val cell: SheetCell? = when (type) {
                "s" -> v?.trim()?.toIntOrNull()?.let { strings.getOrNull(it) }?.let { SheetCell(it, CellKind.Text, it, fm, style) }
                "inlineStr" -> (inline?.toString() ?: v)?.let { SheetCell(it, CellKind.Text, it, fm, style) }
                "str" -> SheetCell(v.orEmpty(), CellKind.Text, v, fm, style)
                "b" -> v?.let { val on = it.trim() == "1" || it.trim().equals("true", true); SheetCell(if (on) "TRUE" else "FALSE", CellKind.Bool, if (on) "TRUE" else "FALSE", fm, style) }
                "e" -> SheetCell(v.orEmpty(), CellKind.Error, v, fm, style)
                "d" -> v?.let { SheetCell(it.replace('T', ' ').removeSuffix("Z"), CellKind.Date, it, fm, style) }
                else -> if (v != null && v.isNotBlank()) {
                    val format = formats.getOrNull(style) ?: NumberFormat.GENERAL
                    val kind = if (XlsxNumbers.isDate(format)) CellKind.Date else CellKind.Number
                    SheetCell(XlsxNumbers.format(v, format, date1904), kind, v.trim(), fm, style)
                } else {
                    null
                }
            } ?: if (fm != null || style != 0) SheetCell("", CellKind.Empty, null, fm, style) else null
            if (cell != null) {
                cells[CellRefs.key(row, col)] = cell.copy(sharedFormulaMaster = sharedMaster, arrayFormula = array)
                budget--
                if (cell.kind != CellKind.Empty || cell.formula != null) {
                    if (row > maxRow) maxRow = row
                    if (col > maxCol) maxCol = col
                }
            }
        }
    }
}
