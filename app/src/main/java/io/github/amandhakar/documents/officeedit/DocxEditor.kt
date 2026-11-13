package io.github.amandhakar.documents.officeedit

import io.github.amandhakar.documents.ooxml.OoxmlException
import io.github.amandhakar.documents.ooxml.OoxmlLimits
import io.github.amandhakar.documents.ooxml.XmlText
import io.github.amandhakar.documents.ooxml.parseXml
import io.github.amandhakar.documents.word.DocxDocument
import io.github.amandhakar.documents.word.DocxParser
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

/** Why an edit couldn't be written. The message is safe to show. */
class OfficeEditException(message: String) : IOException(message)

/**
 * Writes [DocxEdits] into a real .docx: only word/document.xml is changed (the text of the paragraphs that were edited,
 * deleted or added); every other part of the file (styles, pictures, headers, settings...) is copied through as it was,
 * and so is everything in the paragraph except its text. Where a paragraph had several differently formatted pieces,
 * the new text takes the look of the first one. Nothing is run and nothing outside the zip is touched. Free of Android
 * calls so it can be unit tested.
 */
object DocxEditor {
    private const val DOCUMENT = "word/document.xml"
    private const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private const val XML_NS = "http://www.w3.org/XML/1998/namespace"
    private val TEXT_PARTS = setOf("t", "tab", "cr", "noBreakHyphen")

