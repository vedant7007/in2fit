import com.android.build.api.artifact.SingleArtifact

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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

        ndk {
            // arm64 only. 32-bit devices cannot run the model, and building armeabi-v7a
            // doubles NDK build time for hardware that would fail anyway.
            abiFilters += "arm64-v8a"
        }
    }

    // Pinned so a different machine cannot silently build against a different NDK.
    ndkVersion = "28.2.13676358"

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
    buildFeatures {
        compose = true
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

    // Room runtime only. The compiler is a KSP processor and is added once KSP is pinned;
    // the contracts compile without it because nothing yet asks for a generated DAO.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.barcode.scanning)

    testImplementation(libs.junit)
    testImplementation(libs.sqlite.jdbc)
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
androidComponents {
    onVariants(selector().withFlavor("delivery" to "demo")) { variant ->
        val verify = tasks.register("verify${variant.name.replaceFirstChar { it.uppercase() }}HasNoInternet") {
            group = "verification"
            description = "Fails if the merged ${variant.name} manifest declares INTERNET"

            val manifestFile = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
            val reportFile = rootProject.layout.projectDirectory
                .file("logs/merged-manifest-${variant.name}.xml").asFile

            inputs.file(manifestFile)
            outputs.file(reportFile)

            doLast {
                val text = manifestFile.get().asFile.readText()
                reportFile.parentFile.mkdirs()
                reportFile.writeText(text)

                val offenders = Regex("""uses-permission[^>]*android\.permission\.INTERNET""")
                    .findAll(text).map { it.value }.toList()

                if (offenders.isNotEmpty()) {
                    throw GradleException(
                        "The ${variant.name} build declares INTERNET, which breaks the offline " +
                            "guarantee in spec 14.4. Offending lines: $offenders"
                    )
                }
                println("[verify] ${variant.name}: no INTERNET permission in the merged manifest")
                println("[verify] merged manifest copied to ${reportFile.absolutePath}")
            }
        }
        tasks.matching { it.name == "assemble${variant.name.replaceFirstChar { c -> c.uppercase() }}" }
            .configureEach { dependsOn(verify) }
    }
}
