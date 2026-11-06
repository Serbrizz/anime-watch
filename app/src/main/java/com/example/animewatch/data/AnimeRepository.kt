package com.example.animewatch.data

import android.util.Log
import com.example.animewatch.data.firebase.toAnime
import com.example.animewatch.data.firebase.toMap
import com.example.animewatch.data.model.Anime
import com.example.animewatch.data.remote.anilist.AniListClient
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

/**
 * Accesso ai metadati degli anime con tre livelli di cache, dal più veloce al più lento:
 *   1. memoria (per sessione)
 *   2. Firestore `anime/{id}` (condiviso tra TUTTI gli utenti dell'app)
 *   3. AniList (fonte di verità, con rate limit di ~90 richieste/min)
 *
 * Ogni scheda scaricata da AniList viene salvata in Firestore: il prossimo utente che
 * la richiede non genera nessuna chiamata esterna.
 */
class AnimeRepository(
    private val aniList: AniListClient,
    private val db: FirebaseFirestore,
) {
    private val memory = ConcurrentHashMap<Int, Anime>()
    private val collection get() = db.collection("anime")

    /** Ricerca live su AniList. I risultati restano solo in memoria: si scrivono su Firestore
     *  quando l'utente li aggiunge a una lista (evita decine di scritture per ogni ricerca). */
    suspend fun search(text: String): List<Anime> =
        aniList.search(text).also { remember(it) }

    /** Rende le schede disponibili alla UI (dettaglio) senza scrivere su Firestore. */
    fun remember(animes: Collection<Anime>) {
        animes.forEach { memory[it.id] = it }
    }

    suspend fun getMany(ids: Collection<Int>): Map<Int, Anime> {
        val wanted = ids.distinct()
        val result = HashMap<Int, Anime>()
        for (id in wanted) {
            memory[id]?.takeIf { !it.isStale() }?.let { result[id] = it }
        }

        // Livello 2: Firestore, 30 ID per query (limite di whereIn).
        val stale = HashMap<Int, Anime>()
        for (chunk in wanted.filter { it !in result }.chunked(30)) {
            val docs = runCatching {
                collection.whereIn(FieldPath.documentId(), chunk.map { it.toString() }).get().await()
            }.onFailure { Log.w(TAG, "Lettura cache Firestore fallita", it) }.getOrNull() ?: continue
            for (anime in docs.documents.mapNotNull { it.toAnime() }) {
                if (anime.isStale()) stale[anime.id] = anime else result[anime.id] = anime
            }
        }

        // Livello 3: AniList per ciò che manca o è scaduto, 50 ID per richiesta.
        for (chunk in wanted.filter { it !in result }.chunked(50)) {
            val fresh = runCatching { aniList.byIds(chunk) }
                .onFailure { Log.w(TAG, "Lettura AniList fallita", it) }
                .getOrDefault(emptyList())
            fresh.forEach { result[it.id] = it }
            cache(fresh)
        }

        // Se AniList non risponde, meglio un dato vecchio che nessun dato.
        for ((id, anime) in stale) result.putIfAbsent(id, anime)

        memory.putAll(result)
        return result
    }

    suspend fun get(id: Int): Anime? = getMany(listOf(id))[id]

    /** Scrittura in Firestore (fire-and-forget, vedi nota in UserDataSource). */
    fun cache(animes: Collection<Anime>) {
        if (animes.isEmpty()) return
        remember(animes)
        animes.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.set(collection.document(it.id.toString()), it.toMap()) }
            batch.commit().addOnFailureListener { Log.w(TAG, "Scrittura cache anime fallita", it) }
        }
    }

    private companion object {
        const val TAG = "AnimeRepository"
    }
}