    /** Copies [input] to [output] with the edits applied. */
    fun apply(input: InputStream, output: OutputStream, edits: DocxEdits, limits: OoxmlLimits = OoxmlLimits()) {
        var found = false
        var entries = 0
        var inflated = 0L
        try {
            ZipInputStream(input).use { zin ->
                ZipOutputStream(output).use { zout ->
                    while (true) {
                        val entry = zin.nextEntry ?: break
                        if (++entries > limits.maxEntries) throw OfficeEditException("This file has too many parts to edit.")
                        val copy = ZipEntry(entry.name).also { if (entry.time != -1L) it.time = entry.time }
                        zout.putNextEntry(copy)
                        if (entry.name == DOCUMENT) {
                            found = true
                            val bytes = readLimited(zin, limits.maxXmlPartBytes)
                            inflated += bytes.size
                            zout.write(changed(bytes, edits))
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
            throw OfficeEditException("This isn't a valid .docx file, so it can't be edited.")
        } catch (e: OoxmlException) {
            throw OfficeEditException(e.message ?: "This file couldn't be edited.")
        }
        if (!found) throw OfficeEditException("This isn't a Word document (word/document.xml is missing).")
    }

    /** The edited file as bytes, for tests and small files. */
    fun applyToBytes(original: ByteArray, edits: DocxEdits): ByteArray {
        val out = ByteArrayOutputStream()
        apply(ByteArrayInputStream(original), out, edits)
        return out.toByteArray()
    }

    /**
     * True when the file read back from [result] says exactly what [expected] (the on-screen preview of the edits)
     * says, so a save that went wrong in some way nobody foresaw is caught before it replaces anything.
     */
    fun matches(result: InputStream, expected: DocxDocument): Boolean = try {
        DocxParser.parse(result).plainText() == expected.plainText()
    } catch (e: IOException) {
        false
    }

    private fun readLimited(input: InputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = input.read(chunk)
            if (n < 0) break
            total += n
            if (total > max) throw OfficeEditException("A part of this document is too big to edit.")
            out.write(chunk, 0, n)
        }
        return out.toByteArray()
    }

    // ---- the XML ----

    private fun changed(bytes: ByteArray, edits: DocxEdits): ByteArray {
        val doc = parseXml(String(bytes, Charsets.UTF_8).removePrefix("﻿"))
        val r = edits.resolved()
        val list = doc.getElementsByTagNameNS(W, "p")
        val paragraphs = (0 until list.length).map { list.item(it) as Element }
        fun at(id: Int): Element = paragraphs.getOrNull(id) ?: throw OfficeEditException("A paragraph you changed can't be found in the file any more.")

        for ((id, text) in r.replaced) setText(doc, at(id), text)
        // Added paragraphs go in first (they hang off paragraphs that may be deleted below).
        val anchors = (r.inserts.map { it.afterId }.filter { it < DocxEdits.TEMP_BASE }).distinct()
        for (id in anchors) emitInserts(doc, at(id), id, at(id), r)
        for (id in r.deleted) delete(doc, at(id))
        return serialize(doc)
    }

    private fun emitInserts(doc: Document, anchor: Element, afterId: Int, template: Element, r: ResolvedDocxEdits): Element {
        var last = anchor
        for (ins in r.insertsAfter(afterId)) {
            val created = newParagraph(doc, template, ins.text)
            last.parentNode.insertBefore(created, last.nextSibling)
            last = emitInserts(doc, created, ins.id, created, r)
        }
        return last
    }

    private fun childElements(e: Element, name: String): List<Element> {
        val out = ArrayList<Element>()
        var n: Node? = e.firstChild
        while (n != null) {
            if (n is Element && n.localName == name && n.namespaceURI == W) out += n
            n = n.nextSibling
        }
        return out
    }

    /** The runs of paragraph [p]: its own, and those inside links, tracked insertions and the like, but not those of a nested text box. */
    private fun runsOf(p: Element): List<Element> {
        val all = p.getElementsByTagNameNS(W, "r")
        val out = ArrayList<Element>()
        for (i in 0 until all.length) {
            val run = all.item(i) as Element
            var parent: Node? = run.parentNode
            var nested = false
            while (parent != null && parent !== p) {
                if (parent is Element && parent.localName == "p" && parent.namespaceURI == W) {
                    nested = true
                    break
                }
                parent = parent.parentNode
            }
            if (!nested) out += run
        }
        return out
    }

    private fun isTextPart(e: Element) =
        e.namespaceURI == W && (e.localName in TEXT_PARTS || (e.localName == "br" && e.getAttributeNS(W, "type") != "page"))

    private fun textChildren(run: Element): List<Element> {
        val out = ArrayList<Element>()
        var n: Node? = run.firstChild
        while (n != null) {
            if (n is Element && isTextPart(n)) out += n
            n = n.nextSibling
        }
        return out
    }

    /** True for a run that has nothing in it but (maybe) its formatting. */
    private fun isEmpty(run: Element): Boolean {
        var n: Node? = run.firstChild
        while (n != null) {
            if (n is Element && !(n.localName == "rPr" && n.namespaceURI == W)) return false
            n = n.nextSibling
        }
        return true
    }

    /** Puts [text] into [run] after its formatting: text, with line breaks and tabs as their own elements. */
    private fun fillRun(doc: Document, run: Element, text: String) {
        val prefix = run.prefix?.let { "$it:" } ?: ""
        fun el(name: String) = doc.createElementNS(W, prefix + name)
        val sb = StringBuilder()
        fun flush() {
            if (sb.isNotEmpty()) {
                val t = el("t")
                t.setAttributeNS(XML_NS, "xml:space", "preserve")
                t.textContent = sb.toString()
                run.appendChild(t)
                sb.setLength(0)
            }
        }
        for (c in XmlText.clean(text)) {
            when (c) {
                '\n' -> { flush(); run.appendChild(el("br")) }
                '\r' -> Unit
                '\t' -> { flush(); run.appendChild(el("tab")) }
                else -> sb.append(c)
            }
        }
        flush()
    }

    private fun setText(doc: Document, p: Element, text: String) {
        val runs = runsOf(p)
        val textRuns = runs.filter { textChildren(it).isNotEmpty() }
        for (run in textRuns) for (part in textChildren(run)) run.removeChild(part)
        if (text.isEmpty()) {
            for (run in textRuns) if (isEmpty(run)) run.parentNode.removeChild(run)
            return
        }
        val first = textRuns.firstOrNull()
        if (first == null) {
            val prefix = p.prefix?.let { "$it:" } ?: ""
            val run = doc.createElementNS(W, prefix + "r")
            p.appendChild(run)
            fillRun(doc, run, text)
            return
        }
        for (run in textRuns.drop(1)) if (isEmpty(run)) run.parentNode.removeChild(run)
        fillRun(doc, first, text)
    }

    private fun delete(doc: Document, p: Element) {
        val parent = p.parentNode as? Element ?: return
        val holdsPicture = p.getElementsByTagNameNS(W, "drawing").length > 0 || p.getElementsByTagNameNS(W, "pict").length > 0
        val holdsSection = childElements(p, "pPr").firstOrNull()?.let { childElements(it, "sectPr").isNotEmpty() } ?: false
        val onlyOneInCell = parent.localName == "tc" && parent.namespaceURI == W && childElements(parent, "p").size == 1
        if (holdsPicture || holdsSection || onlyOneInCell) {
            setText(doc, p, "") // the paragraph has to stay (a picture, a section break, or a table cell's last paragraph): just empty it
        } else {
            parent.removeChild(p)
        }
    }

    private fun newParagraph(doc: Document, template: Element, text: String): Element {
        val prefix = template.prefix?.let { "$it:" } ?: ""
        val p = doc.createElementNS(W, prefix + "p")
        childElements(template, "pPr").firstOrNull()?.let { pPr ->
            val copy = pPr.cloneNode(true) as Element
            // A section break or a forced page break belongs to the original paragraph, not to its copy.
            for (name in listOf("sectPr", "pageBreakBefore")) childElements(copy, name).forEach { copy.removeChild(it) }
            p.appendChild(copy)
        }
        val run = doc.createElementNS(W, prefix + "r")
        runsOf(template).firstOrNull { textChildren(it).isNotEmpty() }?.let { look ->
            childElements(look, "rPr").firstOrNull()?.let { run.appendChild(it.cloneNode(true)) }
        }
        p.appendChild(run)
        fillRun(doc, run, text)
        return p
    }

    private fun serialize(doc: Document): ByteArray {
        val out = ByteArrayOutputStream()
        // The declaration is written by hand so it is the same everywhere (Word writes standalone="yes").
        out.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>".toByteArray(Charsets.UTF_8))
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        transformer.setOutputProperty(OutputKeys.INDENT, "no")
        transformer.transform(DOMSource(doc), StreamResult(out))
        return out.toByteArray()
    }
}
