package com.example.animewatch.data.local

import com.example.animewatch.data.AnimeStore
import com.example.animewatch.data.UserStore
import com.example.animewatch.data.model.Anime
import com.example.animewatch.data.model.AnimeList
import com.example.animewatch.data.model.WatchProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID

/*
 * PERSISTENZA TEMPORANEA su file JSON, in attesa del database vero (vedi data/Stores.kt).
 *
 *   database/anime/{anilistId}.json   una scheda per file, cioè il risultato delle chiamate AniList
 *   database/lists.json               tutte le liste dell'utente
 *   database/progress.json            tutte le puntate viste
 *
 * Le due collezioni piccole (liste e progressi) vivono in memoria come StateFlow: la UI si
 * aggiorna subito e il salvataggio su disco avviene in background.
 */

private val LISTS_SERIALIZER = ListSerializer(AnimeList.serializer())
private val PROGRESS_SERIALIZER = ListSerializer(WatchProgress.serializer())

class JsonAnimeStore(
    private val files: JsonStore,
    private val scope: CoroutineScope,
) : AnimeStore {

    override suspend fun get(ids: Collection<Int>): List<Anime> = withContext(Dispatchers.IO) {
        ids.mapNotNull { files.read("anime/$it.json", Anime.serializer()) }
    }

    override fun put(animes: Collection<Anime>) {
        val copy = animes.toList() // la collezione del chiamante potrebbe cambiare prima della scrittura
        scope.launch {
            copy.forEach { files.write("anime/${it.id}.json", Anime.serializer(), it) }
        }
    }
}

class JsonUserStore(
    private val files: JsonStore,
    private val scope: CoroutineScope,
) : UserStore {

    private val lists = MutableStateFlow(
        files.read(LISTS_FILE, LISTS_SERIALIZER).orEmpty()
    )
    private val progress = MutableStateFlow(
        files.read(PROGRESS_FILE, PROGRESS_SERIALIZER).orEmpty().associateBy { it.animeId }
    )

    // Il valore da scrivere si legge DENTRO il lock: così l'ultima scrittura a finire
    // contiene sempre lo stato più recente, anche se due salvataggi partono quasi insieme.
    private val listsLock = Mutex()
    private val progressLock = Mutex()

    override fun observeLists(): Flow<List<AnimeList>> = lists

    override fun createList(name: String) {
        val list = AnimeList(
            id = UUID.randomUUID().toString(),
            name = name,
            createdAt = System.currentTimeMillis(),
            animeIds = emptyList(),
        )
        lists.update { it + list }
        persistLists()
    }

    override fun deleteList(listId: String) {
        lists.update { all -> all.filterNot { it.id == listId } }
        persistLists()
    }

    override fun addToList(listId: String, animeId: Int) {
        lists.update { all ->
            all.map { if (it.id == listId && animeId !in it.animeIds) it.copy(animeIds = it.animeIds + animeId) else it }
        }
        persistLists()
    }

    override fun removeFromList(listId: String, animeId: Int) {
        lists.update { all ->
            all.map { if (it.id == listId) it.copy(animeIds = it.animeIds - animeId) else it }
        }
        persistLists()
    }

    override fun observeProgress(): Flow<Map<Int, WatchProgress>> = progress

    override fun saveProgress(progress: WatchProgress) {
        this.progress.update { all ->
            if (progress.watchedEpisodes.isEmpty()) all - progress.animeId else all + (progress.animeId to progress)
        }
        persistProgress()
    }

    private fun persistLists() {
        scope.launch {
            listsLock.withLock { files.write(LISTS_FILE, LISTS_SERIALIZER, lists.value) }
        }
    }

    private fun persistProgress() {
        scope.launch {
            progressLock.withLock { files.write(PROGRESS_FILE, PROGRESS_SERIALIZER, progress.value.values.toList()) }
        }
    }

    private companion object {
        const val LISTS_FILE = "lists.json"
        const val PROGRESS_FILE = "progress.json"
    }
}
