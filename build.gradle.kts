import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.api.tasks.testing.Test

plugins {
    kotlin("multiplatform") version "2.2.0"
    id("org.jetbrains.dokka") version "2.0.0"
}

group = "com.decimal128.hugeint"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

/*
 * Dokka tasks (V2 migration helpers may print a warning; that's fine).
 * Keep these simple so the plugin resolves correctly.
 */
tasks.dokkaHtml {
    outputDirectory.set(layout.buildDirectory.dir("documentation/html"))
}

tasks.dokkaGfm {
    outputDirectory.set(layout.buildDirectory.dir("documentation/markdown"))
}

/*
 * Optional: a JVM test task configured with diagnostic args (keeps your prior behavior).
 * It depends on jvmTestClasses, so it's only relevant if the JVM tests are present.
 */
tasks.register<Test>("testHsdis") {
    group = "verification"
    description = "Runs tests with JIT disassembly enabled (JVM-only)"

    // depend on jvm test classes task - will exist for KMP JVM target
    dependsOn("jvmTestClasses")
    useJUnitPlatform()

    jvmArgs(
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:+PrintInlining",
        "-XX:+PrintAssembly",
        "-XX:PrintAssemblyOptions=syntax=intel",
        "-XX:CompileThreshold=1"
    )

    testLogging {
        events = setOf(
            TestLogEvent.FAILED,
            TestLogEvent.SKIPPED,
            TestLogEvent.STANDARD_OUT,
            TestLogEvent.STANDARD_ERROR
        )
        showStandardStreams = true
    }
}

/*
 * Kotlin Multiplatform configuration.
 *
 * Note:
 *  - jvmToolchain(...) is set at the kotlin-extension level (affects all JVM compilations).
 *  - This file intentionally does NOT call the newer compilerOptions DSL (which had
 *    caused unresolved-reference errors in your environment). The Kotlin plugin will
 *    emit suggestions but this configuration is stable and will build.
 */
@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
kotlin {

    // ---------------------------
    // 1. Target configurations
    // ---------------------------

    jvm {
        // your existing JVM test setup (keep it)
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    js(IR) {
        browser()
        nodejs()
    }

    wasmJs {
        browser()
        nodejs()
    }

    wasmWasi {
        nodejs()
    }

    macosX64()
    macosArm64()
    // (Add linuxX64() or mingwX64() if needed)


    // ---------------------------
    // 2. Source-set hierarchy
    // ---------------------------

    sourceSets {

        val commonMain by getting
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val jvmMain by getting
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val jsMain by getting
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val wasmJsMain by getting
        val wasmJsTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val wasmWasiMain by getting
        val wasmWasiTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val macosX64Main by getting
        val macosArm64Main by getting

        // ---------------------------------------------
        // Shared fallback source set for JS + WASM
        // ---------------------------------------------
        val jsWasmMain by creating {
            dependsOn(commonMain)
        }
        jsMain.dependsOn(jsWasmMain)
        wasmJsMain.dependsOn(jsWasmMain)
        wasmWasiMain.dependsOn(jsWasmMain)

        val jsWasmTest by creating {
            dependsOn(commonTest)
        }
        jsTest.dependsOn(jsWasmTest)
        wasmJsTest.dependsOn(jsWasmTest)
        wasmWasiTest.dependsOn(jsWasmTest)

        // ---------------------------------------------
        // Shared implementation for all Native targets
        // ---------------------------------------------
        val nativeMain by creating {
            dependsOn(commonMain)
        }
        macosX64Main.dependsOn(nativeMain)
        macosArm64Main.dependsOn(nativeMain)
    }

}

/*
 * Optional: copy native libs for tests if you use native test fixtures.
 * If you don't have any native directory, this task is harmless but can be removed.
 */
val nativeInputDir = layout.projectDirectory.dir("native")
val nativeTestDir = layout.buildDirectory.dir("native")

tasks.register<Copy>("copyNativeForTests") {
    from(nativeInputDir)
    into(nativeTestDir)
}

/*
 * Ensure all Test tasks (JVM) get configured to run and see native lib path if necessary.
 * If you don't use JNA/native libs, this is harmless (property simply set).
 */
tasks.withType<Test> {
    dependsOn("copyNativeForTests")
    // set property even if nativeTestDir doesn't contain anything
    systemProperty("jna.library.path", nativeTestDir.get().asFile.absolutePath)

    useJUnitPlatform()
    testLogging {
        events = setOf(
            TestLogEvent.FAILED,
            TestLogEvent.SKIPPED,
            TestLogEvent.STANDARD_OUT,
            TestLogEvent.STANDARD_ERROR
        )
        showStandardStreams = true
    }
}
