package com.mliem.android.update

/**
 * User-facing copy for the shared update flow. Every app passes its own strings so the
 * messages match the rest of its UI.
 */
data class UpdateMessages(
    val unsupported: String = "Updates are off for this build.",
    val checkFailed: String = "Could not check for updates. Check your connection and try again.",
    val noApk: String = "This release does not include an APK.",
    val insecure: String = "The update is not served over https.",
    val permission: String = "Allow this app to install apps in the settings screen that just opened, then tap Update again.",
    val differentApp: String = "This update is for a different app.",
    val notNewer: String = "This update is not newer than the installed app.",
    val incomplete: String = "The download was incomplete, so it was not installed.",
    val checksum: String = "The downloaded file did not match its checksum, so it was not installed.",
    val digestMissing: String = "The release does not publish a checksum, so it was not installed.",
    val signature: String = "This build is signed with a different key than the installed app. Uninstall the app once, then install the new APK.",
    val installFailed: String = "The installer could not install the update.",
)
