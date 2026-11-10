package com.mliem.android.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagParserTest {

    @Test
    fun readsTheUnifiedNightlyTag() {
        val tag = TagParser.parse("nightly-20261105-1793849665")!!
        assertEquals(Channel.NIGHTLY, tag.channel)
        assertEquals(1793849665L, tag.build)
        assertEquals("2026-11-05", tag.date.toString())
    }

    @Test
    fun readsTheThreeLegacyFormats() {
        assertEquals(1781154541L, TagParser.parse("nightly-20260611-050901")!!.build)
        assertEquals(300L, TagParser.parse("nightly-20260612-300")!!.build)
        assertEquals(1781592056L, TagParser.parse("nightly_20261105_1781592056")!!.build)
    }

    @Test
    fun rejectsLegacyTagsWithoutARealBuild() {
        assertNull(TagParser.parse("nightly_20261030_8"))
        assertNull(TagParser.parse("nightly-20261105"))
        assertNull(TagParser.parse("nightly_20261105"))
        assertNull(TagParser.parse("nightly-20261340-1793849665"))
    }

    @Test
    fun readsStableTags() {
        assertEquals(Channel.STABLE, TagParser.parse("v3.0.0")!!.channel)
        assertEquals(Semver(0, 0, 6), TagParser.parse("v0.0.6")!!.version)
        assertEquals(Semver(1, 2, 3), TagParser.parse("1.2.3")!!.version)
    }

    @Test
    fun ignoresEverythingElse() {
        assertNull(TagParser.parse("latest-nightly"))
        assertNull(TagParser.parse("nightly"))
        assertNull(TagParser.parse("v0.0"))
        assertNull(TagParser.parse(""))
    }

    @Test
    fun aLegacyTimeTagConvertsToUtcEpoch() {
        // Playtivity's old tag carried a wall-clock time; the release notes' +build is the
        // authority, but the tag itself still resolves to a comparable instant.
        val tag = TagParser.parse("nightly-20260611-050901")!!
        assertEquals(1781154541L, tag.build)
        assertTrue(tag.build > 1_000_000_000L)
        assertNotNull(tag.date)
    }
}
