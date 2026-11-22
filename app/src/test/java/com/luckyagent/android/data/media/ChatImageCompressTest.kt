package com.luckyagent.android.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatImageCompressTest {
    @Test
    fun longEdgeAbove1080ScalesDownAndShortEdgeKeepsRatio() {
        assertEquals(1080 to 810, ChatImageCompress.targetSize(4000, 3000))
        assertEquals(810 to 1080, ChatImageCompress.targetSize(3000, 4000))
        assertEquals(1080 to 1080, ChatImageCompress.targetSize(2000, 2000))
    }

    @Test
    fun smallImageIsNotEnlarged() {
        assertEquals(800 to 600, ChatImageCompress.targetSize(800, 600))
        assertEquals(1080 to 720, ChatImageCompress.targetSize(1080, 720))
    }

    @Test
    fun gifVideoAndPdfAreNeverReencoded() {
        assertTrue(ChatImageCompress.isSkippedMime("image/gif", "anim.gif"))
        assertTrue(ChatImageCompress.isSkippedMime("image/gif", "anim"))
        assertTrue(ChatImageCompress.isSkippedMime("application/octet-stream", "clip.GIF"))
        assertTrue(ChatImageCompress.isSkippedMime("video/mp4", "clip.mp4"))
        assertTrue(ChatImageCompress.isSkippedMime("application/octet-stream", "clip.webm"))
        assertTrue(ChatImageCompress.isSkippedMime("application/pdf", "note.pdf"))
        assertTrue(ChatImageCompress.isSkippedMime("application/octet-stream", "note.PDF"))
        assertFalse(ChatImageCompress.isCompressibleMime("image/gif", "anim.gif"))
        assertFalse(ChatImageCompress.isCompressibleMime("video/mp4", "clip.mp4"))
        assertFalse(ChatImageCompress.isCompressibleMime("application/pdf", "note.pdf"))
    }

    @Test
    fun decodeableBitmapsAreCompressedAndOtherFilesAreLeftAlone() {
        assertTrue(ChatImageCompress.isCompressibleMime("image/jpeg", "photo.jpg"))
        assertTrue(ChatImageCompress.isCompressibleMime("image/png", "shot.png"))
        assertTrue(ChatImageCompress.isCompressibleMime("image/webp", "shot.webp"))
        assertTrue(ChatImageCompress.isCompressibleMime("image/heic", "shot.heic"))
        assertTrue(ChatImageCompress.isCompressibleMime(null, "shot.HEIC"))
        assertFalse(ChatImageCompress.isCompressibleMime("text/plain", "notes.txt"))
        assertFalse(ChatImageCompress.isCompressibleMime("application/zip", "pack.zip"))
    }

    @Test
    fun qualityStartsAt80AndFloorsAt70WhenStillLarge() {
        assertEquals(80, ChatImageCompress.JPEG_QUALITY)
        assertEquals(70, ChatImageCompress.JPEG_QUALITY_FLOOR)
        assertEquals(80, ChatImageCompress.qualityForSize(200_000))
        assertEquals(75, ChatImageCompress.qualityForSize(600_000, quality = 80))
        assertEquals(70, ChatImageCompress.qualityForSize(900_000, quality = 70))
        assertEquals(70, ChatImageCompress.qualityForSize(900_000, quality = 72))
    }

    @Test
    fun orientationNeedsATransformOnlyForNonNormalExif() {
        assertFalse(ChatImageCompress.needsOrientation(0))
        assertFalse(ChatImageCompress.needsOrientation(1))
        assertTrue(ChatImageCompress.needsOrientation(6))
        assertTrue(ChatImageCompress.needsOrientation(8))
        assertTrue(ChatImageCompress.needsOrientation(3))
    }

    @Test
    fun jpegNameReplacesTheExtension() {
        assertEquals("photo.jpg", ChatImageCompress.jpegFileName("photo.PNG"))
        assertEquals("image.jpg", ChatImageCompress.jpegFileName("image"))
    }
}
