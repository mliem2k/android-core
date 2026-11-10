package com.mliem.android.update

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Turns GitHub's release list into the one release this app should install, if any. */
object ReleaseParser {

    fun parse(json: String, installed: AppVersion, config: UpdateConfig): UpdateInfo? =
        parse(json, installed, config.channels, config.requireDigest)

    internal fun parse(
        json: String,
        installed: AppVersion,
        channels: List<Channel>,
        requireDigest: Boolean,
    ): UpdateInfo? = parse(JSONArray(json), installed, channels, requireDigest)

    internal fun parse(
        releases: JSONArray,
        installed: AppVersion,
        channels: List<Channel>,
        requireDigest: Boolean,
    ): UpdateInfo? {
        val candidates = (0 until releases.length())
            .mapNotNull { releases.optJSONObject(it) }
            .mapNotNull { parseRelease(it, requireDigest) }

        // The channel order is the app's policy: Playtivity tries nightly first when opted in,
        // then stable; the nightly-only apps list one channel.
        for (channel in channels) {
            val newer = candidates.filter { it.channel == channel && isNewer(it, installed) }
            val best = when (channel) {
                Channel.NIGHTLY -> newer.maxWithOrNull(
                    compareBy<UpdateInfo> { it.build }.thenBy { it.publishedAtMillis }
                )
                Channel.STABLE -> newer.maxWithOrNull(
                    compareBy<UpdateInfo> { it.baseVersion ?: Semver(0, 0, 0) }.thenBy { it.publishedAtMillis }
                )
            }
            if (best != null) return best
        }
        return null
    }

    private fun parseRelease(release: JSONObject, requireDigest: Boolean): UpdateInfo? {
        if (release.optBoolean("draft", false)) return null
        val tagText = release.optString("tag_name").takeIf { it.isNotEmpty() } ?: return null
        val tag = TagParser.parse(tagText) ?: return null
        if (tag.channel == Channel.STABLE && release.optBoolean("prerelease", false)) return null

        val assets = release.optJSONArray("assets") ?: return null
        val asset = (0 until assets.length())
            .mapNotNull { assets.optJSONObject(it) }
            .firstOrNull {
                it.optString("name").endsWith(".apk", ignoreCase = true) &&
                    it.optString("browser_download_url").startsWith("https://")
            } ?: return null

        val digest = if (asset.isNull("digest")) {
            null
        } else {
            asset.optString("digest")
                .removePrefix("sha256:")
                .lowercase()
                .ifBlank { null }
        }
        if (requireDigest && digest == null) return null

        val body = if (release.isNull("body")) "" else release.optString("body")
        val versionLabel = when (tag.channel) {
            Channel.STABLE -> tag.version.toString()
            Channel.NIGHTLY -> nightlyVersionLabel(body) ?: tagText
        }
        val baseVersion = when (tag.channel) {
            Channel.STABLE -> tag.version
            Channel.NIGHTLY -> nightlyBaseVersion(body) ?: Semver.parse(versionLabel.substringBefore("-nightly-"))
        }
        // A legacy Playtivity tag names a local wall-clock time, so the release notes' +build
        // is the authority when it is there. Unified tags already carry the build themselves.
        val build = if (tag.channel == Channel.NIGHTLY) nightlyBuild(body) ?: tag.build else tag.build

        return UpdateInfo(
            tag = tagText,
            releaseUrl = release.optString("html_url"),
            apkUrl = asset.optString("browser_download_url"),
            version = versionLabel,
            build = build,
            sizeBytes = asset.optLong("size", -1L),
            sha256 = digest,
            channel = tag.channel,
            publishedAtMillis = runCatching { Instant.parse(release.optString("published_at")).toEpochMilli() }
                .getOrDefault(0L),
            notes = body,
            nightlyDate = tag.date,
            baseVersion = baseVersion,
        )
    }

    private fun isNewer(candidate: UpdateInfo, installed: AppVersion): Boolean = when (candidate.channel) {
        Channel.NIGHTLY -> {
            if (candidate.build <= installed.versionCode) {
                false
            } else {
                // A nightly from an older release line never replaces a newer stable or nightly.
                val installedBase = installed.baseVersion
                val candidateBase = candidate.baseVersion
                !(installedBase != null && candidateBase != null && candidateBase < installedBase)
            }
        }
        Channel.STABLE -> {
            val installedBase = installed.baseVersion
            installedBase == null || (candidate.baseVersion ?: Semver(0, 0, 0)) > installedBase
        }
    }

    private val VERSION_MARKDOWN = Regex("""(?i)\*\*version\*\*:\s*`([^`]+)`""")
    private val VERSION_PLAIN = Regex("""(?i)(?:^|\n)\s*version:\s*`?([^`\s]+)`?""")
    private val BASE_MARKDOWN = Regex("""(?i)\*\*base version\*\*:\s*`([^`]+)`""")
    private val BUILD_PLUS = Regex("""(?i)\*\*version\*\*:\s*`[^`]*\+(\d+)`""")
    private val BUILD_NUMBER = Regex("""(?i)\*\*build number\*\*:\s*`?(\d+)`?""")

    /** The version a release note names, without the build suffix. */
    private fun nightlyVersionLabel(body: String): String? {
        val match = VERSION_MARKDOWN.find(body) ?: VERSION_PLAIN.find(body) ?: return null
        return match.groupValues[1].substringBefore("+").takeIf { it.isNotEmpty() }
    }

    private fun nightlyBaseVersion(body: String): Semver? =
        BASE_MARKDOWN.find(body)?.groupValues?.get(1)?.let(Semver::parse)

    private fun nightlyBuild(body: String): Long? =
        (BUILD_PLUS.find(body) ?: BUILD_NUMBER.find(body))
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()
            ?.takeIf { it > 0 }
}
