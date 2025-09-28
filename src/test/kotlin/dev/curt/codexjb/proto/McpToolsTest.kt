package dev.curt.codexjb.proto

import com.google.gson.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

class McpToolsTest {

    @Test
    fun handlesMcpToolsListEvent() {
        val model = McpToolsModel()
        val msg = JsonObject()
        msg.addProperty("type", "McpListToolsResponse")

        val toolsObject = JsonObject().apply {
            add("server/tool1", JsonObject().apply {
                addProperty("name", "test_tool")
                addProperty("description", "A test tool")
            })
            add("server/tool2", JsonObject().apply {
                addProperty("name", "another_tool")
                addProperty("description", "Another test tool")
            })
        }

        msg.add("tools", toolsObject)
        
        model.onEvent("test-id", msg)
        
        assertEquals(2, model.tools.size)
        assertEquals("test_tool", model.tools[0].name)
        assertEquals("A test tool", model.tools[0].description)
        assertEquals("another_tool", model.tools[1].name)
        assertEquals("Another test tool", model.tools[1].description)
        assertTrue(model.isServerAvailable)
        assertFalse(model.hasError())
        assertNull(model.getErrorMessage())
    }
    
    @Test
    fun handlesMcpToolsErrorEvent() {
        val model = McpToolsModel()
        val msg = JsonObject()
        msg.addProperty("type", "McpToolsError")
        msg.addProperty("message", "Connection failed")
        
        model.onEvent("test-id", msg)
        
        assertEquals(0, model.tools.size)
        assertFalse(model.isServerAvailable)
        assertTrue(model.hasError())
        assertEquals("Connection failed", model.getErrorMessage())
    }
    
    @Test
    fun handlesMcpServerUnavailableEvent() {
        val model = McpToolsModel()
        val msg = JsonObject()
        msg.addProperty("type", "McpServerUnavailable")
        
        model.onEvent("test-id", msg)
        
        assertEquals(0, model.tools.size)
        assertFalse(model.isServerAvailable)
        assertTrue(model.hasError())
        assertEquals("MCP server is not running or not configured", model.getErrorMessage())
    }
    
    @Test
    fun handlesEmptyToolsList() {
        val model = McpToolsModel()
        val msg = JsonObject()
        msg.addProperty("type", "McpListToolsResponse")
        msg.add("tools", JsonObject())
        
        model.onEvent("test-id", msg)
        
        assertEquals(0, model.tools.size)
        assertTrue(model.isServerAvailable)
        assertFalse(model.hasError())
    }
    
    @Test
    fun handlesInvalidToolEntries() {
        val model = McpToolsModel()
        val msg = JsonObject()
        msg.addProperty("type", "McpListToolsResponse")

        val toolsArray = com.google.gson.JsonArray()
        val validTool = JsonObject().apply {
            addProperty("name", "valid_tool")
            addProperty("description", "Valid tool")
        }
        toolsArray.add(validTool)

        val invalidTool = JsonObject().apply {
            addProperty("description", "No name")
        }
        toolsArray.add(invalidTool)

        val anotherValidTool = JsonObject().apply {
            addProperty("name", "another_valid")
            addProperty("description", "Another valid")
        }
        toolsArray.add(anotherValidTool)

        msg.add("tools", toolsArray)
        
        model.onEvent("test-id", msg)
        
        // Should only include valid tools
        assertEquals(2, model.tools.size)
        assertEquals("valid_tool", model.tools[0].name)
        assertEquals("another_valid", model.tools[1].name)
    }
    
    @Test
    fun ignoresUnrelatedEvents() {
        val model = McpToolsModel()
        val msg = JsonObject()
        msg.addProperty("type", "SomeOtherEvent")
        
        model.onEvent("test-id", msg)
        
        assertEquals(0, model.tools.size)
        assertTrue(model.isServerAvailable)
        assertFalse(model.hasError())
    }
}
