package de.jonasbroeckmann.kzip

import kotlinx.io.IOException
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem


interface Zip : AutoCloseable {
    val numberOfEntries: ULong
    fun entry(path: Path, block: Entry.() -> Unit)
    fun entry(index: ULong, block: Entry.() -> Unit)

    interface Entry {
        val path: Path
        val isDirectory: Boolean
        val uncompressedSize: ULong
        val compressedSize: ULong
        val crc32: Long

        fun read(): ByteArray
        fun readToPath(path: Path)
    }

    enum class CompressionLevel(val level: Int) {
        NoCompression(0),
        BestSpeed(1),
        BetterSpeed(2),
        GoodSpeed(3),
        MediumBetterSpeed(4),
        Medium(5),
        MediumBetterCompression(6),
        GoodCompression(7),
        BetterCompression(8),
        BestCompression(9);
        companion object {
            val Default = MediumBetterCompression
        }
    }

    companion object
}

interface WritableZip : Zip {
    override fun entry(path: Path, block: Zip.Entry.() -> Unit) = writableEntry(path, block)
    fun writableEntry(path: Path, block: Entry.() -> Unit)
    fun writeFolder(path: Path)
    fun deleteEntries(paths: List<Path>)
    fun deleteEntries(vararg indices: ULong)

    interface Entry : Zip.Entry {
        fun write(data: ByteArray)
        fun writeFromPath(path: Path)
    }

    enum class Mode {
        Write, Append
    }

    companion object
}

fun Zip.Entry.readToString(): String = read().decodeToString()
fun WritableZip.Entry.writeFromString(data: String) = write(data.encodeToByteArray())

internal fun Zip.Companion.pathToEntryName(path: Path): String {
    if (path.isAbsolute) throw IllegalArgumentException("Path for zip entry must be relative: $path")
    path.parent?.let { parent ->
        return "${pathToEntryName(parent)}$ZipEntryNameSeparator${path.name}"
    }
    return path.name
}
internal fun Zip.Companion.entryNameToPath(name: String): Path {
    val elements = name.split(ZipEntryNameSeparator)
    return Path(elements.first(), *elements.drop(1).toTypedArray())
}
internal const val ZipEntryNameSeparator = '/'


expect fun Zip.Companion.open(path: Path): Zip

expect fun WritableZip.Companion.open(
    path: Path,
    level: Zip.CompressionLevel = Zip.CompressionLevel.Default,
    mode: WritableZip.Mode = WritableZip.Mode.Write
): WritableZip


expect class ZipException(message: String) : IOException


inline fun Zip.forEachEntryIndexed(crossinline block: (ULong, Zip.Entry) -> Unit) {
    for (i in 0uL until numberOfEntries) {
        entry(i) { block(i, this) }
    }
}

inline fun Zip.forEachEntry(crossinline block: (Zip.Entry) -> Unit) = forEachEntryIndexed { _, entry ->
    block(entry)
}

fun Zip.extractTo(directory: Path) {
    forEachEntry { entry ->
        val target = Path(directory, entry.path.toString())
        if (entry.isDirectory) {
            SystemFileSystem.createDirectories(target)
        } else {
            target.parent?.let { SystemFileSystem.createDirectories(it) }
            entry.readToPath(target)
        }
    }
}

fun WritableZip.compress(path: Path, pathInZip: Path? = null) {
    val metadata = SystemFileSystem.metadataOrNull(path) ?: throw IOException("Cannot read metadata of $path")
    if (metadata.isDirectory) {
        println("Compressing directory $pathInZip")
        if (pathInZip != null) {
            writeFolder(pathInZip)
        }
        SystemFileSystem.list(path).forEach { child ->
            compress(child, pathInZip?.let { Path(it, child.name) } ?: Path(child.name))
        }
    } else if (metadata.isRegularFile) {
        println("Compressing file $pathInZip")
        if (pathInZip != null) {
//            writableEntry(pathInZip) {
//                writeFromPath(path)
//            }
        }
    } else {
        throw UnsupportedOperationException("Unsupported file type at $path")
    }
}


fun main() {
//    SystemFileSystem.list(Path(".")).forEach {
//        println(it)
//    }

    val zipFile = Path("test.klib")

    val otherZipFile = Path("testother.zip")

    val testDir = Path("testdir")

//    Zip.open(zipFile).use { zip ->
//        zip.forEachEntry {
//            println("${it.path}")
//        }
//        zip.extractTo(testDir)
//    }

    WritableZip.open(otherZipFile).use { zip ->
//        zip.writableEntry(Path("mydir", "myfile.txt")) {
//            writeFromString("Hello, World!")
//        }
        zip.compress(testDir)
    }
    Zip.open(otherZipFile).use { zip ->
        zip.forEachEntry {
            println("${it.path}")
        }
    }
}
