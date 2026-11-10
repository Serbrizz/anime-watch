package com.example.animewatch.data.model

/** Scheda anime: copia normalizzata dei dati AniList (e documento della cache condivisa). */
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

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

/** Progresso di un utente su una scheda anime. Una sola copia, condivisa da tutte le liste. */
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

/** Utente collegato, dall'account Google. L'email è sempre minuscola: è la chiave degli inviti. */
data class UserProfile(
    val uid: String,
    val name: String,
    val email: String,
    val photoUrl: String?,
)

/** Ruolo in una lista. Il valore `key` è quello salvato su Firestore e controllato dalle regole. */
enum class ListRole(val key: String) {
    OWNER("owner"),
    EDITOR("editor"),
    VIEWER("viewer");

    val canEdit: Boolean get() = this != VIEWER

    companion object {
        fun fromKey(key: String?): ListRole? = entries.firstOrNull { it.key == key }
    }
}

/** Nome e foto di un membro, copiati nella lista perché un utente non può leggere il profilo di un altro. */
data class MemberProfile(val name: String, val photoUrl: String?)

/**
 * Lista di anime, personale o condivisa. Contiene solo gli ID: i dati stanno nella cache.
 * Il proprietario la crea ed è l'unico a gestire membri e inviti.
 */
data class AnimeList(
    val id: String,
    val name: String,
    val createdAt: Long,
    val animeIds: List<Int>,
    val ownerId: String,
    val members: Map<String, ListRole>,
    val profiles: Map<String, MemberProfile> = emptyMap(),
) {
    fun roleOf(uid: String?): ListRole? = uid?.let { members[it] }

    fun canEdit(uid: String?): Boolean = roleOf(uid)?.canEdit == true

    fun isOwner(uid: String?): Boolean = roleOf(uid) == ListRole.OWNER

    /** Condivisa = c'è almeno un membro oltre al proprietario. */
    val isShared: Boolean get() = members.size > 1
}

/**
 * Invito in attesa verso una persona, identificata dall'email del suo account Google.
 * L'ID è `listId_email`: un solo invito per persona e lista, e le regole lo ricostruiscono senza leggerlo.
 */
data class ListInvite(
    val listId: String,
    val listName: String,
    val role: ListRole,
    val fromUid: String,
    val fromName: String,
    val toEmail: String,
    val createdAt: Long,
) {
    val id: String get() = inviteId(listId, toEmail)

    companion object {
        fun inviteId(listId: String, email: String): String = "${listId}_${email.trim().lowercase()}"

        /** Controllo minimo (qualcosa@dominio.tld, senza spazi né `/`, vietato negli ID Firestore). */
        fun isPlausibleEmail(email: String): Boolean = EMAIL.matches(email)

        private val EMAIL = Regex("^[^@\\s/]+@[^@\\s/]+\\.[^@\\s/]+$")
    }
}
