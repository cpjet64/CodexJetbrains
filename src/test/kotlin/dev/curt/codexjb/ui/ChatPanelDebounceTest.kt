package dev.curt.codexjb.ui

import com.google.gson.JsonObject
import dev.curt.codexjb.proto.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ChatPanelDebounceTest {

    @Test
    fun debouncesRefreshCalls() {
        val mockSender = object : ProtoSender {
            val sentMessages = mutableListOf<String>()
            override fun send(line: String) {
                sentMessages.add(line)
            }
        }
        
        val mockBus = object : EventBus {
            val listeners = mutableMapOf<String, (String, JsonObject) -> Unit>()
            override fun addListener(eventType: String, listener: (String, JsonObject) -> Unit) {
                listeners[eventType] = listener
            }
            override fun removeListener(eventType: String) {}
        }
        
        val mockTurns = object : TurnRegistry {
            override fun createTurn(model: String, effort: String, cwd: Path?): String = "test-turn"
            override fun getTurn(id: String): Turn? = null
        }
        
        val panel = ChatPanel(
            sender = mockSender,
            bus = mockBus,
            turns = mockTurns,
            modelProvider = { "test-model" },
            effortProvider = { "test-effort" },
            cwdProvider = { null }
        )
        
        // Simulate rapid refresh button clicks
        val refreshCount = AtomicInteger(0)
        val latch = CountDownLatch(5)
        
        // Override the refresh method to count calls
        val originalRefresh = panel::class.java.getDeclaredMethod("refreshTools")
        originalRefresh.isAccessible = true
        
        // Simulate rapid clicks within debounce window
        repeat(5) {
            panel.refreshTools()
            Thread.sleep(100) // Within 1 second debounce window
        }
        
        // Should only send one refresh request due to debouncing
        assertTrue(mockSender.sentMessages.size <= 1)
    }
    
    @Test
    fun allowsRefreshAfterDebounceWindow() {
        val mockSender = object : ProtoSender {
            val sentMessages = mutableListOf<String>()
            override fun send(line: String) {
                sentMessages.add(line)
            }
        }
        
        val mockBus = object : EventBus {
            val listeners = mutableMapOf<String, (String, JsonObject) -> Unit>()
            override fun addListener(eventType: String, listener: (String, JsonObject) -> Unit) {
                listeners[eventType] = listener
            }
            override fun removeListener(eventType: String) {}
        }
        
        val mockTurns = object : TurnRegistry {
            override fun createTurn(model: String, effort: String, cwd: Path?): String = "test-turn"
            override fun getTurn(id: String): Turn? = null
        }
        
        val panel = ChatPanel(
            sender = mockSender,
            bus = mockBus,
            turns = mockTurns,
            modelProvider = { "test-model" },
            effortProvider = { "test-effort" },
            cwdProvider = { null }
        )
        
        // First refresh
        panel.refreshTools()
        val firstCount = mockSender.sentMessages.size
        
        // Wait for debounce window to expire
        Thread.sleep(1100)
        
        // Second refresh after debounce window
        panel.refreshTools()
        val secondCount = mockSender.sentMessages.size
        
        // Should have sent two requests
        assertTrue(secondCount > firstCount)
    }
}

// Mock ProtoSender interface for testing
interface ProtoSender {
    fun send(line: String)
}
