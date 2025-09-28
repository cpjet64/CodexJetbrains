package dev.curt.codexjb.proto

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class PromptsModelTest {
    @Test
    fun parsesCustomPromptsResponse() {
        val model = PromptsModel()
        val msg = JsonObject().apply {
            addProperty("type", "ListCustomPromptsResponse")
            add("custom_prompts", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("name", "Greeting")
                    addProperty("description", "Friendly greeting")
                    addProperty("content", "Hello there!")
                })
            })
        }

        model.onEvent("test", msg)

        assertEquals(1, model.prompts.size)
        val prompt = model.prompts.first()
        assertEquals("Greeting", prompt.name)
        assertEquals("Friendly greeting", prompt.description)
        assertEquals("Hello there!", prompt.content)
    }
}
