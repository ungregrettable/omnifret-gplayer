import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

group = "com.omnifret"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(17)

    compilerOptions {
        // The transpiled Kotlin output uses redundant explicit types,
        // null assertions, and cast operations as artifacts of the
        // TypeScript-to-Kotlin translation. Suppress the noise.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            // -Xno-*-assertions only exist in the JVM compiler. They
            // strip null checks from compiled bytecode, useful because
            // the transpiled Kotlin emits a lot of redundant
            // null-asserted call sites that have no semantic meaning on
            // JVM. Native doesn't run them in the same way, so the flags
            // would just produce warnings.
            freeCompilerArgs.addAll(
                "-Xno-call-assertions",
                "-Xno-receiver-assertions",
                "-Xno-param-assertions",
            )
        }
    }

    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { target ->
        target.binaries.framework {
            baseName = "OmniFretGplayer"
            isStatic = true
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        // KMP's `commonTest/resources/` doesn't reach the Android JVM
        // unit-test classpath via source-set hierarchy alone (AGP 9
        // removed the old `android.sourceSets[].resources.srcDirs`
        // API). Explicitly extend the androidUnitTest resource roots
        // so `parseFixture(...)` resolves binary fixtures.
        androidUnitTest {
            resources.srcDir("src/commonTest/resources")
        }
    }
}

android {
    namespace = "com.omnifret.gplayer"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Bundle the MPL-2.0 LICENSE inside the AAR's META-INF directory so binary
// consumers (the OmniFret app, anyone else who pulls the AAR) can find the
// license without having to follow the GitHub link. AGP's bundleAar tasks
// are Zip-based; we hook in via afterEvaluate so the tasks exist when we
// configure them.
afterEvaluate {
    tasks.matching { it.name.startsWith("bundle") && it.name.endsWith("Aar") }
        .configureEach {
            (this as org.gradle.api.tasks.bundling.Zip).from(rootProject.file("LICENSE")) {
                into("META-INF")
            }
        }
}

// ---------------------------------------------------------------------------
// Test fixture wiring.
//
// `commonTest/resources/` doesn't reach the Android JVM unit-test classpath
// nor the Kotlin/Native iOS test executable on its own. AGP 9's new DSL
// removed `android.sourceSets[].resources.srcDirs`, and KMP's source-set
// declaration (`androidUnitTest { resources.srcDir(...) }`) doesn't
// propagate to AGP's resource-processing task in this version. So we
// stage fixtures explicitly:
//   - For Android: mirror commonTest/resources into src/androidUnitTest/
//     resources, which AGP DOES scan. The mirror is .gitignored.
//   - For iOS: copy into a build dir and pass the path via env var.
val omnifretTestResourcesDir = layout.buildDirectory.dir("omnifret-test-resources")
val syncOmnifretTestResources = tasks.register<Sync>("syncOmnifretTestResources") {
    from("src/commonTest/resources")
    into(omnifretTestResourcesDir)
}

val syncOmnifretAndroidTestResources = tasks.register<Sync>("syncOmnifretAndroidTestResources") {
    from("src/commonTest/resources")
    into("src/androidUnitTest/resources")
}

tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest>()
    .configureEach {
        dependsOn(syncOmnifretTestResources)
        environment("OMNIFRET_TEST_RESOURCES", omnifretTestResourcesDir.get().asFile.absolutePath)
    }

afterEvaluate {
    tasks.matching { it.name == "processDebugUnitTestJavaRes" || it.name == "processReleaseUnitTestJavaRes" }
        .configureEach {
            dependsOn(syncOmnifretAndroidTestResources)
        }
}

