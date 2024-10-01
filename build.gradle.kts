import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform") version "2.0.0"
}

group = "de.jonasbroeckmann.kzip"
version = "1.0.0"

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    mingwX64()
    linuxX64()
    targets.withType<KotlinNativeTarget> {
//        compilerOptions.freeCompilerArgs.add("-OptIn=kotlinx.cinterop.ExperimentalForeignApi")
        compilations.getByName("main") {
            cinterops {
                val zip by creating {
                    includeDirs.allHeaders(files("src/nativeInterop/cinterop"))
                    packageName("kuba.zip")
                }
            }
        }
        binaries.executable {
            entryPoint = "de.jonasbroeckmann.kzip.main"
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