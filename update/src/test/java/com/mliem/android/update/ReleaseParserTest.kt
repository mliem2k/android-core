package com.mliem.android.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseParserTest {

    private val channels = listOf(Channel.NIGHTLY)

    @Test
    fun picksTheHighestBuildEvenWhenTheListIsOutOfOrder() {
        val json = """[
            {"tag_name":"nightly-20261105-1781592150","assets":[
                {"name":"mid.apk","browser_download_url":"https://x/mid.apk","digest":"sha256:mid"}]},
            {"tag_name":"nightly-20261106-1781592900","draft":true,"assets":[
                {"name":"d.apk","browser_download_url":"https://x/d.apk","digest":"sha256:d"}]},
            {"tag_name":"nightly-20261105-1781592400","assets":[
                {"name":"notes.txt","browser_download_url":"https://x/notes.txt"},
                {"name":"carlyrics.apk","size":4619645,"digest":"sha256:ABC123",
                 "browser_download_url":"https://x/carlyrics.apk"}]}
        ]"""
        val info = ReleaseParser.parse(
            json,
            AppVersion("build 1", versionCode = 100),
            channels,
            requireDigest = true,
        )!!
        assertEquals("nightly-20261105-1781592400", info.tag)
        assertEquals("https://x/carlyrics.apk", info.apkUrl)
        assertEquals(4619645L, info.sizeBytes)
        assertEquals("abc123", info.sha256)
    }

    @Test
    fun skipsReleasesWithoutAnApk() {
        val json = """[
            {"tag_name":"nightly-20261105-1781592500","assets":[]},
            {"tag_name":"nightly-20261105-1781592400","assets":[
                {"name":"a.apk","browser_download_url":"https://x/a.apk","digest":"sha256:a"}]}
        ]"""
        assertEquals(
            "nightly-20261105-1781592400",
            ReleaseParser.parse(json, AppVersion("build 1", 100), channels, true)!!.tag,
        )
    }

    @Test
    fun aDigestIsRequiredByDefaultAndOptionalByPolicy() {
        val json = """[{"tag_name":"nightly-20261105-1781592400","assets":[
            {"name":"a.apk","size":5,"browser_download_url":"https://x/a.apk"}]}]"""
        assertNull(ReleaseParser.parse(json, AppVersion("build 1", 100), channels, true))
        val info = ReleaseParser.parse(json, AppVersion("build 1", 100), channels, false)!!
        assertNull(info.sha256)
    }

    @Test
    fun aNullDigestIsMissingNotTheWordNull() {
        val json = """[{"tag_name":"nightly-20261105-1781592400","assets":[
            {"name":"a.apk","size":5,"digest":null,"browser_download_url":"https://x/a.apk"}]}]"""
        assertNull(ReleaseParser.parse(json, AppVersion("build 1", 100), channels, true))
        assertNull(ReleaseParser.parse(json, AppVersion("build 1", 100), channels, false)!!.sha256)
    }

    @Test
    fun legacyUnderscoreTagsStillUpdate() {
        val json = """[{"tag_name":"nightly_20261105_1781592400","assets":[
            {"name":"a.apk","browser_download_url":"https://x/a.apk","digest":"sha256:a"}]}]"""
        assertNotNull(ReleaseParser.parse(json, AppVersion("2.0.0", 1781592300), channels, true))
    }

    @Test
    fun dayCounterTagsNeverUpdate() {
        val json = """[{"tag_name":"nightly_20261030_8","assets":[
            {"name":"a.apk","browser_download_url":"https://x/a.apk","digest":"sha256:a"}]}]"""
        assertNull(ReleaseParser.parse(json, AppVersion("2.0.0", 7), channels, true))
    }

    @Test
    fun aNightlyFromAnOlderReleaseLineIsNeverOffered() {
        val json = nightlyJson()
        val nightly = listOf(Channel.NIGHTLY)

        assertNotNull(ReleaseParser.parse(json, AppVersion("0.0.2-nightly-x", 1781212140), nightly, false))
        assertNull(ReleaseParser.parse(json, AppVersion("0.0.2-nightly-x", 1781212141), nightly, false))
        assertNull(ReleaseParser.parse(json, AppVersion("0.0.2", 1781212142), nightly, false))
        assertNull(ReleaseParser.parse(json, AppVersion("0.0.3", 1), nightly, false))
        assertNotNull(ReleaseParser.parse(json, AppVersion("0.0.2", 1), nightly, false))
    }

    @Test
    fun pullsTheNightlyVersionFromItsNotes() {
        val info = ReleaseParser.parse(nightlyJson(), AppVersion("0.0.2", 1), channels, false)!!
        assertEquals("0.0.2-nightly-20260611-050901", info.version)
        assertEquals("0.0.2", info.baseVersion.toString())
        assertEquals("1781212141", info.build.toString())
    }

    @Test
    fun stableChannelPicksTheHighestSemver() {
        val json = """[
            {"tag_name":"v0.0.3","published_at":"2026-06-12T00:00:00Z","assets":[
                {"name":"a.apk","browser_download_url":"https://x/a.apk","digest":"sha256:a"}]},
            {"tag_name":"v0.0.4","published_at":"2026-06-14T00:00:00Z","assets":[
                {"name":"b.apk","browser_download_url":"https://x/b.apk","digest":"sha256:b"}]},
            {"tag_name":"nightly-20260611-050901","assets":[
                {"name":"n.apk","browser_download_url":"https://x/n.apk","digest":"sha256:n"}]}
        ]"""
        val stable = listOf(Channel.STABLE)
        assertEquals("0.0.4", ReleaseParser.parse(json, AppVersion("0.0.2", 1), stable, false)!!.version)
        assertNull(ReleaseParser.parse(json, AppVersion("0.0.4", 1), stable, false))
        assertEquals("0.0.4", ReleaseParser.parse(json, AppVersion("0.0.3-nightly-x", 1), stable, false)!!.version)
    }

    @Test
    fun channelOrderIsRespected() {
        val json = """[
            {"tag_name":"nightly-20261105-1781592500","assets":[
                {"name":"n.apk","browser_download_url":"https://x/n.apk","digest":"sha256:n"}]},
            {"tag_name":"v0.0.4","assets":[
                {"name":"s.apk","browser_download_url":"https://x/s.apk","digest":"sha256:s"}]}
        ]"""
        val installed = AppVersion("0.0.2", 1)
        assertEquals(
            Channel.NIGHTLY,
            ReleaseParser.parse(json, installed, listOf(Channel.NIGHTLY, Channel.STABLE), false)!!.channel,
        )
        assertEquals(
            Channel.STABLE,
            ReleaseParser.parse(json, installed, listOf(Channel.STABLE), false)!!.channel,
        )
    }

    @Test
    fun rejectsStablePrereleases() {
        val json = """[{"tag_name":"v0.0.4","prerelease":true,"assets":[
            {"name":"a.apk","browser_download_url":"https://x/a.apk","digest":"sha256:a"}]}]"""
        assertNull(ReleaseParser.parse(json, AppVersion("0.0.3", 1), listOf(Channel.STABLE), false))
    }

    @Test
    fun everyCandidateMustBeHttps() {
        val json = """[{"tag_name":"nightly-20261105-1781592400","assets":[
            {"name":"a.apk","browser_download_url":"http://x/a.apk","digest":"sha256:a"}]}]"""
        assertNull(ReleaseParser.parse(json, AppVersion("build 1", 1), channels, false))
    }

    private fun nightlyJson() = """[{
        "tag_name":"nightly-20260611-050901",
        "published_at":"2026-06-11T05:09:01Z",
        "body":"**Version**: `0.0.2-nightly-20260611-050901+1781212141`",
        "assets":[{"name":"p.apk","browser_download_url":"https://x/p.apk","size":5}]
    }]"""
}
