package com.example.animewatch.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedListTest {

    private val list = AnimeList(
        id = "L1",
        name = "Da vedere",
        createdAt = 0,
        animeIds = emptyList(),
        ownerId = "alice",
        members = mapOf("alice" to ListRole.OWNER, "bob" to ListRole.EDITOR, "carol" to ListRole.VIEWER),
    )

    @Test
    fun `i permessi seguono il ruolo`() {
        assertTrue(list.isOwner("alice"))
        assertTrue(list.canEdit("alice"))
        assertTrue(list.canEdit("bob"))
        assertFalse(list.isOwner("bob"))
        assertFalse(list.canEdit("carol"))
        assertFalse(list.canEdit("dave"))
        assertNull(list.roleOf("dave"))
        assertFalse(list.canEdit(null))
    }

    @Test
    fun `condivisa solo con almeno un altro membro`() {
        assertTrue(list.isShared)
        assertFalse(list.copy(members = mapOf("alice" to ListRole.OWNER)).isShared)
    }

    @Test
    fun `una serie è nella lista se c'è una qualunque sua stagione`() {
        val withS1 = list.copy(animeIds = listOf(1))
        val franchises = mapOf(2 to Franchise(1, listOf(1, 2)))
        assertTrue(withS1.containsSeries(1, franchises))
        assertTrue(withS1.containsSeries(2, franchises))
        // Senza franchise noto conta solo la scheda stessa.
        assertFalse(withS1.containsSeries(2, emptyMap()))
        assertFalse(withS1.containsSeries(3, franchises))
    }

    @Test
    fun `i ruoli si leggono dalle chiavi salvate su Firestore`() {
        assertEquals(ListRole.EDITOR, ListRole.fromKey("editor"))
        assertEquals(ListRole.VIEWER, ListRole.fromKey("viewer"))
        assertNull(ListRole.fromKey("admin"))
        assertNull(ListRole.fromKey(null))
    }

    @Test
    fun `l'ID dell'invito usa l'email minuscola, come le regole`() {
        assertEquals("L1_mario.rossi@gmail.com", ListInvite.inviteId("L1", "  Mario.Rossi@Gmail.com "))
    }

    @Test
    fun `controllo minimo sulle email`() {
        assertTrue(ListInvite.isPlausibleEmail("mario@gmail.com"))
        assertFalse(ListInvite.isPlausibleEmail("mario"))
        assertFalse(ListInvite.isPlausibleEmail("mario@gmail"))
        assertFalse(ListInvite.isPlausibleEmail("ma rio@gmail.com"))
        assertFalse(ListInvite.isPlausibleEmail("a/b@gmail.com")) // '/' non è ammesso negli ID Firestore
    }
}
