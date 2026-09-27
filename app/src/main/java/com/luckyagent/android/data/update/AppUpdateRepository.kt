package com.luckyagent.android.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import android.util.Base64
import com.luckyagent.android.BuildConfig
import java.io.File
import java.util.concurrent.CancellationException
import java.util.zip.ZipFile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val assets: List<GitHubReleaseAsset> = emptyList(),
)

@Serializable
data class GitHubReleaseAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    @SerialName("content_type") val contentType: String? = null,
    val size: Long = 0,
)

data class AvailableUpdate(
    val release: GitHubRelease,
    val apk: GitHubReleaseAsset,
)

@Serializable
private data class UpdateManifest(
    @SerialName("tag_name") val tagName: String,
    @SerialName("version_name") val versionName: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val body: String? = null,
    val assets: List<UpdateManifestAsset> = emptyList(),
)

@Serializable
private data class UpdateManifestAsset(
    val name: String,
    @SerialName("download_url") val downloadUrl: String,
    val size: Long = 0,
    val sha256: String? = null,
)

class AppUpdateRepository(private val context: Context) {
    companion object {
        private const val manifestUrl = "https://github.com/yurika0211/luckyagent-android/releases/latest/download/update.json"
        private const val cacheName = "app_update_manifest"
        private const val cacheReleaseKey = "release"
        private const val cacheCheckedAtKey = "checked_at"
        private const val cacheTtlMs = 24L * 60L * 60L * 1000L
        private const val manualCheckMinIntervalMs = 15L * 60L * 1000L
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun checkLatest(force: Boolean = false): Result<AvailableUpdate?> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = readCachedRelease()
        val lastCheckedAt = cachePrefs().getLong(cacheCheckedAtKey, 0L)
        val minInterval = if (force) manualCheckMinIntervalMs else cacheTtlMs
        if (cached != null && now - lastCheckedAt in 0 until minInterval) {
            return@withContext runCatching { availableUpdate(cached) }
        }

        try {
            val request = Request.Builder()
                .url(manifestUrl)
                .header("Accept", "application/json")
                .header("User-Agent", "LuckyAgent-Android/${BuildConfig.VERSION_NAME}")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("Update manifest ${response.code}: ${body.take(240)}")
                val manifest = json.decodeFromString<UpdateManifest>(body)
                val release = manifest.toRelease()
                cacheRelease(release, now)
                Result.success(availableUpdate(release))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            if (cached != null) {
                runCatching { availableUpdate(cached) }
            } else {
                Result.failure(error)
            }
        }
    }

    private fun UpdateManifest.toRelease(): GitHubRelease = GitHubRelease(
        tagName = tagName,
        name = versionName,
        body = body,
        htmlUrl = htmlUrl ?: "https://github.com/yurika0211/luckyagent-android/releases/tag/$tagName",
        assets = assets.map {
            GitHubReleaseAsset(
                name = it.name,
                browserDownloadUrl = it.downloadUrl,
                size = it.size,
            )
        },
    )

    private fun availableUpdate(release: GitHubRelease): AvailableUpdate? {
        val apk = release.assets
            .filter { it.name.endsWith(".apk", ignoreCase = true) }
            .sortedWith(compareByDescending<GitHubReleaseAsset> { !it.name.contains("unsigned", ignoreCase = true) }.thenByDescending { it.size })
            .firstOrNull()
            ?: error("Release ${release.tagName} has no APK asset")
        return if (!isNewer(release.tagName, BuildConfig.VERSION_NAME)) null else AvailableUpdate(release, apk)
    }

    private fun cachePrefs() = context.getSharedPreferences(cacheName, Context.MODE_PRIVATE)

    private fun cacheRelease(release: GitHubRelease, checkedAt: Long) {
        cachePrefs().edit()
            .putString(cacheReleaseKey, json.encodeToString(release))
            .putLong(cacheCheckedAtKey, checkedAt)
            .apply()
    }

    private fun readCachedRelease(): GitHubRelease? = runCatching {
        cachePrefs().getString(cacheReleaseKey, null)?.let { json.decodeFromString<GitHubRelease>(it) }
    }.getOrNull()

    suspend fun download(update: AvailableUpdate, onProgress: (Int) -> Unit = {}): Result<File> = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val safeTag = update.release.tagName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(directory, "luckyagent-$safeTag.apk")
        val partial = File(directory, "$safeTag.apk.part")
        try {
            val url = update.apk.browserDownloadUrl
            require(url.startsWith("https://")) { "Update URL must use HTTPS" }
            val request = Request.Builder().url(url).header("User-Agent", "LuckyAgent-Android/${BuildConfig.VERSION_NAME}").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("APK download ${response.code}")
                if (!response.request.url.isHttps) error("APK download redirected to a non-HTTPS URL")
                val body = response.body ?: error("GitHub returned an empty APK")
                val total = body.contentLength()
                body.byteStream().use { input -> partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copied += count
                        if (total > 0L) onProgress(((copied * 100) / total).toInt().coerceIn(0, 100))
                    }
                } }
            }
            ZipFile(partial).use { zip -> require(zip.getEntry("AndroidManifest.xml") != null) { "Downloaded file is not an APK" } }
            if (target.exists()) target.delete()
            check(partial.renameTo(target)) { "Unable to store downloaded APK" }
            Result.success(target)
        } catch (cancelled: CancellationException) {
            partial.delete()
            throw cancelled
        } catch (error: Throwable) {
            partial.delete()
            Result.failure(error)
        }
    }

    fun installBlockReason(file: File): Result<String?> = runCatching {
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, packageInfoFlags())
            ?: error("Downloaded APK is invalid")
        require(archive.packageName == context.packageName) {
            "This build uses a different app ID than the release APK. Install the release variant first."
        }
        val installed = context.packageManager.getPackageInfo(context.packageName, packageInfoFlags())
        val downloadedSigners = signerDigests(archive)
        val installedSigners = signerDigests(installed)
        require(downloadedSigners.isNotEmpty()) { "Release APK is unsigned; it cannot update this installation." }
        require(installedSigners.isNotEmpty() && downloadedSigners == installedSigners) {
            "Release APK signing key differs from the installed app. Updating in place is blocked to protect app data."
        }
        val downloadedVersion = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        val installedVersion = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
        require(downloadedVersion > installedVersion) { "The downloaded APK is not newer than this installation." }
        null
    }

    fun install(file: File): Result<Unit> = runCatching {
        require(file.exists()) { "Downloaded APK is missing" }
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            error("Allow installs from LuckyAgent in Android settings, then tap Install again.")
        }
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    fun openReleasePage(update: AvailableUpdate?) = runCatching {
        val url = update?.release?.htmlUrl ?: "https://github.com/yurika0211/luckyagent-android/releases/latest"
        require(url.startsWith("https://github.com/yurika0211/luckyagent-android/")) { "Unexpected Release URL" }
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @Suppress("DEPRECATION")
    private fun packageInfoFlags(): Int = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        }
        return signatures.orEmpty().map { Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP) }.toSet()
    }

    private fun isNewer(remoteTag: String, localVersion: String): Boolean {
        fun parts(value: String): List<Int> = value.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }.take(3).let { it + List(3 - it.size) { 0 } }
        return parts(remoteTag).zip(parts(localVersion)).firstOrNull { it.first != it.second }?.let { it.first > it.second } ?: false
    }
}
