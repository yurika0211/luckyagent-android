package com.luckyagent.android.data.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Ordinary chat-image compression.
 *
 * Numbers come from third-party measurements of WeChat's ordinary-image send,
 * not from an official Tencent specification. Tencent has not published that
 * standard. Some devices measured 1280–1440 on the long edge; this client
 * fixes 1080. Quality is not published; 80 is the start, and it may drop to
 * 70 when the file is still clearly large. There is no hard byte cap beyond
 * the existing 31MB upload limit.
 *
 * Moments and official-account cover sizes are a different set and are not
 * used here.
 */
object ChatImageCompress {
    const val MAX_LONG_EDGE = 1080
    const val JPEG_QUALITY = 80
    const val JPEG_QUALITY_FLOOR = 70

    /** Above this, drop quality toward the floor. Not a hard size limit. */
    const val SHRINK_ABOVE_BYTES = 500 * 1024

    const val JPEG_MIME = "image/jpeg"

    fun isSkippedMime(mimeType: String?, fileName: String = ""): Boolean {
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (mime == "image/gif" || ext == "gif") return true
        if (mime.startsWith("video/") || ext in VIDEO_EXTENSIONS) return true
        if (mime == "application/pdf" || ext == "pdf") return true
        return false
    }

    fun isCompressibleMime(mimeType: String?, fileName: String = ""): Boolean {
        if (isSkippedMime(mimeType, fileName)) return false
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        if (mime.startsWith("image/")) return true
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in BITMAP_EXTENSIONS
    }

    fun targetSize(width: Int, height: Int, maxLongEdge: Int = MAX_LONG_EDGE): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 1 to 1
        val edge = max(width, height)
        if (edge <= maxLongEdge) return width to height
        val scale = maxLongEdge.toFloat() / edge
        return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
    }

    /**
     * Applies EXIF orientation to pixels. The caller then writes JPEG without
     * copying EXIF, so the sent file is upright and has no orientation tag.
     */
    fun applyOrientation(bitmap: Bitmap, exifOrientation: Int): Bitmap {
        val matrix = orientationMatrix(exifOrientation) ?: return bitmap
        val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (oriented !== bitmap) bitmap.recycle()
        return oriented
    }

    /** EXIF 1 and 0 leave pixels as decoded. Other values need a transform before EXIF is stripped. */
    fun needsOrientation(exifOrientation: Int): Boolean = exifOrientation in 2..8

    fun orientationMatrix(exifOrientation: Int): Matrix? {
        val matrix = Matrix()
        when (exifOrientation) {
            2 -> matrix.setScale(-1f, 1f)
            3 -> matrix.setRotate(180f)
            4 -> matrix.setScale(1f, -1f)
            5 -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            6 -> matrix.setRotate(90f)
            7 -> {
                matrix.setRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            8 -> matrix.setRotate(270f)
            else -> return null
        }
        return matrix
    }

    /** Opaque copy on white so a transparent PNG/WebP does not become a black JPEG. */
    fun flattenOnWhite(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap
        val opaque = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(opaque)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        if (opaque !== bitmap) bitmap.recycle()
        return opaque
    }

    fun scaleToLongEdge(bitmap: Bitmap, maxLongEdge: Int = MAX_LONG_EDGE): Bitmap {
        val (width, height) = targetSize(bitmap.width, bitmap.height, maxLongEdge)
        if (width == bitmap.width && height == bitmap.height) return bitmap
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    /**
     * JPEG quality for [bytes]. Starts at 80. Drops one step at a time down to
     * 70 while the file stays above [SHRINK_ABOVE_BYTES].
     */
    fun qualityForSize(bytes: Int, quality: Int = JPEG_QUALITY, floor: Int = JPEG_QUALITY_FLOOR): Int {
        if (bytes <= SHRINK_ABOVE_BYTES || quality <= floor) return quality.coerceIn(floor, JPEG_QUALITY)
        return (quality - 5).coerceAtLeast(floor)
    }

    fun jpegFileName(fileName: String): String {
        val base = fileName.substringBeforeLast('.', fileName).ifBlank { "image" }
        return "$base.jpg"
    }

    private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "mkv", "webm", "3gp", "m4v")
    private val BITMAP_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp", "gif")
}
