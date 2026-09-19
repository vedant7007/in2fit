package io.github.vedant7007.katori.domain

import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The arbiter decides what is resident and what gets evicted, and it is the component the demo
 * rests on. All of that is admission arithmetic, eviction order and lease bookkeeping, none of
 * which needs Android or a gigabyte of weights, so it is all tested here behind the three seams.
 *
 * What is NOT tested here, and is not pretended otherwise: whether a real model actually fits.
 * That is [ModelArbiter.canCoReside]'s job and it answers it by measuring on the device.
 */
class ModelArbiterTest {

    // --- the doubles --------------------------------------------------------------------------

    private class FakeMemory(
        var total: Long = gb(8),
        var available: Long = gb(3),
        var threshold: Long = mb(432),
        var pss: Long = mb(126),
    ) : DeviceMemory {
        override fun totalBytes() = total
        override fun availableBytes() = available
        override fun lowMemoryThresholdBytes() = threshold
        override fun processPssBytes() = pss
    }

    private class FakeLoader(
        val failOn: Set<String> = emptySet(),
    ) : ModelLoader {
        val loaded = mutableListOf<String>()
        val unloaded = mutableListOf<String>()
        override suspend fun load(handle: ModelHandle): Any {
            if (handle.id in failOn) throw IllegalStateException("cannot load ${handle.id}")
            loaded += handle.id
            return "native:${handle.id}"
        }
        override fun unload(handle: ModelHandle, native: Any) { unloaded += handle.id }
    }

    private class FakeLog : MeasurementLog {
        val written = mutableListOf<MeasurementRow>()
        override fun append(row: MeasurementRow) { written += row }
        override fun rows(): List<MeasurementRow> = written
    }

    private fun arbiter(
        memory: FakeMemory = FakeMemory(),
        loader: FakeLoader = FakeLoader(),
        log: FakeLog = FakeLog(),
    ) = DefaultModelArbiter(memory, loader, log, buildTag = "test", clockMs = { 1_000L })

    // --- the ceiling --------------------------------------------------------------------------

    @Test
    fun `the ceiling is derived from this device rather than being a constant`() {
        val small = arbiter(FakeMemory(total = gb(4), threshold = mb(200), pss = mb(100)))
        val large = arbiter(FakeMemory(total = gb(8), threshold = mb(432), pss = mb(126)))
        assertTrue(
            "a larger device must get a larger ceiling, or the figure is not measured at all",
            large.memoryCeilingBytes() > small.memoryCeilingBytes(),
        )
    }

    @Test
    fun `the ceiling never takes more than the capped share of the device`() {
        val memory = FakeMemory(total = gb(8), threshold = mb(1), pss = mb(1))
        val ceiling = arbiter(memory).memoryCeilingBytes()
        assertTrue(
            "with a tiny threshold the arithmetic allows almost everything; the cap must bite",
            ceiling <= memory.total * 6 / 10,
        )
    }

    @Test
    fun `a device with no room gets a zero ceiling rather than a negative one`() {
        // Negative would sail through every `used + needed <= ceiling` check by accident.
        val ceiling = arbiter(FakeMemory(total = mb(512), threshold = mb(400), pss = mb(100)))
            .memoryCeilingBytes()
        assertTrue("ceiling must not be negative, was $ceiling", ceiling >= 0)
    }

    @Test
    fun `calibrating writes a row, because the trajectory is the point`() {
        val log = FakeLog()
        arbiter(log = log).memoryCeilingBytes()
        assertEquals(1, log.written.size)
        assertEquals(MeasurementRow.Kind.CEILING_CALIBRATION, log.written[0].kind)
    }

    @Test
    fun `a measurement row survives a round trip through its text form`() {
        val row = MeasurementRow(
            atEpochMs = 1_700_000_000_000L,
            buildTag = "dotprod+fp16",
            kind = MeasurementRow.Kind.CO_RESIDENCY,
            handleIds = listOf("asr.te", "llm.qwen", "tts.piper"),
            measuredPeakBytes = 2_338_176_000L,
            ceilingBytes = 4_000_000_000L,
            totalRamBytes = 7_990_000_000L,
            fits = true,
        )
        assertEquals(row, MeasurementRow.parse(row.toLine()))
    }

