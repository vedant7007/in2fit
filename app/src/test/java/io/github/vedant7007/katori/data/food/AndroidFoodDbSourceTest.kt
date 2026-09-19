package io.github.vedant7007.katori.data.food

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The asset-copy step, on the JVM.
 *
 * The bug this guards: the copy used to be keyed by a hard-coded filename and skipped whenever
 * that file existed, so a device that had run any earlier build kept its food database forever
 * while the bundled one was rebuilt three times underneath it. Nothing here needs Android; the
 * SQLite open that follows the copy is the only part left untested on the JVM.
 */
class AndroidFoodDbSourceTest {

    @get:Rule val dir = TemporaryFolder()

    private fun copies() = dir.root.listFiles()!!.filter { it.name.startsWith("katori-food-") }

    @Test fun `first run copies the asset out`() {
        val asset = "build one".toByteArray()
        val f = AndroidFoodDbSource.stage(dir.root, asset)
        assertArrayEquals(asset, f.readBytes())
        assertEquals(1, copies().size)
    }

    @Test fun `a rebuilt asset replaces the copy from the previous build`() {
        val old = AndroidFoodDbSource.stage(dir.root, "build one".toByteArray())
        val fresh = "build two, same length".toByteArray()
        val f = AndroidFoodDbSource.stage(dir.root, fresh)
        assertTrue("the old copy must be removed, not left beside the new one", !old.exists())
        assertArrayEquals(fresh, f.readBytes())
        assertEquals(1, copies().size)
    }

    @Test fun `a copy left by the pre-hash naming scheme is removed too`() {
        dir.newFile("katori-food-v1.db").writeBytes("stale".toByteArray())
        AndroidFoodDbSource.stage(dir.root, "current".toByteArray())
        assertTrue(copies().none { it.name == "katori-food-v1.db" })
        assertEquals(1, copies().size)
    }

    @Test fun `the same asset is not copied twice`() {
        val asset = "unchanged".toByteArray()
        val first = AndroidFoodDbSource.stage(dir.root, asset)
        val stamp = first.lastModified()
        first.setLastModified(stamp - 60_000)
        val second = AndroidFoodDbSource.stage(dir.root, asset)
        assertEquals(first, second)
        assertEquals("an unchanged asset must not be rewritten", stamp - 60_000, second.lastModified())
    }

    @Test fun `a truncated copy is recopied rather than opened`() {
        val asset = "the whole database".toByteArray()
        val f = AndroidFoodDbSource.stage(dir.root, asset)
        f.writeBytes(asset.copyOf(4))
        assertArrayEquals(asset, AndroidFoodDbSource.stage(dir.root, asset).readBytes())
    }
}
