package com.luckyagent.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.luckyagent.android.ui.theme.CloverLine
import com.luckyagent.android.ui.theme.CloverText
import com.luckyagent.android.ui.theme.CloverText3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

data class LinkPreview(
    val url: String,
    val title: String,
    val description: String,
    val site: String,
    val image: String?,
)

private const val linkPreviewLimit = 256 * 1024

private val linkPreviewClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .callTimeout(4, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
}

/** Open Graph fields from a short HTML prefix. Null when the page is not HTML or has no title. */
internal fun parseLinkPreview(url: String, html: String, contentType: String?): LinkPreview? {
    val type = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
    if (type.isNotEmpty() && type != "text/html" && type != "application/xhtml+xml") return null
    val doc = Jsoup.parse(html, url)
    fun meta(property: String): String? =
        doc.selectFirst("meta[property=$property]")?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }
            ?: doc.selectFirst("meta[name=$property]")?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }
    val title = meta("og:title") ?: doc.title().trim().takeIf { it.isNotEmpty() } ?: return null
    val description = meta("og:description") ?: meta("description").orEmpty()
    val host = url.substringAfter("://", "").substringBefore('/').substringBefore('?')
    val site = meta("og:site_name") ?: host
    val image = meta("og:image")?.let { raw ->
        val resolved = doc.selectFirst("meta[property=og:image]")?.attr("abs:content")?.takeIf { it.isNotBlank() }
        inAppBrowserUrl(resolved ?: raw)
    }
    return LinkPreview(url = url, title = title, description = description, site = site, image = image)
}

@Composable
internal fun LinkPreviewCard(url: String, onOpen: () -> Unit) {
    var preview by remember(url) { mutableStateOf<LinkPreview?>(null) }
    LaunchedEffect(url) { preview = fetchLinkPreview(url) }
    val card = preview ?: return
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CloverLine.copy(alpha = 0.35f))
            .clickable(onClick = onOpen),
    ) {
        card.image?.let { image ->
            AsyncImage(
                model = ImageRequest.Builder(context).data(image).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp),
            )
        }
        Column(Modifier.padding(10.dp)) {
            if (card.site.isNotBlank()) {
                Text(card.site, color = CloverText3, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            Text(card.title, color = CloverText, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (card.description.isNotBlank()) {
                Text(card.description, color = CloverText3, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

internal suspend fun fetchLinkPreview(url: String): LinkPreview? = withContext(Dispatchers.IO) {
    val page = inAppBrowserUrl(url) ?: return@withContext null
    runCatching {
        val request = Request.Builder().url(page).header("User-Agent", "LuckyAgent-Android").build()
        linkPreviewClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val type = response.header("Content-Type")
            val body = response.body ?: return@use null
            val bytes = ByteArray(linkPreviewLimit)
            val read = body.byteStream().read(bytes)
            if (read <= 0) return@use null
            parseLinkPreview(page, String(bytes, 0, read, Charsets.UTF_8), type)
        }
    }.getOrNull()
}
