import com.android.build.api.artifact.SingleArtifact

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "io.github.vedant7007.katori"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.vedant7007.katori"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Exported schemas are committed, so a schema change shows up as a reviewable diff
        // instead of being discovered as a crash on someone's phone.
        ksp { arg("room.schemaLocation", "$projectDir/schemas") }

        // Exported schemas are committed, so a schema change shows up as a reviewable diff
        // instead of being discovered as a crash on someone's phone.

        ndk {
            // arm64 only. 32-bit devices cannot run the model, and building armeabi-v7a
            // doubles NDK build time for hardware that would fail anyway.
            abiFilters += "arm64-v8a"
        }
    }

    // Pinned so a different machine cannot silently build against a different NDK.
    ndkVersion = "28.2.13676358"

    // The JNI shim only. llama.cpp itself is prebuilt into app/src/main/jniLibs by
    // tools/5-build-llama-android.ps1; see app/src/main/cpp/CMakeLists.txt for why.
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    flavorDimensions += "delivery"
    productFlavors {
        create("demo") {
            dimension = "delivery"
            // No INTERNET permission at all. The permission is declared only in the `full`
            // source set, so it is absent here by construction rather than by configuration.
            // This is the build that goes on stage.
        }
        create("full") {
            dimension = "delivery"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
    }

    androidResources {
        // The three languages the app is localised into (spec 5.11), and no others. Library
        // translations for every other locale are dropped from the APK, and a device set to a
        // fourth language falls back to the default string table rather than to whichever
        // library happens to ship that locale. Adding a language is a values-<tag>/ directory
        // plus an entry here and in res/xml/locales_config.xml. docs/decisions/0017.
        localeFilters += listOf("en", "te", "hi")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.all {
            // The import-scan test reads the real source tree rather than a copy.
            it.systemProperty("katori.projectDir", rootProject.projectDir.absolutePath)

            // These tests read real files that Gradle cannot infer as inputs: the bundled food
            // database and the utterance set. Without declaring them, Gradle marks the test task
            // UP-TO-DATE after the database is rebuilt and reports a stale pass.
            //
            // That actually happened: an alias was added, the database was rebuilt, the build
            // went green, and the match-rate report was the previous run's. A green build that
            // did not run is worse than a red one.
            it.inputs.file(rootProject.file("app/src/main/assets/food/katori-food.db"))
                .withPropertyName("bundledFoodDatabase")
            it.inputs.file(rootProject.file("data-authoring/utterance-test-set.csv"))
                .withPropertyName("utteranceTestSet")
            // Same reason, for the knowledge-facts file and the intent test set (0020).
            it.inputs.file(rootProject.file("app/src/main/assets/knowledge/facts.csv"))
                .withPropertyName("knowledgeFacts")
            it.inputs.file(rootProject.file("data-authoring/intent-test-set.csv"))
                .withPropertyName("intentTestSet")
            // The safety set, the answer-quality set, the reviewer's word sheet (0024) and the
            // ASR renderings the matcher is measured against (0026): all read through
            // katori.projectDir, so all declared, per the rule recorded on 20 September.
            it.inputs.file(rootProject.file("data-authoring/safety-adversarial-set.csv"))
                .withPropertyName("safetyAdversarialSet")
            it.inputs.file(rootProject.file("data-authoring/answer-quality-set.csv"))
                .withPropertyName("answerQualitySet")
            it.inputs.file(rootProject.file("data-authoring/log-words-review.md"))
                .withPropertyName("logWordsReviewSheet")
            it.inputs.file(rootProject.file("data-authoring/codemix-renderings.csv"))
                .withPropertyName("codeMixRenderings")
            it.inputs.file(rootProject.file("data-authoring/qualified-dishes.csv"))
                .withPropertyName("qualifiedDishes")
            it.inputs.file(rootProject.file("data-authoring/demo-utterance-set.csv"))
                .withPropertyName("demoUtteranceSet")
            it.inputs.file(rootProject.file("data-authoring/candidates.csv"))
                .withPropertyName("candidatesByContext")
            it.inputs.file(rootProject.file("data-authoring/us-record-sweep.csv"))
                .withPropertyName("usRecordSweep")
            // And the string tables: StringResourcesTest reads res/values*/strings.xml directly,
            // and without this a Telugu import left the test task UP-TO-DATE and its report
            // reading "0 awaiting review" over a file with 48 REVIEW markers in it.
            it.inputs.files(fileTree("src/main/res") { include("values*/strings.xml") })
                .withPropertyName("stringTables")
        }
    }
}

/**
 * The sherpa-onnx AAR is gitignored (39 MB), so a fresh clone does not have it, and `files()` on
 * a missing path is silently empty: the failure would surface as unresolved `com.k2fsa` symbols
 * deep in a compile log. That is HANDOVER §7 bug 1 in another coat. Same remedy as
 * app/src/main/cpp/CMakeLists.txt uses for libllama.so: fail before anything is built, naming
 * the script that fetches it.
 */
