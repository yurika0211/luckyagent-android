package com.luckyagent.android.data.api

import kotlinx.serialization.Serializable

@Serializable
data class PerfDiagnosticsUploadRequest(
    val captureId: String,
    val sequence: Long,
    val endpointHash: String,
    val sessionHash: String,
    val appVersion: String,
    val deviceModel: String,
    val screenClass: String,
    val startedAt: String,
    val endedAt: String,
    val sampleCount: Long,
    val payloadBase64: String,
    val payloadSHA256: String,
)

@Serializable
data class PerfDiagnosticsUploadResponse(
    val captureId: String,
    val sequence: Long,
    val status: String,
)

class PerfDiagnosticsHttpException(
    val statusCode: Int,
    message: String,
) : Exception(message)
