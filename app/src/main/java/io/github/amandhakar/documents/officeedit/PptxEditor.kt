package io.github.amandhakar.documents.officeedit

import io.github.amandhakar.documents.ooxml.OoxmlException
import io.github.amandhakar.documents.ooxml.OoxmlLimits
import io.github.amandhakar.documents.ooxml.XmlText
import io.github.amandhakar.documents.ooxml.parseXml
import io.github.amandhakar.documents.slides.PptxDeck
import io.github.amandhakar.documents.slides.PptxParser
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Writes [PptxEdits] into a real .pptx: only the slide files that had text changed are rewritten (the text of the
 * paragraphs that were edited); every other part (layouts, masters, themes, pictures, notes, other slides) is copied
 * through as it was, and so is everything in a paragraph except its text. Where a paragraph had several differently
 * formatted pieces, the new text takes the look of the first one. Free of Android calls so it can be unit tested.
 */
object PptxEditor {
    private const val A = "http://schemas.openxmlformats.org/drawingml/2006/main"

    /** Copies [input] to [output] with the edits applied. */
    fun apply(input: InputStream, output: OutputStream, edits: PptxEdits, limits: OoxmlLimits = OoxmlLimits()) {
        val bySlide = edits.resolved().entries.groupBy({ it.key.first }, { it.key.second to it.value }).mapValues { it.value.toMap() }
        val done = HashSet<String>()
        var entries = 0
        var inflated = 0L
        try {
            ZipInputStream(input).use { zin ->
                ZipOutputStream(output).use { zout ->
                    while (true) {
                        val entry = zin.nextEntry ?: break
                        if (++entries > limits.maxEntries) throw OfficeEditException("This file has too many parts to edit.")
                        zout.putNextEntry(ZipEntry(entry.name).also { if (entry.time != -1L) it.time = entry.time })
                        val slideEdits = bySlide[entry.name]
                        if (slideEdits != null) {
                            val buffer = ByteArrayOutputStream()
                            val chunk = ByteArray(16 * 1024)
                            while (true) {
                                val n = zin.read(chunk)
                                if (n < 0) break
                                if (buffer.size() + n > limits.maxXmlPartBytes) throw OfficeEditException("A slide is too big to edit.")
                                buffer.write(chunk, 0, n)
                            }
                            inflated += buffer.size()
                            zout.write(changed(buffer.toByteArray(), slideEdits))
                            done += entry.name
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
        } catch (e: java.util.zip.ZipException) {
            throw OfficeEditException("This isn't a valid .pptx file, so it can't be edited.")
        } catch (e: OoxmlException) {
            throw OfficeEditException(e.message ?: "This file couldn't be edited.")
        }
        if (done != bySlide.keys) throw OfficeEditException("A slide you changed can't be found in the file any more.")
    }

    /** The edited file as bytes, for tests and small files. */
    fun applyToBytes(original: ByteArray, edits: PptxEdits): ByteArray {
        val out = ByteArrayOutputStream()
        apply(ByteArrayInputStream(original), out, edits)
        return out.toByteArray()
    }

    /** True when the file read back from [result] says exactly what [expected] (the on-screen preview of the edits) says. */
    fun matches(result: InputStream, expected: PptxDeck): Boolean = try {
        val read = PptxParser.parse(result)
        read.slides.size == expected.slides.size && read.slides.indices.all { read.slides[it].plainText() == expected.slides[it].plainText() }
    } catch (e: IOException) {
        false
    }

    // ---- the XML ----

    private fun changed(bytes: ByteArray, edits: Map<Int, String>): ByteArray {
        val doc = parseXml(String(bytes, Charsets.UTF_8).removePrefix("\uFEFF"))
        val list = doc.getElementsByTagNameNS(A, "p")
        val paragraphs = (0 until list.length).map { list.item(it) as Element }
        for ((id, text) in edits) {
            val p = paragraphs.getOrNull(id) ?: throw OfficeEditException("A paragraph you changed can't be found in the file any more.")
            setText(doc, p, text)
        }
        val out = ByteArrayOutputStream()
        out.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>".toByteArray(Charsets.UTF_8))
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        transformer.setOutputProperty(OutputKeys.INDENT, "no")
        transformer.transform(DOMSource(doc), StreamResult(out))
        return out.toByteArray()
    }

    private fun kids(e: Element, name: String): List<Element> {
        val out = ArrayList<Element>()
        var n: Node? = e.firstChild
        while (n != null) {
            if (n is Element && n.localName == name && n.namespaceURI == A) out += n
            n = n.nextSibling
        }
        return out
    }

    private fun setText(doc: Document, p: Element, text: String) {
        val prefix = p.prefix?.let { "$it:" } ?: ""
        val runs = kids(p, "r")
        kids(p, "br").forEach { p.removeChild(it) }
        runs.drop(1).forEach { p.removeChild(it) }
        val clean = XmlText.clean(text).replace("\r", "")
        var first = runs.firstOrNull()
        if (clean.isEmpty()) {
            first?.let { p.removeChild(it) }
            return
        }
        if (first == null) {
            first = doc.createElementNS(A, prefix + "r")
            val rPr = doc.createElementNS(A, prefix + "rPr")
            val end = kids(p, "endParaRPr").firstOrNull()
            if (end != null) {
                // The paragraph's end mark carries the look of its text: the new run starts with the same.
                val attrs = end.attributes
                for (i in 0 until attrs.length) rPr.setAttribute(attrs.item(i).nodeName, attrs.item(i).nodeValue)
                var c: Node? = end.firstChild
                while (c != null) {
                    rPr.appendChild(c.cloneNode(true))
                    c = c.nextSibling
                }
            } else {
                rPr.setAttribute("lang", "en-US")
            }
            first.appendChild(rPr)
            first.appendChild(doc.createElementNS(A, prefix + "t"))
            p.insertBefore(first, end)
        }
        val lines = clean.split('\n')
        setRunText(doc, first, lines[0], prefix)
        var last: Element = first
        for (line in lines.drop(1)) {
            val br = doc.createElementNS(A, prefix + "br")
            kids(first, "rPr").firstOrNull()?.let { br.appendChild(it.cloneNode(true)) }
            p.insertBefore(br, last.nextSibling)
            val run = first.cloneNode(true) as Element
            setRunText(doc, run, line, prefix)
            p.insertBefore(run, br.nextSibling)
            last = run
        }
    }

    private fun setRunText(doc: Document, run: Element, text: String, prefix: String) {
        val t = kids(run, "t").firstOrNull() ?: doc.createElementNS(A, prefix + "t").also { run.appendChild(it) }
        t.textContent = text
    }
}
