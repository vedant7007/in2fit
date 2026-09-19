package io.github.vedant7007.katori

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Spec 14.3 and 14.4.
 *
 * Kotlin has no package-private, so in a single Gradle module the compiler cannot make it
 * impossible for a core pipeline to import a network client. This test is the enforcement that was
 * chosen instead: it fails the build if any core package references networking.
 *
 * The companion assertion, that the merged demo manifest carries no INTERNET permission, lives in
 * app/build.gradle.kts as verifyDemoDebugHasNoInternet, because only the Android build can produce
 * a merged manifest. That assertion is the one that actually protects the stage demo, since a
 * permission can arrive from a dependency without appearing in our own source.
 *
 * If a network feature is ever built, it goes in a package listed in [networkPackages] and nothing
 * in [corePackages] may import it.
 */
class NetworkIsolationTest {

    private val corePackages = listOf(
        "domain",
        "data/food",
        "data/local",
        "ml/asr",
        "ml/tts",
        "ml/llm",
        "ml/vision",
    )

    private val forbiddenImports = listOf(
        "java.net.",
        "javax.net.",
        "okhttp3.",
        "retrofit2.",
        "io.ktor.",
        "java.net.URL",
        "android.net.ConnectivityManager",
        "io.github.vedant7007.katori.data.network",
    )

    private fun sourceRoot(): File {
        val projectDir = System.getProperty("katori.projectDir")
            ?: error("katori.projectDir system property not set; see app/build.gradle.kts testOptions")
        return File(projectDir, "app/src/main/java/io/github/vedant7007/katori")
    }

    @Test
    fun `core packages do not reference the network`() {
        val root = sourceRoot()
        assertTrue("Source root not found: ${root.absolutePath}", root.isDirectory)

        val violations = mutableListOf<String>()

        corePackages.forEach { pkg ->
            val dir = File(root, pkg)
            if (!dir.isDirectory) return@forEach
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    file.readLines().forEachIndexed { index, line ->
                        val code = line.trim()
                        // Doc comments legitimately name these in prose; only real code counts.
                        if (code.startsWith("*") || code.startsWith("//") || code.startsWith("/*")) {
                            return@forEachIndexed
                        }
                        forbiddenImports.forEach { forbidden ->
                            if (code.contains(forbidden)) {
                                violations += "${file.relativeTo(root)}:${index + 1}: $code"
                            }
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            fail(
                "Core packages must not reach the network (spec 14.3). Violations:\n" +
                    violations.joinToString("\n")
            )
        }
    }

    @Test
    fun `every core package that should exist does exist`() {
        val root = sourceRoot()
        val missing = corePackages.filterNot { File(root, it).isDirectory }
        assertTrue(
            "These core packages are missing, so the isolation check above scanned nothing: $missing",
            missing.isEmpty()
        )
    }
}
