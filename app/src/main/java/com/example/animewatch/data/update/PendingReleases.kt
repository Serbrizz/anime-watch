package com.example.animewatch.data.update

/** Note di una versione, da mostrare nel popup di aggiornamento. */
data class ReleaseNotes(val version: String, val notes: String)

/** Scelta fra le release GitHub di quelle da proporre. Pura: testata sulla JVM. */
object PendingReleases {
    /**
     * Le release successive a [current], dalla più recente, escluse bozze, pre-release e tag che non sono versioni.
     * La prima è quella da installare, ma solo se allega [assetName]: senza APK non c'è nulla da proporre, e
     * allora si restituisce una lista vuota anche se esistono versioni più nuove.
     */
    fun newerThan(releases: List<GitHubRelease>, current: String, assetName: String): List<GitHubRelease> {
        val newer = releases
            .filter { !it.draft && !it.prerelease && AppVersion.isNewer(versionOf(it), current) }
            .sortedWith { a, b -> compare(versionOf(b), versionOf(a)) }
        val latest = newer.firstOrNull() ?: return emptyList()
        return if (latest.assets.any { it.name.equals(assetName, ignoreCase = true) }) newer else emptyList()
    }

    fun versionOf(release: GitHubRelease): String = release.tagName.trim().removePrefix("v")

    private fun compare(a: String, b: String): Int = when {
        AppVersion.isNewer(a, b) -> 1
        AppVersion.isNewer(b, a) -> -1
        else -> 0
    }
}
