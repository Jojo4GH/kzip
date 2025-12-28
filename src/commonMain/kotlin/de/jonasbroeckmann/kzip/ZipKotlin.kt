package de.jonasbroeckmann.kzip

import kotlinx.io.*
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

internal const val LOCAL_FILE_HEADER_SIG = 0x04034b50L
internal const val CENTRAL_DIRECTORY_HEADER_SIG = 0x02014b50L
internal const val EOCD_SIG = 0x06054b50L

internal class ZipKotlin(
    val path: Path,
    override val mode: Zip.Mode,
    val level: Zip.CompressionLevel
) : Zip {
    private val entries = mutableListOf<ZipKotlinEntry>()
    private var isClosed = false

    init {
        when (mode) {
            Zip.Mode.Read, Zip.Mode.Append -> {
                readCentralDirectory()
            }

            Zip.Mode.Write -> {
                // Start with empty entries
            }
        }
    }

    override val numberOfEntries: Int
        get() = entries.size

    private fun readCentralDirectory() {
        SystemFileSystem.source(path).buffered().use { source ->
            // In a real ZIP, we should find EOCD by scanning from the end.
            // For now, let's assume no comment and read the whole thing?
            // No, that's bad. But without random access it's hard.
            // Let's read the whole file into a buffer for now if it's small,
            // or just read sequentially if we can't seek.
            // Actually, we can read the whole file to find the EOCD.
            val bytes = source.readByteArray()
            val buffer = Buffer()
            buffer.write(bytes)

            val eocdOffset = findEOCD(bytes)
            if (eocdOffset == -1) throw ZipException("Not a ZIP file (EOCD not found)")

            val eocdBuffer = Buffer().apply { write(bytes, eocdOffset, bytes.size) }
            eocdBuffer.readIntLe() // sig
            eocdBuffer.readShortLe() // disk number
            eocdBuffer.readShortLe() // disk start
            val entriesOnDisk = eocdBuffer.readShortLe().toInt() and 0xFFFF
            val totalEntries = eocdBuffer.readShortLe().toInt() and 0xFFFF
            val cdSize = eocdBuffer.readIntLe().toLong() and 0xFFFFFFFFL
            val cdOffset = eocdBuffer.readIntLe().toLong() and 0xFFFFFFFFL

            val cdBuffer = Buffer().apply { write(bytes, cdOffset.toInt(), (cdOffset + cdSize).toInt()) }
            for (i in 0 until totalEntries) {
                val sig = cdBuffer.readIntLe().toLong() and 0xFFFFFFFFL
                if (sig != CENTRAL_DIRECTORY_HEADER_SIG) throw ZipException("Invalid Central Directory Header signature")

                cdBuffer.readShortLe() // version made by
                cdBuffer.readShortLe() // version needed
                val flags = cdBuffer.readShortLe().toInt() and 0xFFFF
                val method = cdBuffer.readShortLe().toInt() and 0xFFFF
                val modTime = cdBuffer.readShortLe().toInt() and 0xFFFF
                val modDate = cdBuffer.readShortLe().toInt() and 0xFFFF
                val crc = cdBuffer.readIntLe().toLong() and 0xFFFFFFFFL
                val compressedSize = cdBuffer.readIntLe().toLong() and 0xFFFFFFFFL
                val uncompressedSize = cdBuffer.readIntLe().toLong() and 0xFFFFFFFFL
                val nameLen = cdBuffer.readShortLe().toInt() and 0xFFFF
                val extraLen = cdBuffer.readShortLe().toInt() and 0xFFFF
                val commentLen = cdBuffer.readShortLe().toInt() and 0xFFFF
                cdBuffer.readShortLe() // disk start
                cdBuffer.readShortLe() // internal attr
                cdBuffer.readIntLe() // external attr
                val localHeaderOffset = cdBuffer.readIntLe().toLong() and 0xFFFFFFFFL

                val name = cdBuffer.readString(nameLen.toLong())
                cdBuffer.skip(extraLen.toLong())
                cdBuffer.skip(commentLen.toLong())

                val entryData = extractDataFromBytes(bytes, localHeaderOffset, compressedSize.toInt())

                entries.add(
                    ZipKotlinEntry(
                        this,
                        name,
                        method,
                        compressedSize.toULong(),
                        uncompressedSize.toULong(),
                        crc,
                        localHeaderOffset
                    ).apply {
                        this.data = entryData
                    })
            }
        }
    }

    private fun extractDataFromBytes(bytes: ByteArray, offset: Long, size: Int): ByteArray {
        val buffer = Buffer().apply { write(bytes, offset.toInt(), bytes.size) }
        val sig = buffer.readIntLe().toLong() and 0xFFFFFFFFL
        if (sig != LOCAL_FILE_HEADER_SIG) throw ZipException("Invalid Local File Header signature at $offset")
        buffer.skip(22) // skip to nameLen
        val nameLen = buffer.readShortLe().toInt() and 0xFFFF
        val extraLen = buffer.readShortLe().toInt() and 0xFFFF
        val dataOffset = offset.toInt() + 30 + nameLen + extraLen
        val result = ByteArray(size)
        bytes.copyInto(result, 0, dataOffset, dataOffset + size)
        return result
    }

    private fun findEOCD(bytes: ByteArray): Int {
        // Search from the end for EOCD signature
        for (i in bytes.size - 22 downTo 0) {
            if (bytes[i] == 0x50.toByte() &&
                bytes[i + 1] == 0x4b.toByte() &&
                bytes[i + 2] == 0x05.toByte() &&
                bytes[i + 3] == 0x06.toByte()
            ) {
                return i
            }
        }
        return -1
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
        addEntry(Zip.pathToEntryName(entry), bytes, false)
    }

    override fun entryFromPath(entry: Path, file: Path) {
        requireWritable()
        file.requireFile()
        val bytes = SystemFileSystem.source(file).buffered().use { it.readByteArray() }
        addEntry(Zip.pathToEntryName(entry), bytes, false)
    }

    override fun folderEntry(entry: Path) {
        requireWritable()
        addEntry(Zip.pathToFolderEntryName(entry), byteArrayOf(), true)
    }

    private fun addEntry(name: String, data: ByteArray, isDirectory: Boolean) {
        // For now, we just add to the list and write everything on close.
        // In a more efficient implementation, we would write the local header and data immediately.
        val existing = entries.find { it.pathName == name }
        val newEntry = ZipKotlinEntry(
            this,
            name,
            if (isDirectory) 0 else 0, // STORED for now
            data.size.toULong(),
            data.size.toULong(),
            crc32(data),
            0 // offset will be calculated on write
        ).apply {
            this.data = data
            this.isDir = isDirectory
        }

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
        SystemFileSystem.sink(path).buffered().use { sink ->
            val cdEntries = mutableListOf<ZipKotlinEntry>()
            var currentOffset = 0L

            for (entry in entries) {
                val offset = currentOffset
                val data = entry.data ?: readEntryData(entry)

                // Write Local File Header
                sink.writeIntLe(LOCAL_FILE_HEADER_SIG.toInt())
                sink.writeShortLe(20) // version needed
                sink.writeShortLe(0) // flags
                sink.writeShortLe(entry.method.toShort())
                sink.writeShortLe(0) // mod time
                sink.writeShortLe(0) // mod date
                sink.writeIntLe(entry.crc32.toInt())
                sink.writeIntLe(entry.compressedSize.toInt())
                sink.writeIntLe(entry.uncompressedSize.toInt())
                sink.writeShortLe(entry.pathName.length.toShort())
                sink.writeShortLe(0) // extra len
                sink.writeString(entry.pathName)
                // No extra field

                sink.write(data)

                val entryWithOffset = entry.copy(localHeaderOffset = offset)
                cdEntries.add(entryWithOffset)

                currentOffset += 30 + entry.pathName.length + data.size
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

            // Write EOCD
            sink.writeIntLe(EOCD_SIG.toInt())
            sink.writeShortLe(0) // disk number
            sink.writeShortLe(0) // disk start
            sink.writeShortLe(cdEntries.size.toShort())
            sink.writeShortLe(cdEntries.size.toShort())
            sink.writeIntLe(cdSize.toInt())
            sink.writeIntLe(cdOffset.toInt())
            sink.writeShortLe(0) // comment len
        }
    }

    internal fun readEntryData(entry: ZipKotlinEntry): ByteArray {
        if (entry.data != null) return entry.data!!

        SystemFileSystem.source(path).buffered().use { source ->
            source.skip(entry.localHeaderOffset)
            val sig = source.readIntLe().toLong() and 0xFFFFFFFFL
            if (sig != LOCAL_FILE_HEADER_SIG) throw ZipException("Invalid Local File Header signature at ${entry.localHeaderOffset}")

            source.skip(22) // skip to name len
            val nameLen = source.readShortLe().toInt() and 0xFFFF
            val extraLen = source.readShortLe().toInt() and 0xFFFF
            source.skip(nameLen.toLong() + extraLen.toLong())

            val data = source.readByteArray(entry.compressedSize.toInt())
            if (entry.method == 0) {
                return data
            } else {
                throw ZipException("Unsupported compression method: ${entry.method}")
            }
        }
    }
}

internal data class ZipKotlinEntry(
    val zip: ZipKotlin,
    val pathName: String,
    val method: Int,
    override val compressedSize: ULong,
    override val uncompressedSize: ULong,
    override val crc32: Long,
    val localHeaderOffset: Long
) : Zip.Entry {
    override val path: Path get() = Zip.entryNameToPath(pathName)
    internal var isDir: Boolean = pathName.endsWith('/')
    override val isDirectory: Boolean get() = isDir

    internal var data: ByteArray? = null

    override fun readToSource(): Source = Buffer().apply { write(readToBytes()) }
    override fun readToBytes(): ByteArray = zip.readEntryData(this)
    override fun readToPath(path: Path) {
        SystemFileSystem.sink(path).buffered().use { it.write(readToBytes()) }
    }
}

internal fun crc32(data: ByteArray): Long {
    var crc = 0xFFFFFFFFL
    for (b in data) {
        crc = crc xor (b.toInt() and 0xFF).toLong()
        for (i in 0 until 8) {
            crc = if (crc and 1L != 0L) (crc ushr 1) xor 0xEDB88320L else crc ushr 1
        }
    }
    return crc xor 0xFFFFFFFFL
}

public fun Zip.Companion.openKotlin(
    path: Path,
    mode: Zip.Mode = Zip.Mode.Read,
    level: Zip.CompressionLevel = Zip.CompressionLevel.Default
): Zip = ZipKotlin(path, mode, level)
