package com.example.animewatch.ui.update

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.animewatch.data.update.AppRelease
import com.example.animewatch.data.update.AppUpdater
import com.example.animewatch.data.update.InstallResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UpdateState(
    /** Versione più recente disponibile, null se l'app è aggiornata (o non si è ancora controllato). */
    val available: AppRelease? = null,
    val dialogOpen: Boolean = false,
    /** Controllo su GitHub in corso (anche quello automatico). */
    val checking: Boolean = false,
    val downloading: Boolean = false,
    /** Frazione scaricata, null se non nota. */
    val progress: Float? = null,
    val error: String? = null,
)

/**
 * Controllo degli aggiornamenti: all'avvio e a ogni ritorno nell'app, ma al massimo ogni 6 ore dopo un controllo
 * riuscito (ogni 2 minuti dopo uno fallito, tipicamente offline). Il popup compare da solo una volta per versione;
 * dopo resta "Aggiorna" nel Profilo.
 */
class UpdateViewModel(private val updater: AppUpdater) : ViewModel() {
    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Messaggi brevi da mostrare come toast (esito di "Verifica aggiornamenti", installazione avviata). */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val currentVersion: String get() = updater.currentVersion

    private var checkJob: Job? = null
    private var nextCheckAt = 0L

    init {
        updater.cleanup()
    }

    /** Da chiamare a ogni ON_RESUME dell'Activity. */
    fun onResume() {
        updater.resumePendingInstall()
        if (SystemClock.elapsedRealtime() >= nextCheckAt) check(manual = false)
    }

    fun checkNow() = check(manual = true)

    private fun check(manual: Boolean) {
        if (checkJob?.isActive == true) return
        checkJob = viewModelScope.launch {
            _state.update { it.copy(checking = true) }
            val release = try {
                updater.latestNewer().also { nextCheckAt = SystemClock.elapsedRealtime() + RECHECK_MS }
            } catch (e: CancellationException) {
                _state.update { it.copy(checking = false) }
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "controllo aggiornamenti fallito", e)
                nextCheckAt = SystemClock.elapsedRealtime() + RETRY_MS
                _state.update { it.copy(checking = false) }
                if (manual) _messages.tryEmit("Impossibile verificare gli aggiornamenti: sei connesso a Internet?")
                return@launch
            }
            _state.update { s ->
                val isNew = release != null && release.version != s.available?.version
                val open = when {
                    release == null -> s.downloading
                    manual -> true
                    else -> s.dialogOpen || (isNew && release.version != updater.seenVersion)
                }
                s.copy(available = release ?: s.available.takeIf { s.downloading }, dialogOpen = open, checking = false)
            }
            if (manual && release == null) _messages.tryEmit("Hai già l'ultima versione ($currentVersion).")
        }
    }

    fun openDialog() = _state.update { if (it.available != null) it.copy(dialogOpen = true, error = null) else it }

    /** "Più tardi": il popup non ricompare da solo per questa versione. */
    fun dismissDialog() {
        val s = _state.value
        if (s.downloading) return
        s.available?.let { updater.seenVersion = it.version }
        _state.update { it.copy(dialogOpen = false, error = null) }
    }

    fun install() {
        val s = _state.value
        val release = s.available ?: return
        if (s.downloading) return
        _state.update { it.copy(downloading = true, progress = null, error = null) }
        viewModelScope.launch {
            try {
                val apk = updater.download(release) { p -> _state.update { it.copy(progress = p) } }
                updater.seenVersion = release.version
                val message = when (updater.install(apk)) {
                    InstallResult.STARTED -> "Conferma l'installazione nella schermata di Android."
                    InstallResult.NEEDS_PERMISSION ->
                        "Consenti ad AnimeWatch di installare app, poi torna qui: l'installazione partirà da sola."
                }
                _state.update { it.copy(downloading = false, dialogOpen = false) }
                _messages.tryEmit(message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "aggiornamento non riuscito", e)
                _state.update { it.copy(downloading = false, error = "Aggiornamento non riuscito: ${e.message ?: e}") }
            }
        }
    }

    private companion object {
        const val TAG = "Update"
        const val RECHECK_MS = 6 * 60 * 60 * 1000L
        const val RETRY_MS = 2 * 60 * 1000L
    }
}
