package com.luckyagent.android.data.media

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CaptureTarget(
    val file: File,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
)

object CaptureMediaStore {
    private fun captureDir(context: Context): File {
        val dir = File(context.cacheDir, "capture")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun stamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

    fun newPhotoTarget(context: Context): CaptureTarget {
        val name = "photo_${stamp()}.jpg"
        val file = File(captureDir(context), name)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return CaptureTarget(file = file, uri = uri, displayName = name, mimeType = "image/jpeg")
    }

    fun newVoiceTarget(context: Context): CaptureTarget {
        val name = "voice_${stamp()}.m4a"
        val file = File(captureDir(context), name)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return CaptureTarget(file = file, uri = uri, displayName = name, mimeType = "audio/mp4")
    }
}
