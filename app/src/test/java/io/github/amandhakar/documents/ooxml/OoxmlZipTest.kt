package io.github.amandhakar.documents.ooxml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OoxmlZipTest {
    @Test fun resolvesRelativeTargets() {
        assertEquals("ppt/media/a.png", OoxmlZip.resolve("ppt/slides", "../media/a.png"))
        assertEquals("ppt/slides/slide2.xml", OoxmlZip.resolve("ppt", "slides/slide2.xml"))
        assertEquals("ppt/media/a.png", OoxmlZip.resolve("ppt/slides", "/ppt/media/a.png"))
        assertEquals("a.png", OoxmlZip.resolve("ppt/slides", "../../../../a.png"))
    }

    @Test fun findsTheRelationshipsPartOfAPart() {
        assertEquals("ppt/slides/_rels/slide1.xml.rels", OoxmlZip.relsPathOf("ppt/slides/slide1.xml"))
        assertEquals("ppt/slides", OoxmlZip.folderOf("ppt/slides/slide1.xml"))
        assertEquals("", OoxmlZip.folderOf("file.xml"))
    }

    @Test fun onlyPictureTypesAreKept() {
        assertTrue(OoxmlZip.isImageName("ppt/media/a.PNG"))
        assertTrue(OoxmlZip.isImageName("a.jpeg"))
        assertFalse(OoxmlZip.isImageName("a.emf"))
        assertFalse(OoxmlZip.isImageName("a.svg"))
        assertFalse(OoxmlZip.isImageName("noextension"))
    }

    @Test fun relationshipsLeaveOutExternalLinksAndKeepTheType() {
        val xml = "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://x/relationships/image\" Target=\"../media/a.png\"/>" +
            "<Relationship Id=\"rId2\" Type=\"http://x/relationships/hyperlink\" Target=\"https://example.com\" TargetMode=\"External\"/></Relationships>"
        val rels = parseRelationships(xml, "ppt/slides")
        assertEquals(setOf("rId1"), rels.keys)
        assertEquals("ppt/media/a.png", rels["rId1"]!!.path)
        assertEquals("image", rels["rId1"]!!.type)
    }
}
