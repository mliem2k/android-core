package com.mliem.android.update

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

internal class UpdateException(message: String) : Exception(message)

/**
 * Downloads an APK and refuses it unless it is the same package, a higher version code, and
 * (when the release publishes one) the exact file GitHub recorded.
 */
internal class ApkDownloader(
    private val context: Context,
    private val http: OkHttpClient,
    private val config: UpdateConfig,
    private val installed: AppVersion,
    private val onProgress: (Int) -> Unit,
) {
    fun download(info: UpdateInfo): File {
        val url = info.apkUrl ?: throw UpdateException(config.messages.noApk)
        if (!url.startsWith("https://")) throw UpdateException(config.messages.insecure)

        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(directory, "update.apk")
        val temp = File(directory, "update.apk.tmp")
        target.delete()
        temp.delete()

        val digest = MessageDigest.getInstance("SHA-256")
        try {
            val request = Request.Builder()
                .url(url)
                // Hash the bytes GitHub stored, not a gzip-decoded copy.
                .header("Accept-Encoding", "identity")
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw UpdateException("${config.messages.installFailed} (HTTP ${response.code})")
                }
                val body = response.body
                val total = body.contentLength().takeIf { it > 0 } ?: info.sizeBytes
                var done = 0L
                body.byteStream().use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            done += read
                            if (total > 0) onProgress(((done * 100) / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
                if (info.sizeBytes > 0 && done != info.sizeBytes) {
                    throw UpdateException(config.messages.incomplete)
                }
            }

            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            when (val expected = info.sha256) {
                null -> if (config.requireDigest) throw UpdateException(config.messages.digestMissing)
                else -> if (!actual.equals(expected, ignoreCase = true)) {
                    throw UpdateException(config.messages.checksum)
                }
            }

            verifyArchive(temp)
            if (!temp.renameTo(target)) throw UpdateException(config.messages.installFailed)
            return target
        } catch (error: Exception) {
            temp.delete()
            target.delete()
            throw error
        }
    }

    private fun verifyArchive(file: File) {
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            ?: throw UpdateException(config.messages.differentApp)
        if (archive.packageName != context.packageName) {
            throw UpdateException(config.messages.differentApp)
        }
        if (versionCode(archive) <= installed.versionCode) {
            throw UpdateException(config.messages.notNewer)
        }
    }

    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
}
