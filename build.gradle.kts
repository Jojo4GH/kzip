plugins {
    kotlin("multiplatform") version "2.4.0"
    id("org.jetbrains.dokka") version "2.1.0"
    id("com.vanniktech.maven.publish") version "0.35.0"
}

group = "de.jonasbroeckmann.kzip"
version = "1.1.1"

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

    macosX64()
    macosArm64()

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    tvosX64()
    tvosArm64()
    tvosSimulatorArm64()

    watchosX64()
    watchosArm32()
    watchosArm64()
    watchosSimulatorArm64()
    // watchosDeviceArm64() // waiting for dev.karmakrafts.kompress

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
    applyHierarchyTemplate {
        common {
            group("okio") {
                withJvm()
                group("native") {
                    group("mingw") { withMingw() }
                    group("linux") { withLinux() }
                    group("apple") {
                        withApple()
                        group("ios") { withIos() }
                        group("tvos") { withTvos() }
                        group("watchos") { withWatchos() }
                        group("macos") { withMacos() }
                    }
                }
                withWasmWasi()
            }
            group("nonOkio") {
                group("web") {
                    withJs()
                    withWasmJs()
                }
                group("androidNative") { withAndroidNative() }
            }
        }
    }

    sourceSets {
        val kotlinxIOVersion = "0.9.1"
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-io-core:$kotlinxIOVersion")
            implementation("dev.karmakrafts.kompress:kompress-core:1.3.0")
        }
        getByName("okioMain").dependencies {
            implementation("com.squareup.okio:okio:3.16.2")
            implementation ("org.jetbrains.kotlinx:kotlinx-io-okio:${kotlinxIOVersion}")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation("net.lingala.zip4j:zip4j:2.11.5")
        }
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
