package com.example.animewatch.domain

import com.example.animewatch.data.model.Anime
import com.example.animewatch.data.model.Franchise
import com.example.animewatch.data.model.WatchProgress
import kotlin.math.sqrt

data class Recommendation(
    val anime: Anime,
    /** 0..1, più alto = più simile ai gusti dell'utente. */
    val score: Double,
    /** Generi del candidato presenti nel profilo, dal più al meno pesante. */
    val matchedGenres: List<String>,
    /** Motivazione in italiano, presente solo se il riordino AI è stato usato. */
    val reason: String? = null,
)

/**
 * Raccomandazione basata sui generi, senza rete e senza costi.
 *
 * Idea: ogni genere è una dimensione di uno spazio vettoriale. Il profilo dell'utente è un
 * vettore (peso = quanto ha guardato di quel genere), ogni candidato è un vettore binario
 * (genere presente / assente). La similarità è il coseno dell'angolo fra i due vettori:
 * 1 = stessi gusti, 0 = nessun genere in comune. Il coseno ignora la "lunghezza" dei vettori,
 * quindi non favorisce chi ha guardato molto né chi ha molti generi.
 */
object RecommendationEngine {
    /** Anche una sola puntata vista conta almeno così, altrimenti le serie lunghe pesano zero. */
    private const val MIN_WEIGHT = 0.2
    private const val GENRE_SHARE = 0.75
    private const val QUALITY_SHARE = 0.25
    private const val UNKNOWN_SCORE = 60.0

    fun buildProfile(progress: Collection<WatchProgress>): Map<String, Double> {
        val raw = HashMap<String, Double>()
        for (p in progress) {
            if (p.watchedEpisodes.isEmpty()) continue
            val weight = p.fraction.coerceAtLeast(MIN_WEIGHT)
            for (genre in p.genres) raw[genre] = (raw[genre] ?: 0.0) + weight
        }
        val max = raw.values.maxOrNull() ?: return emptyMap()
        return raw.mapValues { it.value / max }
    }

    fun topGenres(profile: Map<String, Double>, n: Int): List<String> =
        profile.entries
            .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            .take(n)
            .map { it.key }

    /** Coseno fra il vettore del profilo e quello binario dei generi del candidato. */
    fun similarity(genres: List<String>, profile: Map<String, Double>): Double {
        if (genres.isEmpty() || profile.isEmpty()) return 0.0
        val dot = genres.sumOf { profile[it] ?: 0.0 }
        val profileNorm = sqrt(profile.values.sumOf { it * it })
        val candidateNorm = sqrt(genres.size.toDouble())
        return if (profileNorm == 0.0) 0.0 else dot / (profileNorm * candidateNorm)
    }

    fun rank(
        candidates: Collection<Anime>,
        profile: Map<String, Double>,
        excludeIds: Set<Int>,
    ): List<Recommendation> =
        candidates
            .asSequence()
            // Anche il seguito o il prequel di ciò che si conosce già: è la stessa serie, non un consiglio.
            .filter { it.id !in excludeIds && it.relatedIds.none { id -> id in excludeIds } }
            .mapNotNull { anime ->
                val cosine = similarity(anime.genres, profile)
                if (cosine <= 0.0) return@mapNotNull null
                val quality = (anime.averageScore ?: UNKNOWN_SCORE.toInt()) / 100.0
                Recommendation(
                    anime = anime,
                    score = GENRE_SHARE * cosine + QUALITY_SHARE * quality,
                    matchedGenres = anime.genres
                        .filter { it in profile }
                        .sortedByDescending { profile[it] },
                )
            }
            .sortedByDescending { it.score }
            .toList()

    /**
     * Un consiglio per serie: le stagioni di uno stesso anime contano una volta sola, col punteggio della
     * migliore, e si mostrano con la prima uscita fra i candidati (di solito la S1: si comincia da lì).
     *
     * Senza richieste di rete: due candidati sono la stessa serie se sono collegati come prequel/sequel,
     * anche passando da una stagione che non è fra i candidati (S1 e S3 si ritrovano attraverso l'ID della
     * S2, che entrambi citano). Sfugge solo un buco di due stagioni consecutive assenti, raro fra i più popolari.
     * [ranked] è in ordine di punteggio.
     */
    fun onePerSeries(ranked: List<Recommendation>): List<Recommendation> {
        val parent = HashMap<Int, Int>()
        fun find(id: Int): Int {
            var root = parent.getOrPut(id) { id }
            while (root != parent.getValue(root)) root = parent.getValue(root)
            parent[id] = root
            return root
        }
        for (rec in ranked) {
            for (other in rec.anime.relatedIds) parent[find(rec.anime.id)] = find(other)
        }
        return ranked
            .groupBy { find(it.anime.id) } // groupBy mantiene l'ordine: il primo di ogni gruppo è il migliore
            .values
            .map { series ->
                series.first().copy(anime = series.map { it.anime }.minWith(Franchise.RELEASE_ORDER))
            }
    }
}
