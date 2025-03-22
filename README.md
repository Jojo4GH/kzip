<div align="center">

# 📂 kzip 📂

[![Maven Central][kzip-maven-badge]][kzip-maven]
[![GitHub][kzip-license-badge]](LICENSE)

[![Kotlin Multiplatform][kotlin-multiplatform-badge]][kotlin-multiplatform]
[![JVM Platform][jvm-platform-badge]][kotlin-jvm]
[![Linux X64 Platform][linux-x64-platform-badge]][kotlin-native]
[![Linux ARM64 Platform][linux-x64-platform-badge]][kotlin-native]
[![MinGW X64 Platform][mingw-x64-platform-badge]][kotlin-native]

[kzip-maven-badge]: https://img.shields.io/maven-central/v/de.jonasbroeckmann.kzip/kzip?label=Latest
[kzip-license-badge]: https://img.shields.io/github/license/Jojo4GH/kzip
[kotlin-multiplatform-badge]: https://img.shields.io/badge/Kotlin_Multiplatform-bababb?logo=kotlin
[jvm-platform-badge]: https://img.shields.io/badge/JVM-4dbb5f
[linux-x64-platform-badge]: https://img.shields.io/badge/Linux_X64-e082f3
[linux-arm64-platform-badge]: https://img.shields.io/badge/Linux_ARM64-e082f3
[mingw-x64-platform-badge]: https://img.shields.io/badge/MinGW_X64-e082f3

[kzip-maven]: https://central.sonatype.com/artifact/de.jonasbroeckmann.kzip/kzip
[kotlin-multiplatform]: https://kotlinlang.org/docs/multiplatform.html
[kotlin-native]: https://kotlinlang.org/docs/native-overview.html
[kotlin-jvm]: https://kotlinlang.org/docs/jvm-get-started.html

A lightweight Kotlin Multiplatform library for reading, writing and modifying ZIP files.

</div>

---

## ⭐️ Main Features

- 🗂️ **Reading** ZIP entries and metadata
- 🗜️ Easy **extraction** and **compression** of files and directories
- 📝 **Modifying** existing ZIP files

The kotlin file I/O interface uses [kotlinx-io](https://github.com/Kotlin/kotlinx-io) making it compatible with other Kotlin Multiplatform libraries.  
Currently, kzip supports the JVM, Linux X64, Linux ARM64 and MinGW X64 targets, but more targets are planned (see [Contributing](#-contributing)).

## 🛠️ Installation

The kzip dependency is available on Maven Central and can be added to your common source set.
Just replace `$kzipVersion` with the [latest version](#-kzip-).

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

## 🚀 Usage

### Reading a ZIP file

```kotlin
val zip = Zip.open(Path("example.zip"))

// Access a specific entry
zip.entry(Path("content.txt")) {
    println("Entry content.txt has size $uncompressedSize")
    println("Entry content.txt has content:")
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

### Modifying a ZIP file

```kotlin
val textSource = Buffer().apply { writeString("Hello, World!") }
Zip.open(Path("example.zip"), mode = Zip.Mode.Append).use { zip ->
    // Add a folder
    zip.folderEntry(Path("subfolder"))
    // Add a file from a path
    zip.entryFromPath(Path("compressed.txt"), Path("example.txt"))
    // Add a file from a source
    zip.entryFromSource(Path("hello_world.txt"), textSource)
}
textSource.close()
```

## 🚧 Contributing

If you have any ideas, feel free to open an issue or create a pull request.

## 📄 License

This project is licensed under the [MIT License](LICENSE).

