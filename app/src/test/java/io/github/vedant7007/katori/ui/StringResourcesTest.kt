package io.github.vedant7007.katori.ui

import io.github.vedant7007.katori.domain.model.ConfidenceReason
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * The localisation rules in res/values/strings.xml, enforced (docs/decisions/0017).
 *
 * Same shape as [io.github.vedant7007.katori.NetworkIsolationTest]: the compiler cannot stop a
 * composable from being handed a literal, so a test reads the source tree and fails the build if
 * one is. The locale files are then checked against the default table, and the state of every
 * translation is PRINTED rather than asserted, because an untranslated key is an honest runtime
 * fallback and not a defect, while a key that exists only in a locale file is a typo that would
 * silently never be shown.
 */
class StringResourcesTest {

    private val projectDir: File by lazy {
        File(
            System.getProperty("katori.projectDir")
                ?: error("katori.projectDir system property not set; see app/build.gradle.kts testOptions")
        )
    }
    private val res get() = File(projectDir, "app/src/main/res")
    private val locales = listOf("te", "hi", "en")

    /** A literal handed straight to something that renders it. */
    private val hardcoded = listOf(
        Regex("""\bText\(\s*"[^"]"""),
        Regex("""\b(text|contentDescription|label|placeholder|title|headline|supportingText)\s*=\s*"[^"]"""),
    )

    @Test
    fun `composables carry no hardcoded user-facing text`() {
        val ui = File(projectDir, "app/src/main/java/io/github/vedant7007/katori/ui")
        assertTrue("ui source root not found: ${ui.absolutePath}", ui.isDirectory)

        val violations = mutableListOf<String>()
        ui.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                val code = line.trim()
                if (code.startsWith("*") || code.startsWith("//") || code.startsWith("/*")) return@forEachIndexed
                if (hardcoded.any { it.containsMatchIn(code) }) {
                    violations += "${file.relativeTo(ui)}:${index + 1}: $code"
                }
            }
        }
        if (violations.isNotEmpty()) {
            fail(
                "User-facing text must be a key in res/values/strings.xml, never a literal in a " +
                    "composable, or it cannot be localised (spec 5.11). Violations:\n" +
                    violations.joinToString("\n")
            )
        }
    }

    @Test
    fun `every key in a locale file exists in the default table`() {
        val default = keysOf(File(res, "values/strings.xml")).keys
        val orphans = locales.flatMap { tag ->
            (keysOf(File(res, "values-$tag/strings.xml")).keys - default).map { "values-$tag: $it" }
        }
        if (orphans.isNotEmpty()) {
            fail(
                "These keys exist in a locale file but not in the default table, so nothing can " +
                    "ever look them up. A renamed or misspelt key:\n" + orphans.joinToString("\n")
            )
        }
    }

    @Test
    fun `every confidence reason has a sentence in the default table`() {
        val default = keysOf(File(res, "values/strings.xml")).keys
        val missing = ConfidenceReason.values()
            .map { "confidence_reason_${it.name.lowercase()}" }
            .filterNot { it in default }
        assertTrue(
            "Confidence.kt: a reason with no user-facing sentence is a bug (spec 15.1.1). " +
                "Add these to res/values/strings.xml: $missing",
            missing.isEmpty()
        )
    }

    /** Not an assertion. The review queue, printed so it is read off a test run, not remembered. */
    @Test
    fun `report translation coverage per locale`() {
        val default = keysOf(File(res, "values/strings.xml"))
        val translatable = default.filterValues { it.translatable }.keys
        println("--- translation coverage, ${translatable.size} translatable keys in values/ ---")
        for (tag in locales) {
            val entries = keysOf(File(res, "values-$tag/strings.xml"))
            val done = entries.keys.intersect(translatable)
            val unreviewed = entries.filter { it.key in translatable && it.value.needsReview }.keys
            val missing = translatable - entries.keys
            println("values-$tag: ${done.size}/${translatable.size} present, ${unreviewed.size} awaiting review, ${missing.size} missing")
            unreviewed.sorted().forEach { println("  REVIEW   $it") }
            missing.sorted().forEach { println("  missing  $it") }
        }
    }

    private data class Entry(val translatable: Boolean, val needsReview: Boolean)

    /**
     * Reads one strings.xml. A <string> preceded by a comment containing REVIEW is unreviewed.
     * Comments are kept by the parser by default, which is what makes the marker readable here.
     */
    private fun keysOf(file: File): Map<String, Entry> {
        assertTrue("string table not found: ${file.absolutePath}", file.isFile)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val out = linkedMapOf<String, Entry>()
        var pendingReview = false
        val children = doc.documentElement.childNodes
        for (i in 0 until children.length) {
            val n = children.item(i)
            when {
                n.nodeType == Node.COMMENT_NODE -> pendingReview = pendingReview || n.nodeValue.contains("REVIEW")
                n is Element && n.tagName == "string" -> {
                    out[n.getAttribute("name")] = Entry(
                        translatable = n.getAttribute("translatable") != "false",
                        needsReview = pendingReview,
                    )
                    pendingReview = false
                }
            }
        }
        return out
    }
}
