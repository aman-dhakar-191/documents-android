package io.github.amandhakar.documents.sheets

import io.github.amandhakar.documents.officeedit.OfficeEditException
import io.github.amandhakar.documents.ooxml.OoxmlException
import io.github.amandhakar.documents.ooxml.OoxmlLimits
import io.github.amandhakar.documents.ooxml.XmlText
import io.github.amandhakar.documents.ooxml.parseXml
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Writes [XlsxEdits] into a real .xlsx. Only the sheets with changed cells are rewritten (just those cells: the cell's
 * look is kept, its old value or formula is replaced), plus two small changes so Excel stays correct: the workbook is
 * marked to recalculate every formula when it is next opened (so totals that use a changed cell are right), and the
 * calculation chain (Excel's cache of formula order, which it rebuilds) is left out. Every other part (styles, charts,
 * pictures, other sheets) is copied through byte for byte. Text is written into the cell itself (an "inline string"),
 * which Excel, LibreOffice and Google Sheets all read. Free of Android calls so it can be unit tested.
 */
object XlsxEditor {
    private const val CONTENT_TYPES = "[Content_Types].xml"

    fun apply(input: InputStream, output: OutputStream, workbook: Workbook, edits: XlsxEdits, limits: OoxmlLimits = OoxmlLimits()) {
        val bySheet = edits.resolved().entries.groupBy({ it.key.first }, { it.key.second to it.value }).mapValues { it.value.toMap() }
        if (bySheet.isEmpty()) throw OfficeEditException("There are no changes to save.")
        val sheets = workbook.sheets.associateBy { it.path }
        for ((path, cells) in bySheet) {
            val sheet = sheets[path] ?: throw OfficeEditException("A sheet you changed can't be found in the file any more.")
            for (key in cells.keys) sheet.editBlock(CellRefs.rowOf(key), CellRefs.colOf(key))?.let { throw OfficeEditException(it) }
        }
        val workbookPath = workbook.workbookPath ?: throw OfficeEditException("This file can't be edited.")
        val calcChain = workbook.calcChainPath
        val workbookRels = workbookPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" } + "_rels/" + workbookPath.substringAfterLast('/') + ".rels"
        val done = HashSet<String>()
        var entries = 0
        var inflated = 0L
        try {
            ZipInputStream(input).use { zin ->
                ZipOutputStream(output).use { zout ->
                    while (true) {
                        val entry = zin.nextEntry ?: break
                        if (++entries > limits.maxEntries) throw OfficeEditException("This file has too many parts to edit.")
                        val name = entry.name
                        if (name == calcChain) continue
                        val rewrite: ((ByteArray) -> ByteArray)? = when {
                            bySheet.containsKey(name) -> { bytes -> sheetXml(bytes, bySheet.getValue(name), workbook, sheets.getValue(name)) }
                            name == workbookPath -> ::workbookXml
                            calcChain != null && name == workbookRels -> { bytes -> withoutRelationship(bytes, calcChain) }
                            calcChain != null && name == CONTENT_TYPES -> { bytes -> withoutOverride(bytes, "/$calcChain") }
                            else -> null
                        }
                        zout.putNextEntry(ZipEntry(name).also { if (entry.time != -1L) it.time = entry.time })
                        if (rewrite != null) {
                            val bytes = readPart(zin, limits)
                            inflated += bytes.size
                            zout.write(rewrite(bytes))
                            if (bySheet.containsKey(name)) done += name
                        } else {
                            val chunk = ByteArray(16 * 1024)
                            while (true) {
                                val n = zin.read(chunk)
                                if (n < 0) break
                                inflated += n
                                if (inflated > limits.maxInflatedBytes) throw OfficeEditException("This file is too big to edit on this phone.")
                                zout.write(chunk, 0, n)
                            }
                        }
                        zout.closeEntry()
                    }
                }
            }
        } catch (e: ZipException) {
            throw OfficeEditException("This isn't a valid .xlsx file, so it can't be edited.")
        } catch (e: OoxmlException) {
            throw OfficeEditException(e.message ?: "This file couldn't be edited.")
        }
        if (done != bySheet.keys) throw OfficeEditException("A sheet you changed can't be found in the file any more.")
    }

