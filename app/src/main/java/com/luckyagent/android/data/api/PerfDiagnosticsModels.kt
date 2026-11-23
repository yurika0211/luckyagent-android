package com.luckyagent.android.data.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class PerfDiagnosticsUploadRequest(
    @SerialName("capture_id")
    val captureId: String,
    @SerialName("sequence")
    val sequence: Long,
    @SerialName("endpoint_hash")
    val endpointHash: String,
    @SerialName("session_hash")
    val sessionHash: String,
    @SerialName("app_version")
    val appVersion: String,
    @SerialName("device_model")
    val deviceModel: String,
    @SerialName("screen_class")
    val screenClass: String,
    @SerialName("started_at")
    val startedAt: String,
    @SerialName("ended_at")
    val endedAt: String,
    @SerialName("sample_count")
    val sampleCount: Long,
    @SerialName("payload_base64")
    val payloadBase64: String,
    @SerialName("payload_sha256")
    val payloadSHA256: String,
)

@Serializable
data class PerfDiagnosticsUploadResponse(
    @SerialName("capture_id")
    val captureId: String,
    @SerialName("sequence")
    val sequence: Long,
    val status: String,
)

class PerfDiagnosticsHttpException(
    val statusCode: Int,
    message: String,
) : Exception(message)
