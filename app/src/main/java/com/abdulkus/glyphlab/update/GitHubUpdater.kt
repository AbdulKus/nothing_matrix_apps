package com.abdulkus.glyphlab.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.abdulkus.glyphlab.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object GitHubUpdater {
    private const val REPO = "AbdulKus/nothing_matrix_apps"
    private const val REF_API = "https://api.github.com/repos/$REPO/git/ref/tags/nightly"
    private const val RELEASE_API = "https://api.github.com/repos/$REPO/releases/tags/nightly"
    private const val USER_AGENT = "GlyphLab/${BuildConfig.VERSION_NAME}"
    private const val PREFS = "glyphlab_updates"
    private const val PENDING_APK = "pending_apk"

    data class UpdateInfo(
        val sha: String,
        val shortSha: String,
        val apkUrl: String
    )

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        val localSha = BuildConfig.GIT_SHA.trim()
        if (localSha.isBlank() || localSha == "unknown") return@withContext null

        val ref = JSONObject(getText(REF_API))
        val remoteSha = ref.getJSONObject("object").getString("sha")
        if (remoteSha == localSha) return@withContext null

        val release = JSONObject(getText(RELEASE_API))
        val assets = release.getJSONArray("assets")
        var apkUrl: String? = null
        for (index in 0 until assets.length()) {
            val asset = assets.getJSONObject(index)
            if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                apkUrl = asset.getString("browser_download_url")
                break
            }
        }
        if (apkUrl.isNullOrBlank()) return@withContext null

        UpdateInfo(remoteSha, remoteSha.take(7), apkUrl)
    }

    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val targetDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val partial = File(targetDir, "GlyphLab-nightly.apk.part")
        val target = File(targetDir, "GlyphLab-nightly.apk")
        partial.delete()

        val connection = open(info.apkUrl)
        val total = connection.contentLengthLong.coerceAtLeast(0L)
        connection.inputStream.use { input ->
            partial.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var copied = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    if (total > 0L) onProgress((copied.toDouble() / total).toFloat().coerceIn(0f, 1f))
                }
            }
        }
        connection.disconnect()
        if (target.exists()) target.delete()
        if (!partial.renameTo(target)) {
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }
        onProgress(1f)
        target
    }

    fun install(context: Context, apk: File): Boolean {
        if (!apk.exists()) return false
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(PENDING_APK, apk.absolutePath).apply()
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                )
            )
            return false
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(PENDING_APK).apply()
        openInstaller(context, apk)
        return true
    }

    fun resumePendingInstall(context: Context): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val path = prefs.getString(PENDING_APK, null) ?: return false
        val apk = File(path)
        prefs.edit().remove(PENDING_APK).apply()
        if (!apk.exists()) return false
        openInstaller(context, apk)
        return true
    }

    private fun openInstaller(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
    }

    private fun getText(url: String): String {
        val connection = open(url)
        return connection.inputStream.bufferedReader().use { it.readText() }.also { connection.disconnect() }
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", USER_AGENT)
        connect()
        if (responseCode !in 200..299) {
            val code = responseCode
            disconnect()
            error("HTTP $code")
        }
    }
}
