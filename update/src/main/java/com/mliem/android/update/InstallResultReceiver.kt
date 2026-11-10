package com.mliem.android.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

/**
 * The app declares a thin subclass in its manifest. The subclass returns the app's
 * [UpdateClient], which lets the receiver reach it without a process-wide registry.
 */
abstract class InstallResultReceiver : BroadcastReceiver() {

    protected abstract fun updateClient(context: Context): UpdateClient

    override fun onReceive(context: Context, intent: Intent) {
        val client = runCatching { updateClient(context) }.getOrNull() ?: return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
            }
            PackageInstaller.STATUS_SUCCESS -> client.onInstalled()
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            -> client.onInstallFailed(client.config.messages.signature)
            else -> client.onInstallFailed(
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?: client.config.messages.installFailed
            )
        }
    }
}
