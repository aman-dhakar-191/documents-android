package io.github.amandhakar.documents.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignatureImageTest {
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()
    private val navy = 0xFF1A237E.toInt()

    private fun alpha(argb: Int) = (argb ushr 24) and 0xFF

    @Test fun whitePaperBecomesSeeThrough() = assertEquals(0, alpha(SignatureImage.whiteToTransparent(intArrayOf(white))[0]))

    @Test fun darkInkIsKeptExactly() {
        val out = SignatureImage.whiteToTransparent(intArrayOf(black, navy))
        assertEquals(black, out[0])
        assertEquals(navy, out[1])
    }

    @Test fun lightGreyIsPartlyTransparentAndKeepsItsColour() {
        val grey = 0xFFB4B4B4.toInt() // brightness 180: between KEEP_UNTIL and CLEAR_FROM
        val out = SignatureImage.whiteToTransparent(intArrayOf(grey))[0]
        assertTrue(alpha(out) in 1..254)
        assertEquals(grey and 0x00FFFFFF, out and 0x00FFFFFF)
    }

    @Test fun alreadyTransparentStaysTransparent() =
        assertEquals(0, alpha(SignatureImage.whiteToTransparent(intArrayOf(0x00000000))[0]))

    @Test fun anEmptyPictureStaysEmpty() = assertEquals(0, SignatureImage.whiteToTransparent(IntArray(0)).size)

    @Test fun boundsAreTheSmallestBoxAroundTheInk() {
        // 5 x 4 picture, ink at (1,1) and (3,2).
        val pixels = IntArray(20)
        pixels[1 * 5 + 1] = black
        pixels[2 * 5 + 3] = black
        assertEquals(PixelBox(1, 1, 4, 3), SignatureImage.inkBounds(pixels, 5, 4))
    }

    @Test fun noInkMeansNoBounds() = assertNull(SignatureImage.inkBounds(IntArray(20), 5, 4))

    @Test fun nearlyInvisibleSpecksAreIgnored() {
        val pixels = IntArray(20)
        pixels[0] = 0x05000000 // alpha 5
        assertNull(SignatureImage.inkBounds(pixels, 5, 4))
    }

    @Test(expected = IllegalArgumentException::class) fun wrongSizeIsRejected() {
        SignatureImage.inkBounds(IntArray(3), 5, 4)
    }

    @Test fun paddingStaysInsideThePicture() {
        assertEquals(PixelBox(0, 0, 10, 8), SignatureImage.padded(PixelBox(1, 1, 9, 7), 5, 10, 8))
        assertEquals(PixelBox(2, 3, 8, 8), SignatureImage.padded(PixelBox(4, 5, 6, 6), 2, 10, 8))
    }

    @Test fun smallPicturesAreNotShrunk() = assertEquals(1, SignatureImage.sampleSize(800, 600, 1000))

    @Test fun bigPicturesAreHalvedWhileStillBigEnough() {
        assertEquals(2, SignatureImage.sampleSize(2400, 1800, 1000))
        assertEquals(4, SignatureImage.sampleSize(4000, 3000, 1000))
        assertEquals(1, SignatureImage.sampleSize(1900, 1200, 1000))
    }

    @Test fun badSizesDoNotShrink() {
        assertEquals(1, SignatureImage.sampleSize(0, 0, 1000))
        assertEquals(1, SignatureImage.sampleSize(4000, 3000, 0))
    }
}
