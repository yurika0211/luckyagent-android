package com.luckyagent.android.data.media

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

/**
 * Prepares one picked chat attachment before [com.luckyagent.android.data.api.LuckyAgentApi.uploadAttachment].
 *
 * Decodeable bitmaps are compressed unless [sendOriginal] is set. GIF, video,
 * and PDF are never reencoded. A compress failure is returned as a failure;
 * the original is not uploaded in its place.
 */
object ChatImageUpload {
    data class Prepared(
        val uri: Uri,
        val fileName: String,
        val mimeType: String,
        /** Temp file the caller deletes after the upload attempt. */
        val cleanup: File? = null,
    )

    fun prepare(
        resolver: ContentResolver,
        uri: Uri,
        fileName: String,
        mimeType: String?,
        sendOriginal: Boolean,
        cacheDir: File,
    ): Result<Prepared> {
        val mime = mimeType?.substringBefore(';')?.trim()?.ifBlank { null }
        if (sendOriginal || !ChatImageCompress.isCompressibleMime(mime, fileName)) {
            return Result.success(Prepared(uri, fileName, mime ?: "application/octet-stream"))
        }
        return runCatching { compress(resolver, uri, fileName, cacheDir) }
    }

    private fun compress(resolver: ContentResolver, uri: Uri, fileName: String, cacheDir: File): Prepared {
        val orientation = readOrientation(resolver, uri)
        var bitmap = decode(resolver, uri) ?: error("无法压缩图片")
        try {
            bitmap = ChatImageCompress.applyOrientation(bitmap, orientation)
            bitmap = ChatImageCompress.flattenOnWhite(bitmap)
            bitmap = ChatImageCompress.scaleToLongEdge(bitmap)
            val jpeg = encodeJpeg(bitmap)
            val out = File(cacheDir, "chat-image-${System.nanoTime()}.jpg")
            out.writeBytes(jpeg)
            return Prepared(
                uri = Uri.fromFile(out),
                fileName = ChatImageCompress.jpegFileName(fileName),
                mimeType = ChatImageCompress.JPEG_MIME,
                cleanup = out,
            )
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun encodeJpeg(bitmap: Bitmap): ByteArray {
        var quality = ChatImageCompress.JPEG_QUALITY
        var encoded = compressAt(bitmap, quality)
        while (quality > ChatImageCompress.JPEG_QUALITY_FLOOR && encoded.size > ChatImageCompress.SHRINK_ABOVE_BYTES) {
            val next = ChatImageCompress.qualityForSize(encoded.size, quality)
            if (next >= quality) break
            quality = next
            encoded = compressAt(bitmap, quality)
        }
        return encoded
    }

    private fun compressAt(bitmap: Bitmap, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) error("无法压缩图片")
        return out.toByteArray()
    }

    private fun readOrientation(resolver: ContentResolver, uri: Uri): Int =
        runCatching {
            resolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

    private fun decode(resolver: ContentResolver, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= 28) {
            val decoded = runCatching {
                val source = ImageDecoder.createSource(resolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val edge = max(info.size.width, info.size.height)
                    if (edge > ChatImageCompress.MAX_LONG_EDGE) {
                        val scale = ChatImageCompress.MAX_LONG_EDGE.toFloat() / edge
                        decoder.setTargetSize(
                            (info.size.width * scale).toInt().coerceAtLeast(1),
                            (info.size.height * scale).toInt().coerceAtLeast(1),
                        )
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }.getOrNull()
            if (decoded != null) return decoded
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = sampleSize(bounds.outWidth, bounds.outHeight)
        return resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                inSampleSize = sample
            })
        }
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        val limit = ChatImageCompress.MAX_LONG_EDGE * 2
        while (max(width, height) / sample > limit) sample *= 2
        return sample
    }
}
