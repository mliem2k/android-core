package com.mliem.android.update

import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

enum class Channel { STABLE, NIGHTLY }

/** A release tag resolved into the facts the updater needs. */
data class ReleaseTag(
    val tag: String,
    val channel: Channel,
    val build: Long,
    val version: Semver?,
    val date: LocalDate?,
)

/**
 * The one tag policy every app uses from now on:
 *
 * - nightly: `nightly-YYYYMMDD-<unix seconds>`
 * - stable: `v<major>.<minor>.<patch>`
 *
 * Legacy tags remain readable so installed builds can move across without a manual update:
 *
 * - Playtivity: `nightly-YYYYMMDD-HHMMSS` (the time is converted to the same epoch build)
 * - Scientific Calculator: `nightly-YYYYMMDD-<counter>` (small counters stay counters)
 * - CarLyrics: `nightly_YYYYMMDD_<unix seconds>`
 */
object TagParser {
    private const val MIN_UNIX_TIME = 1_000_000_000L

    private val HYPHEN_NIGHTLY = Regex("""^nightly-(\d{8})-(\d+)$""")
    private val UNDERSCORE_NIGHTLY = Regex("""^nightly_(\d{8})_(\d+)$""")
    private val STABLE = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""")
    private val DATE = DateTimeFormatter.BASIC_ISO_DATE

    fun parse(tag: String): ReleaseTag? {
        STABLE.matchEntire(tag)?.let { match ->
            val version = Semver(
                major = match.groupValues[1].toIntOrNull() ?: return null,
                minor = match.groupValues[2].toIntOrNull() ?: return null,
                patch = match.groupValues[3].toIntOrNull() ?: return null,
            )
            return ReleaseTag(tag, Channel.STABLE, build = 0L, version = version, date = null)
        }

        HYPHEN_NIGHTLY.matchEntire(tag)?.let { match ->
            val date = parseDate(match.groupValues[1]) ?: return null
            val build = hyphenBuild(match.groupValues[2], date) ?: return null
            return ReleaseTag(tag, Channel.NIGHTLY, build, version = null, date = date)
        }

        UNDERSCORE_NIGHTLY.matchEntire(tag)?.let { match ->
            val date = parseDate(match.groupValues[1]) ?: return null
            val build = match.groupValues[2].toLongOrNull() ?: return null
            // An underscore tag without a real Unix build is an old day counter, not an update.
            if (build < MIN_UNIX_TIME) return null
            return ReleaseTag(tag, Channel.NIGHTLY, build, version = null, date = date)
        }

        return null
    }

    private fun parseDate(raw: String): LocalDate? =
        runCatching { LocalDate.parse(raw, DATE) }.getOrNull()

    private fun hyphenBuild(raw: String, date: LocalDate): Long? {
        val number = raw.toLongOrNull() ?: return null
        if (number >= MIN_UNIX_TIME) return number
        if (raw.length == 6) {
            val hour = raw.substring(0, 2).toIntOrNull() ?: return number
            val minute = raw.substring(2, 4).toIntOrNull() ?: return number
            val second = raw.substring(4, 6).toIntOrNull() ?: return number
            if (hour in 0..23 && minute in 0..59 && second in 0..59) {
                // Playtivity's legacy HHMMSS tag and its versionCode were the same instant.
                return runCatching {
                    date.atTime(hour, minute, second).toEpochSecond(ZoneOffset.UTC)
                }.getOrNull()
            }
        }
        return number
    }
}
