package dev.gabrielglaize.teknotes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchTest {
    @Test
    fun normalizeDropsCaseAccentsSpacesAndPunctuation() {
        assertEquals("teknokrates", Search.normalize("Tekno Krates !"))
        assertEquals("echobleu", Search.normalize("Écho Bleu"))
        assertEquals("subzero", Search.normalize("Sub Zéro"))
    }

    @Test
    fun distanceCountsEdits() {
        assertEquals(0, Search.distance("tekno", "tekno"))
        assertEquals(1, Search.distance("tekno", "tecno"))
        assertEquals(2, Search.distance("caisson", "kaison"))
    }

    @Test
    fun exactBeatsPrefixBeatsContainsBeatsTypo() {
        val exact = Search.score("talus", "Talus")!!
        val prefix = Search.score("talus", "Talus Tribe")!!
        val contains = Search.score("talus", "Tribu des Talus")!!
        val typo = Search.score("tallus", "Talus")!!
        assertTrue(exact > prefix && prefix > contains && contains > typo)
    }

    @Test
    fun typosAreForgivenButNotNonsense() {
        assertNotNull(Search.score("teknokrat", "Teknokrates"))
        assertNotNull(Search.score("kaisson noir", "Caisson Noir"))
        assertNull(Search.score("zz", "Caisson Noir"))
        assertNull(Search.score("frenchcore", "Basse Marée"))
    }

    @Test
    fun aliasesAreSearchedAndReported() {
        val match = Search.bestMatch("TKS", "Teknokrates", listOf("Tekno Krates", "TKS"))
        assertEquals("TKS", match?.second)
    }

    @Test
    fun rankPutsBestFirst() {
        val items = listOf(
            Soundsystem("1", "Tribu des Talus", emptyList(), null, emptyList(), emptyList()),
            Soundsystem("2", "Talus", emptyList(), null, emptyList(), emptyList()),
        )
        assertEquals(listOf("2", "1"), Search.rank(Search.soundsystems("talus", items)).map { it.id })
    }
}
