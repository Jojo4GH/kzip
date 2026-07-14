plugins {
    kotlin("multiplatform") version "2.3.0"
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

    val kubaZipTargets = listOf(
        mingwX64(),
        linuxX64(),
        linuxArm64(),
        androidNativeX64(),
        androidNativeArm64(),
    )

    js {
        browser()
        nodejs()
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
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

    // Configure cinterop for kuba zip library
    kubaZipTargets.forEach {
        it.compilations.getByName("main") {
            cinterops {
                cinterops.create("zip") {
                    includeDirs("src/nativeInterop/cinterop")
                }
            }
        }
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("native") {
                withMingw()
                withLinux()
                withAndroidNative()
            }
            group("webAndApple") {
                group("web") {
                    withJs()
                    withWasmJs()
                }
                withWasmWasi()
                withApple()
                withJvm()
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.5.4")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmMain.dependencies {
            implementation("net.lingala.zip4j:zip4j:2.11.5")
        }
        getByName("webAndAppleMain").dependencies {
            implementation("dev.karmakrafts.kompress:kompress-core:1.3.0")
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
