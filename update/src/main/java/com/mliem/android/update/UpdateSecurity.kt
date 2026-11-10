package com.mliem.android.update

/** The download checks that do not need Android, kept separate so they are unit tested. */
internal object UpdateSecurity {
    fun isTrustedUrl(url: String): Boolean = url.startsWith("https://")

    /** GitHub reports lowercase hex; comparison is case-insensitive. */
    fun checksumMatches(actualHex: String, expectedHex: String?): Boolean =
        expectedHex == null || actualHex.equals(expectedHex, ignoreCase = true)
}
