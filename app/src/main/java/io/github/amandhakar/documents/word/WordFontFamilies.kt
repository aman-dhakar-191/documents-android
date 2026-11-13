package io.github.amandhakar.documents.word

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import io.github.amandhakar.documents.R

/** The bundled fonts (res/font) as Compose font families, one per [WordFace]. They are used unmodified; licences are in assets/licenses/fonts.txt. */
object WordFontFamilies {
    private fun family(regular: Int, bold: Int, italic: Int, boldItalic: Int) = FontFamily(
        Font(regular, FontWeight.Normal, FontStyle.Normal),
        Font(bold, FontWeight.Bold, FontStyle.Normal),
        Font(italic, FontWeight.Normal, FontStyle.Italic),
        Font(boldItalic, FontWeight.Bold, FontStyle.Italic),
    )

    private val carlito by lazy { family(R.font.carlito_regular, R.font.carlito_bold, R.font.carlito_italic, R.font.carlito_bold_italic) }
    private val caladea by lazy { family(R.font.caladea_regular, R.font.caladea_bold, R.font.caladea_italic, R.font.caladea_bold_italic) }
    private val sans by lazy { family(R.font.liberation_sans_regular, R.font.liberation_sans_bold, R.font.liberation_sans_italic, R.font.liberation_sans_bold_italic) }
    private val serif by lazy { family(R.font.liberation_serif_regular, R.font.liberation_serif_bold, R.font.liberation_serif_italic, R.font.liberation_serif_bold_italic) }

    // Courier's letters are all the same width, so the slanted style is the regular font drawn slanted by Android.
    private val mono by lazy {
        FontFamily(
            Font(R.font.liberation_mono_regular, FontWeight.Normal, FontStyle.Normal),
            Font(R.font.liberation_mono_bold, FontWeight.Bold, FontStyle.Normal),
        )
    }

    fun of(face: WordFace): FontFamily = when (face) {
        WordFace.Carlito -> carlito
        WordFace.Caladea -> caladea
        WordFace.LiberationSans -> sans
        WordFace.LiberationSerif -> serif
        WordFace.LiberationMono -> mono
    }

    /** The family for a font name from a document. */
    fun forName(name: String?): FontFamily = of(WordFonts.faceFor(name))
}
