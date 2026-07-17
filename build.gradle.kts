plugins {
    kotlin("multiplatform") version "2.4.0"
    id("org.jetbrains.kotlinx.benchmark") version "0.4.17"
    id("org.jetbrains.dokka") version "2.1.0"
    id("com.vanniktech.maven.publish") version "0.35.0"
    id("io.github.terrakok.kmp-hierarchy") version "1.1"
}

group = "de.jonasbroeckmann.kzip"
version = "2.0.0"

repositories {
    mavenCentral()
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    explicitApi()

    jvm()

    mingwX64()

    linuxX64()
    linuxArm64()

    androidNativeX64()
    androidNativeArm64()

    js {
        nodejs()
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
    }

    // waiting for dev.karmakrafts.kompress
    // @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    // wasmWasi {
    //     nodejs()
    // }

    // macosX64() // no support from dev.karmakrafts.kompress
    macosArm64()

    iosArm64()
    iosSimulatorArm64()

    tvosArm64()
    tvosSimulatorArm64()

    watchosArm32()
    watchosArm64()
    watchosSimulatorArm64()
    // watchosDeviceArm64() // waiting for dev.karmakrafts.kompress

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            // Source sets with Okio implementations
            group("okio") {
                withJvm()
                group("mingw")
                group("linux")
                group("apple")
            }
            // Source sets without Okio implementations
            group("nonOkio") {
                group("web") {
                    withJs()
                    withWasmJs()
                }
                withWasmWasi()
                group("androidNative") { withAndroidNative() }
            }
            // Source sets for benchmarks
            group("benchmark") {
                withJvm()
                group("kubaZip") {
                    withMingwX64()
                    withLinuxX64()
                }
            }
        }
    }

    listOf(
        mingwX64(),
        linuxX64(),
    ).forEach { target ->
        target.compilations.configureEach {
            if (name == "test" || name.endsWith("TestBenchmark")) {
                cinterops {
                    create("zip") {
                        val path = "src/kubaZipTest/cinterop"
                        definitionFile.set(project.file("$path/zip.def"))
                        includeDirs(path)
                    }
                }
            }
        }
    }

    sourceSets {
        val kotlinxIOVersion = "0.9.1"
        commonMain.dependencies {
            api("org.jetbrains.kotlinx:kotlinx-io-core:$kotlinxIOVersion")
            // waiting for https://github.com/karmakrafts/Kompress/issues/6
            implementation("dev.karmakrafts.kompress:kompress-core:1.4.3")
        }
        getByName("okioMain").dependencies {
            implementation("com.squareup.okio:okio:3.16.2")
            implementation("org.jetbrains.kotlinx:kotlinx-io-okio:${kotlinxIOVersion}")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.17")
        }
        jvmTest.dependencies {
            implementation("net.lingala.zip4j:zip4j:2.11.5")
        }
    }
}

benchmark {
    targets {
        register("jvmTest")
        register("mingwX64Test")
        register("linuxX64Test")
    }
}

dokka {
    dokkaSourceSets.configureEach {
        sourceLink {
            localDirectory.set(rootDir)
            remoteUrl("https://github.com/Jojo4GH/kzip/tree/master")
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates("$group", name, "$version")
    pom {
        name = "Kzip"
        description = "Lightweight Kotlin Multiplatform library for reading, writing and modifying ZIP files."
        inceptionYear = "2024"
        url = "https://github.com/Jojo4GH/kzip"
        licenses {
            license {
                name = "MIT"
                distribution = "repo"
            }
        }
        scm {
            url = "https://github.com/Jojo4GH/"
            connection = "scm:git:git://github.com/Jojo4GH/kzip.git"
            developerConnection = "scm:git:ssh://github.com/Jojo4GH/kzip.git"
        }
        developers {
            developer {
                id = "Jojo4GH"
                name = "Jonas Broeckmann"
                url = "https://github.com/Jojo4GH/"
            }
        }
    }
}
