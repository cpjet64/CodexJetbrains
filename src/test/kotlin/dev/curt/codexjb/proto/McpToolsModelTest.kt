package dev.curt.codexjb.proto

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class McpToolsModelTest {
    @Test
    fun parsesToolList() {
        val m = McpToolsModel()
        val arr = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("name", "rg")
                addProperty("description", "ripgrep")
            })
        }
        val msg = JsonObject().apply {
            addProperty("type", "McpToolsList")
            add("tools", arr)
        }
        m.onEvent("t1", msg)
        assertEquals(1, m.tools.size)
        assertEquals("rg", m.tools.first().name)
    }
}

