package com.example.animewatch.data

import android.util.Log
import com.example.animewatch.data.model.Anime
import com.example.animewatch.data.remote.anilist.AniListClient
import java.util.concurrent.ConcurrentHashMap

/**
 * Accesso ai metadati degli anime con tre livelli, dal più veloce al più lento:
 *   1. memoria (per sessione)
 *   2. [AnimeStore] (oggi file JSON, domani il database)
 *   3. AniList (fonte di verità, ~90 richieste/min)
 *
 * Il risultato di ogni chiamata ad AniList viene salvato nello store: la volta dopo non serve la rete.
 */
class AnimeRepository(
    private val aniList: AniListClient,
    private val store: AnimeStore,
) {
    private val memory = ConcurrentHashMap<Int, Anime>()

    suspend fun search(text: String): List<Anime> =
        aniList.search(text).also { cache(it) }

    suspend fun getMany(ids: Collection<Int>): Map<Int, Anime> {
        val wanted = ids.distinct()
        val result = HashMap<Int, Anime>()
        for (id in wanted) {
            memory[id]?.takeIf { !it.isStale() }?.let { result[id] = it }
        }

        // Livello 2: store locale.
        val stale = HashMap<Int, Anime>()
        val fromStore = runCatching { store.get(wanted.filter { it !in result }) }
            .onFailure { Log.w(TAG, "Lettura dallo store fallita", it) }
            .getOrDefault(emptyList())
        for (anime in fromStore) {
            if (anime.isStale()) stale[anime.id] = anime else result[anime.id] = anime
        }

        // Livello 3: AniList per ciò che manca o è scaduto, 50 ID per richiesta.
        for (chunk in wanted.filter { it !in result }.chunked(50)) {
            val fresh = runCatching { aniList.byIds(chunk) }
                .onFailure { Log.w(TAG, "Lettura AniList fallita", it) }
                .getOrDefault(emptyList())
            fresh.forEach { result[it.id] = it }
            store.put(fresh)
        }

        // Se AniList non risponde, meglio un dato vecchio che nessun dato.
        for ((id, anime) in stale) result.putIfAbsent(id, anime)

        memory.putAll(result)
        return result
    }

    suspend fun get(id: Int): Anime? = getMany(listOf(id))[id]

    /** Tiene le schede in memoria e le salva nello store. */
    fun cache(animes: Collection<Anime>) {
        if (animes.isEmpty()) return
        animes.forEach { memory[it.id] = it }
        store.put(animes)
    }

    private companion object {
        const val TAG = "AnimeRepository"
    }
}
