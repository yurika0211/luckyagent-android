package com.luckyagent.android.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CronListResponse(
    val running: Boolean = false,
    val count: Int = 0,
    val jobs: List<CronJob> = emptyList(),
)

@Serializable
data class CronJob(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val schedule: String = "",
    val status: String = "unknown",
    @SerialName("last_run") val lastRun: String? = null,
    @SerialName("next_run") val nextRun: String? = null,
    @SerialName("run_count") val runCount: Int = 0,
    @SerialName("error_count") val errorCount: Int = 0,
    @SerialName("last_error") val lastError: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("delete_after_run") val deleteAfterRun: Boolean = false,
    val metadata: Map<String, String> = emptyMap(),
)
