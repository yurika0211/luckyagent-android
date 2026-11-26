package com.luckyagent.android.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi

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
    /** The original five-field expression when the server exposes one. */
    val expression: String = "",
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class CronCreateRequest(
    val name: String = "",
    val description: String = "",
    val schedule: String,
    val mode: String = "agent",
    val command: String,
    val metadata: Map<String, String> = emptyMap(),
    @SerialName("delete_after_run") val deleteAfterRun: Boolean = false,
)

@Serializable
data class CronActionRequest(val action: String)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class CronDeviceRegistration(
    val id: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val channel: String = "android",
    @SerialName("fcm_token") val fcmToken: String? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("notifications_enabled") val notificationsEnabled: Boolean = true,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("channel_enabled") val channelEnabled: Boolean = true,
    @SerialName("cursor_at") val cursorAt: String? = null,
    @SerialName("cursor_execution_id") val cursorExecutionId: String? = null,
)

@Serializable
data class CronDevice(
    val id: String = "",
    val channel: String = "android",
    @SerialName("registered_at") val registeredAt: String? = null,
    @SerialName("cursor_at") val cursorAt: String? = null,
    @SerialName("cursor_execution_id") val cursorExecutionId: String? = null,
)

@Serializable
data class CronExecution(
    val id: String = "",
    @SerialName("task_id") val taskId: String = "",
    @SerialName("scheduled_at") val scheduledAt: String = "",
    @SerialName("started_at") val startedAt: String = "",
    @SerialName("finished_at") val finishedAt: String? = null,
    val status: String = "unknown",
    val result: String = "",
    val error: String = "",
)

@Serializable
data class CronPendingItem(
    val execution: CronExecution,
    val task: CronResultTask,
    val delivery: CronDelivery,
)

@Serializable
data class CronResultTask(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val schedule: String = "",
    val status: String = "unknown",
    @SerialName("next_run_at") val nextRunAt: String? = null,
    val channels: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class CronExecutionsResponse(
    val task: CronResultTask,
    val executions: List<CronExecution> = emptyList(),
)

@Serializable
data class CronDelivery(
    val id: String = "",
    @SerialName("execution_id") val executionId: String = "",
    val channel: String = "android",
    val status: String = "pending",
)

@Serializable
data class CronPendingResponse(
    @SerialName("device_id") val deviceId: String = "",
    val items: List<CronPendingItem> = emptyList(),
)

@Serializable
data class CronAckRequest(@SerialName("device_id") val deviceId: String)
