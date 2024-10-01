package de.jonasbroeckmann.kzip

import kotlinx.io.asOutputStream
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.sink
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.FileHeader
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import java.io.File
import java.util.zip.ZipEntry as JavaZipEntry
import java.util.zip.ZipException as JavaZipException
import java.util.zip.ZipFile as JavaZipFile


private class ReadOnlyJavaZip(private val zip: JavaZipFile) : Zip, AutoCloseable by zip {
    override val numberOfEntries: ULong get() = zip.size().orError().toULong()
    private val entries by lazy { zip.entries().toList() }

    override fun entry(path: Path, block: Zip.Entry.() -> Unit) {
        val name = Zip.pathToEntryName(path)
        Entry(zip.getEntry(name) ?: throw JavaZipException("Entry not found: $name")).block()

    }
    override fun entry(index: ULong, block: Zip.Entry.() -> Unit) {
        Entry(entries[index.toInt()]).block()
    }

    private inner class Entry(private val entry: JavaZipEntry) : Zip.Entry {
        override val path: Path by lazy { Zip.entryNameToPath(entry.name) }
        override val isDirectory: Boolean get() = entry.isDirectory
        override val uncompressedSize: ULong get() = entry.size.orError()
        override val compressedSize: ULong get() = entry.compressedSize.orError()
        override val crc32: Long get() = entry.crc

        override fun read(): ByteArray = zip.getInputStream(entry).use { it.readBytes() }
        override fun readToPath(path: Path) {
            zip.getInputStream(entry).asSource().buffered().use { source ->
                SystemFileSystem.sink(path).buffered().use { sink ->
                    source.transferTo(sink)
                }
            }
        }
    }
}

//private class Zip4JZip(
//    private val zip: net.lingala.zip4j.ZipFile,
//    compressionLevel: Zip.CompressionLevel
//) : WritableZip, AutoCloseable by zip {
//    private val compressionLevel = compressionLevel.toZip4JLevel()
//
//    override val numberOfEntries: ULong get() = zip.fileHeaders.size.toULong()
//
//    override fun writableEntry(path: Path, block: WritableZip.Entry.() -> Unit) {
//        Entry(queriedPath = path) { zip.getFileHeader(Zip.pathToEntryName(path)) }.block()
//    }
//    override fun entry(index: ULong, block: Zip.Entry.() -> Unit) {
//        Entry(queriedPath = null) { zip.fileHeaders[index.toInt()] }.block()
//    }
//
//    override fun writeFolder(path: Path) {
//
//
//        zip.addFolder(path.toFile(), ZipParameters().apply {
//            fileNameInZip = Zip.pathToEntryName(path)
//            compressionLevel = this@Zip4JZip.compressionLevel
//        })
//    }
//
//    override fun deleteEntries(paths: List<Path>) {
//        zip.removeFiles(paths.map { Zip.pathToEntryName(it) })
//    }
//    @OptIn(ExperimentalUnsignedTypes::class)
//    override fun deleteEntries(vararg indices: ULong) {
//        zip.removeFiles(indices.map { zip.fileHeaders[it.toInt()].fileName })
//    }
//
//    private inner class Entry(
//        private val queriedPath: Path?,
//        private val headerGetter: () -> FileHeader?
//    ) : WritableZip.Entry {
//        private val requireHeader: FileHeader get() = headerGetter()
//            ?: throw ZipException("Entry not found: $path")
//        override val path: Path by lazy {
//            headerGetter()?.let { Zip.entryNameToPath(it.fileName) }
//                ?: queriedPath
//                ?: throw ZipException("Entry not found: $path")
//        }
//        override val isDirectory: Boolean get() = requireHeader.isDirectory
//        override val uncompressedSize: ULong get() = requireHeader.uncompressedSize.orError()
//        override val compressedSize: ULong get() = requireHeader.compressedSize.orError()
//        override val crc32: Long get() = requireHeader.signature.value
//
//        override fun read(): ByteArray = zip.getInputStream(requireHeader).use { it.readBytes() }
//        override fun readToPath(path: Path) {
//            zip.getInputStream(requireHeader).asSource().buffered().use { source ->
//                SystemFileSystem.sink(path).buffered().use { sink ->
//                    source.transferTo(sink)
//                }
//            }
//        }
//
//        private fun createWriteParameters() = ZipParameters().apply {
//            fileNameInZip = headerGetter()?.fileName
//                ?: queriedPath?.let { Zip.pathToEntryName(it) }
//                ?: throw ZipException("Cannot create entry without path")
//            compressionLevel = this@Zip4JZip.compressionLevel
//        }
//        override fun write(data: ByteArray) {
//            zip.addStream(data.inputStream(), createWriteParameters())
//        }
//        override fun writeFromPath(path: Path) {
//            zip.addFile(path.toString(), createWriteParameters())
//        }
//    }
//}
//
//private fun Zip.CompressionLevel.toZip4JLevel() = when (this) {
//    Zip.CompressionLevel.NoCompression -> CompressionLevel.NO_COMPRESSION
//    Zip.CompressionLevel.BestSpeed -> CompressionLevel.FASTEST
//    Zip.CompressionLevel.BetterSpeed -> CompressionLevel.FASTER
//    Zip.CompressionLevel.GoodSpeed -> CompressionLevel.FAST
//    Zip.CompressionLevel.MediumBetterSpeed -> CompressionLevel.MEDIUM_FAST
//    Zip.CompressionLevel.Medium -> CompressionLevel.NORMAL
//    Zip.CompressionLevel.MediumBetterCompression -> CompressionLevel.HIGHER
//    Zip.CompressionLevel.GoodCompression -> CompressionLevel.MAXIMUM
//    Zip.CompressionLevel.BetterCompression -> CompressionLevel.PRE_ULTRA
//    Zip.CompressionLevel.BestCompression -> CompressionLevel.ULTRA
//}


private fun Long.orError(): ULong {
    if (this < 0) throw ZipException("Negative value: $this")
    return toULong()
}
private fun Int.orError(): UInt {
    if (this < 0) throw ZipException("Negative value: $this")
    return toUInt()
}


actual fun Zip.Companion.open(path: Path): Zip {
//    return ReadOnlyJavaZip(JavaZipFile(path.toFile()))
    return Zip4JZip(net.lingala.zip4j.ZipFile(path.toString()), Zip.CompressionLevel.Default)
}

actual fun WritableZip.Companion.open(path: Path, level: Zip.CompressionLevel, mode: WritableZip.Mode): WritableZip {
    if (mode == WritableZip.Mode.Append) throw UnsupportedOperationException("Append mode is not supported on JVM")
    return Zip4JZip(net.lingala.zip4j.ZipFile(path.toString()), level)
}


actual typealias ZipException = JavaZipException



private fun Path.toFile(): File = File(toString())
