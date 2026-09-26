package com.luckyagent.android.data.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class VoiceRecorder {
    private var recorder: MediaRecorder? = null
    private var output: File? = null

    val isRecording: Boolean
        get() = recorder != null

    fun start(context: Context, outputFile: File) {
        stopQuietly(deleteOutput = false)
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mediaRecorder.setAudioEncodingBitRate(128_000)
        mediaRecorder.setAudioSamplingRate(44_100)
        mediaRecorder.setOutputFile(outputFile.absolutePath)
        mediaRecorder.prepare()
        mediaRecorder.start()
        recorder = mediaRecorder
        output = outputFile
    }

    fun stop(): File? {
        val file = output
        val active = recorder
        recorder = null
        output = null
        if (active == null) return null
        return try {
            active.stop()
            active.release()
            file?.takeIf { it.exists() && it.length() > 0L }
        } catch (_: RuntimeException) {
            runCatching { active.release() }
            file?.delete()
            null
        }
    }

    fun cancel() {
        val file = output
        stopQuietly(deleteOutput = false)
        file?.delete()
    }

    private fun stopQuietly(deleteOutput: Boolean) {
        val file = output
        val active = recorder
        recorder = null
        output = null
        if (active != null) {
            runCatching { active.stop() }
            runCatching { active.release() }
        }
        if (deleteOutput) file?.delete()
    }
}
