package io.github.vedant7007.katori.ui

import io.github.vedant7007.katori.domain.ContextText
import io.github.vedant7007.katori.domain.Orchestrator
import io.github.vedant7007.katori.domain.OrchestratorEvent
import io.github.vedant7007.katori.domain.Stage
import io.github.vedant7007.katori.domain.TriggerText
import io.github.vedant7007.katori.domain.UserIntent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The view-model's half of push-to-talk and the language default, against a recording
 * orchestrator. No Android: the test constructor takes no context and no preferences.
 */
class TalkViewModelTest {

    /** Records every intent; replays a scripted event list per intent; counts down when asked. */
    private class Recording(private val script: (UserIntent) -> List<OrchestratorEvent>) : Orchestrator {
        val intents = CopyOnWriteArrayList<UserIntent>()
        val seen = CountDownLatch(1)
        override fun handle(intent: UserIntent): Flow<OrchestratorEvent> = flow {
            intents += intent
            script(intent).forEach { emit(it) }
            seen.countDown()
        }
    }

    private fun vm(orchestrator: Orchestrator) = TalkViewModel(
        orchestrator, TriggerText(TriggerText.ENGLISH), ContextText(ContextText.ENGLISH, TriggerText.ENGLISH),
        scripted = null, prefs = null,
    )

    @Test
    fun `the language defaults to Hindi`() {
        assertEquals("hi", vm(Recording { emptyList() }).state.value.language)
        assertEquals("hi", TalkViewModel.DEFAULT_LANGUAGE)
    }

    @Test
    fun `release sends EndSpeech and nothing else`() {
        val o = Recording { listOf(OrchestratorEvent.Completed) }
        vm(o).endSpeech()
        assertTrue("EndSpeech never reached the orchestrator", o.seen.await(5, TimeUnit.SECONDS))
        assertEquals(listOf<UserIntent>(UserIntent.EndSpeech), o.intents.toList())
    }

    @Test
    fun `the cue lights on MicrophoneLive, not on the press or on RECORDING`() {
        val afterRecording = CountDownLatch(1)
        val afterLive = CountDownLatch(1)
        val vm = vm(object : Orchestrator {
            override fun handle(intent: UserIntent): Flow<OrchestratorEvent> = flow {
                emit(OrchestratorEvent.Progress(Stage.RECORDING))
                afterRecording.await(5, TimeUnit.SECONDS)
                emit(OrchestratorEvent.MicrophoneLive)
                afterLive.await(5, TimeUnit.SECONDS)
                emit(OrchestratorEvent.Progress(Stage.TRANSCRIBING))
                emit(OrchestratorEvent.Completed)
            }
        })
        vm.speak()
        waitUntil { vm.state.value.stage == Stage.RECORDING }
        assertFalse("the cue lit on the press: RECORDING is the turn beginning, not the microphone", vm.state.value.micLive)
        afterRecording.countDown()
        waitUntil { vm.state.value.micLive }
        afterLive.countDown()
        waitUntil { !vm.state.value.busy }
        assertFalse("the cue must clear once the turn moves past recording", vm.state.value.micLive)
    }

    private fun waitUntil(timeoutMs: Long = 5000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < end) Thread.sleep(10)
        assertTrue("condition not met in time", condition())
    }
}
