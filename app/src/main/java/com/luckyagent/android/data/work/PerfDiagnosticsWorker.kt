package com.luckyagent.android.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.luckyagent.android.LuckyAgentApp
import com.luckyagent.android.data.api.PerfDiagnosticsHttpException
import com.luckyagent.android.data.api.PerfDiagnosticsUploadRequest
import com.luckyagent.android.perf.PerfCaptureManifest
import com.luckyagent.android.perf.PerfDiagnosticsManager
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.CancellationException

private const val MAX_BATCH_BYTES = 3 * 1024 * 1024

class PerfDiagnosticsWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? LuckyAgentApp ?: return Result.failure()
        val settings = app.container.settingsRepository.snapshot()
        if (!settings.performanceDiagnosticsEnabled || settings.connectionMode == "standalone") return Result.success()
        if (settings.apiBase.isBlank()) return Result.success()

        val captures = app.container.perfDiagnostics.pendingCaptures()
        for (capture in captures) {
            val upload = uploadCapture(app, capture)
            when (upload) {
                UploadCaptureResult.Success -> app.container.perfDiagnostics.deleteCapture(capture.directory)
                UploadCaptureResult.Discard -> app.container.perfDiagnostics.deleteCapture(capture.directory)
                UploadCaptureResult.Retry -> return Result.retry()
            }
        }
        return Result.success()
    }

    private suspend fun uploadCapture(
        app: LuckyAgentApp,
        capture: com.luckyagent.android.perf.PendingPerfCapture,
    ): UploadCaptureResult {
        val batches = gzipBatches(capture.lines)
        if (batches.isEmpty()) return UploadCaptureResult.Success
        for ((sequence, payload) in batches.withIndex()) {
            val request = PerfDiagnosticsUploadRequest(
                captureId = capture.manifest.captureId,
                sequence = sequence.toLong(),
                endpointHash = capture.manifest.endpointHash,
                sessionHash = capture.manifest.sessionHash,
                appVersion = capture.manifest.appVersion,
                deviceModel = capture.manifest.deviceModel,
                screenClass = capture.manifest.screenClass,
                startedAt = capture.manifest.startedAt,
                endedAt = capture.manifest.endedAt,
                sampleCount = capture.manifest.sampleCount,
                payloadBase64 = Base64.getEncoder().encodeToString(payload),
                payloadSHA256 = sha256(payload),
            )
            val result = try {
                app.container.api.uploadPerformanceDiagnostics(request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            result?.onSuccess { response ->
                if (response.captureId != request.captureId || response.sequence != request.sequence) {
                    return UploadCaptureResult.Discard
                }
            }?.onFailure { error ->
                if (error is PerfDiagnosticsHttpException && error.statusCode in 400..499) {
                    return UploadCaptureResult.Discard
                }
            }
            if (result == null || result.isFailure) {
                val nextAttempt = capture.manifest.attempts + 1
                if (nextAttempt <= 1) {
                    app.container.perfDiagnostics.markAttempt(capture.directory, nextAttempt)
                    return UploadCaptureResult.Retry
                }
                return UploadCaptureResult.Discard
            }
        }
        return UploadCaptureResult.Success
    }

    private fun gzipBatches(lines: List<String>): List<ByteArray> {
        if (lines.isEmpty()) return emptyList()
        val batches = mutableListOf<ByteArray>()
        val current = ByteArrayOutputStream()
        fun flush() {
            if (current.size() > 0) {
                batches += gzip(current.toByteArray())
                current.reset()
            }
        }
        for (line in lines) {
            val bytes = (line + "\n").toByteArray(Charsets.UTF_8)
            if (current.size() > 0 && current.size() + bytes.size > MAX_BATCH_BYTES) flush()
            current.write(bytes)
        }
        flush()
        return batches
    }

    private fun gzip(payload: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(payload) }
        return output.toByteArray()
    }

    private fun sha256(payload: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }

    private sealed interface UploadCaptureResult {
        data object Success : UploadCaptureResult
        data object Retry : UploadCaptureResult
        data object Discard : UploadCaptureResult
    }
}
