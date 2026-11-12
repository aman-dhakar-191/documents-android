package io.github.amandhakar.documents.ooxml

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/** Parses XML text. No DTDs, so no entity tricks ("billion laughs", reading files): an Office file never needs one. */
fun parseXml(xml: String): org.w3c.dom.Document {
    if (xml.contains("<!DOCTYPE") || xml.contains("<!ENTITY")) throw OoxmlException("This file contains something unsafe and wasn't opened.")
    val factory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = true
    factory.isExpandEntityReferences = false
    val builder = factory.newDocumentBuilder()
    builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
    try {
        return builder.parse(InputSource(StringReader(xml)))
    } catch (e: org.xml.sax.SAXException) {
        throw OoxmlException("The document's text couldn't be read. The file may be damaged.")
    }
}

fun Element.kids(): List<Element> {
    val out = ArrayList<Element>()
    var n: Node? = firstChild
    while (n != null) {
        if (n is Element) out += n
        n = n.nextSibling
    }
    return out
}

/** Child elements with this namespace and local name. */
fun Element.kids(ns: String, name: String): List<Element> = kids().filter { it.localName == name && it.namespaceURI == ns }

fun Element.kid(ns: String, name: String): Element? = kids(ns, name).firstOrNull()

/** An attribute with no namespace (like `sz`, `lvl`, `val`), or null when missing or empty. */
fun Element.attr(name: String): String? = getAttribute(name).takeIf { it.isNotEmpty() }

/** An attribute in the relationships namespace (like `r:embed`), or null. */
fun Element.relAttr(name: String): String? =
    getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", name).takeIf { it.isNotEmpty() }

/** Parses a relationships part into id -> target (external links left out), each target resolved against [baseFolder]. */
fun parseRelationships(xml: String, baseFolder: String): Map<String, RelationshipInfo> {
    val result = HashMap<String, RelationshipInfo>()
    for (rel in parseXml(xml).documentElement.kids()) {
        if (rel.localName != "Relationship") continue
        if (rel.getAttribute("TargetMode").equals("External", ignoreCase = true)) continue
        val id = rel.getAttribute("Id")
        val target = rel.getAttribute("Target")
        if (id.isNotEmpty() && target.isNotEmpty()) {
            result[id] = RelationshipInfo(OoxmlZip.resolve(baseFolder, target), rel.getAttribute("Type").substringAfterLast('/'))
        }
    }
    return result
}

/** [path] is the resolved path inside the zip; [type] is the last part of the relationship type, e.g. "slide", "image", "slideLayout". */
class RelationshipInfo(val path: String, val type: String)
