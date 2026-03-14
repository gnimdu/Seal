package com.junkfood.seal.util

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Resolves URLs that yt-dlp cannot handle directly by transforming them into URLs that existing
 * yt-dlp extractors can process. Currently supports MathWorks video pages by extracting the
 * underlying Brightcove embed URL from JSON-LD structured data.
 */
object UrlResolver {

    private const val TAG = "UrlResolver"

    private val jsonFormat = Json { ignoreUnknownKeys = true }

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

    private val MATHWORKS_URL_PATTERN =
        Pattern.compile("https?://(?:\\w+\\.)?mathworks\\.com/videos/.+", Pattern.CASE_INSENSITIVE)

    private val JSON_LD_PATTERN =
        Pattern.compile(
            "<script[^>]*type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>",
            Pattern.DOTALL
        )

    private val BRIGHTCOVE_EMBED_PATTERN =
        Pattern.compile(
            "https?://players\\.brightcove\\.net/\\d+/[^/]+/index\\.html\\?videoId=\\d+"
        )

    /**
     * Resolves a URL, transforming it if needed for yt-dlp compatibility.
     * If the URL is not recognized or resolution fails, the original URL is returned unchanged.
     * This method performs blocking I/O and must be called from a background thread.
     */
    fun resolve(url: String): String =
        try {
            when {
                isMathWorksUrl(url) -> resolveMathWorks(url)
                else -> url
            }
        } catch (e: Exception) {
            Log.w(TAG, "URL resolution failed for $url, using original", e)
            url
        }

    fun isMathWorksUrl(url: String): Boolean =
        MATHWORKS_URL_PATTERN.matcher(url).matches()

    private fun resolveMathWorks(url: String): String {
        Log.d(TAG, "Resolving MathWorks URL: $url")

        val html = fetchPage(url)

        // Strategy 1: Extract Brightcove embed URL from JSON-LD
        val brightcoveUrl = extractBrightcoveFromJsonLd(html)
        if (brightcoveUrl != null) {
            Log.d(TAG, "Resolved MathWorks → Brightcove: $brightcoveUrl")
            return brightcoveUrl
        }

        // Strategy 2: Look for Brightcove embed URL directly in HTML
        val directUrl = extractBrightcoveFromHtml(html)
        if (directUrl != null) {
            Log.d(TAG, "Resolved MathWorks → Brightcove (from HTML): $directUrl")
            return directUrl
        }

        Log.w(TAG, "Could not extract Brightcove URL from MathWorks page: $url")
        return url
    }

    private fun fetchPage(url: String): String {
        val request =
            Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                )
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Fetch-User", "?1")
                .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Failed to fetch $url: HTTP ${response.code}")
            }
            return response.body?.string()
                ?: throw RuntimeException("Empty response body for $url")
        }
    }

    /**
     * Extracts Brightcove embed URL from JSON-LD structured data.
     * MathWorks pages include a VideoObject with an embedUrl pointing to Brightcove.
     */
    private fun extractBrightcoveFromJsonLd(html: String): String? {
        val matcher = JSON_LD_PATTERN.matcher(html)

        while (matcher.find()) {
            val jsonContent = matcher.group(1) ?: continue
            try {
                val embedUrl = parseJsonLdForEmbedUrl(jsonContent.trim())
                if (embedUrl != null && embedUrl.contains("brightcove.net")) {
                    return embedUrl
                }
            } catch (e: Exception) {
                Log.d(TAG, "Failed to parse JSON-LD block", e)
            }
        }
        return null
    }

    private fun parseJsonLdForEmbedUrl(jsonString: String): String? {
        val element = jsonFormat.parseToJsonElement(jsonString)

        // JSON-LD can be a single object or an array of objects
        val objects =
            when (element) {
                is JsonArray -> element.jsonArray.mapNotNull { it as? JsonObject }
                is JsonObject -> listOf(element.jsonObject)
                else -> return null
            }

        for (obj in objects) {
            // Check if this is a VideoObject
            val type = obj["@type"]?.jsonPrimitive?.content
            if (type == "VideoObject") {
                val embedUrl = obj["embedUrl"]?.jsonPrimitive?.content
                if (embedUrl != null) return embedUrl

                // Some pages use contentUrl instead
                val contentUrl = obj["contentUrl"]?.jsonPrimitive?.content
                if (contentUrl != null) return contentUrl
            }
        }
        return null
    }

    /**
     * Fallback: search for Brightcove player URL directly in page HTML.
     */
    private fun extractBrightcoveFromHtml(html: String): String? {
        val matcher = BRIGHTCOVE_EMBED_PATTERN.matcher(html)
        return if (matcher.find()) matcher.group() else null
    }
}
