package de.jonasbroeckmann.kzip

import kotlinx.io.*
import kotlinx.io.files.Path


public interface Zip : AutoCloseable {
    public val mode: Mode
    public val numberOfEntries: ULong

    public fun entry(entry: Path, block: Entry.() -> Unit)
    public fun entry(index: ULong, block: Entry.() -> Unit)

//    public fun deleteEntries(paths: List<Path>)
//    public fun deleteEntries(indices: List<ULong>)

    public fun entryFromSource(entry: Path, data: Source)
    public fun entryFromPath(entry: Path, file: Path)
    public fun folderEntry(entry: Path)

    public interface Entry {
        public val path: Path
        public val isDirectory: Boolean
        public val uncompressedSize: ULong
        public val compressedSize: ULong
        public val crc32: Long

        public fun readToSource(): Source
        public fun readToBytes(): ByteArray
        public fun readToPath(path: Path)
    }

    public enum class Mode {
        Read, Write, Append
    }

    public enum class CompressionLevel(public val zlibLevel: Int) {
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
        public companion object {
            public val Default: CompressionLevel = MediumBetterCompression
        }
    }

    public companion object
}

public expect fun Zip.Companion.open(
    path: Path,
    mode: Zip.Mode = Zip.Mode.Read,
    level: Zip.CompressionLevel = Zip.CompressionLevel.Default
): Zip
