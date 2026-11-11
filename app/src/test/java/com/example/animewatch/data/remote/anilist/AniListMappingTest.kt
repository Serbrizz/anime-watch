package com.example.animewatch.data.remote.anilist

import org.junit.Assert.assertEquals
import org.junit.Test

class AniListMappingTest {

    private fun edge(type: String, id: Int, format: String?, mediaType: String = "ANIME") =
        RelationEdgeDto(type, RelationNodeDto(id, mediaType, format))

    @Test
    fun `la catena passa dagli speciali e dalle schede annunciate senza formato`() {
        // Dr. Stone: Stone Wars -> speciale "Ryusui" -> New World; Cour 3 -> Terraforming (annunciato).
        val media = MediaDto(
            id = 1,
            relations = RelationsDto(
                listOf(
                    edge("PREQUEL", 10, "SPECIAL"),
                    edge("SEQUEL", 20, null),
                    edge("SEQUEL", 30, "MUSIC"),
                    edge("SEQUEL", 40, "MANGA", mediaType = "MANGA"),
                    edge("SIDE_STORY", 50, "TV"),
                ),
            ),
        )
        val anime = media.toAnime()
        assertEquals(listOf(10), anime.prequelIds)
        assertEquals(listOf(20), anime.sequelIds)
    }
}
