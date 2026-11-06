package com.example.animewatch.data.model

import kotlinx.serialization.Serializable

/** Un giorno in millisecondi, per i TTL della cache. */
private const val DAY_MS = 24L * 60 * 60 * 1000

/** Scheda anime: copia normalizzata dei dati AniList (e documento della cache condivisa). */
@Serializable
data class Anime(
    val id: Int,
    /** Titolo romaji (sempre presente). */
    val title: String,
    val titleEnglish: String?,
    val coverUrl: String?,
    val genres: List<String>,
    /** Puntate totali previste; null se AniList non le conosce (serie in corso). */
    val episodes: Int?,
    /** Puntate già uscite per le serie in corso (da nextAiringEpisode - 1). */
    val airedEpisodes: Int?,
    /** FINISHED, RELEASING, NOT_YET_RELEASED, CANCELLED, HIATUS. */
    val status: String?,
    val season: String?,
    val seasonYear: Int?,
    val averageScore: Int?,
    val description: String?,
    val cachedAt: Long = System.currentTimeMillis(),
) {
    val isFinished: Boolean get() = status == "FINISHED"

    /** Puntate che l'utente può effettivamente guardare adesso. */
    val availableEpisodes: Int
        get() = when (status) {
            "NOT_YET_RELEASED" -> 0
            "RELEASING" -> airedEpisodes ?: episodes ?: 0
            else -> episodes ?: airedEpisodes ?: 0
        }

    /** Una stagione è "vista" solo se è conclusa e tutte le puntate sono segnate. */
    fun isSeasonComplete(watchedCount: Int): Boolean =
        isFinished && availableEpisodes > 0 && watchedCount >= availableEpisodes

    /** La cache vale poco per le serie in corso (nuove puntate), molto per le concluse. */
    fun isStale(now: Long = System.currentTimeMillis()): Boolean {
        val ttl = if (isFinished) 30L * DAY_MS else DAY_MS / 2
        return now - cachedAt > ttl
    }
}

/** Progresso di un utente su una scheda anime. Una sola copia, condivisa da tutte le liste. */
@Serializable
data class WatchProgress(
    val animeId: Int,
    val watchedEpisodes: Set<Int> = emptySet(),
    /** Generi copiati dall'anime: permettono di costruire il profilo senza rileggere le schede. */
    val genres: List<String> = emptyList(),
    val totalEpisodes: Int = 0,
    val updatedAt: Long = 0,
) {
    /** Frazione vista, in (0, 1]; 1 se il totale è sconosciuto ma qualcosa è stato visto. */
    val fraction: Double
        get() = when {
            watchedEpisodes.isEmpty() -> 0.0
            totalEpisodes <= 0 -> 1.0
            else -> (watchedEpisodes.size.toDouble() / totalEpisodes).coerceAtMost(1.0)
        }
}

/** Lista personale di anime da vedere. Contiene solo gli ID: i dati stanno nella cache. */
@Serializable
data class AnimeList(
    val id: String,
    val name: String,
    val createdAt: Long,
    val animeIds: List<Int>,
)
