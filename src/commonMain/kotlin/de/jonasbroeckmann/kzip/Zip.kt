package de.jonasbroeckmann.kzip

import kotlinx.io.*
import kotlinx.io.files.Path


interface Zip : AutoCloseable {
    val mode: Mode
    val numberOfEntries: ULong

    fun entry(entry: Path, block: Entry.() -> Unit)
    fun entry(index: ULong, block: Entry.() -> Unit)

//    fun deleteEntries(paths: List<Path>)
//    fun deleteEntries(indices: List<ULong>)

    fun entryFromSource(entry: Path, data: Source)
    fun entryFromPath(entry: Path, file: Path)
    fun folderEntry(entry: Path)

    interface Entry {
        val path: Path
        val isDirectory: Boolean
        val uncompressedSize: ULong
        val compressedSize: ULong
        val crc32: Long

        fun readToSource(): Source
        fun readToBytes(): ByteArray
        fun readToPath(path: Path)
    }

    enum class Mode {
        Read, Write, Append
    }

    enum class CompressionLevel(val zlibLevel: Int) {
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

expect fun Zip.Companion.open(
    path: Path,
    mode: Zip.Mode = Zip.Mode.Read,
    level: Zip.CompressionLevel = Zip.CompressionLevel.Default
): Zip
