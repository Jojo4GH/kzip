plugins {
    kotlin("multiplatform") version "2.1.20"
    id("org.jetbrains.dokka") version "2.0.0"
    id("com.vanniktech.maven.publish.base") version "0.31.0"
}

group = property("GROUP")!!
version = property("VERSION_NAME")!!

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

    // Configure cinterop for kuba zip library
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        compilations.getByName("main") {
            cinterops {
                cinterops.create("zip") {
                    includeDirs("src/nativeInterop/cinterop")
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

dokka {
    dokkaSourceSets.configureEach {
        sourceLink {
            localDirectory.set(rootDir)
            remoteUrl("https://github.com/Jojo4GH/kzip/tree/master")
        }
    }
}

mavenPublishing {
    pomFromGradleProperties()
    configure(com.vanniktech.maven.publish.KotlinMultiplatform())
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
}