    fun applyToBytes(original: ByteArray, workbook: Workbook, edits: XlsxEdits): ByteArray {
        val out = ByteArrayOutputStream()
        apply(ByteArrayInputStream(original), out, workbook, edits)
        return out.toByteArray()
    }

    /**
     * True when the file read back from [result] shows exactly what [expected] (the on-screen preview of the edits) shows,
     * in every sheet: the same sheets, and the same text and formula in every cell.
     */
    fun matches(result: InputStream, expected: Workbook): Boolean = try {
        val read = XlsxParser.parse(result)
        read.sheets.size == expected.sheets.size && read.sheets.indices.all { i ->
            val a = read.sheets[i]
            val b = expected.sheets[i]
            a.name == b.name && sameCells(a.cells, b.cells)
        }
    } catch (e: IOException) {
        false
    }

    private fun sameCells(a: Map<Long, SheetCell>, b: Map<Long, SheetCell>): Boolean {
        fun shown(m: Map<Long, SheetCell>) = m.filterValues { it.kind != CellKind.Empty || it.formula != null }.mapValues { it.value.text to it.value.formula }
        return shown(a) == shown(b)
    }

    private fun readPart(zin: ZipInputStream, limits: OoxmlLimits): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        while (true) {
            val n = zin.read(chunk)
            if (n < 0) break
            if (buffer.size() + n > limits.maxXmlPartBytes) throw OfficeEditException("A sheet is too big to edit.")
            buffer.write(chunk, 0, n)
        }
        return buffer.toByteArray()
    }

    // ---- the XML ----

    private fun read(bytes: ByteArray): Document = parseXml(String(bytes, Charsets.UTF_8).removePrefix("\uFEFF"))

    private fun write(doc: Document): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\r\n".toByteArray(Charsets.UTF_8))
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        transformer.setOutputProperty(OutputKeys.INDENT, "no")
        transformer.transform(DOMSource(doc), StreamResult(out))
        return out.toByteArray()
    }

    private fun Element.children(name: String): List<Element> {
        val out = ArrayList<Element>()
        var n: Node? = firstChild
        while (n != null) {
            if (n is Element && n.localName == name) out += n
            n = n.nextSibling
        }
        return out
    }

    private fun Element.child(name: String): Element? = children(name).firstOrNull()

    private fun newElement(doc: Document, like: Element, name: String): Element {
        val prefix = like.prefix?.let { "$it:" } ?: ""
        return doc.createElementNS(like.namespaceURI, prefix + name)
    }

    private fun sheetXml(bytes: ByteArray, edits: Map<Long, String>, workbook: Workbook, sheet: SheetData): ByteArray {
        val doc = read(bytes)
        val root = doc.documentElement
        val sheetData = root.child("sheetData") ?: throw OfficeEditException("This sheet can't be edited (it has no cells part).")
        // Write every row and cell position out, so new cells never shift ones whose place was only implied.
        var lastRow = 0
        for (row in sheetData.children("row")) {
            val r = row.getAttribute("r").toIntOrNull() ?: (lastRow + 1)
            row.setAttribute("r", r.toString())
            lastRow = r
            var lastCol = -1
            for (c in row.children("c")) {
                val ref = c.getAttribute("r").takeIf { it.isNotEmpty() }?.let { CellRefs.parse(it) }
                val col = ref?.second ?: (lastCol + 1)
                c.setAttribute("r", CellRefs.name(r - 1, col))
                lastCol = col
            }
        }
        var maxRow = -1
        var maxCol = -1
        for ((key, text) in edits) {
            val row = CellRefs.rowOf(key)
            val col = CellRefs.colOf(key)
            val c = cellElement(doc, sheetData, row, col)
            val style = c.getAttribute("s").toIntOrNull() ?: 0
            setValue(doc, c, XlsxNumbers.interpret(text, workbook.formatOf(style), workbook.date1904))
            maxRow = maxOf(maxRow, row)
            maxCol = maxOf(maxCol, col)
        }
        // Keep the sheet's stated size covering the cells.
        root.child("dimension")?.let { dim ->
            val old = CellRefs.parseRange(dim.getAttribute("ref"))
            if (old != null && (maxRow > old.bottom || maxCol > old.right)) {
                dim.setAttribute("ref", CellRefs.rangeName(CellRange(old.top, old.left, maxOf(old.bottom, maxRow), maxOf(old.right, maxCol))))
            }
        }
        return write(doc)
    }

    /** The <c> element for a cell, made (in its place among its row's cells, and the row among the rows) if it isn't there. */
    private fun cellElement(doc: Document, sheetData: Element, row: Int, col: Int): Element {
        val rowNumber = row + 1
        val rows = sheetData.children("row")
        var rowEl = rows.firstOrNull { it.getAttribute("r").toIntOrNull() == rowNumber }
        if (rowEl == null) {
            rowEl = newElement(doc, sheetData, "row")
            rowEl.setAttribute("r", rowNumber.toString())
            val after = rows.firstOrNull { (it.getAttribute("r").toIntOrNull() ?: 0) > rowNumber }
            sheetData.insertBefore(rowEl, after)
        }
        val cells = rowEl.children("c")
        val name = CellRefs.name(row, col)
        cells.firstOrNull { it.getAttribute("r") == name }?.let { return it }
        val c = newElement(doc, rowEl, "c")
        c.setAttribute("r", name)
        val after = cells.firstOrNull { (CellRefs.parse(it.getAttribute("r"))?.second ?: -1) > col }
        rowEl.insertBefore(c, after ?: rowEl.child("extLst"))
        // A row's "spans" is only a hint for Excel; drop it rather than leave it wrong.
        rowEl.removeAttribute("spans")
        return c
    }

    private fun setValue(doc: Document, c: Element, input: CellInput) {
        for (name in listOf("f", "v", "is")) c.children(name).forEach { c.removeChild(it) }
        c.removeAttribute("t")
        c.removeAttribute("cm")
        c.removeAttribute("vm")
        val before = c.child("extLst")
        when (input) {
            CellInput.Empty -> Unit
            is CellInput.Number -> {
                val v = newElement(doc, c, "v")
                v.textContent = input.value
                c.insertBefore(v, before)
            }
            is CellInput.Bool -> {
                c.setAttribute("t", "b")
                val v = newElement(doc, c, "v")
                v.textContent = if (input.value) "1" else "0"
                c.insertBefore(v, before)
            }
            is CellInput.Text -> {
                c.setAttribute("t", "inlineStr")
                val isEl = newElement(doc, c, "is")
                val t = newElement(doc, c, "t")
                val clean = XmlText.clean(input.value)
                if (clean != clean.trim() || clean.contains('\n')) t.setAttributeNS("http://www.w3.org/XML/1998/namespace", "xml:space", "preserve")
                t.textContent = clean
                isEl.appendChild(t)
                c.insertBefore(isEl, before)
            }
        }
    }

    /** Asks Excel to recalculate every formula when the file is next opened, since cached results may now be out of date. */
    private fun workbookXml(bytes: ByteArray): ByteArray {
        val doc = read(bytes)
        val root = doc.documentElement
        var calcPr = root.child("calcPr")
        if (calcPr == null) {
            calcPr = newElement(doc, root, "calcPr")
            val after = listOf("oleSize", "customWorkbookViews", "pivotCaches", "smartTagPr", "smartTagTypes", "webPublishing",
                "fileRecoveryPr", "webPublishObjects", "extLst").firstNotNullOfOrNull { root.child(it) }
            root.insertBefore(calcPr, after)
        }
        calcPr.setAttribute("fullCalcOnLoad", "1")
        return write(doc)
    }

    private fun withoutRelationship(bytes: ByteArray, targetPath: String): ByteArray {
        val doc = read(bytes)
        val root = doc.documentElement
        val file = targetPath.substringAfterLast('/')
        root.children("Relationship").filter {
            it.getAttribute("Type").endsWith("/calcChain") || it.getAttribute("Target").substringAfterLast('/') == file && it.getAttribute("Type").endsWith("calcChain")
        }.forEach { root.removeChild(it) }
        return write(doc)
    }

    private fun withoutOverride(bytes: ByteArray, partName: String): ByteArray {
        val doc = read(bytes)
        val root = doc.documentElement
        root.children("Override").filter { it.getAttribute("PartName").equals(partName, ignoreCase = true) }.forEach { root.removeChild(it) }
        return write(doc)
    }
}
