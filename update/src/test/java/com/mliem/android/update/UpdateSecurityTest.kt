package com.mliem.android.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateSecurityTest {

    @Test
    fun onlyHttpsDownloadsAreTrusted() {
        assertTrue(UpdateSecurity.isTrustedUrl("https://github.com/x/releases/download/a.apk"))
        assertFalse(UpdateSecurity.isTrustedUrl("http://github.com/x/a.apk"))
        assertFalse(UpdateSecurity.isTrustedUrl("file:///sdcard/a.apk"))
    }

    @Test
    fun checksumIsCaseInsensitiveAndMissingIsNotAMismatch() {
        assertTrue(UpdateSecurity.checksumMatches("abc123", "ABC123"))
        assertFalse(UpdateSecurity.checksumMatches("abc123", "def456"))
        assertTrue(UpdateSecurity.checksumMatches("abc123", null))
    }
}
