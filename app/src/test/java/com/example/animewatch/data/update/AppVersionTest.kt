package com.example.animewatch.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test
    fun parsesTagsAndSuffixes() {
        assertEquals(listOf(1, 2, 3), AppVersion.parse("v1.2.3"))
        assertEquals(listOf(1, 2, 0), AppVersion.parse("1.2"))
        assertEquals(listOf(2, 0, 1), AppVersion.parse(" 2.0.1-beta "))
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("1.2.3.4"))
        assertNull(AppVersion.parse("uno.due"))
    }

    @Test
    fun comparesNumerically() {
        assertTrue(AppVersion.isNewer("0.1.10", "0.1.9"))
        assertTrue(AppVersion.isNewer("v1.0.0", "0.9.9"))
        assertTrue(AppVersion.isNewer("0.2.0", "0.1.5"))
        assertFalse(AppVersion.isNewer("0.1.0", "0.1.0"))
        assertFalse(AppVersion.isNewer("0.1.0", "0.2.0"))
    }

    @Test
    fun invalidVersionIsNeverNewer() {
        assertFalse(AppVersion.isNewer("latest", "0.1.0"))
        assertFalse(AppVersion.isNewer("1.0.0", "dev"))
    }
}
