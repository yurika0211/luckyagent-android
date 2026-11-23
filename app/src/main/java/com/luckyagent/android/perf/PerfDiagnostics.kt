package com.luckyagent.android.perf

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.luckyagent.android.BuildConfig
import com.luckyagent.android.data.settings.ClientSettings
import com.luckyagent.android.data.settings.SettingsRepository
import com.luckyagent.android.data.work.PerfDiagnosticsWorker
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PerfStackFrameWire(
    val className: String,
    val methodName: String,
    val lineNumber: Int,
)

@Serializable
data class PerfSampleWire(
    val section: String,
    val variant: String,
    val elapsedNanos: Long,
    val memoryBefore: Long,
    val memoryAfter: Long,
    val memoryTotalBefore: Long,
    val memoryTotalAfter: Long,
    val memoryMaxBefore: Long,
    val memoryMaxAfter: Long,
    val stackThread: String,
    val stack: List<PerfStackFrameWire>,
    val inputUnits: Int,
    val capturedAtEpochMs: Long,
    val attributes: Map<String, String>,
)

@Serializable
data class PerfCaptureManifest(
    val captureId: String,
    val endpointHash: String,
    val sessionHash: String,
    val appVersion: String,
    val deviceModel: String,
    val screenClass: String,
    val startedAt: String,
    val endedAt: String = "",
    val sampleCount: Long = 0,
    val attempts: Int = 0,
)

internal data class PendingPerfCapture(
    val directory: File,
    val manifest: PerfCaptureManifest,
    val lines: List<String>,
)

private const val PERF_DIAGNOSTICS_DIR = "perf-diagnostics"
private const val MANIFEST_FILE = "manifest.json"
private const val EVENTS_FILE = "events.jsonl"
private const val PERF_UPLOAD_WORK = "luckyagent-perf-diagnostics-upload"

class PerfDiagnosticsManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _active = MutableStateFlow(false)
    private var activeCapture: ActiveCapture? = null

    @Volatile
    var contextProvider: (() -> Map<String, String>)? = null

    init {
        recoverInterruptedCaptures()
        val settings = settingsRepository.snapshot()
        if (settings.performanceDiagnosticsEnabled) {
            scheduleUpload(context, settings.performanceDiagnosticsWifiOnly)
        } else {
            deletePendingCaptures()
        }
    }

    val active: StateFlow<Boolean> = _active.asStateFlow()

    @Synchronized
    fun startCapture(): Boolean {
        if (!settingsRepository.snapshot().performanceDiagnosticsEnabled) return false
        if (activeCapture != null) return true
        val settings = settingsRepository.snapshot()
        val id = UUID.randomUUID().toString()
        val dir = File(context.filesDir, "$PERF_DIAGNOSTICS_DIR/$id")
        if (!dir.mkdirs() && !dir.isDirectory) return false
        val manifest = PerfCaptureManifest(
            captureId = id,
            endpointHash = stableHash(settings.apiBase),
            sessionHash = stableHash(settings.sessionId),
            appVersion = BuildConfig.VERSION_NAME,
            deviceModel = Build.MODEL.orEmpty(),
            screenClass = if (context.resources.configuration.screenWidthDp >= 700) "wide" else "narrow",
            startedAt = Instant.now().toString(),
        )
        writeManifest(dir, manifest)
        val channel = Channel<PerfSample>(capacity = 256)
        val closed = AtomicBoolean(false)
        val writer = scope.launch {
            val file = File(dir, EVENTS_FILE)
            for (sample in channel) {
                file.appendText(json.encodeToString(sample.toWire()) + "\n")
            }
        }
        activeCapture = ActiveCapture(dir, manifest, channel, writer, closed)
        PerfProbe.shared.clear()
        PerfProbe.shared.enabled = true
        PerfProbe.shared.sink = { sample ->
            if (!closed.get()) {
                val context = contextProvider?.invoke().orEmpty()
                channel.trySend(
                    if (context.isEmpty()) sample
                    else sample.copy(attributes = sample.attributes + context),
                )
            }
        }
        _active.value = true
        scheduleUpload(context, settings.performanceDiagnosticsWifiOnly)
        return true
    }

    @Synchronized
    fun stopCapture() {
        val capture = activeCapture ?: return
        activeCapture = null
        PerfProbe.shared.sink = null
        PerfProbe.shared.enabled = false
        capture.closed.set(true)
        capture.channel.close()
        runBlocking { capture.writer.join() }
        val lines = File(capture.directory, EVENTS_FILE).takeIf { it.exists() }?.useLines { it.count().toLong() } ?: 0L
        writeManifest(
            capture.directory,
            capture.manifest.copy(endedAt = Instant.now().toString(), sampleCount = lines),
        )
        _active.value = false
        scheduleUpload(context, settingsRepository.snapshot().performanceDiagnosticsWifiOnly)
    }

    fun onSettingsChanged(previous: ClientSettings, next: ClientSettings) {
        if (previous.performanceDiagnosticsEnabled && !next.performanceDiagnosticsEnabled) {
            stopCapture()
            deletePendingCaptures()
            cancelUpload(context)
        } else if (!previous.performanceDiagnosticsEnabled && next.performanceDiagnosticsEnabled) {
            scheduleUpload(context, next.performanceDiagnosticsWifiOnly)
        } else if (next.performanceDiagnosticsWifiOnly != previous.performanceDiagnosticsWifiOnly) {
            scheduleUpload(context, next.performanceDiagnosticsWifiOnly)
        }
    }

    internal fun pendingCaptures(): List<PendingPerfCapture> {
        val root = File(context.filesDir, PERF_DIAGNOSTICS_DIR)
        if (!root.isDirectory) return emptyList()
        return root.listFiles().orEmpty().mapNotNull { dir ->
            if (!dir.isDirectory) return@mapNotNull null
            val manifestFile = File(dir, MANIFEST_FILE)
            val eventsFile = File(dir, EVENTS_FILE)
            val manifest = runCatching { json.decodeFromString<PerfCaptureManifest>(manifestFile.readText()) }.getOrNull()
            if (manifest == null || manifest.endedAt.isBlank() || !eventsFile.exists()) null
            else PendingPerfCapture(dir, manifest, eventsFile.readLines())
        }
    }

    fun deleteCapture(directory: File) {
        directory.deleteRecursively()
    }

    fun markAttempt(directory: File, attempts: Int) {
        val file = File(directory, MANIFEST_FILE)
        val manifest = runCatching { json.decodeFromString<PerfCaptureManifest>(file.readText()) }.getOrNull() ?: return
        writeManifest(directory, manifest.copy(attempts = attempts))
    }

    private fun writeManifest(directory: File, manifest: PerfCaptureManifest) {
        File(directory, MANIFEST_FILE).writeText(json.encodeToString(manifest))
    }

    private fun deletePendingCaptures() {
        File(context.filesDir, PERF_DIAGNOSTICS_DIR).deleteRecursively()
    }

    private fun recoverInterruptedCaptures() {
        val root = File(context.filesDir, PERF_DIAGNOSTICS_DIR)
        if (!root.isDirectory) return
        root.listFiles().orEmpty().forEach { dir ->
            val file = File(dir, MANIFEST_FILE)
            val manifest = runCatching { json.decodeFromString<PerfCaptureManifest>(file.readText()) }.getOrNull()
            if (manifest != null && manifest.endedAt.isBlank()) {
                val count = File(dir, EVENTS_FILE).takeIf { it.exists() }?.useLines { it.count().toLong() } ?: 0L
                writeManifest(dir, manifest.copy(endedAt = Instant.now().toString(), sampleCount = count))
            }
        }
    }

    private data class ActiveCapture(
        val directory: File,
        val manifest: PerfCaptureManifest,
        val channel: Channel<PerfSample>,
        val writer: Job,
        val closed: AtomicBoolean,
    )

    companion object {
        fun scheduleUpload(context: Context, wifiOnly: Boolean) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<PerfDiagnosticsWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                PERF_UPLOAD_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancelUpload(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(PERF_UPLOAD_WORK)
        }

        fun stableHash(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.trim().toByteArray())
            return digest.joinToString("") { byte -> "%02x".format(byte) }
        }
    }
}

private fun PerfSample.toWire(): PerfSampleWire = PerfSampleWire(
    section = section,
    variant = variant.name,
    elapsedNanos = elapsedNanos,
    memoryBefore = memoryBefore.usedBytes,
    memoryAfter = memoryAfter.usedBytes,
    memoryTotalBefore = memoryBefore.totalBytes,
    memoryTotalAfter = memoryAfter.totalBytes,
    memoryMaxBefore = memoryBefore.maxBytes,
    memoryMaxAfter = memoryAfter.maxBytes,
    stackThread = stack.threadName,
    stack = stack.frames.map { frame -> PerfStackFrameWire(frame.className, frame.methodName, frame.lineNumber) },
    inputUnits = inputUnits,
    capturedAtEpochMs = capturedAtEpochMs,
    attributes = attributes,
)
