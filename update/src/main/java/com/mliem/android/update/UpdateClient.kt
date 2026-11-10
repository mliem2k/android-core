package com.mliem.android.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The whole self-update flow: check GitHub, pick the release the app's channel policy allows,
 * download it, verify it, and hand it to the system installer.
 *
 * Apps own their copy and UI; this class owns the state machine and the security checks.
 */
class UpdateClient(
    private val context: Context,
    val config: UpdateConfig,
    private val http: OkHttpClient = OkHttpClient(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    private val prefs = context.getSharedPreferences("mliem_updates", Context.MODE_PRIVATE)
    private val checkMutex = Mutex()
    private val installMutex = Mutex()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersion: AppVersion = readInstalledVersion()
    val enabled: Boolean get() = config.updatesEnabled

    /** Launches a check and publishes its result on [state]. */
    fun check(force: Boolean = false, channels: List<Channel> = config.channels) {
        if (!enabled) {
            _state.value = UpdateState.Unsupported
            return
        }
        val current = _state.value
        if (current is UpdateState.Checking || current is UpdateState.Downloading || current is UpdateState.Installing) {
            return
        }
        scope.launch { checkNow(force, channels) }
    }

    /**
     * Checks now and returns the outcome for callers that show their own dialog. The state flow
     * is updated as well, so a UI can use either style.
     */
    suspend fun checkNow(
        force: Boolean = false,
        channels: List<Channel> = config.channels,
    ): CheckResult = checkMutex.withLock {
        if (!enabled) {
            _state.value = UpdateState.Unsupported
            return@withLock CheckResult.Unsupported
        }
        if (!force && config.minCheckIntervalMillis > 0) {
            val last = prefs.getLong(lastCheckKey(), 0L)
            if (last > 0 && System.currentTimeMillis() - last < config.minCheckIntervalMillis) {
                return@withLock CheckResult.Throttled
            }
        }

        _state.value = UpdateState.Checking
        try {
            val json = withContext(Dispatchers.IO) { fetchReleases() }
            val info = ReleaseParser.parse(json, currentVersion, channels, config.requireDigest)
            prefs.edit().putLong(lastCheckKey(), System.currentTimeMillis()).apply()
            if (info != null) {
                _state.value = UpdateState.Available(info)
                CheckResult.Available(info)
            } else {
                _state.value = UpdateState.UpToDate
                CheckResult.UpToDate
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Update check failed", error)
            _state.value = UpdateState.Failed(config.messages.checkFailed, FailureKind.CHECK)
            CheckResult.Failed(config.messages.checkFailed)
        }
    }

    /** Starts the install of the release currently offered by [state]. */
    fun downloadAndInstall() {
        val info = (_state.value as? UpdateState.Available)?.info ?: return
        scope.launch { install(info) }
    }

    /** Downloads [info], verifies it, and commits it to the system installer. */
    suspend fun install(info: UpdateInfo): InstallResult = installMutex.withLock {
        if (!enabled) {
            val message = config.messages.unsupported
            _state.value = UpdateState.Failed(message, FailureKind.INSTALL)
            return@withLock InstallResult.Failed(message)
        }
        if (info.apkUrl == null) {
            val message = config.messages.noApk
            _state.value = UpdateState.Failed(message, FailureKind.INSTALL)
            return@withLock InstallResult.Failed(message)
        }
        if (!canRequestPackageInstalls()) {
            openUnknownSourcesSettings()
            _state.value = UpdateState.Failed(config.messages.permission, FailureKind.INSTALL)
            return@withLock InstallResult.PermissionRequired(config.messages.permission)
        }

        try {
            _state.value = UpdateState.Downloading(0)
            val apk = withContext(Dispatchers.IO) {
                ApkDownloader(context, http, config, currentVersion) { percent ->
                    _state.value = UpdateState.Downloading(percent)
                }.download(info)
            }
            _state.value = UpdateState.Installing
            withContext(Dispatchers.IO) { ApkInstaller(context, config).commit(apk) }
            InstallResult.Started
        } catch (error: CancellationException) {
            throw error
        } catch (error: UpdateException) {
            val message = error.message ?: config.messages.installFailed
            _state.value = UpdateState.Failed(message, FailureKind.INSTALL)
            InstallResult.Failed(message)
        } catch (error: Exception) {
            Log.w(TAG, "Update install failed", error)
            _state.value = UpdateState.Failed(config.messages.installFailed, FailureKind.INSTALL)
            InstallResult.Failed(config.messages.installFailed)
        }
    }

    /** The system installer accepted the update. */
    fun onInstalled() {
        _state.value = UpdateState.UpToDate
    }

    /** The system installer refused the update; the receiver passes its reason. */
    fun onInstallFailed(message: String) {
        _state.value = UpdateState.Failed(message, FailureKind.INSTALL)
    }

    fun reset() {
        _state.value = UpdateState.Idle
    }

    private fun fetchReleases(): String {
        val url = "${config.apiBaseUrl.trimEnd('/')}/repos/${config.repo}/releases?per_page=30"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", config.userAgent)
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("GitHub returned ${response.code}")
            return response.body.string()
        }
    }

    private fun canRequestPackageInstalls(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    private fun openUnknownSourcesSettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun lastCheckKey(): String = "last_check_${config.repo}"

    private fun readInstalledVersion(): AppVersion {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return AppVersion(
            versionName = info.versionName ?: "0",
            versionCode = versionCode(info),
        )
    }

    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }

    private companion object {
        const val TAG = "UpdateClient"
    }
}
