plugins {
    kotlin("multiplatform") version "2.0.0"
    `maven-publish`
}

group = "de.jonasbroeckmann.kzip"
version = "1.0.0"

repositories {
    mavenCentral()
}

kotlin {
    explicitApi()
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvm()

    mingwX64()
    linuxX64()

    // Configure cinterop for kuba zip library
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        val targetName = targetName
        compilations.getByName("main") {
            cinterops {
                create("zip${targetName.replaceFirstChar { it.titlecase() }}") {
                    includeDirs.allHeaders(files("src/nativeInterop/cinterop"))
                    packageName("kuba.zip")
                }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.5.4")
        }
        jvmMain.dependencies {
            implementation("net.lingala.zip4j:zip4j:2.11.5")
        }
    }
}
