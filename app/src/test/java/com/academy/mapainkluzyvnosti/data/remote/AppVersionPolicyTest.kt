package com.academy.mapainkluzyvnosti.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppVersionPolicyTest {

    @Test
    fun olderBuildMustUpdate() {
        val config = AppConfig(minVersionCode = 4, latestVersionName = "1.2.0", apkUrl = "https://example.org/apk")
        assertEquals(UpdateRequirement.Required("1.2.0", "https://example.org/apk"), AppVersionPolicy.evaluate(3, config))
    }

    @Test
    fun sameOrNewerBuildIsFine() {
        assertEquals(UpdateRequirement.UpToDate, AppVersionPolicy.evaluate(3, AppConfig(minVersionCode = 3)))
        assertEquals(UpdateRequirement.UpToDate, AppVersionPolicy.evaluate(5, AppConfig(minVersionCode = 3)))
    }

    @Test
    fun missingConfigNeverBlocks() {
        assertEquals(UpdateRequirement.Unknown, AppVersionPolicy.evaluate(3, null))
        assertEquals(UpdateRequirement.Unknown, AppVersionPolicy.evaluate(3, AppConfig(minVersionCode = null)))
    }

    @Test
    fun nonHttpsLinkFallsBackToReleasesPage() {
        val required = AppVersionPolicy.evaluate(1, AppConfig(minVersionCode = 2, apkUrl = "http://evil.example/x.apk"))
        assertEquals(UpdateRequirement.Required(null, AppVersionPolicy.DEFAULT_DOWNLOAD_URL), required)
    }

    @Test
    fun parsesPostgrestRowAndIgnoresNewColumns() {
        val body = """[{"min_version_code":3,"latest_version_name":"1.1.1","apk_url":null,"future_column":true}]"""
        assertEquals(AppConfig(3, "1.1.1", null), AppVersionPolicy.parse(body))
    }

    @Test
    fun unexpectedBodiesAreIgnored() {
        assertNull(AppVersionPolicy.parse("[]"))
        assertNull(AppVersionPolicy.parse("""{"code":"42501","message":"permission denied"}"""))
        assertNull(AppVersionPolicy.parse("<html>502</html>"))
    }
}
