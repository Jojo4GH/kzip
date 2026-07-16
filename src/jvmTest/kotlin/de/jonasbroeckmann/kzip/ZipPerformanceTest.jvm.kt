package de.jonasbroeckmann.kzip

import de.jonasbroeckmann.kzip.implementation.AbstractZip
import de.jonasbroeckmann.kzip.implementation.EntryNameUtils
import kotlinx.io.Source
import kotlinx.io.asInputStream
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.io.files.Path
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel as Zip4jCompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import java.io.ByteArrayInputStream
import java.io.File

actual fun Zip.Companion.openNative(
    path: Path,
    mode: Zip.Mode,
    level: Zip.CompressionLevel
): Zip = JavaZip(
    zipPath = path,
    mode = mode,
    level = level
)

private class JavaZip(
    zipPath: Path,
    override val mode: Zip.Mode,
    private val level: Zip.CompressionLevel
) : AbstractZip() {
    private val zipFile = ZipFile(File(zipPath.toString()))

    init {
        if (mode == Zip.Mode.Read && !zipFile.file.exists()) {
            throw ZipException("File does not exist: $zipPath")
        }
        if (mode == Zip.Mode.Write && zipFile.file.exists()) {
            zipFile.file.delete()
        }
    }

    override val numberOfEntries: Int get() = zipFile.fileHeaders.size

    override fun <R> entry(entry: Path, block: Zip.Entry.() -> R): R {
        requireReadable()
        val name = EntryNameUtils.pathToFileName(entry)
        val fileHeader = zipFile.getFileHeader(name) ?: throw ZipException("Entry not found: $name")
        return Entry(fileHeader).block()
    }

    override fun <R> entry(index: Int, block: Zip.Entry.() -> R): R {
        requireReadable()
        val fileHeader = zipFile.fileHeaders[index]
        return Entry(fileHeader).block()
    }

    override fun deleteEntries(paths: List<Path>) {
        requireWritable()
        paths.forEach { zipFile.removeFile(EntryNameUtils.pathToFileName(it)) }
    }

    override fun deleteEntriesByIndex(indices: List<Int>) {
        requireWritable()
        val headers = zipFile.fileHeaders
        indices.sortedDescending().forEach { index ->
            zipFile.removeFile(headers[index])
        }
    }

    override fun entryFromSource(entry: Path, data: Source) {
        requireWritable()
        val parameters = ZipParameters().apply {
            fileNameInZip = EntryNameUtils.pathToFileName(entry)
            compressionMethod = CompressionMethod.DEFLATE
            compressionLevel = mapCompressionLevel(level)
        }
        zipFile.addStream(data.asInputStream(), parameters)
    }

    override fun entryFromPath(entry: Path, file: Path) {
        requireWritable()
        requireValidFile(file)
        val parameters = ZipParameters().apply {
            fileNameInZip = EntryNameUtils.pathToFileName(entry)
            compressionMethod = CompressionMethod.DEFLATE
            compressionLevel = mapCompressionLevel(level)
        }
        zipFile.addFile(File(file.toString()), parameters)
    }

    override fun folderEntry(entry: Path) {
        requireWritable()
        val parameters = ZipParameters().apply {
            fileNameInZip = EntryNameUtils.pathToFolderName(entry)
            compressionMethod = CompressionMethod.STORE
        }
        zipFile.addStream(ByteArrayInputStream(ByteArray(0)), parameters)
    }

    override fun closeImpl() {
        zipFile.close()
    }

    private fun mapCompressionLevel(level: Zip.CompressionLevel): Zip4jCompressionLevel = when (level.zlibLevel) {
        0 -> Zip4jCompressionLevel.NO_COMPRESSION
        in 1..2 -> Zip4jCompressionLevel.FASTEST
        in 3..4 -> Zip4jCompressionLevel.FAST
        in 5..6 -> Zip4jCompressionLevel.NORMAL
        in 7..8 -> Zip4jCompressionLevel.MAXIMUM
        else -> Zip4jCompressionLevel.ULTRA
    }

    private inner class Entry(private val header: net.lingala.zip4j.model.FileHeader) : Zip.Entry {
        override val path: Path get() = EntryNameUtils.entryNameToPath(header.fileName)
        override val isDirectory: Boolean get() = header.isDirectory
        override val uncompressedSize: ULong get() = header.uncompressedSize.toULong()
        override val compressedSize: ULong get() = header.compressedSize.toULong()
        override val crc32: Long get() = header.crc

        override fun readToSource(): Source = zipFile.getInputStream(header).asSource().buffered()
    }
}
