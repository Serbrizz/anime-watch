package com.example.animewatch.ui

import android.content.Context
import android.util.Log
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import androidx.lifecycle.viewModelScope
import com.example.animewatch.data.model.Anime
import com.example.animewatch.data.model.AnimeList
import com.example.animewatch.data.model.Franchise
import com.example.animewatch.data.model.ListInvite
import com.example.animewatch.data.model.ListRole
import com.example.animewatch.data.model.UserProfile
import com.example.animewatch.data.model.WatchProgress
import com.example.animewatch.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AuthState {
    /** Firebase non ha ancora detto se c'è una sessione salvata: dura una frazione di secondo all'avvio. */
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: UserProfile) : AuthState
}

/**
 * Stato condiviso da tutte le schermate: utente, liste, inviti, progressi e schede anime in memoria.
 * Liste e progressi arrivano da Firestore come flussi in tempo reale (anche offline, dalla cache
 * locale), quindi la UI non deve mai "ricaricare" dopo una modifica.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(private val container: AppContainer) : ViewModel() {

    val authState: StateFlow<AuthState> = container.auth.currentUser
        .map { if (it == null) AuthState.SignedOut else AuthState.SignedIn(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Loading)

    val user: StateFlow<UserProfile?> = authState
        .map { (it as? AuthState.SignedIn)?.user }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val uid: String? get() = user.value?.uid

    private val _signingIn = MutableStateFlow(false)
    val signingIn: StateFlow<Boolean> = _signingIn.asStateFlow()
    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Tutte le liste di cui l'utente è membro: sue e condivise con lui. */
    val lists: StateFlow<List<AnimeList>> = perUser(emptyList(), "delle liste") { container.listData.observeLists(it.uid) }

    /** Solo le liste in cui l'utente può aggiungere anime (non quelle in sola visualizzazione). */
    val editableLists: StateFlow<List<AnimeList>> = combine(lists, user) { all, u -> all.filter { it.canEdit(u?.uid) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val incomingInvites: StateFlow<List<ListInvite>> =
        perUser(emptyList(), "degli inviti") { container.listData.observeIncomingInvites(it.email) }

    val progress: StateFlow<Map<Int, WatchProgress>> =
        perUser(emptyMap(), "dei progressi") { container.userData.observeProgress(it.uid) }

    private val _anime = MutableStateFlow<Map<Int, Anime>>(emptyMap())
    val anime: StateFlow<Map<Int, Anime>> = _anime.asStateFlow()

    /** Per ogni scheda già risolta, il franchise (tutte le stagioni) a cui appartiene. */
    private val _franchises = MutableStateFlow<Map<Int, Franchise>>(emptyMap())
    val franchises: StateFlow<Map<Int, Franchise>> = _franchises.asStateFlow()

    /** ID in corso di risoluzione: evita di ripercorrere la stessa catena da più schermate e mostra l'attesa. */
    private val _resolving = MutableStateFlow<Set<Int>>(emptySet())
    val resolving: StateFlow<Set<Int>> = _resolving.asStateFlow()

    /** Invito che si sta accettando (per mostrare l'attesa sul pulsante giusto). */
    private val _accepting = MutableStateFlow<String?>(null)
    val accepting: StateFlow<String?> = _accepting.asStateFlow()

    init {
        viewModelScope.launch {
            user.filterNotNull().distinctUntilChangedBy { it.uid }.collect { u ->
                runCatching { container.listData.migrateLegacyLists(u) }
                    .onFailure { if (it is CancellationException) throw it else Log.w(TAG, "Migrazione liste", it) }
            }
        }
    }

    /**
     * Flusso legato all'utente collegato. Il catch sta dentro flatMapLatest: un errore chiude solo il
     * flusso di quell'utente, non quello esterno, così dopo un nuovo accesso la sincronizzazione riparte.
     */
    private fun <T> perUser(empty: T, what: String, source: (UserProfile) -> Flow<T>): StateFlow<T> = user
        .distinctUntilChangedBy { it?.uid }
        .flatMapLatest { u ->
            if (u == null) {
                flowOf(empty)
            } else {
                source(u).catch {
                    reportSyncError(u.uid, "Sincronizzazione $what non riuscita: ${it.message}")
                    emit(empty)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, empty)

    /** All'uscita i listener del vecchio utente possono fallire per permessi: quell'errore non va mostrato. */
    private fun reportSyncError(forUid: String, message: String) {
        if (uid == forUid) _error.value = message
    }

    // ---------- Accesso ----------

    /** Il context dell'Activity serve solo per mostrare il selettore degli account, non viene conservato. */
    fun signInWithGoogle(activityContext: Context) {
        if (_signingIn.value) return
        viewModelScope.launch {
            _signingIn.value = true
            _loginError.value = null
            try {
                container.auth.signInWithGoogle(activityContext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _loginError.value = signInErrorMessage(e)
            } finally {
                _signingIn.value = false
            }
        }
    }

    /**
     * I messaggi di Credential Manager e Firebase sono tecnici e in inglese: per i casi comuni si dice
     * all'utente cosa fare. Il testo originale resta in fondo e in Crashlytics, per la diagnosi.
     */
    private fun signInErrorMessage(e: Exception): String {
        val detail = e.message.orEmpty()
        val hint = when {
            e is NoCredentialException ->
                "Nessun account Google disponibile: aggiungine uno nelle impostazioni del telefono e riprova."
            e is GetCredentialProviderConfigurationException || e is GetCredentialUnsupportedException ->
                "Questo telefono non supporta l'accesso con Google: servono i Google Play Services aggiornati."
            e is GetCredentialInterruptedException -> "Accesso interrotto, riprova."
            // DEVELOPER_ERROR: l'APK è firmato con una chiave il cui SHA-1 non è registrato in Firebase.
            "28444" in detail || "Developer console" in detail ->
                "Questa copia dell'app non è autorizzata per l'accesso con Google (SHA-1 della firma non " +
                    "registrato in Firebase). Installa l'APK ufficiale."
            e is FirebaseNetworkException || "network" in detail.lowercase() ->
                "Serve la connessione a internet per il primo accesso."
            e is FirebaseTooManyRequestsException -> "Troppi tentativi, riprova fra qualche minuto."
            e is FirebaseAuthInvalidUserException -> "Questo account è stato disabilitato."
            else -> null
        }
        return if (hint != null) "$hint\n($detail)" else "Accesso non riuscito: $detail"
    }

    fun signOut() {
        _error.value = null
        viewModelScope.launch { container.auth.signOut() }
    }

    fun dismissError() {
        _error.value = null
    }

    /** Carica (cache -> Firestore -> AniList) le schede non ancora in memoria. */
    fun ensureAnime(ids: Collection<Int>) {
        val missing = ids.filter { it !in _anime.value }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val loaded = container.animeRepo.getMany(missing)
            _anime.update { it + loaded }
        }
    }

    /** Carica le schede e tutte le stagioni dei loro franchise (per le liste e per il selettore di stagione). */
    fun ensureFranchises(ids: Collection<Int>) {
        val missing = ids.filter { it !in _franchises.value && it !in _resolving.value }.distinct()
        if (missing.isEmpty()) return
        _resolving.update { it + missing }
        viewModelScope.launch {
            try {
                resolve(missing)
            } finally {
                _resolving.update { it - missing.toSet() }
            }
        }
    }

    /** Le serie già complete si pubblicano a ogni ondata, le altre alla fine (anche se i limiti le hanno troncate). */
    private suspend fun resolve(ids: Collection<Int>): Map<Int, Franchise> {
        val related = container.animeRepo.getWithRelated(ids) { found, pending ->
            publish(found, Franchise.groupComplete(found, pending))
        }
        val groups = Franchise.group(related)
        publish(related, groups)
        return groups
    }

    private fun publish(animes: Map<Int, Anime>, groups: Map<Int, Franchise>) {
        if (groups.isEmpty()) return
        _anime.update { it + groups.keys.associateWith { id -> animes.getValue(id) } }
        _franchises.update { it + groups }
    }

    /** Senza rete la catena può restare incompleta: la scheda vale allora come franchise a sé. */
    private suspend fun franchiseOf(id: Int): Franchise =
        _franchises.value[id]
            ?: runCatching { resolve(listOf(id))[id] }
                .onFailure { if (it is CancellationException) throw it else Log.w(TAG, "Stagioni di $id", it) }
                .getOrNull()
            ?: Franchise.single(id)

    // ---------- Liste ----------

    fun createList(name: String) {
        val u = user.value ?: return
        val clean = name.trim()
        if (clean.isNotEmpty()) container.listData.createList(u, clean)
    }

    /** Lista nuova che contiene già [anime], salvato come la sua serie (vedi [setInList]). */
    fun createListWith(name: String, anime: Anime) {
        val u = user.value ?: return
        val clean = name.trim()
        if (clean.isEmpty()) return
        container.animeRepo.cache(listOf(anime))
        _anime.update { it + (anime.id to anime) }
        viewModelScope.launch {
            container.listData.createList(u, clean, listOf(franchiseOf(anime.id).rootId))
        }
    }

    /** Il proprietario la elimina per tutti; gli altri membri escono soltanto. */
    fun deleteOrLeaveList(list: AnimeList) {
        val u = uid ?: return
        if (list.isOwner(u)) {
            viewModelScope.launch { container.listData.deleteList(u, list.id) }
        } else {
            container.listData.removeMember(list.id, u)
        }
    }

    /** Stagioni del franchise di [animeId] presenti nella lista (più d'una solo nelle liste di prima). */
    fun idsInList(list: AnimeList, animeId: Int): List<Int> {
        val seasons = _franchises.value[animeId]?.seasonIds ?: listOf(animeId)
        return list.animeIds.filter { it in seasons }
    }

    /**
     * Nella lista va il franchise, salvato con l'ID della prima stagione: aggiungere la S2 di una serie
     * già presente non crea un doppione. Togliere rimuove tutte le sue stagioni.
     */
    fun setInList(list: AnimeList, anime: Anime, add: Boolean) {
        if (!add) {
            container.listData.removeFromList(list.id, idsInList(list, anime.id))
            return
        }
        container.animeRepo.cache(listOf(anime)) // la scheda entra nella cache condivisa
        _anime.update { it + (anime.id to anime) }
        viewModelScope.launch {
            val franchise = franchiseOf(anime.id)
            if (list.animeIds.none { it in franchise.seasonIds }) {
                container.listData.addToList(list.id, franchise.rootId)
            }
        }
    }

    fun removeFromList(list: AnimeList, franchise: Franchise) {
        container.listData.removeFromList(list.id, list.animeIds.filter { it in franchise.seasonIds })
    }

    // ---------- Condivisione ----------

    fun sendInvite(list: AnimeList, email: String, role: ListRole) {
        val u = user.value ?: return
        if (list.isOwner(u.uid)) container.listData.sendInvite(list, u, email, role)
    }

    fun revokeInvite(invite: ListInvite) = container.listData.deleteInvite(invite)

    fun declineInvite(invite: ListInvite) = container.listData.deleteInvite(invite)

    fun acceptInvite(invite: ListInvite) {
        val u = user.value ?: return
        if (_accepting.value != null) return
        // Già membro (es. invitato due volte): le regole rifiuterebbero l'ingresso, basta togliere l'invito.
        if (lists.value.any { it.id == invite.listId }) {
            container.listData.deleteInvite(invite)
            return
        }
        viewModelScope.launch {
            _accepting.value = invite.id
            try {
                container.listData.acceptInvite(invite, u)
            } catch (e: TimeoutCancellationException) {
                _error.value = "Per accettare un invito serve la connessione."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Invito non accettato", e)
                _error.value = "Invito non più valido: la lista potrebbe essere stata eliminata."
            } finally {
                _accepting.value = null
            }
        }
    }

    fun setMemberRole(list: AnimeList, memberUid: String, role: ListRole) {
        if (list.isOwner(uid) && memberUid != list.ownerId) container.listData.setMemberRole(list.id, memberUid, role)
    }

    fun removeMember(list: AnimeList, memberUid: String) {
        if (list.isOwner(uid) && memberUid != list.ownerId) container.listData.removeMember(list.id, memberUid)
    }

    /** Inviti in attesa mandati per una lista: servono solo nella schermata dei membri. */
    fun outgoingInvites(listId: String): Flow<List<ListInvite>> {
        val u = uid ?: return flowOf(emptyList())
        return container.listData.observeOutgoingInvites(u, listId).catch { emit(emptyList()) }
    }

    // ---------- Puntate e stagioni ----------

    fun toggleEpisode(anime: Anime, episode: Int) {
        val current = progress.value[anime.id]?.watchedEpisodes.orEmpty()
        save(anime, if (episode in current) current - episode else current + episode)
    }

    /** "Stagione vista": segna tutte le puntate disponibili. */
    fun markAllWatched(anime: Anime) = save(anime, (1..anime.availableEpisodes).toSet())

    fun clearProgress(anime: Anime) = save(anime, emptySet())

    private fun save(anime: Anime, watched: Set<Int>) {
        val u = uid ?: return
        container.userData.saveProgress(
            u,
            WatchProgress(
                animeId = anime.id,
                watchedEpisodes = watched,
                genres = anime.genres,
                totalEpisodes = anime.availableEpisodes,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private companion object {
        const val TAG = "AppViewModel"
    }
}
