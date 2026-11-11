package com.example.animewatch.data.update

/** Versioni "X.Y.Z" dell'app, come nei tag delle release GitHub ("vX.Y.Z"). Pura: testata sulla JVM. */
object AppVersion {
    /** I tre numeri di "1.2.3" ("v" iniziale e suffissi come "-beta" ignorati, parti mancanti = 0); null se non è una versione. */
    fun parse(version: String): List<Int>? {
        val core = version.trim().removePrefix("v").substringBefore('-').substringBefore('+')
        val parts = core.split('.')
        if (parts.size > 3) return null
        val numbers = parts.map { part -> part.toIntOrNull()?.takeIf { it >= 0 } ?: return null }
        return numbers + List(3 - numbers.size) { 0 }
    }

    /** true se [latest] è successiva a [current]. Una versione non interpretabile non è mai più recente. */
    fun isNewer(latest: String, current: String): Boolean {
        val l = parse(latest) ?: return false
        val c = parse(current) ?: return false
        for (i in 0 until 3) if (l[i] != c[i]) return l[i] > c[i]
        return false
    }
}
