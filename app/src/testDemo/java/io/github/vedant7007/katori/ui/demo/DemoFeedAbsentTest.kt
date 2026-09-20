package io.github.vedant7007.katori.ui.demo

import io.github.vedant7007.katori.domain.ContextText
import io.github.vedant7007.katori.domain.DefaultRulesEngine
import io.github.vedant7007.katori.domain.TriggerText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * The scripted feed is absent from the demo build (0027, ruled by Vedant): not switchable, not
 * hidden, not compiled. This runs in the demo flavour's suite and nowhere else.
 */
class DemoFeedAbsentTest {

    @Test
    fun `the demo build has no feed to switch on`() {
        assertFalse(DemoFeed.available)
        DemoFeed.set(true)
        assertFalse("set(true) must be a no-op in the demo build", DemoFeed.enabled.value)
        assertNull(DemoFeed.orchestrator(DefaultRulesEngine(), ContextText(ContextText.ENGLISH, TriggerText.ENGLISH), TriggerText(TriggerText.ENGLISH), emptyMap()))
        assertNull(DemoFeed.scriptedReport())
    }

    @Test
    fun `the scripted orchestrator's source is not in the demo build's source sets`() {
        val projectDir = File(System.getProperty("katori.projectDir") ?: error("katori.projectDir not set"))
        val demoSets = listOf("main", "demo")
        demoSets.forEach { set ->
            val f = File(projectDir, "app/src/$set/java/io/github/vedant7007/katori/ui/demo/ScriptedOrchestrator.kt")
            assertFalse("ScriptedOrchestrator.kt must not be under src/$set", f.exists())
        }
    }
}
