package io.github.amandhakar.documents.ooxml

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/** Why an Office file couldn't be read. The message is safe to show. */
class OoxmlException(message: String) : IOException(message)

/** Size limits that keep a hostile or huge file from using all the phone's memory or time. */
data class OoxmlLimits(
    val maxXmlPartBytes: Int = 20 * 1024 * 1024,
    val maxTotalXmlBytes: Long = 80L * 1024 * 1024,
    val maxImageBytes: Int = 5 * 1024 * 1024,
    val maxTotalImageBytes: Int = 25 * 1024 * 1024,
    val maxEntries: Int = 5000,
    /** Everything unzipped, including parts that are skipped: stops a "zip bomb". */
    val maxInflatedBytes: Long = 200L * 1024 * 1024,
)

/** The XML parts (as text) and pictures (as bytes) that were asked for, by path inside the zip. */
class OoxmlParts(val xml: Map<String, String>, val media: Map<String, ByteArray>)

/**
 * Reads the parts of an Office Open XML file (.docx, .pptx) that the caller wants. Nothing is written anywhere:
 * parts are read into memory under the limits, and everything else is only counted and thrown away.
 * Free of Android calls so it can be unit tested.
 */
object OoxmlZip {
    val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp")

    fun isImageName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

    /** [wantXml] says which parts to keep as text; [wantMedia] which to keep as pictures (only picture types are kept). */
    fun read(
        input: InputStream,
        limits: OoxmlLimits,
        wantXml: (String) -> Boolean,
        wantMedia: (String) -> Boolean,
    ): OoxmlParts {
        val xml = HashMap<String, String>()
        val media = HashMap<String, ByteArray>()
        var entries = 0
        var inflated = 0L
        var xmlTotal = 0L
        var imageTotal = 0L
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++entries > limits.maxEntries) throw OoxmlException("This file has too many parts.")
                    val name = entry.name
                    val isXml = wantXml(name)
                    val isMedia = !isXml && wantMedia(name) && isImageName(name)
                    val cap = when {
                        isXml -> limits.maxXmlPartBytes
                        isMedia -> limits.maxImageBytes
                        else -> 0
                    }
                    val buffer = ByteArrayOutputStream()
                    val chunk = ByteArray(16 * 1024)
                    var size = 0
                    var overCap = false
                    while (true) {
                        val n = zip.read(chunk)
                        if (n < 0) break
                        size += n
                        inflated += n
                        if (inflated > limits.maxInflatedBytes) throw OoxmlException("This file unpacks to more than the app can handle.")
                        if (size <= cap) buffer.write(chunk, 0, n) else overCap = true
                    }
                    when {
                        isXml && overCap -> throw OoxmlException("A part of this document is too big to open.")
                        isXml -> {
                            xmlTotal += size
                            if (xmlTotal > limits.maxTotalXmlBytes) throw OoxmlException("This document's text is too big to open.")
                            xml[name] = buffer.toString(Charsets.UTF_8.name())
                        }
                        isMedia && !overCap && imageTotal + size <= limits.maxTotalImageBytes -> {
                            imageTotal += size
                            media[name] = buffer.toByteArray()
                        }
                    }
                }
            }
        } catch (e: ZipException) {
            throw OoxmlException("This isn't a valid Office file. It may be damaged or password-protected.")
        }
        if (entries == 0) throw OoxmlException("This isn't a valid Office file. It may be damaged or password-protected.")
        return OoxmlParts(xml, media)
    }

    /** A relationship target resolved against the folder of the part that holds it: ("ppt/slides", "../media/a.png") -> "ppt/media/a.png". */
    fun resolve(baseFolder: String, target: String): String {
        val parts = ArrayList<String>()
        val full = if (target.startsWith("/")) target.removePrefix("/") else "$baseFolder/$target"
        for (p in full.split('/')) {
            when (p) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts += p
            }
        }
        return parts.joinToString("/")
    }

    /** The folder part of a path: "ppt/slides/slide1.xml" -> "ppt/slides". */
    fun folderOf(path: String): String = path.substringBeforeLast('/', "")

    /** The relationships file of a part: "ppt/slides/slide1.xml" -> "ppt/slides/_rels/slide1.xml.rels". */
    fun relsPathOf(path: String): String = folderOf(path) + "/_rels/" + path.substringAfterLast('/') + ".rels"
}
