package com.example.animewatch.data

import com.example.animewatch.data.model.Anime
import com.example.animewatch.data.model.AnimeList
import com.example.animewatch.data.model.WatchProgress
import kotlinx.coroutines.flow.Flow

/*
 * PUNTO DI SOSTITUZIONE DEL DATABASE.
 *
 * Queste due interfacce sono tutto ciò che l'app sa della persistenza. Oggi sono implementate
 * da file JSON (data/local/). Quando il DB vero è pronto basta:
 *   1. scrivere una classe che implementa UserStore e una che implementa AnimeStore;
 *   2. cambiare le due righe in di/AppContainer.kt che le istanziano;
 *   3. eliminare la cartella data/local/.
 * UI, ViewModel e algoritmo dei consigli non cambiano.
 */

/** Dati dell'utente: liste e puntate viste. I flussi devono riemettere a ogni modifica. */
interface UserStore {
    fun observeLists(): Flow<List<AnimeList>>
    fun createList(name: String)
    fun deleteList(listId: String)
    fun addToList(listId: String, animeId: Int)
    fun removeFromList(listId: String, animeId: Int)

    fun observeProgress(): Flow<Map<Int, WatchProgress>>

    /** Se `watchedEpisodes` è vuoto il progresso di quell'anime va rimosso. */
    fun saveProgress(progress: WatchProgress)
}

/** Copia locale delle schede anime già scaricate da AniList (evita di richiamare l'API). */
interface AnimeStore {
    /** Restituisce le schede trovate; gli ID sconosciuti vengono semplicemente omessi. */
    suspend fun get(ids: Collection<Int>): List<Anime>

    /** Salva o sostituisce le schede. Non deve bloccare il chiamante. */
    fun put(animes: Collection<Anime>)
}
