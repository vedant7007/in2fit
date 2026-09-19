plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

/**
 * Resolves the newest STABLE version of a dependency we have not pinned yet and writes the answer
 * to logs/versions-resolved.log.
 *
 * Kept after the first use on purpose: guessing a version string produces a build failure that
 * reads like a code problem, and asking the repository costs one task run.
 */
val probeVersions by tasks.registering {
    group = "verification"
    description = "Resolve newest stable versions of unpinned dependencies"

    val targets = listOf(
        "com.android.tools.build:gradle",
        "org.xerial:sqlite-jdbc",
        "com.google.devtools.ksp:symbol-processing-gradle-plugin",
        "com.google.dagger:hilt-android",
        "com.google.dagger:hilt-android-compiler",
        "androidx.hilt:hilt-navigation-compose",
    )

    // KSP versions are <kotlin>-<ksp>, so only versions for the Kotlin we use are acceptable.
    val kotlinVersion = libs.versions.kotlin.get()

    val outFile = layout.projectDirectory.file("logs/versions-resolved.log").asFile
    val configs = targets.associateWith { coords ->
        configurations.detachedConfiguration(dependencies.create("$coords:+")).apply {
            isTransitive = false
            resolutionStrategy {
                cacheDynamicVersionsFor(0, "seconds")
                componentSelection {
                    all {
                        val v = candidate.version.lowercase()
                        val unstable = listOf("alpha", "beta", "rc", "dev", "snapshot")
                        if (unstable.any { v.contains(it) }) reject("not a stable release")
                        if (candidate.module.contains("symbol-processing") &&
                            !candidate.version.startsWith("$kotlinVersion-")
                        ) {
                            reject("KSP version does not match Kotlin $kotlinVersion")
                        }
                    }
                }
            }
        }
    }

    doLast {
        outFile.parentFile.mkdirs()
        val lines = mutableListOf("=== resolved stable versions (kotlin $kotlinVersion) ===")
        configs.forEach { (coords, cfg) ->
            lines += try {
                val id = cfg.resolvedConfiguration.resolvedArtifacts.first().moduleVersion.id
                "OK    $coords -> ${id.version}"
            } catch (e: Exception) {
                "FAIL  $coords -> ${e.message?.lineSequence()?.firstOrNull()}"
            }
        }
        lines.forEach { println(it) }
        outFile.writeText(lines.joinToString(System.lineSeparator()))
        println("written: ${outFile.absolutePath}")
    }
}
