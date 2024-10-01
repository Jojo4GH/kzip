package de.jonasbroeckmann.kzip

import kotlinx.io.*
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import java.io.File
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry as JavaZipEntry
import java.util.zip.ZipException as JavaZipException
import java.util.zip.ZipFile as JavaZipFile


private class JavaZip(
    private val zipFile: Path,
    override val mode: Zip.Mode,
    private val level: Zip.CompressionLevel
) : Zip {
    private var _zip: JavaZipFile? = null
    private val zip: JavaZipFile get() {
        requireReadable()
        return _zip ?: JavaZipFile(File(zipFile.toString())).also { _zip = it }
    }

    private var _zos: ZipOutputStream? = null
    private val zos: ZipOutputStream get() {
        requireWritable()
        return _zos
            ?: ZipOutputStream(SystemFileSystem.sink(zipFile).buffered().asOutputStream())
                .apply { setLevel(level.zlibLevel) }
                .also { _zos = it }
    }

    override val numberOfEntries: ULong get() = zip.size().orError().toULong()
    private val entries by lazy { zip.entries().toList() }

    override fun entry(entry: Path, block: Zip.Entry.() -> Unit) {
        val name = Zip.pathToEntryName(entry)
        Entry(zip.getEntry(name) ?: throw JavaZipException("Entry not found: $name")).block()
    }
    override fun entry(index: ULong, block: Zip.Entry.() -> Unit) {
        Entry(entries[index.toInt()]).block()
    }

//    override fun deleteEntries(indices: List<ULong>) {
//        throw UnsupportedOperationException("Deleting entries is not supported on JVM")
//    }
//    override fun deleteEntries(paths: List<Path>) {
//        throw UnsupportedOperationException("Deleting entries is not supported on JVM")
//    }

    override fun entryFromSource(entry: Path, data: Source) {
        zos.putNextEntry(JavaZipEntry(Zip.pathToEntryName(entry)))
        data.transferTo(zos.asSink())
    }
    override fun entryFromPath(entry: Path, file: Path) {
        file.requireFile()
        zos.putNextEntry(JavaZipEntry(Zip.pathToEntryName(entry)))
        SystemFileSystem.source(file).buffered().use { it.transferTo(zos.asSink()) }
    }
    override fun folderEntry(entry: Path) {
        zos.putNextEntry(JavaZipEntry(Zip.pathToEntryName(entry) + '/'))
    }

    override fun close() {
        _zos?.close()
        _zos = null
        _zip?.close()
        _zip = null
    }

    private inner class Entry(private val entry: JavaZipEntry) : Zip.Entry {
        override val path: Path by lazy { Zip.entryNameToPath(entry.name) }
        override val isDirectory: Boolean get() = entry.isDirectory
        override val uncompressedSize: ULong get() = entry.size.orError()
        override val compressedSize: ULong get() = entry.compressedSize.orError()
        override val crc32: Long get() = entry.crc

        override fun readToSource(): Source = zip.getInputStream(entry).asSource().buffered()
        override fun readToBytes(): ByteArray = readToSource().use { it.readByteArray() }
        override fun readToPath(path: Path) {
            readToSource().use { source ->
                SystemFileSystem.sink(path).buffered().use { sink ->
                    source.transferTo(sink)
                }
            }
        }
    }
}

actual fun Zip.Companion.open(
    path: Path,
    mode: Zip.Mode,
    level: Zip.CompressionLevel
): Zip = JavaZip(
    zipFile = path,
    mode = mode,
    level = level
)

private fun Long.orError(): ULong {
    if (this < 0) throw ZipException("Negative value: $this")
    return toULong()
}

private fun Int.orError(): UInt {
    if (this < 0) throw ZipException("Negative value: $this")
    return toUInt()
}
