package com.junkfood.seal

import com.junkfood.seal.util.UrlResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlResolverTest {

    @Test
    fun `isMathWorksUrl detects standard MathWorks video URLs`() {
        assertTrue(
            UrlResolver.isMathWorksUrl(
                "https://www.mathworks.com/videos/deep-learning-for-engineers-part-1-1617287683265.html"
            )
        )
    }

    @Test
    fun `isMathWorksUrl detects localized subdomains`() {
        assertTrue(
            UrlResolver.isMathWorksUrl(
                "https://fr.mathworks.com/videos/deep-learning-for-engineers-part-1-1617287683265.html"
            )
        )
        assertTrue(
            UrlResolver.isMathWorksUrl(
                "https://de.mathworks.com/videos/some-video-12345.html"
            )
        )
        assertTrue(
            UrlResolver.isMathWorksUrl(
                "https://jp.mathworks.com/videos/some-video-12345.html"
            )
        )
    }

    @Test
    fun `isMathWorksUrl detects bare domain`() {
        assertTrue(
            UrlResolver.isMathWorksUrl(
                "https://mathworks.com/videos/some-video-12345.html"
            )
        )
    }

    @Test
    fun `isMathWorksUrl detects http URLs`() {
        assertTrue(
            UrlResolver.isMathWorksUrl(
                "http://www.mathworks.com/videos/some-video-12345.html"
            )
        )
    }

    @Test
    fun `isMathWorksUrl rejects non-video MathWorks pages`() {
        assertFalse(UrlResolver.isMathWorksUrl("https://www.mathworks.com/products/matlab.html"))
        assertFalse(UrlResolver.isMathWorksUrl("https://www.mathworks.com/help/matlab/"))
    }

    @Test
    fun `isMathWorksUrl rejects non-MathWorks URLs`() {
        assertFalse(UrlResolver.isMathWorksUrl("https://www.youtube.com/watch?v=12345"))
        assertFalse(UrlResolver.isMathWorksUrl("https://www.google.com"))
        assertFalse(UrlResolver.isMathWorksUrl("https://mathworks.evil.com/videos/test.html"))
    }

    @Test
    fun `resolve returns original URL for non-MathWorks URLs`() {
        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        assertEquals(url, UrlResolver.resolve(url))
    }

    @Test
    fun `extractBrightcoveFromJsonLd parses VideoObject with embedUrl`() {
        // Use reflection to test private method
        val method = UrlResolver::class.java.getDeclaredMethod(
            "extractBrightcoveFromJsonLd", String::class.java
        )
        method.isAccessible = true

        val html = """
            <html>
            <head>
            <script type="application/ld+json">
            {
                "@context": "https://schema.org",
                "@type": "VideoObject",
                "name": "Test Video",
                "embedUrl": "https://players.brightcove.net/62009828001/default_default/index.html?videoId=6245925599001",
                "thumbnailUrl": "https://example.com/thumb.jpg",
                "uploadDate": "2021-04-01"
            }
            </script>
            </head>
            </html>
        """.trimIndent()

        val result = method.invoke(UrlResolver, html) as String?
        assertNotNull(result)
        assertEquals(
            "https://players.brightcove.net/62009828001/default_default/index.html?videoId=6245925599001",
            result
        )
    }

    @Test
    fun `extractBrightcoveFromJsonLd handles JSON-LD array`() {
        val method = UrlResolver::class.java.getDeclaredMethod(
            "extractBrightcoveFromJsonLd", String::class.java
        )
        method.isAccessible = true

        val html = """
            <html>
            <head>
            <script type="application/ld+json">
            [
                {
                    "@context": "https://schema.org",
                    "@type": "WebPage",
                    "name": "Some Page"
                },
                {
                    "@context": "https://schema.org",
                    "@type": "VideoObject",
                    "name": "Test Video",
                    "embedUrl": "https://players.brightcove.net/62009828001/default_default/index.html?videoId=999"
                }
            ]
            </script>
            </head>
            </html>
        """.trimIndent()

        val result = method.invoke(UrlResolver, html) as String?
        assertNotNull(result)
        assertEquals(
            "https://players.brightcove.net/62009828001/default_default/index.html?videoId=999",
            result
        )
    }

    @Test
    fun `extractBrightcoveFromJsonLd returns null when no VideoObject`() {
        val method = UrlResolver::class.java.getDeclaredMethod(
            "extractBrightcoveFromJsonLd", String::class.java
        )
        method.isAccessible = true

        val html = """
            <html>
            <head>
            <script type="application/ld+json">
            {"@type": "WebPage", "name": "No video here"}
            </script>
            </head>
            </html>
        """.trimIndent()

        val result = method.invoke(UrlResolver, html) as String?
        assertEquals(null, result)
    }

    @Test
    fun `extractBrightcoveFromHtml finds Brightcove URL in page source`() {
        val method = UrlResolver::class.java.getDeclaredMethod(
            "extractBrightcoveFromHtml", String::class.java
        )
        method.isAccessible = true

        val html = """
            <html>
            <script>
            var playerUrl = "https://players.brightcove.net/62009828001/default_default/index.html?videoId=6245925599001";
            </script>
            </html>
        """.trimIndent()

        val result = method.invoke(UrlResolver, html) as String?
        assertNotNull(result)
        assertEquals(
            "https://players.brightcove.net/62009828001/default_default/index.html?videoId=6245925599001",
            result
        )
    }
}
