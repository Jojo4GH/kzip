# Kzip

[![Maven Central][kzip-maven-badge]][kzip-maven]
![GitHub Workflow Status][github-build-status]
[![GitHub][kzip-license-badge]](LICENSE)

[![Kotlin Multiplatform][kotlin-multiplatform-badge]][kotlin-multiplatform]
[![JVM Platform][jvm-platform-badge]][kotlin-jvm]
[![Android Platform][android-platform-badge]][kotlin-android]
[![Native Platform][native-platform-badge]][kotlin-native]
[![Apple Platform][apple-platform-badge]][kotlin-apple]
[![JS Platform][js-platform-badge]][kotlin-js]
[![WASM/JS Platform][wasmjs-platform-badge]][kotlin-wasmjs]

[kzip-maven-badge]: https://img.shields.io/maven-central/v/de.jonasbroeckmann.kzip/kzip?label=Latest
[github-build-status]: https://img.shields.io/github/actions/workflow/status/Jojo4GH/kzip/build.yaml
[kzip-license-badge]: https://img.shields.io/github/license/Jojo4GH/kzip?cacheSeconds=3600
[kotlin-multiplatform-badge]: https://img.shields.io/badge/Kotlin_Multiplatform-grey?logo=kotlin
[jvm-platform-badge]: https://img.shields.io/badge/-JVM-ed8b00?logo=kotlin&labelColor=grey
[android-platform-badge]: https://img.shields.io/badge/-Android-3ddc84?logo=kotlin&labelColor=grey
[native-platform-badge]: https://img.shields.io/badge/-Native-e082f3?logo=kotlin&labelColor=grey
[apple-platform-badge]: https://img.shields.io/badge/-Apple-lightgrey?logo=kotlin&labelColor=grey
[js-platform-badge]: https://img.shields.io/badge/-JS-efd81d?logo=kotlin&labelColor=grey
[wasmjs-platform-badge]: https://img.shields.io/badge/-WASM/JS-654ff0?logo=kotlin&labelColor=grey

[kzip-maven]: https://central.sonatype.com/artifact/de.jonasbroeckmann.kzip/kzip
[kotlin-multiplatform]: https://kotlinlang.org/docs/multiplatform.html
[kotlin-jvm]: https://kotlinlang.org/docs/comparison-to-java.html
[kotlin-android]: https://kotlinlang.org/docs/android-overview.html
[kotlin-native]: https://kotlinlang.org/docs/native-overview.html
[kotlin-apple]: https://kotlinlang.org/docs/apple-framework.html
[kotlin-js]: https://kotlinlang.org/docs/js-overview.html
[kotlin-wasmjs]: https://kotlinlang.org/docs/wasm-overview.html

A lightweight Kotlin Multiplatform library for reading, writing and modifying ZIP files.

## ⭐️ Main Features

- 🗂️ **Reading** ZIP entries and metadata
- 🗜️ Easy **extraction** and **compression** of files and directories
- 📝 **Modifying** existing ZIP files

The Kotlin file I/O interface uses [kotlinx-io](https://github.com/Kotlin/kotlinx-io) making it compatible with other Kotlin Multiplatform libraries.
The multiplatform implementation is tested and benchmarked against [zip4j](https://github.com/srikanth-lingala/zip4j) on JVM and [kuba--/zip](https://github.com/kuba--/zip) on native targets with up to 2x performance improvement.


Kzip currently supports the following targets:

- JVM
- Native:
  - Linux: `linuxX64`, `linuxArm64`
  - Windows: `mingwX64`
  - Android native: `androidNativeX64`[^nonOkio], `androidNativeArm64`[^nonOkio]
  - Apple:
    - macOS: `macosX64`, `macosArm64`
    - iOS: `iosArm64`, `iosSimulatorArm64`
    - tvOS: `tvosArm64`, `tvosSimulatorArm64`
    - watchOS: `watchosArm64`, `watchosSimulatorArm64`, `watchosArm32`
- Web:
  - `js`[^nonOkio]
  - `wasmJs`[^nonOkio]

[^nonOkio]: Extraction performance may be impacted currently depending on the file system

More features are planned, including support for suspending functions, more access to metadata, a ZIP file system, more utilities and integrations into other KMP libraries (see also [Contributing](#-contributing)).

## 🛠️ Installation

The kzip dependency is available on Maven Central and can be added to your common source set.
Just replace `$kzipVersion` with the [latest version](#kzip).

<details open>
<summary>Gradle - Kotlin DSL</summary>

```kotlin
implementation("de.jonasbroeckmann.kzip:kzip:$kzipVersion")
```
</details>

<details>
<summary>Gradle - Groovy DSL</summary>

```groovy
implementation "de.jonasbroeckmann.kzip:kzip:$kzipVersion"
```
</details>

<details>
<summary>Maven</summary>

```xml
<dependencies>
    <dependency>
        <groupId>de.jonasbroeckmann.kzip</groupId>
        <artifactId>kzip</artifactId>
        <version>$kzipVersion</version>
    </dependency>
</dependencies>
```
</details>

## 🚀 Usage Examples

### Reading a ZIP file

```kotlin
val zip = Zip.open(Path("example.zip"))

// Access a specific entry
zip.entry(Path("my_compressed.txt")) {
    println("Entry my_compressed.txt has size $compressedSize/$uncompressedSize")
    println("Entry my_compressed.txt has content:")
    println(readToSource().readString())
}

// Access all entries
zip.forEachEntry { entry ->
    println("Entry ${entry.path} has size ${entry.uncompressedSize}")

    if (entry.isDirectory) {
        println("Entry is a directory")
    } else {
        println("Entry is a file with content:")
        println(entry.readToSource().readString())
    }
}

zip.close()
```

### Modifying a ZIP file

```kotlin
Zip.open(Path("example.zip"), mode = Zip.Mode.Append).use { zip ->
    // Add a folder
    zip.folderEntry(Path("subfolder"))
    // Add a file from a path
    zip.entryFromPath(Path("subfolder", "compressed.txt"), Path("example.txt"))
    // Add a file from a source
    zip.entryFromSource(Path("hello_world.txt"), Buffer().apply { writeString("Hello, World!") })
    // Delete an entry
    zip.deleteEntries(Path("old_file.txt"))
}
```

### Extracting a ZIP file

```kotlin
Zip.open(Path("example.zip")).use { zip ->
    zip.extractTo(Path("example"))
}
```

### Creating a ZIP file from a directory

```kotlin
Zip.open(
    path = Path("example.zip"),
    mode = Zip.Mode.Write,
    // Optional: Set compression level
    level = Zip.CompressionLevel.BetterCompression
).use { zip ->
    zip.compressFrom(Path("example"))
}
```

## 🚧 Contributing

If you have any ideas, feel free to open an issue or create a pull request.

## 📄 License

This project is licensed under the [MIT License](LICENSE).

