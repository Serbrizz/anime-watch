package com.example.animewatch.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.animewatch.data.CorruptDownloadException
import com.example.animewatch.data.ServiceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Una versione più recente di quella installata, con l'APK da scaricare. */
data class AppRelease(
    val version: String,
    /** Note di tutte le versioni mancanti, dalla più recente ([version]) a quella subito dopo l'installata. */
    val changes: List<ReleaseNotes>,
    val apkUrl: String,
    val size: Long,
    /** sha256 esadecimale dell'APK, se GitHub lo fornisce. */
    val sha256: String?,
)

enum class InstallResult {
    /** Installer di Android aperto: l'utente conferma lì. */
    STARTED,

    /** Aperte le impostazioni "installa app sconosciute": l'installer parte al ritorno ([AppUpdater.resumePendingInstall]). */
    NEEDS_PERMISSION,
}

/**
 * Aggiornamenti dell'app dalle release GitHub di [repo] ("owner/nome"): ogni release (tag "vX.Y.Z", creata da
 * release.ps1) allega [ASSET_NAME]. L'APK nuovo deve avere la stessa firma di quello installato e un versionCode
 * maggiore, altrimenti Android rifiuta l'installazione.
 */
class AppUpdater(
    context: Context,
    private val api: GitHubApi,
    private val http: OkHttpClient,
    private val repo: String,
    val currentVersion: String,
) {
    private val context = context.applicationContext
    private val prefs = context.getSharedPreferences("update", Context.MODE_PRIVATE)

    /** Cartella dell'APK scaricato, esposta all'installer dal FileProvider (res/xml/update_paths.xml). */
    private val dir = File(context.filesDir, "update")

    /** APK in attesa del permesso di installare app: vive quanto il processo, come l'AppContainer. */
    @Volatile
    private var pendingApk: File? = null

    /** Versione di cui è già stato mostrato il popup: compare una sola volta per versione. */
    var seenVersion: String?
        get() = prefs.getString(KEY_SEEN, null)
        set(value) = prefs.edit().putString(KEY_SEEN, value).apply()

    /**
     * La release più recente se è successiva a quella installata e allega l'APK, con le note di tutte le versioni
     * saltate (al massimo le ultime 30 release); null se l'app è aggiornata.
     */
    suspend fun latestNewer(): AppRelease? {
        val (owner, name) = repo.split('/', limit = 2)
        val response = api.releases(owner, name)
        if (response.code() == 404) return null // repository privato o inesistente: per l'app nessuna release
        if (!response.isSuccessful) throw ServiceException("GitHub ha risposto HTTP ${response.code()}")
        val all = response.body() ?: throw ServiceException("risposta di GitHub vuota")
        val newer = PendingReleases.newerThan(all, currentVersion, ASSET_NAME)
        val release = newer.firstOrNull() ?: return null
        val apk = release.assets.first { it.name.equals(ASSET_NAME, ignoreCase = true) }
        return AppRelease(
            version = PendingReleases.versionOf(release),
            changes = newer.map { ReleaseNotes(PendingReleases.versionOf(it), it.body.orEmpty().trim()) },
            apkUrl = apk.url,
            size = apk.size,
            sha256 = apk.digest?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:"),
        )
    }

    /**
     * Scarica l'APK su un file temporaneo e lo rinomina solo dopo averne verificato dimensione e sha256:
     * un download interrotto o corrotto non arriva mai all'installer. [onProgress] riceve la frazione scaricata
     * (null se la dimensione non è nota).
     */
    suspend fun download(release: AppRelease, onProgress: (Float?) -> Unit): File = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val dest = File(dir, ASSET_NAME)
        val tmp = File(dir, "$ASSET_NAME.part")
        val request = Request.Builder().url(release.apkUrl).build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw ServiceException("download fallito: HTTP ${response.code}")
                val body = response.body ?: throw CorruptDownloadException("download vuoto")
                val total = body.contentLength().takeIf { it > 0 } ?: release.size
                val digest = MessageDigest.getInstance("SHA-256")
                var read = 0L
                body.byteStream().use { input ->
                    tmp.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            read += n
                            onProgress(if (total > 0) read.toFloat() / total else null)
                        }
                    }
                }
                if (release.size > 0 && read != release.size) {
                    throw CorruptDownloadException("download incompleto ($read di ${release.size} byte)")
                }
                val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
                if (release.sha256 != null && !release.sha256.equals(sha256, ignoreCase = true)) {
                    throw CorruptDownloadException("il file scaricato è corrotto (impronta sha256 diversa)")
                }
            }
            dest.delete()
            if (!tmp.renameTo(dest)) throw IOException("impossibile salvare l'aggiornamento")
            dest
        } finally {
            tmp.delete()
        }
    }

    /** Apre l'installer di Android sull'APK; la prima volta Android chiede di consentire l'installazione di app. */
    fun install(apk: File): InstallResult {
        if (!context.packageManager.canRequestPackageInstalls()) {
            pendingApk = apk
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return InstallResult.NEEDS_PERMISSION
        }
        launchInstaller(apk)
        return InstallResult.STARTED
    }

    /**
     * Al ritorno nell'app: se un APK aspettava il permesso e ora c'è, apre l'installer. Se il permesso è stato negato
     * l'APK si dimentica (si riprova da "Aggiorna"). true se l'installer è stato aperto.
     */
    fun resumePendingInstall(): Boolean {
        val apk = pendingApk ?: return false
        pendingApk = null
        if (!context.packageManager.canRequestPackageInstalls() || !apk.isFile) return false
        launchInstaller(apk)
        return true
    }

    /** Toglie l'APK di un aggiornamento precedente (già installato o annullato). */
    fun cleanup() {
        if (pendingApk == null) dir.deleteRecursively()
    }

    private fun launchInstaller(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    companion object {
        /** Nome dell'APK allegato a ogni release (release.ps1 lo carica con questo nome). */
        const val ASSET_NAME = "AnimeWatch.apk"
        private const val KEY_SEEN = "seenVersion"
    }
}
