package io.github.vedant7007.katori.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Push-to-talk was built, tested and never connected: the Talk screen's release handler sat as
 * an empty block for a day while every rehearsal assumed the button existed (Nila's read of
 * master, 21 Sep). This reads the screen's source, the way StringResourcesTest does, and fails
 * the build if the release or the cue is ever unwired again.
 */
class TalkScreenWiringTest {

    private val screen: String by lazy {
        val projectDir = File(System.getProperty("katori.projectDir") ?: error("katori.projectDir not set"))
        File(projectDir, "app/src/main/java/io/github/vedant7007/katori/ui/TalkScreen.kt").readText()
    }

    @Test
    fun `release ends the recording and is never an empty block`() {
        assertTrue("the hold button's release must send EndSpeech", screen.contains("onRelease = vm::endSpeech"))
        val emptyReleases = Regex("""onRelease\s*=\s*\{\s*\}""").findAll(screen)
            // The stop pill legitimately has no release: it is a tap, not a hold.
            .count { m -> !screen.substring(maxOf(0, m.range.first - 200), m.range.first).contains("stop_speaking") }
        assertTrue("an empty onRelease on the hold button is the defect this test exists for", emptyReleases == 0)
    }

    @Test
    fun `the recording cue lights on the microphone, never on the press`() {
        assertTrue("the cue must be driven by micLive", screen.contains("active = state.micLive"))
        assertFalse("the cue must not be driven by the stage, which begins at the press", screen.contains("active = recording"))
    }
}
