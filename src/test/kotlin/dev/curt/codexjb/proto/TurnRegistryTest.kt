package dev.curt.codexjb.proto

import com.google.gson.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TurnRegistryTest {
    @Test
    fun removesOnTaskComplete() {
        val reg = TurnRegistry()
        val id = "t1"
        reg.put(Turn(id))
        val msg = JsonObject().apply { addProperty("type", "TaskComplete") }

        reg.onEvent(id, msg)

        assertNull(reg.get(id))
        assertEquals(0, reg.size())
    }
}

