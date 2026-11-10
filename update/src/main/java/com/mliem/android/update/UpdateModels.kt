package com.mliem.android.update

import java.time.LocalDate

/** The installed app, as Android reports it. */
data class AppVersion(val versionName: String, val versionCode: Long) {
    /**
     * The release line this build belongs to. Nightly version names carry their base version
     * before `-nightly-`; builds without a semver (the calculator's "build 123") have none.
     */
    val baseVersion: Semver?
        get() = Semver.parse(versionName.substringBefore("-nightly-").substringBefore("+").trim())
}

/** A release the app can install. */
data class UpdateInfo(
    val tag: String,
    val releaseUrl: String,
    val apkUrl: String?,
    /** What to show a person: a semver for stable releases, the nightly's own version if its notes carry one. */
    val version: String,
    val build: Long,
    val sizeBytes: Long,
    val sha256: String?,
    val channel: Channel,
    val publishedAtMillis: Long,
    val notes: String,
    val nightlyDate: LocalDate?,
    val baseVersion: Semver?,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState

    /** Debug builds and builds outside the production package never self update. */
    data object Unsupported : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val percent: Int) : UpdateState

    /** The APK is with the system installer; its result arrives through [InstallResultReceiver]. */
    data object Installing : UpdateState
    data class Failed(val message: String, val kind: FailureKind) : UpdateState
}

enum class FailureKind { CHECK, INSTALL }

sealed interface CheckResult {
    data object UpToDate : CheckResult

    /** A launch check inside the configured interval did not reach GitHub. */
    data object Throttled : CheckResult
    data object Unsupported : CheckResult
    data class Available(val info: UpdateInfo) : CheckResult
    data class Failed(val message: String) : CheckResult
}

sealed interface InstallResult {
    /** The system installer has the APK and will show its own confirmation. */
    data object Started : InstallResult

    /** The app opened the unknown-sources screen; the person has to allow installs and retry. */
    data class PermissionRequired(val message: String) : InstallResult
    data class Failed(val message: String) : InstallResult
}
