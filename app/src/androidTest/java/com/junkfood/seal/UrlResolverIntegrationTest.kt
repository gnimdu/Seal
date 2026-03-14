package com.junkfood.seal

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.junkfood.seal.util.UrlResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration test that verifies UrlResolver can fetch a real MathWorks page
 * and extract the Brightcove embed URL from JSON-LD.
 */
@RunWith(AndroidJUnit4::class)
class UrlResolverIntegrationTest {

    @Test
    fun resolveMathWorksUrl_returnsBrightcoveUrl() {
        val mathworksUrl =
            "https://fr.mathworks.com/videos/deep-learning-for-engineers-part-1-why-choose-deep-learning-1617287683265.html"

        val resolved = UrlResolver.resolve(mathworksUrl)

        // Should have been transformed to a Brightcove URL
        assertTrue(
            "Expected Brightcove URL but got: $resolved",
            resolved.contains("players.brightcove.net")
        )
        assertTrue(
            "Expected videoId parameter but got: $resolved",
            resolved.contains("videoId=")
        )
    }

    @Test
    fun resolveNonMathWorksUrl_returnsOriginal() {
        val youtubeUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

        val resolved = UrlResolver.resolve(youtubeUrl)

        assertEquals(youtubeUrl, resolved)
    }
}
