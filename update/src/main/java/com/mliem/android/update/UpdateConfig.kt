package com.mliem.android.update

import android.content.BroadcastReceiver

/**
 * Everything an app has to say about its own releases. The shared code owns the flow; the
 * app owns its repository, package, channel policy, and copy.
 */
data class UpdateConfig(
    /** GitHub repository as owner/name. */
    val repo: String,
    /** The app's receiver, which must extend [InstallResultReceiver]. */
    val installReceiver: Class<out BroadcastReceiver>,
    /** Channels in the order they are tried: the first one with a newer release wins. */
    val channels: List<Channel> = listOf(Channel.NIGHTLY),
    /** False for debug builds and for preview flavors that must not self update. */
    val updatesEnabled: Boolean = true,
    /** Refuse assets GitHub did not publish a SHA-256 digest for. */
    val requireDigest: Boolean = true,
    /** Launch checks inside this interval are skipped unless forced. Zero checks every time. */
    val minCheckIntervalMillis: Long = 0L,
    val messages: UpdateMessages = UpdateMessages(),
    val apiBaseUrl: String = "https://api.github.com",
    val userAgent: String = "mliem-android-update/1.0",
)
