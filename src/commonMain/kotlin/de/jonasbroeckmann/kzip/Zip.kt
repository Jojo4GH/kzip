package de.jonasbroeckmann.kzip

import kotlinx.io.*
import kotlinx.io.files.Path

/**
 * A ZIP file.
 *
 * Use the [open] function to open a ZIP file.
 */
public interface Zip : AutoCloseable {
    /**
     * The mode the ZIP file was opened in.
     */
    public val mode: Mode

    /**
     * The number of entries in the ZIP file.
     */
    public val numberOfEntries: ULong

    /**
     * Obtains an entry in the ZIP file by its path.
     *
     * @param entry the path of the entry
     * @param block the block to execute on the entry
     */
    public fun entry(entry: Path, block: Entry.() -> Unit)

    /**
     * Obtains an entry in the ZIP file by its index.
     *
     * @param index the index of the entry
     * @param block the block to execute on the entry
     */
    public fun entry(index: ULong, block: Entry.() -> Unit)

    /**
     * Deletes entries from the ZIP file.
     *
     * **Note:** This operation is not supported on JVM.
     *
     * @param paths the paths of the entries to delete
     */
    public fun deleteEntries(paths: List<Path>)

    /**
     * Deletes entries from the ZIP file.
     *
     * **Note:** This operation is not supported on JVM.
     *
     * @param indices the indices of the entries to delete
     */
    public fun deleteEntries(indices: List<ULong>)

    /**
     * Adds an entry to the ZIP file from a [Source].
     *
     * @param entry the path of the entry
     * @param data the source to read the entry from
     */
    public fun entryFromSource(entry: Path, data: Source)

    /**
     * Adds an entry to the ZIP file from a file.
     *
     * @param entry the path of the entry
     * @param file the path to the file to read the entry from
     */
    public fun entryFromPath(entry: Path, file: Path)

    /**
     * Adds a folder entry to the ZIP file.
     *
     * @param entry the path of the folder entry
     */
    public fun folderEntry(entry: Path)

    /**
     * An entry in a ZIP file. Use the [entry] functions to obtain an entry.
     */
    public interface Entry {
        /**
         * The path of the entry.
         */
        public val path: Path

        /**
         * Whether the entry is a directory.
         */
        public val isDirectory: Boolean

        /**
         * The uncompressed size of the entry in bytes.
         */
        public val uncompressedSize: ULong

        /**
         * The compressed size of the entry in bytes.
         */
        public val compressedSize: ULong

        /**
         * The CRC-32 checksum of the entry.
         */
        public val crc32: Long

        /**
         * Reads the entry to a [Source].
         *
         * @return the source to read the entry from
         */
        public fun readToSource(): Source

        /**
         * Reads the entry to a byte array.
         *
         * @return the byte array to read the entry from
         */
        public fun readToBytes(): ByteArray

        /**
         * Reads the entry to a file.
         *
         * @param path the path to read the entry to
         */
        public fun readToPath(path: Path)
    }

    /**
     * The mode to open a ZIP file in.
     */
    public enum class Mode {
        /**
         * The ZIP file is opened for reading. The file must exist.
         */
        Read,

        /**
         * The ZIP file is opened for writing.
         * If the file exists, it will be truncated.
         * If the file does not exist, it will be created.
         */
        Write,

        /**
         * The ZIP file is opened for appending.
         */
        Append
    }

    /**
     * The compression level to use when writing to a ZIP file.
     * The default is [CompressionLevel.Default].
     *
     * @property zlibLevel the zlib compression level
     */
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
            /**
             * The default compression level.
             */
            public val Default: CompressionLevel = MediumBetterCompression
        }
    }

    public companion object
}

/**
 * Opens a ZIP file at the given [path].
 *
 * @param path the path to the ZIP file. If the file does not exist, it will be created.
 * @param mode the mode to open the ZIP file in. Defaults to [Zip.Mode.Read]
 * @param level the compression level to use when writing to the ZIP file. Defaults to [Zip.CompressionLevel.Default]
 */
public expect fun Zip.Companion.open(
    path: Path,
    mode: Zip.Mode = Zip.Mode.Read,
    level: Zip.CompressionLevel = Zip.CompressionLevel.Default
): Zip
