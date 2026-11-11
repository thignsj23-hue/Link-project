package com.link.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Checks the public GitHub Releases channel and hands a signed APK to Android's installer. */
object InAppUpdater {
    private const val RELEASE_URL = "https://api.github.com/repos/thignsj23-hue/Link-project/releases/latest"
    private const val APK_NAME = "LINK-release.apk"

    suspend fun checkAndInstall(context: Context): String = withContext(Dispatchers.IO) {
        val release = requestJson(RELEASE_URL)
        val latestVersion = release.optString("tag_name").removePrefix("v").trim()
        require(latestVersion.isNotBlank()) { "A versão publicada não foi informada." }
        val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        if (compareVersions(latestVersion, currentVersion) <= 0) return@withContext "O LINK já está atualizado (v$currentVersion)."

        val apkUrl = release.optJSONArray("assets")?.let { assets ->
            (0 until assets.length()).map { assets.optJSONObject(it) }
                .firstOrNull { it?.optString("name") == APK_NAME }
                ?.optString("browser_download_url")
        }.orEmpty()
        require(apkUrl.startsWith("https://github.com/")) { "A atualização publicada não contém o APK oficial do LINK." }

        if (!context.packageManager.canRequestPackageInstalls()) {
            withContext(Dispatchers.Main) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            }
            return@withContext "Autorize o LINK a instalar atualizações e toque novamente em Atualizar."
        }

        val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apk = File(updateDir, APK_NAME)
        download(apkUrl, apk)
        withContext(Dispatchers.Main) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
            val install = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(install)
        }
        "Atualização v$latestVersion baixada. Confirme a instalação na tela do Android."
    }

    private fun requestJson(address: String): JSONObject {
        val connection = (URL(address).openConnection() as HttpsURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "LINK-Android-Updater")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException(if (code == 404) "Ainda não há uma versão oficial publicada." else "Não consegui consultar atualizações (HTTP $code).")
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }
    }

    private fun download(address: String, destination: File) {
        val connection = (URL(address).openConnection() as HttpsURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 60000
            setRequestProperty("User-Agent", "LINK-Android-Updater")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("Não consegui baixar a atualização (HTTP $code).")
            connection.inputStream.use { input -> destination.outputStream().use(input::copyTo) }
            require(destination.length() > 1_000_000L) { "O arquivo recebido não parece ser um APK válido." }
        } catch (error: Exception) {
            destination.delete()
            throw error
        } finally { connection.disconnect() }
    }

    private fun compareVersions(left: String, right: String): Int {
        val a = left.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val b = right.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(a.size, b.size)) {
            val comparison = (a.getOrElse(index) { 0 }).compareTo(b.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
    }
}
