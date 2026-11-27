package io.github.amandhakar.documents.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSyntaxTest {
    @Test fun knownEndings() {
        val cases = mapOf(
            "a.json" to TextSyntax.Json, "B.JSON" to TextSyntax.Json, "map.geojson" to TextSyntax.Json,
            "c.yaml" to TextSyntax.Yaml, "docker-compose.yml" to TextSyntax.Yaml,
            "pom.xml" to TextSyntax.Xml, "feed.rss" to TextSyntax.Xml,
            "data.csv" to TextSyntax.Csv, "data.tsv" to TextSyntax.Tsv,
            "Cargo.toml" to TextSyntax.Toml, "php.ini" to TextSyntax.Ini, "app.properties" to TextSyntax.Ini, "nginx.conf" to TextSyntax.Ini,
            "crash.log" to TextSyntax.Log, "notes.txt" to TextSyntax.Plain,
        )
        for ((name, syntax) in cases) assertEquals(name, syntax, TextSyntax.fromFileName(name))
    }

    @Test fun unknownOrLookalikeEndingsAreNull() {
        for (name in listOf(null, "", "json", "a.json.exe", "photo.jpg", "x.", "archive.yaml.zip", "script.js", "sheet.xlsx")) {
            assertNull(name, TextSyntax.fromFileName(name))
        }
    }

    @Test fun mimeTypes() {
        assertEquals(TextSyntax.Json, TextSyntax.fromMimeType("application/json; charset=utf-8"))
        assertEquals(TextSyntax.Yaml, TextSyntax.fromMimeType("application/x-yaml"))
        assertEquals(TextSyntax.Yaml, TextSyntax.fromMimeType("text/yaml"))
        assertEquals(TextSyntax.Xml, TextSyntax.fromMimeType("TEXT/XML"))
        assertEquals(TextSyntax.Csv, TextSyntax.fromMimeType("text/comma-separated-values"))
        assertEquals(TextSyntax.Tsv, TextSyntax.fromMimeType("text/tab-separated-values"))
        assertNull(TextSyntax.fromMimeType("text/plain")) // the name decides
        assertNull(TextSyntax.fromMimeType("application/octet-stream"))
        assertNull(TextSyntax.fromMimeType(null))
    }

    @Test fun theNameWinsOverTheMimeType() {
        assertEquals(TextSyntax.Yaml, TextSyntax.of("config.yml", "text/plain"))
        assertEquals(TextSyntax.Json, TextSyntax.of(null, "application/json"))
        assertEquals(TextSyntax.Plain, TextSyntax.of("README", "text/plain"))
    }

    @Test fun onlyCsvAndTsvAreTables() {
        assertTrue(TextSyntax.Csv.isTable)
        assertTrue(TextSyntax.Tsv.isTable)
        assertFalse(TextSyntax.Json.isTable)
        assertEquals('\t', TextSyntax.Tsv.delimiter)
        assertEquals(',', TextSyntax.Csv.delimiter)
    }

    @Test fun textKindsOpenAsTextAndExcelAsExcel() {
        assertEquals(DocumentFormat.Text, DocumentFormat.fromFileName("settings.json"))
        assertEquals(DocumentFormat.Text, DocumentFormat.fromMimeType("application/x-yaml"))
        assertEquals(DocumentFormat.Xlsx, DocumentFormat.fromFileName("Budget.XLSX"))
        assertEquals(DocumentFormat.Xlsx, DocumentFormat.fromMimeType(DocumentFormat.XLSX_MIME))
        assertEquals(DocumentFormat.XLSX_MIME, DocumentFormat.Xlsx.mimeType)
        assertNull(DocumentFormat.fromFileName("old.xls")) // old binary Excel isn't read
        assertNull(DocumentFormat.fromFileName("macro.xlsm"))
        assertNull(DocumentFormat.fromMimeType("application/vnd.ms-excel"))
    }
}