    // --- leases and admission -----------------------------------------------------------------

    @Test
    fun `a model is loaded once and stays resident for a second lease`() = runBlocking {
        val loader = FakeLoader()
        val a = arbiter(loader = loader)
        a.withModel(llm) { }
        a.withModel(llm) { }
        assertEquals(listOf("llm"), loader.loaded)
    }

    @Test
    fun `every model in a round trip is admitted together`() = runBlocking {
        val loader = FakeLoader()
        val a = arbiter(loader = loader)
        val r = a.withModels(listOf(asr, llm, tts)) { it.keys.map { h -> h.id }.sorted() }
        assertEquals(listOf("asr", "llm", "tts"), (r as Outcome.Ok).value)
    }

    @Test
    fun `a set that cannot fit is refused cleanly instead of being loaded anyway`() = runBlocking {
        // Ceiling is about 4.4 GB on this fake device; three of these do not fit.
        val loader = FakeLoader()
        val a = arbiter(loader = loader)
        val huge = (1..3).map { handle("huge$it", gb(2)) }
        when (val r = a.withModels(huge) { }) {
            is Outcome.Unavailable -> assertEquals(UnavailableReason.INSUFFICIENT_MEMORY, r.reason)
            else -> fail("expected INSUFFICIENT_MEMORY, got $r")
        }
        assertTrue("nothing should have been left resident", a.residency.value.resident.isEmpty())
    }

    @Test
    fun `admission evicts the least recently used unpinned model first`() = runBlocking {
        val loader = FakeLoader()
        val a = arbiter(loader = loader)
        // Three that fit together, then a fourth that forces one out.
        val one = handle("one", gb(1))
        val two = handle("two", gb(1))
        val three = handle("three", gb(1))
        val four = handle("four", gb(2))
        a.withModel(one) { }
        a.withModel(two) { }
        a.withModel(three) { }
        a.withModel(four) { }
        assertEquals("the oldest unpinned model is the one that goes", listOf("one"), loader.unloaded)
    }

    @Test
    fun `a pinned model is never evicted to make room`() = runBlocking {
        val loader = FakeLoader()
        val a = arbiter(loader = loader)
        val pinned = handle("pinned", gb(2))
        val filler = handle("filler", gb(1))
        a.withModel(filler) { }
        a.withModel(pinned) {
            // While this lease is open, ask for something that can only fit by evicting.
            val big = handle("big", gb(2))
            a.withModel(big) { }
        }
        assertFalse("the pinned model must survive", loader.unloaded.contains("pinned"))
    }

    @Test
    fun `a lease is released even when the block throws`() = runBlocking {
        val a = arbiter()
        try {
            a.withModel(llm) { throw IllegalStateException("boom") }
            fail("the exception should have propagated")
        } catch (e: IllegalStateException) {
            assertEquals("boom", e.message)
        }
        val entry = a.residency.value.resident.single { it.handle.id == "llm" }
        assertFalse("a thrown block must not leave the model pinned for the process lifetime", entry.pinned)
    }

    @Test
    fun `a failed load rolls back the models admitted alongside it`() = runBlocking {
        val loader = FakeLoader(failOn = setOf("tts"))
        val a = arbiter(loader = loader)
        when (val r = a.withModels(listOf(asr, llm, tts)) { }) {
            is Outcome.Unavailable -> assertEquals(UnavailableReason.MODEL_LOAD_FAILED, r.reason)
            else -> fail("expected MODEL_LOAD_FAILED, got $r")
        }
        assertEquals(
            "a half-acquired lease is what withModels exists to prevent",
            emptyList<ResidentModel>(), a.residency.value.resident,
        )
        assertEquals(listOf("asr", "llm"), loader.unloaded)
    }

