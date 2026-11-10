package com.mliem.android.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemverTest {

    @Test
    fun parsesThreePartVersionsWithOrWithoutV() {
        assertEquals(Semver(0, 0, 6), Semver.parse("0.0.6"))
        assertEquals(Semver(0, 0, 6), Semver.parse("v0.0.6"))
        assertEquals(Semver(12, 34, 56), Semver.parse("12.34.56"))
    }

    @Test
    fun rejectsNightlyAndIncompleteVersions() {
        assertNull(Semver.parse("nightly-20261105-1793849665"))
        assertNull(Semver.parse("0.0"))
        assertNull(Semver.parse("build 123"))
    }

    @Test
    fun comparesEachPartInOrder() {
        assertTrue(Semver(1, 0, 0) > Semver(0, 99, 99))
        assertTrue(Semver(0, 1, 0) > Semver(0, 0, 99))
        assertTrue(Semver(0, 0, 1) > Semver(0, 0, 0))
        assertEquals(0, Semver(1, 2, 3).compareTo(Semver(1, 2, 3)))
    }

    @Test
    fun installedVersionDropsTheNightlySuffix() {
        assertEquals(Semver(0, 0, 6), AppVersion("0.0.6-nightly-20261105-113744", 1).baseVersion)
        assertEquals(Semver(0, 0, 6), AppVersion("0.0.6+2", 2).baseVersion)
        assertNull(AppVersion("build 123", 3).baseVersion)
    }
}