val SHERPA_AAR = "libs/sherpa-onnx-static-link-onnxruntime-1.13.8.aar"
tasks.named("preBuild") {
    doFirst {
        val aar = file(SHERPA_AAR)
        if (!aar.isFile || aar.length() == 0L) {
            throw GradleException(
                "sherpa-onnx AAR missing: ${aar.absolutePath}\n" +
                    "It is gitignored. Run tools/4-fetch-models.bat (tools/fetch-models.ps1), which " +
                    "downloads it and checks its sha256. See docs/decisions/0019."
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)


    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.barcode.scanning)

    // The hardware probe, and NOTHING SHIPPED, loads ONNX sessions: it does so to MEASURE the
    // resident cost of the ASR and TTS models rather than estimate it. As `implementation` this
    // put a 33 MB libonnxruntime.so into the demo APK that no shipped code called. It STAYS in
    // test scope now that sherpa-onnx is in: the probe drives the Java ai.onnxruntime API, which
    // the static-link AAR below does not carry, and that AAR exports no Ort* symbol, so the two
    // do not collide. docs/decisions/0016.
    androidTestImplementation(libs.onnxruntime.android)

    // sherpa-onnx, the ASR and TTS runtime (spec 10.1). Not published to Maven Central; this is the
    // official GitHub release AAR, gitignored, fetched with its sha256 checked by
    // tools/fetch-models.ps1 (URL and hash there and in the ASR decision record). The STATIC-link
    // variant: one libsherpa-onnx-jni.so per ABI with ONNX Runtime inside and no exported Ort*
    // symbol, so it cannot collide with the probe's onnxruntime-android above. A missing file
    // fails preBuild by name; see SHERPA_AAR below.
    implementation(files(SHERPA_AAR))

    testImplementation(libs.junit)
    testImplementation(libs.sqlite.jdbc)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
}

/**
 * Spec 14.3 asks that a core pipeline be structurally unable to reach the network, and spec 14.4
 * makes the absence of the INTERNET permission the proof shown on stage.
 *
 * Kotlin has no package-private, so in a single module the compiler cannot enforce the first part.
 * This task enforces the second part, which is the one that actually protects the demo: it reads
 * the MERGED manifest for every `demo` variant and fails the build if INTERNET appears in it.
 *
 * Reading the merged manifest matters. A permission can arrive from a dependency's manifest without
 * appearing anywhere in our own source, and only the merged result shows that.
 */

/**
 * The complete set of permissions the `demo` build is allowed to declare.
 *
 * WHY A WHITELIST AND NOT A BAN ON INTERNET. The first version of this check only looked for
 * INTERNET, and it did catch a real one: com.google.android.datatransport:transport-backend-cct,
 * a telemetry uploader pulled in transitively by ML Kit, contributed INTERNET and
 * ACCESS_NETWORK_STATE that no file in this project declared.
 *
 * But a denylist only finds what it already knows to look for. The next dependency might add
 * ACCESS_FINE_LOCATION, READ_CONTACTS or READ_PHONE_STATE, and a check for INTERNET would pass
 * while the app shipped with them. On stage the claim is that this build can do nothing but
 * listen and look, so the check is now: these permissions and no others.
 *
 * Adding an entry here is a deliberate act that needs a reason written next to it.
 */
val ALLOWED_DEMO_PERMISSIONS = sortedSetOf(
    // Voice logging. The core input, spec 5.1.
    "android.permission.RECORD_AUDIO",
    // Lab report and label scanning, spec 5.6 and 12.1.
    "android.permission.CAMERA",
    // Added by the platform itself for runtime-registered receivers on newer API levels.
    // Not requested by this project and not grantable by a user.
    "io.github.vedant7007.katori.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
)

androidComponents {
    onVariants(selector().withFlavor("delivery" to "demo")) { variant ->
        val verify = tasks.register("verify${variant.name.replaceFirstChar { it.uppercase() }}Permissions") {
            group = "verification"
            description = "Fails if the merged ${variant.name} manifest declares a permission not on the allow list"

            val manifestFile = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
            val reportFile = rootProject.layout.projectDirectory
                .file("logs/merged-manifest-${variant.name}.xml").asFile

            inputs.file(manifestFile)
            outputs.file(reportFile)

            doLast {
                val text = manifestFile.get().asFile.readText()
                reportFile.parentFile.mkdirs()
                reportFile.writeText(text)

                val declared = Regex("""uses-permission[^>]*android:name="([^"]+)"""")
                    .findAll(text).map { it.groupValues[1] }.toSortedSet()

                val unexpected = declared - ALLOWED_DEMO_PERMISSIONS
                if (unexpected.isNotEmpty()) {
                    throw GradleException(
                        "The ${variant.name} build declares permissions that are not on the allow " +
                            "list: $unexpected\n" +
                            "Allowed: $ALLOWED_DEMO_PERMISSIONS\n" +
                            "A permission can arrive from a dependency without appearing in our own " +
                            "source. If this one is genuinely needed, add it to ALLOWED_DEMO_PERMISSIONS " +
                            "in app/build.gradle.kts with a comment saying why. If it is not, remove it " +
                            "in app/src/demo/AndroidManifest.xml with tools:node=\"remove\"."
                    )
                }
                println("[verify] ${variant.name}: permissions are exactly $declared")
                println("[verify] merged manifest copied to ${reportFile.absolutePath}")
            }
        }
        tasks.matching { it.name == "assemble${variant.name.replaceFirstChar { c -> c.uppercase() }}" }
            .configureEach { dependsOn(verify) }
    }
}
