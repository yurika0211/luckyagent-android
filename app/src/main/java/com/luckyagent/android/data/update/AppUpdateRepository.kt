package com.luckyagent.android.data.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.luckyagent.android.BuildConfig
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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

class AppUpdateRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun checkLatest(): Result<AvailableUpdate?> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://api.github.com/repos/yurika0211/luckyagent-android/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "LuckyAgent-Android/${BuildConfig.VERSION_NAME}")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("GitHub release ${response.code}: ${body.take(240)}")
                val release = json.decodeFromString<GitHubRelease>(body)
                val apk = release.assets
                    .filter { it.name.endsWith(".apk", ignoreCase = true) }
                    .sortedWith(compareByDescending<GitHubReleaseAsset> { !it.name.contains("unsigned", ignoreCase = true) }.thenByDescending { it.size })
                    .firstOrNull()
                    ?: error("Release ${release.tagName} has no APK asset")
                if (!isNewer(release.tagName, BuildConfig.VERSION_NAME)) null else AvailableUpdate(release, apk)
            }
        }
    }

    suspend fun download(update: AvailableUpdate): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val url = update.apk.browserDownloadUrl
            require(url.startsWith("https://")) { "Update URL must use HTTPS" }
            val directory = File(context.cacheDir, "updates").apply { mkdirs() }
            val safeTag = update.release.tagName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val target = File(directory, "luckyagent-$safeTag.apk")
            val partial = File(directory, "$safeTag.apk.part")
            val request = Request.Builder().url(url).header("User-Agent", "LuckyAgent-Android/${BuildConfig.VERSION_NAME}").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("APK download ${response.code}")
                val body = response.body ?: error("GitHub returned an empty APK")
                body.byteStream().use { input -> partial.outputStream().use { output -> input.copyTo(output) } }
            }
            ZipFile(partial).use { zip -> require(zip.getEntry("AndroidManifest.xml") != null) { "Downloaded file is not an APK" } }
            if (target.exists()) target.delete()
            check(partial.renameTo(target)) { "Unable to store downloaded APK" }
            target
        }
    }

    fun install(file: File): Result<Unit> = runCatching {
        require(file.exists()) { "Downloaded APK is missing" }
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun isNewer(remoteTag: String, localVersion: String): Boolean {
        fun parts(value: String): List<Int> = value.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }.take(3).let { it + List(3 - it.size) { 0 } }
        return parts(remoteTag).zip(parts(localVersion)).firstOrNull { it.first != it.second }?.let { it.first > it.second } ?: false
    }
}
