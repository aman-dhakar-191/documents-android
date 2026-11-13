package io.github.amandhakar.documents.export

import android.content.Context
import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import io.github.amandhakar.documents.R
import io.github.amandhakar.documents.word.WordFace

/** The bundled fonts (res/font) as Android typefaces, for drawing a Word document into a PDF. Loaded once each. */
class WordTypefaces(private val context: Context) {
    private val cache = HashMap<Triple<WordFace, Boolean, Boolean>, Typeface>()

    fun get(face: WordFace, bold: Boolean, italic: Boolean): Typeface =
        cache.getOrPut(Triple(face, bold, italic)) { load(face, bold, italic) }

    private fun load(face: WordFace, bold: Boolean, italic: Boolean): Typeface {
        val id = resource(face, bold, italic)
        if (id != null) {
            try {
                return context.resources.getFont(id)
            } catch (e: Exception) {
                // fall through to the phone's own font below
            }
        }
        // Courier's slanted styles are the upright font drawn slanted; also the fallback if a font can't be loaded.
        val upright = if (italic && id == null && face == WordFace.LiberationMono) get(face, bold, false) else null
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Typeface.create(upright ?: if (face == WordFace.LiberationMono) Typeface.MONOSPACE else if (face == WordFace.LiberationSerif || face == WordFace.Caladea) Typeface.SERIF else Typeface.SANS_SERIF, style)
    }

    private fun resource(face: WordFace, bold: Boolean, italic: Boolean): Int? = when (face) {
        WordFace.Carlito -> pick(bold, italic, R.font.carlito_regular, R.font.carlito_bold, R.font.carlito_italic, R.font.carlito_bold_italic)
        WordFace.Caladea -> pick(bold, italic, R.font.caladea_regular, R.font.caladea_bold, R.font.caladea_italic, R.font.caladea_bold_italic)
        WordFace.LiberationSans -> pick(bold, italic, R.font.liberation_sans_regular, R.font.liberation_sans_bold, R.font.liberation_sans_italic, R.font.liberation_sans_bold_italic)
        WordFace.LiberationSerif -> pick(bold, italic, R.font.liberation_serif_regular, R.font.liberation_serif_bold, R.font.liberation_serif_italic, R.font.liberation_serif_bold_italic)
        WordFace.LiberationMono -> if (italic) null else if (bold) R.font.liberation_mono_bold else R.font.liberation_mono_regular
    }

    private fun pick(bold: Boolean, italic: Boolean, regular: Int, b: Int, i: Int, bi: Int) = when {
        bold && italic -> bi
        bold -> b
        italic -> i
        else -> regular
    }
}

/** Draws a stretch of text in one typeface (what a TypefaceSpan does from Android 9 on, written out so it works on older phones). */
class FontSpan(private val typeface: Typeface) : MetricAffectingSpan() {
    override fun updateDrawState(paint: TextPaint) {
        paint.typeface = typeface
    }

    override fun updateMeasureState(paint: TextPaint) {
        paint.typeface = typeface
    }
}