    @Test
    fun `release leaves a pinned model alone`() = runBlocking {
        val loader = FakeLoader()
        val a = arbiter(loader = loader)
        a.withModel(llm) { a.release(llm) }
        assertFalse("releasing out from under a lease is never allowed", loader.unloaded.contains("llm"))
    }

    @Test
    fun `preload leaves the model resident but evictable`() = runBlocking {
        val a = arbiter()
        a.preload(llm)
        val entry = a.residency.value.resident.single { it.handle.id == "llm" }
        assertFalse("a warmed model must still be evictable under pressure", entry.pinned)
    }

    @Test
    fun `releaseAllUnpinned drops everything that is not in use`() = runBlocking {
        val loader = FakeLoader()
        val a = arbiter(loader = loader)
        a.preload(asr)
        a.preload(tts)
        a.releaseAllUnpinned()
        assertTrue(a.residency.value.resident.isEmpty())
        assertEquals(setOf("asr", "tts"), loader.unloaded.toSet())
    }

    @Test
    fun `a model above this device's tier is refused as unsupported, not as out of memory`() = runBlocking {
        // A 2 GB device is LOW; a HIGH-tier model is not a memory problem, it is a device problem
        // and the UI has to say something different about it.
        val a = arbiter(FakeMemory(total = gb(2), threshold = mb(200), pss = mb(80)))
        val demanding = ModelHandle("vision", ModelFamily.VISION_CLASSIFIER, "v.tflite", mb(50), DeviceTier.HIGH)
        when (val r = a.withModel(demanding) { }) {
            is Outcome.Unavailable -> assertEquals(UnavailableReason.HARDWARE_UNSUPPORTED, r.reason)
            else -> fail("expected HARDWARE_UNSUPPORTED, got $r")
        }
    }

    @Test
    fun `using a loaded model after its lease has ended fails loudly`() = runBlocking {
        val a = arbiter()
        var escaped: LoadedModel? = null
        a.withModel(llm) { escaped = it }
        a.releaseAllUnpinned()
        assertNotNull(escaped)
        try {
            escaped!!.native
            fail("reading a released model must throw rather than hand back freed native memory")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("released"))
        }
    }

    // --- co-residency -------------------------------------------------------------------------

    @Test
    fun `co-residency reports the measured peak and writes a row`() = runBlocking {
        val memory = FakeMemory()
        val log = FakeLog()
        val a = arbiter(memory = memory, log = log)
        val r = a.canCoReside(listOf(asr, llm, tts))
        val report = (r as Outcome.Ok).value
        assertTrue(report.fits)
        assertEquals(listOf(asr, llm, tts), report.handles)
        assertTrue("the peak must come from the device, not from the manifest sum", report.measuredPeakBytes > 0)
        assertEquals(1, log.written.count { it.kind == MeasurementRow.Kind.CO_RESIDENCY })
    }

    @Test
    fun `a set that does not fit names what would have to be dropped`() = runBlocking {
        val a = arbiter()
        val huge = listOf(handle("h1", gb(3)), handle("h2", gb(2)), handle("h3", mb(200)))
        val report = (a.canCoReside(huge) as Outcome.Ok).value
        assertFalse(report.fits)
        assertTrue(
            "the largest model is the one worth dropping first",
            report.wouldNeedToDrop.first().id == "h1",
        )
    }

    // --- fixtures -----------------------------------------------------------------------------

    private val asr = handle("asr", mb(200))
    private val llm = handle("llm", gb(1))
    private val tts = handle("tts", mb(64))

    private companion object {
        fun mb(n: Long) = n * 1024L * 1024L
        fun gb(n: Long) = n * 1024L * 1024L * 1024L
        fun handle(id: String, bytes: Long) = ModelHandle(
            id = id,
            family = ModelFamily.LLM,
            relativePath = "$id.bin",
            estimatedResidentBytes = bytes,
            minimumTier = DeviceTier.LOW,
        )
    }
}
