package de.jonasbroeckmann.kzip

import dev.karmakrafts.kompress.Deflater
import dev.karmakrafts.kompress.deflating
import dev.karmakrafts.kompress.inflating
import kotlinx.io.*
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

internal const val LOCAL_FILE_HEADER_SIG = 0x04034b50L
internal const val CENTRAL_DIRECTORY_HEADER_SIG = 0x02014b50L
internal const val EOCD_SIG = 0x06054b50L

internal const val METHOD_STORED = 0
internal const val METHOD_DEFLATED = 8

internal sealed interface DataSource {
    class Memory(val bytes: ByteArray) : DataSource
    class File(val path: Path) : DataSource
    class ZipEntry(
        val zipPath: Path,
        val localHeaderOffset: Long,
        val compressedSize: Long,
        val method: Int
    ) : DataSource
}

internal class BoundedSource(
    private val source: Source,
    private var remaining: Long
) : RawSource {
    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        if (remaining <= 0L) return -1
        val toRead = minOf(byteCount, remaining)
        val read = source.readAtMostTo(sink, toRead)
        if (read > 0) remaining -= read
        return read
    }

    override fun close() = source.close()
}

internal class ZipKotlin(
    val path: Path,
    override val mode: Zip.Mode,
    val level: Zip.CompressionLevel
) : Zip {
    private val entries = mutableListOf<ZipKotlinEntry>()
    private var isClosed = false

    init {
        if (mode == Zip.Mode.Read || mode == Zip.Mode.Append) {
            readCentralDirectory()
        }
    }

    override val numberOfEntries: Int
        get() = entries.size

    private fun readCentralDirectory() {
        val size = SystemFileSystem.metadataOrNull(path)?.size ?: throw ZipException("File not found: $path")
        if (size < 22) throw ZipException("Not a ZIP file (too small)")

        SystemFileSystem.source(path).buffered().use { source ->
            val searchSize = minOf(size, 65535L + 22L)
            source.skip(size - searchSize)
            val bytes = source.readByteArray()

            var eocdOffsetInBytes = -1
            for (i in bytes.size - 22 downTo 0) {
                if (bytes[i] == 0x50.toByte() && bytes[i + 1] == 0x4b.toByte() &&
                    bytes[i + 2] == 0x05.toByte() && bytes[i + 3] == 0x06.toByte()
                ) {
                    eocdOffsetInBytes = i
                    break
                }
            }
            if (eocdOffsetInBytes == -1) throw ZipException("Not a ZIP file (EOCD not found)")

            val eocdBuffer = Buffer().apply { write(bytes, eocdOffsetInBytes, bytes.size) }
            eocdBuffer.readIntLe() // sig
            eocdBuffer.readShortLe() // disk number
            eocdBuffer.readShortLe() // disk start
            eocdBuffer.readShortLe() // entries on disk
            val totalEntries = eocdBuffer.readShortLe().toInt() and 0xFFFF
            val cdSize = eocdBuffer.readIntLe().toLong() and 0xFFFFFFFFL
            val cdOffset = eocdBuffer.readIntLe().toLong() and 0xFFFFFFFFL

            openSourceAt(path, cdOffset).buffered().use { cdSource ->
                for (i in 0 until totalEntries) {
                    val sig = cdSource.readIntLe().toLong() and 0xFFFFFFFFL
                    if (sig != CENTRAL_DIRECTORY_HEADER_SIG) throw ZipException("Invalid Central Directory Header signature at ${cdOffset + i * 46}")

                    cdSource.readShortLe() // version made by
                    cdSource.readShortLe() // version needed
                    val flags = cdSource.readShortLe().toInt() and 0xFFFF
                    val method = cdSource.readShortLe().toInt() and 0xFFFF
                    cdSource.readShortLe() // mod time
                    cdSource.readShortLe() // mod date
                    val crc = cdSource.readIntLe().toLong() and 0xFFFFFFFFL
                    val compressedSize = cdSource.readIntLe().toLong() and 0xFFFFFFFFL
                    val uncompressedSize = cdSource.readIntLe().toLong() and 0xFFFFFFFFL
                    val nameLen = cdSource.readShortLe().toInt() and 0xFFFF
                    val extraLen = cdSource.readShortLe().toInt() and 0xFFFF
                    val commentLen = cdSource.readShortLe().toInt() and 0xFFFF
                    cdSource.readShortLe() // disk start
                    cdSource.readShortLe() // internal attr
                    cdSource.readIntLe() // external attr
                    val localHeaderOffset = cdSource.readIntLe().toLong() and 0xFFFFFFFFL

                    val name = cdSource.readString(nameLen.toLong())
                    cdSource.skip(extraLen.toLong() + commentLen.toLong())

                    entries.add(
                        ZipKotlinEntry(
                            this,
                            name,
                            method,
                            compressedSize.toULong(),
                            uncompressedSize.toULong(),
                            crc,
                            localHeaderOffset,
                            DataSource.ZipEntry(path, localHeaderOffset, compressedSize, method)
                        )
                    )
                }
            }
        }
    }

    override fun entry(entry: Path, block: Zip.Entry.() -> Unit) {
        val name = Zip.pathToEntryName(entry)
        val folderName = Zip.pathToFolderEntryName(entry)
        val e = entries.find { it.pathName == name || it.pathName == folderName }
            ?: throw ZipException("Entry not found: $name")
        e.block()
    }

    override fun entry(index: Int, block: Zip.Entry.() -> Unit) {
        if (index !in entries.indices) throw ZipException("Invalid index: $index")
        entries[index].block()
    }

    override fun deleteEntries(paths: List<Path>) {
        requireWritable()
        val names = paths.map { Zip.pathToEntryName(it) }
        entries.removeAll { it.pathName in names }
    }

    override fun deleteEntriesByIndex(indices: List<Int>) {
        requireWritable()
        val sortedIndices = indices.sortedDescending()
        for (index in sortedIndices) {
            if (index in entries.indices) {
                entries.removeAt(index)
            }
        }
    }

    override fun entryFromSource(entry: Path, data: Source) {
        requireWritable()
        val bytes = data.readByteArray()
        addEntry(Zip.pathToEntryName(entry), DataSource.Memory(bytes), bytes.size.toULong(), false)
    }

    override fun entryFromPath(entry: Path, file: Path) {
        requireWritable()
        file.requireFile()
        val size = SystemFileSystem.metadataOrNull(file)?.size ?: 0L
        addEntry(Zip.pathToEntryName(entry), DataSource.File(file), size.toULong(), false)
    }

    override fun folderEntry(entry: Path) {
        requireWritable()
        addEntry(Zip.pathToFolderEntryName(entry), DataSource.Memory(byteArrayOf()), 0uL, true)
    }

    private fun addEntry(name: String, dataSource: DataSource, size: ULong, isDirectory: Boolean) {
        val existing = entries.find { it.pathName == name }
        // Determine method based on level and type
        val method = if (isDirectory || level == Zip.CompressionLevel.NoCompression) METHOD_STORED else METHOD_DEFLATED

        val newEntry = ZipKotlinEntry(
            this,
            name,
            method,
            if (method == METHOD_STORED) size else 0uL, // compressed size updated during write
            size,
            0,
            0,
            dataSource,
            isDirectory
        )

        if (existing != null) {
            entries[entries.indexOf(existing)] = newEntry
        } else {
            entries.add(newEntry)
        }
    }

    override fun close() {
        if (isClosed) return
        if (mode != Zip.Mode.Read) {
            writeZip()
        }
        isClosed = true
    }

    private fun writeZip() {
        val tempPath = Path(path.toString() + ".tmp")
        try {
            SystemFileSystem.sink(tempPath).buffered().use { sink ->
                val cdEntries = mutableListOf<ZipKotlinEntry>()
                var currentOffset = 0L

                for (entry in entries) {
                    val offset = currentOffset
                    // writeEntryData now returns CRC, CompressedSize, and UncompressedSize
                    val (crc, compSize, uncompSize) = writeEntryData(entry, sink)

                    val entryWithOffset = entry.copy(
                        localHeaderOffset = offset,
                        crc32 = crc,
                        compressedSize = compSize,
                        uncompressedSize = uncompSize
                    ).apply {
                        this.dataSource = entry.dataSource
                        this.isDir = entry.isDir
                    }
                    cdEntries.add(entryWithOffset)

                    currentOffset += 30 + entry.pathName.length + compSize.toLong()
                }

                val cdOffset = currentOffset
                var cdSize = 0L
                for (entry in cdEntries) {
                    sink.writeIntLe(CENTRAL_DIRECTORY_HEADER_SIG.toInt())
                    sink.writeShortLe(20) // made by
                    sink.writeShortLe(20) // needed
                    sink.writeShortLe(0) // flags
                    sink.writeShortLe(entry.method.toShort())
                    sink.writeShortLe(0) // mod time
                    sink.writeShortLe(0) // mod date
                    sink.writeIntLe(entry.crc32.toInt())
                    sink.writeIntLe(entry.compressedSize.toInt())
                    sink.writeIntLe(entry.uncompressedSize.toInt())
                    sink.writeShortLe(entry.pathName.length.toShort())
                    sink.writeShortLe(0) // extra len
                    sink.writeShortLe(0) // comment len
                    sink.writeShortLe(0) // disk start
                    sink.writeShortLe(0) // internal attr
                    sink.writeIntLe(0) // external attr
                    sink.writeIntLe(entry.localHeaderOffset.toInt())
                    sink.writeString(entry.pathName)

                    cdSize += 46 + entry.pathName.length
                }

                sink.writeIntLe(EOCD_SIG.toInt())
                sink.writeShortLe(0) // disk number
                sink.writeShortLe(0) // disk start
                sink.writeShortLe(cdEntries.size.toShort())
                sink.writeShortLe(cdEntries.size.toShort())
                sink.writeIntLe(cdSize.toInt())
                sink.writeIntLe(cdOffset.toInt())
                sink.writeShortLe(0) // comment len
            }
            SystemFileSystem.atomicMove(tempPath, path)
        } finally {
            if (SystemFileSystem.exists(tempPath)) SystemFileSystem.delete(tempPath)
        }
    }

    private fun writeEntryData(entry: ZipKotlinEntry, sink: Sink): Triple<Long, ULong, ULong> {
        val dataSource = entry.dataSource!!
        val finalCrc: Long
        val uncompSize: ULong

        // Pass 1: Calculate CRC and Uncompressed Size
        if (dataSource is DataSource.Memory) {
            finalCrc = Crc32.calculate(dataSource.bytes)
            uncompSize = dataSource.bytes.size.toULong()
        } else {
            val crc = Crc32()
            var size = 0uL
            val s1 = when (dataSource) {
                is DataSource.File -> openSourceAt(dataSource.path, 0L).buffered()
                is DataSource.ZipEntry -> entry.readToSource()
                is DataSource.Memory -> throw IllegalStateException()
            }
            s1.use { s ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = s.readAtMostTo(buffer)
                    if (read == -1) break
                    crc.update(buffer, 0, read)
                    size += read.toULong()
                }
            }
            finalCrc = crc.getValue()
            uncompSize = size
        }

        if (entry.method == METHOD_STORED) {
            // Write Stored data
            val s2 = when (dataSource) {
                is DataSource.Memory -> Buffer().apply { write(dataSource.bytes) }
                is DataSource.File -> openSourceAt(dataSource.path, 0L).buffered()
                is DataSource.ZipEntry -> entry.readToSource()
            }
            s2.use { s ->
                writeLocalHeader(sink, entry.pathName, METHOD_STORED, finalCrc, uncompSize, uncompSize)
                s.transferTo(sink)
            }
            return Triple(finalCrc, uncompSize, uncompSize)
        } else {
            // Write Deflated data
            if (dataSource is DataSource.Memory) {
                // Bulk optimization for memory sources
                val deflated = Deflater.deflate(dataSource.bytes, raw = true, level = level.zlibLevel)
                val compSize = deflated.size.toULong()
                writeLocalHeader(sink, entry.pathName, METHOD_DEFLATED, finalCrc, compSize, uncompSize)
                sink.write(deflated)
                return Triple(finalCrc, compSize, uncompSize)
            } else {
                // Stream to temp file to determine compressed size
                val tempDeflatePath = Path(path.toString() + ".deflate.tmp")
                try {
                    val s2 = when (dataSource) {
                        is DataSource.File -> openSourceAt(dataSource.path, 0L).buffered()
                        is DataSource.ZipEntry -> entry.readToSource()
                        is DataSource.Memory -> throw IllegalStateException()
                    }
                    s2.use { s ->
                        SystemFileSystem.sink(tempDeflatePath).buffered().use { tempSink ->
                            val deflated = s.deflating(level = level.zlibLevel, raw = true).buffered()
                            deflated.use {
                                it.transferTo(tempSink)
                            }
                        }
                    }
                    val compSize = SystemFileSystem.metadataOrNull(tempDeflatePath)?.size?.toULong() ?: 0uL
                    writeLocalHeader(sink, entry.pathName, METHOD_DEFLATED, finalCrc, compSize, uncompSize)
                    SystemFileSystem.source(tempDeflatePath).buffered().use { it.transferTo(sink) }
                    return Triple(finalCrc, compSize, uncompSize)
                } finally {
                    if (SystemFileSystem.exists(tempDeflatePath)) SystemFileSystem.delete(tempDeflatePath)
                }
            }
        }
    }

    private fun writeLocalHeader(sink: Sink, name: String, method: Int, crc: Long, compSize: ULong, uncompSize: ULong) {
        sink.writeIntLe(LOCAL_FILE_HEADER_SIG.toInt())
        sink.writeShortLe(20) // Version needed (2.0 for Deflate)
        sink.writeShortLe(0)  // Flags
        sink.writeShortLe(method.toShort())
        sink.writeShortLe(0)  // Mod time
        sink.writeShortLe(0)  // Mod date
        sink.writeIntLe(crc.toInt())
        sink.writeIntLe(compSize.toInt())
        sink.writeIntLe(uncompSize.toInt())
        sink.writeShortLe(name.length.toShort())
        sink.writeShortLe(0)  // Extra len
        sink.writeString(name)
    }

    internal data class ZipKotlinEntry(
        val zip: ZipKotlin,
        val pathName: String,
        val method: Int,
        override val compressedSize: ULong,
        override val uncompressedSize: ULong,
        override val crc32: Long,
        val localHeaderOffset: Long,
        internal var dataSource: DataSource? = null,
        internal var isDir: Boolean = pathName.endsWith('/')
    ) : Zip.Entry {
        override val path: Path get() = Zip.entryNameToPath(pathName)
        override val isDirectory: Boolean get() = isDir

        override fun readToSource(): Source {
            val rawSource = when (val ds = dataSource!!) {
                is DataSource.Memory -> Buffer().apply { write(ds.bytes) }
                is DataSource.File -> SystemFileSystem.source(ds.path).buffered()
                is DataSource.ZipEntry -> {
                    val s = openSourceAt(ds.zipPath, ds.localHeaderOffset).buffered()
                    val sig = s.readIntLe().toLong() and 0xFFFFFFFFL
                    if (sig != LOCAL_FILE_HEADER_SIG) throw ZipException("Invalid Local File Header at ${ds.localHeaderOffset}")
                    s.skip(22)
                    val nLen = s.readShortLe().toInt() and 0xFFFF
                    val eLen = s.readShortLe().toInt() and 0xFFFF
                    s.skip(nLen.toLong() + eLen.toLong())
                    val bounded = BoundedSource(s, ds.compressedSize).buffered()
                    // Decompress if the source entry is deflated
                    if (ds.method == METHOD_DEFLATED) bounded.inflating(raw = true).buffered() else bounded
                }
            }
            return rawSource
        }

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

public fun Zip.Companion.openKotlin(
    path: Path,
    mode: Zip.Mode = Zip.Mode.Read,
    level: Zip.CompressionLevel = Zip.CompressionLevel.Default
): Zip = ZipKotlin(path, mode, level)
