package com.example.animewatch.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingReleasesTest {

    private fun release(tag: String, apk: Boolean = true, draft: Boolean = false, prerelease: Boolean = false) =
        GitHubRelease(
            tagName = tag,
            draft = draft,
            prerelease = prerelease,
            body = "note $tag",
            assets = if (apk) listOf(GitHubAsset(name = "AnimeWatch.apk")) else emptyList(),
        )

    @Test
    fun `tutte le versioni mancanti, dalla piu recente`() {
        val all = listOf(
            release("v0.3.0"), release("v0.10.0"), release("v0.2.0"), release("v0.4.0", apk = false),
            release("v0.5.0", prerelease = true), release("v0.6.0", draft = true), release("nightly"),
        )
        val newer = PendingReleases.newerThan(all, current = "0.2.0", assetName = "AnimeWatch.apk")
        // L'APK serve solo all'ultima: le note della 0.4.0 si mostrano anche se non allega nulla.
        assertEquals(listOf("0.10.0", "0.4.0", "0.3.0"), newer.map(PendingReleases::versionOf))
    }

    @Test
    fun `niente se l'ultima non allega l'APK o l'app e aggiornata`() {
        assertTrue(PendingReleases.newerThan(listOf(release("v0.3.0", apk = false)), "0.2.0", "AnimeWatch.apk").isEmpty())
        assertTrue(PendingReleases.newerThan(listOf(release("v0.2.0")), "0.2.0", "AnimeWatch.apk").isEmpty())
    }
}
