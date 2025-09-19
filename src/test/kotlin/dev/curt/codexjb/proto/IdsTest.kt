package dev.curt.codexjb.proto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class IdsTest {
    @Test
    fun uuidFormatAndUniqueness() {
        val a = Ids.newId()
        val b = Ids.newId()
        assertNotEquals(a, b)
        assertEquals(36, a.length)
    }
}

