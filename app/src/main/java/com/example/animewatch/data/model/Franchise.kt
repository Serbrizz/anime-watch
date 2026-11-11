package com.example.animewatch.data.model

/**
 * Tutte le stagioni di una serie. In AniList ogni stagione è una scheda a sé, collegata alle altre da
 * relazioni PREQUEL/SEQUEL; film e OVA fanno parte della catena perché spesso stanno fra due stagioni
 * (es. un film che continua la S1 e precede la S2). Nelle liste un franchise conta come un solo anime;
 * il progresso resta per stagione.
 */
data class Franchise(
    /** La stagione uscita per prima: è l'ID che si salva nelle liste. */
    val rootId: Int,
    /** Tutte le stagioni, in ordine di uscita. */
    val seasonIds: List<Int>,
) {
    companion object {
        fun single(id: Int) = Franchise(id, listOf(id))

        /**
         * Raggruppa le schede note in franchise: due schede stanno insieme se una cita l'altra come prequel
         * o sequel, anche in una sola direzione (AniList non sempre mette la relazione inversa).
         * Restituisce per ogni ID il suo franchise.
         */
        fun group(animes: Map<Int, Anime>): Map<Int, Franchise> {
            val parent = HashMap<Int, Int>()
            fun find(id: Int): Int {
                var root = id
                while (parent.getValue(root) != root) root = parent.getValue(root)
                parent[id] = root
                return root
            }
            animes.keys.forEach { parent[it] = it }
            for (anime in animes.values) {
                for (other in anime.relatedIds) {
                    if (other in parent) parent[find(anime.id)] = find(other)
                }
            }
            val result = HashMap<Int, Franchise>()
            for (members in animes.values.groupBy { find(it.id) }.values) {
                val sorted = members.sortedWith(RELEASE_ORDER).map { it.id }
                val franchise = Franchise(sorted.first(), sorted)
                sorted.forEach { result[it] = franchise }
            }
            return result
        }

        /**
         * Gli anime di una lista, uno per franchise, nell'ordine della lista. Le liste salvate prima dei
         * franchise possono contenere più stagioni della stessa serie: qui diventano una riga sola.
         * Gli ID non ancora risolti restano a sé.
         */
        fun groupList(ids: List<Int>, known: Map<Int, Franchise>): List<Franchise> =
            ids.map { known[it] ?: single(it) }.distinctBy { it.rootId }

        /** "S1", "S2"… per le stagioni; "Film", "OVA"… per il resto, numerati solo se più d'uno. */
        fun seasonLabels(seasons: List<Anime>): List<String> {
            val kinds = seasons.map { kindOf(it.format) }
            val totals = kinds.groupingBy { it }.eachCount()
            val seen = HashMap<String, Int>()
            return kinds.map { kind ->
                val n = (seen[kind] ?: 0) + 1
                seen[kind] = n
                when {
                    kind == SEASON -> "S$n"
                    totals.getValue(kind) > 1 -> "$kind $n"
                    else -> kind
                }
            }
        }

        /**
         * La stagione da aprire toccando il franchise: l'ultima iniziata se non è finita, altrimenti la
         * successiva già uscita. Così un film saltato non blocca chi è passato alla stagione dopo.
         */
        fun currentSeasonIndex(seasons: List<Anime>, watchedCount: (Int) -> Int): Int {
            if (seasons.isEmpty()) return 0
            val last = seasons.indexOfLast { watchedCount(it.id) > 0 }
            if (last < 0) return 0
            if (watchedCount(seasons[last].id) < seasons[last].availableEpisodes) return last
            val next = (last + 1 until seasons.size).firstOrNull { seasons[it].availableEpisodes > 0 }
            return next ?: last
        }

        private const val SEASON = "S"

        private fun kindOf(format: String?): String = when (format) {
            "MOVIE" -> "Film"
            "OVA" -> "OVA"
            "SPECIAL" -> "Speciale"
            "MUSIC" -> "Video musicale"
            else -> SEASON
        }

        private val SEASON_ORDER = listOf("WINTER", "SPRING", "SUMMER", "FALL")

        /** Anno, poi stagione dell'anno; a parità vince l'ID più basso (la scheda creata prima). */
        val RELEASE_ORDER: Comparator<Anime> = compareBy(
            { it.seasonYear ?: Int.MAX_VALUE },
            { SEASON_ORDER.indexOf(it.season).let { i -> if (i < 0) SEASON_ORDER.size else i } },
            { it.id },
        )
    }
}
