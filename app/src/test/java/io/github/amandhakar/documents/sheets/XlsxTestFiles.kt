package io.github.amandhakar.documents.sheets

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Small hand-made .xlsx files for the tests. */
object XlsxTestFiles {
    const val NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val PR = "http://schemas.openxmlformats.org/package/2006/relationships"

    fun zip(parts: Map<String, Any>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, content) in parts) {
                z.putNextEntry(ZipEntry(name))
                z.write(if (content is ByteArray) content else (content as String).toByteArray())
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    fun entries(bytes: ByteArray): Map<String, ByteArray> {
        val m = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z -> while (true) { val e = z.nextEntry ?: break; m[e.name] = z.readBytes() } }
        return m
    }

    fun sheet(sheetData: String, extra: String = "", before: String = "") =
        "<worksheet xmlns=\"$NS\" xmlns:r=\"$R\">$before<sheetData>$sheetData</sheetData>$extra</worksheet>"

    /**
     * A workbook with [sheets] (name to sheet XML), shared [strings], and styles: 0 General, 1 "0.00", 2 a date, 3 "0%",
     * 4 "#,##0.00", 5 a custom "dd/mm/yyyy hh:mm".
     */
    fun workbook(
        sheets: List<Pair<String, String>>,
        strings: List<String> = emptyList(),
        extraParts: Map<String, Any> = emptyMap(),
        sheetRels: Map<Int, String> = emptyMap(),
        calcChain: Boolean = false,
        hidden: Set<Int> = emptySet(),
        workbookPr: String = "",
    ): ByteArray {
        val parts = LinkedHashMap<String, Any>()
        parts["[Content_Types].xml"] = "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
            (if (calcChain) "<Override PartName=\"/xl/calcChain.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.calcChain+xml\"/>" else "") +
            "</Types>"
        parts["_rels/.rels"] = "<Relationships xmlns=\"$PR\"><Relationship Id=\"rId1\" Type=\"$R/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>"
        parts["xl/workbook.xml"] = "<workbook xmlns=\"$NS\" xmlns:r=\"$R\">$workbookPr<sheets>" +
            sheets.indices.joinToString("") { i ->
                "<sheet name=\"${sheets[i].first}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"" + (if (i in hidden) " state=\"hidden\"" else "") + "/>"
            } + "</sheets></workbook>"
        parts["xl/_rels/workbook.xml.rels"] = "<Relationships xmlns=\"$PR\">" +
            sheets.indices.joinToString("") { "<Relationship Id=\"rId${it + 1}\" Type=\"$R/worksheet\" Target=\"worksheets/sheet${it + 1}.xml\"/>" } +
            "<Relationship Id=\"rS\" Type=\"$R/sharedStrings\" Target=\"sharedStrings.xml\"/>" +
            "<Relationship Id=\"rT\" Type=\"$R/styles\" Target=\"styles.xml\"/>" +
            (if (calcChain) "<Relationship Id=\"rC\" Type=\"$R/calcChain\" Target=\"calcChain.xml\"/>" else "") +
            "</Relationships>"
        sheets.forEachIndexed { i, s ->
            parts["xl/worksheets/sheet${i + 1}.xml"] = s.second
            sheetRels[i]?.let { parts["xl/worksheets/_rels/sheet${i + 1}.xml.rels"] = it }
        }
        parts["xl/sharedStrings.xml"] = "<sst xmlns=\"$NS\" count=\"${strings.size}\">" + strings.joinToString("") { "<si><t>$it</t></si>" } + "</sst>"
        parts["xl/styles.xml"] = "<styleSheet xmlns=\"$NS\"><numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"dd/mm/yyyy hh:mm\"/></numFmts>" +
            "<cellXfs count=\"6\"><xf numFmtId=\"0\"/><xf numFmtId=\"2\"/><xf numFmtId=\"14\"/><xf numFmtId=\"9\"/><xf numFmtId=\"4\"/><xf numFmtId=\"164\"/></cellXfs></styleSheet>"
        if (calcChain) parts["xl/calcChain.xml"] = "<calcChain xmlns=\"$NS\"><c r=\"C1\" i=\"1\"/></calcChain>"
        parts["xl/media/image1.png"] = byteArrayOf(1, 2, 3, 4)
        parts.putAll(extraParts)
        return zip(parts)
    }

    fun tableRels(target: String = "../tables/table1.xml", type: String = "table") =
        "<Relationships xmlns=\"$PR\"><Relationship Id=\"rId1\" Type=\"$R/$type\" Target=\"$target\"/></Relationships>"
}
