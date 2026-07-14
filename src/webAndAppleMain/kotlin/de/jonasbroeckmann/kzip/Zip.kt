@file:OptIn(ExperimentalUnsignedTypes::class)

package de.jonasbroeckmann.kzip

import dev.karmakrafts.kompress.Deflater
import dev.karmakrafts.kompress.Inflater
import dev.karmakrafts.kompress.deflating
import dev.karmakrafts.kompress.inflating
import kotlinx.io.*
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.decodeToString
import kotlinx.io.bytestring.encodeToByteString
import kotlinx.io.bytestring.lastIndexOf
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.Boolean
import kotlin.comparisons.minOf
import kotlin.use

private object MagicNumbers {
    const val LOCAL_FILE_HEADER = 0x04034b50u
    const val CENTRAL_DIRECTORY_FILE_HEADER = 0x02014b50u
    const val END_OF_CENTRAL_DIRECTORY_RECORD = 0x06054b50u
}

private object CompressionMethod {
    const val NONE: UShort = 0u
    const val DEFLATED: UShort = 8u
}

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

private class LimitedSource(
    private val upstream: RawSource,
    private var remaining: Long
) : RawSource {
    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        if (remaining <= 0L) return -1
        val toRead = minOf(byteCount, remaining)
        val read = upstream.readAtMostTo(sink, toRead)
        if (read > 0) remaining -= read
        return read
    }

    override fun close() = upstream.close()
}

internal fun RawSource.limited(byteCount: Long): RawSource = LimitedSource(upstream = this, remaining = byteCount)

internal class CountingSource(
    private val source: RawSource
) : RawSource {
    var count = 0L
        private set

    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        val read = source.readAtMostTo(sink, byteCount)
        if (read >= 0L) count += read
        return read
    }

    override fun close() = source.close()
}

internal fun RawSource.counting() = CountingSource(source = this)

internal class CountingSink(
    private val sink: RawSink = discardingSink()
) : RawSink {
    var count = 0L
        private set

    override fun write(source: Buffer, byteCount: Long) {
        sink.write(source, byteCount)
        count += byteCount
    }

    override fun flush() = sink.flush()

    override fun close() = sink.close()
}

private class FeedingSource(
    private val source: RawSource,
    private val autoClose: Boolean,
    private val sinks: List<RawSink>
): RawSource {
    private val buffer = Buffer()

    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        val read = source.readAtMostTo(buffer, byteCount)
        if (read > 0) sinks.forEach {
            buffer.copy().use { copy -> copy.transferTo(it) }
        }
        buffer.transferTo(sink)
        return read
    }

    override fun close() {
        buffer.close()
        source.close()
        if (autoClose) sinks.forEach { it.close() }
    }
}

internal fun RawSource.feed(vararg sinks: RawSink, autoClose: Boolean = true): RawSource = FeedingSource(
    source = this,
    autoClose = autoClose,
    sinks = listOf(*sinks)
)

private class ByteArraySource(
    private val bytes: ByteArray,
    startIndex: Int = 0,
    private val endIndex: Int = bytes.size
) : RawSource {
    private var index = startIndex
    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        if (index == endIndex) return -1
        val read = minOf(byteCount, (endIndex - index).toLong()).toInt()
        sink.write(bytes, index, read)
        index += read
        return read.toLong()
    }
    override fun close() {
        index = endIndex
    }
}

internal fun ByteArray.asSource(startIndex: Int = 0, endIndex: Int = size): RawSource = ByteArraySource(
    bytes = this,
    startIndex = startIndex,
    endIndex = endIndex
)

private class WrappedSource(
    private val upstream: RawSource,
    wrapped: RawSource.() -> RawSource
) : RawSource {
    private val wrapped = upstream.wrapped()

    override fun readAtMostTo(sink: Buffer, byteCount: Long) = wrapped.readAtMostTo(sink, byteCount)

    override fun close() {
        wrapped.close()
        upstream.close()
    }
}

internal fun RawSource.wrappedInflating(
    raw: Boolean = true,
    bufferSize: Int = Inflater.DEFAULT_BUFFER_SIZE
): RawSource = WrappedSource(this) { inflating(raw = raw, bufferSize = bufferSize) }

internal fun RawSource.wrappedDeflating(
    raw: Boolean = true,
    level: Int = Deflater.DEFAULT_LEVEL,
    bufferSize: Int = Deflater.DEFAULT_BUFFER_SIZE
): RawSource = WrappedSource(this) { deflating(raw = raw, level = level, bufferSize = bufferSize) }

private class MutableConcatenatedList<T>(
    private val lists: List<MutableList<T>>
) : AbstractMutableList<T>() {
    constructor(list: MutableList<T>, vararg lists: MutableList<T>) : this(listOf(list, *lists))

    init {
        require(lists.isNotEmpty()) {
            "Must have at least one list"
        }
    }

    override val size: Int get() = lists.sumOf { it.size }

    private fun <R> listAt(index: Int, endInclusive: Boolean = false, block: MutableList<T>.(Int) -> R): R {
        if (index < 0) throw IndexOutOfBoundsException("Index: $index")
        var i = index
        lists.forEach { list ->
            if (i < list.size || (endInclusive && i == list.size)) return list.block(i)
            i -= list.size
        }
        throw IndexOutOfBoundsException("Index: $index, Size: ${index - i}")
    }

    override fun get(index: Int): T = listAt(index) { get(it) }

    override fun set(index: Int, element: T): T = listAt(index) { set(it, element) }

    override fun add(index: Int, element: T) = listAt(index, endInclusive = true) { add(it, element) }

    override fun add(element: T) = lists.last().add(element)

    override fun removeAt(index: Int): T = listAt(index) { removeAt(it) }
}

private class ConcatenatedList<out T>(
    private val lists: List<List<T>>
) : AbstractList<T>() {
    constructor(vararg lists: List<T>) : this(listOf(*lists))

    override val size: Int get() = lists.sumOf { it.size }

    private fun <R> listAt(index: Int, endInclusive: Boolean = false, block: List<T>.(Int) -> R): R {
        if (index < 0) throw IndexOutOfBoundsException("Index: $index")
        var i = index
        lists.forEach { list ->
            if (i < list.size || (endInclusive && i == list.size)) return list.block(i)
            i -= list.size
        }
        throw IndexOutOfBoundsException("Index: $index, Size: ${index - i}")
    }

    override fun get(index: Int) = listAt(index) { get(it) }

    override fun contains(element: @UnsafeVariance T) = lists.any { element in it }

    override fun containsAll(elements: Collection<@UnsafeVariance T>) = elements.all { it in this }
}

internal data class LocalFileHeader(
    val versionNeeded: UShort,
    val flags: UShort,
    val compressionMethod: UShort,
    val lastModificationTime: UShort,
    val lastModificationDate: UShort,
    val crc32: UInt,
    val compressedSize: UInt,
    val uncompressedSize: UInt,
    val fileName: ByteString,
    val extraField: ByteString,
) {
    val fileNameLength: UShort get() = fileName.size.toUShort()
    val extraFieldLength: UShort get() = extraField.size.toUShort()

    val decodedFileName by lazy { fileName.decodeToString() }

    fun write(sink: Sink): UInt {
        sink.writeUIntLe(MagicNumbers.LOCAL_FILE_HEADER)
        sink.writeUShortLe(versionNeeded)
        sink.writeUShortLe(flags)
        sink.writeUShortLe(compressionMethod)
        sink.writeUShortLe(lastModificationTime)
        sink.writeUShortLe(lastModificationDate)
        sink.writeUIntLe(crc32)
        sink.writeUIntLe(compressedSize)
        sink.writeUIntLe(uncompressedSize)
        sink.writeUShortLe(fileNameLength)
        sink.writeUShortLe(extraFieldLength)
        sink.write(fileName)
        sink.write(extraField)
        return 30u + fileNameLength + extraFieldLength
    }

    companion object {
        fun read(source: Source): LocalFileHeader {
            val signature = source.readUIntLe()
            if (signature != MagicNumbers.LOCAL_FILE_HEADER) {
                throw ZipException("Invalid local file header signature: ${signature.toHexString()}")
            }
            val versionNeeded = source.readUShortLe()
            val flags = source.readUShortLe()
            val compressionMethod = source.readUShortLe()
            val lastModificationTime = source.readUShortLe()
            val lastModificationDate = source.readUShortLe()
            val crc32 = source.readUIntLe()
            val compressedSize = source.readUIntLe()
            val uncompressedSize = source.readUIntLe()
            val fileNameLength = source.readUShortLe()
            val extraFieldLength = source.readUShortLe()
            val fileName = source.readByteString(fileNameLength.toInt())
            val extraField = source.readByteString(extraFieldLength.toInt())
            return LocalFileHeader(
                versionNeeded = versionNeeded,
                flags = flags,
                compressionMethod = compressionMethod,
                lastModificationTime = lastModificationTime,
                lastModificationDate = lastModificationDate,
                crc32 = crc32,
                compressedSize = compressedSize,
                uncompressedSize = uncompressedSize,
                fileName = fileName,
                extraField = extraField,
            )
        }
    }
}

internal data class CentralDirectory(
    val fileHeaders: List<FileHeader>,
    val endRecord: EndRecord
) {
    data class FileHeader(
        val versionMadeBy: UShort,
        val versionNeeded: UShort,
        val flags: UShort,
        val compressionMethod: UShort,
        val lastModificationTime: UShort,
        val lastModificationDate: UShort,
        val crc32: UInt,
        val compressedSize: UInt,
        val uncompressedSize: UInt,
        val startDisk: UShort,
        val internalFileAttributes: UShort,
        val externalFileAttributes: UInt,
        val localFileHeaderOffset: UInt,
        val fileName: ByteString,
        val extraField: ByteString,
        val comment: ByteString,
    ) {
        val fileNameLength: UShort get() = fileName.size.toUShort()
        val extraFieldLength: UShort get() = extraField.size.toUShort()
        val commentLength: UShort get() = comment.size.toUShort()

        val decodedFileName by lazy { fileName.decodeToString() }

        constructor(
            localFileHeader: LocalFileHeader,
            versionMadeBy: UShort = localFileHeader.versionNeeded,
            startDisk: UShort,
            internalFileAttributes: UShort,
            externalFileAttributes: UInt,
            localFileHeaderOffset: UInt,
            comment: ByteString
        ) : this(
            versionMadeBy = versionMadeBy,
            versionNeeded = localFileHeader.versionNeeded,
            flags = localFileHeader.flags,
            compressionMethod = localFileHeader.compressionMethod,
            lastModificationTime = localFileHeader.lastModificationTime,
            lastModificationDate = localFileHeader.lastModificationDate,
            crc32 = localFileHeader.crc32,
            compressedSize = localFileHeader.compressedSize,
            uncompressedSize = localFileHeader.uncompressedSize,
            startDisk = startDisk,
            internalFileAttributes = internalFileAttributes,
            externalFileAttributes = externalFileAttributes,
            localFileHeaderOffset = localFileHeaderOffset,
            fileName = localFileHeader.fileName,
            extraField = localFileHeader.extraField,
            comment = comment,
        )

        val localFileHeader by lazy {
            LocalFileHeader(
                versionNeeded = versionNeeded,
                flags = flags,
                compressionMethod = compressionMethod,
                lastModificationTime = lastModificationTime,
                lastModificationDate = lastModificationDate,
                crc32 = crc32,
                compressedSize = compressedSize,
                uncompressedSize = uncompressedSize,
                fileName = fileName,
                extraField = extraField
            )
        }

        fun write(sink: Sink): UInt {
            sink.writeUIntLe(MagicNumbers.CENTRAL_DIRECTORY_FILE_HEADER)
            sink.writeUShortLe(versionMadeBy)
            sink.writeUShortLe(versionNeeded)
            sink.writeUShortLe(flags)
            sink.writeUShortLe(compressionMethod)
            sink.writeUShortLe(lastModificationTime)
            sink.writeUShortLe(lastModificationDate)
            sink.writeUIntLe(crc32)
            sink.writeUIntLe(compressedSize)
            sink.writeUIntLe(uncompressedSize)
            sink.writeUShortLe(fileNameLength)
            sink.writeUShortLe(extraFieldLength)
            sink.writeUShortLe(commentLength)
            sink.writeUShortLe(startDisk)
            sink.writeUShortLe(internalFileAttributes)
            sink.writeUIntLe(externalFileAttributes)
            sink.writeUIntLe(localFileHeaderOffset)
            sink.write(fileName)
            sink.write(extraField)
            sink.write(comment)
            return 46u + fileNameLength + extraFieldLength + commentLength
        }

        companion object {
            fun read(source: Source): FileHeader {
                val signature = source.readUIntLe()
                if (signature != MagicNumbers.CENTRAL_DIRECTORY_FILE_HEADER) {
                    throw ZipException("Invalid CDFH signature: ${signature.toHexString()}")
                }
                val versionMadeBy = source.readUShortLe()
                val versionNeeded = source.readUShortLe()
                val flags = source.readUShortLe()
                val compressionMethod = source.readUShortLe()
                val lastModificationTime = source.readUShortLe()
                val lastModificationDate = source.readUShortLe()
                val crc32 = source.readUIntLe()
                val compressedSize = source.readUIntLe()
                val uncompressedSize = source.readUIntLe()
                val fileNameLength = source.readUShortLe()
                val extraFieldLength = source.readUShortLe()
                val commentLength = source.readUShortLe()
                val startDisk = source.readUShortLe()
                val internalFileAttributes = source.readUShortLe()
                val externalFileAttributes = source.readUIntLe()
                val localFileHeaderOffset = source.readUIntLe()
                val fileName = source.readByteString(fileNameLength.toInt())
                val extraField = source.readByteString(extraFieldLength.toInt())
                val comment = source.readByteString(commentLength.toInt())
                return FileHeader(
                    versionMadeBy = versionMadeBy,
                    versionNeeded = versionNeeded,
                    flags = flags,
                    compressionMethod = compressionMethod,
                    lastModificationTime = lastModificationTime,
                    lastModificationDate = lastModificationDate,
                    crc32 = crc32,
                    compressedSize = compressedSize,
                    uncompressedSize = uncompressedSize,
                    startDisk = startDisk,
                    internalFileAttributes = internalFileAttributes,
                    externalFileAttributes = externalFileAttributes,
                    localFileHeaderOffset = localFileHeaderOffset,
                    fileName = fileName,
                    extraField = extraField,
                    comment = comment,
                )
            }
        }

        fun entry(zip: KotlinZip) = Entry(zip, this)

        data class Entry(
            private val zip: KotlinZip,
            val fileHeader: FileHeader
        ) : InternalEntry() {
            override val info by lazy { Info(fileHeader) }

//            override val path = Zip.entryNameToPath(fileHeader.fileName)
//            override val isDirectory = fileHeader.fileName.endsWith('/') || fileHeader.fileName.endsWith('\\')
//            override val uncompressedSize: ULong get() = fileHeader.uncompressedSize.toULong()
//            override val compressedSize: ULong get() = fileHeader.compressedSize.toULong()
//            override val crc32: Long get() = fileHeader.crc32.toLong()

            override fun readToSource() = SystemFileSystem.source(zip.path)
                .buffered()
                .apply {
                    skip(fileHeader.localFileHeaderOffset.toLong())
                    LocalFileHeader.read(this)
                }
                .limited(fileHeader.compressedSize.toLong())
                .run {
                    when (fileHeader.compressionMethod) {
                        CompressionMethod.NONE -> this
                        CompressionMethod.DEFLATED -> wrappedInflating(raw = true)
                        else -> throw ZipException("Unsupported compression method: ${fileHeader.compressionMethod.toHexString()}")
                    }
                }
                .buffered()
        }
    }

    data class EndRecord(
        val disk: UShort,
        val centralDirectoryStartDisk: UShort,
        val numberOfEntriesOnDisk: UShort,
        val totalNumberOfEntries: UShort,
        val centralDirectorySize: UInt,
        val centralDirectoryOffset: UInt,
        val comment: ByteString
    ) {
        val commentLength: UShort get() = comment.size.toUShort()

        fun write(sink: Sink): UInt {
            sink.writeUIntLe(MagicNumbers.END_OF_CENTRAL_DIRECTORY_RECORD)
            sink.writeUShortLe(disk)
            sink.writeUShortLe(centralDirectoryStartDisk)
            sink.writeUShortLe(numberOfEntriesOnDisk)
            sink.writeUShortLe(totalNumberOfEntries)
            sink.writeUIntLe(centralDirectorySize)
            sink.writeUIntLe(centralDirectoryOffset)
            sink.writeUShortLe(commentLength)
            sink.write(comment)
            return 22u + commentLength
        }

        companion object {
            fun read(source: Source): EndRecord {
                val signature = source.readUIntLe()
                if (signature != MagicNumbers.END_OF_CENTRAL_DIRECTORY_RECORD) {
                    throw ZipException("Invalid EOCD signature: ${signature.toHexString()}")
                }
                val disk = source.readUShortLe()
                val centralDirectoryStartDisk = source.readUShortLe()
                val numberOfEntriesOnDisk = source.readUShortLe()
                val totalNumberOfEntries = source.readUShortLe()
                val centralDirectorySize = source.readUIntLe()
                val centralDirectoryOffset = source.readUIntLe()
                val commentLength = source.readUShortLe()
                val comment = source.readByteString(commentLength.toInt())
                return EndRecord(
                    disk = disk,
                    centralDirectoryStartDisk = centralDirectoryStartDisk,
                    numberOfEntriesOnDisk = numberOfEntriesOnDisk,
                    totalNumberOfEntries = totalNumberOfEntries,
                    centralDirectorySize = centralDirectorySize,
                    centralDirectoryOffset = centralDirectoryOffset,
                    comment = comment
                )
            }
        }
    }

    fun write(sink: Sink): UInt {
        return fileHeaders.sumOf { it.write(sink) } + endRecord.write(sink)
    }

    companion object {
        fun read(path: Path): CentralDirectory {
            val size = SystemFileSystem.metadataOrNull(path)?.size ?: throw ZipException("File not found: $path")
            val offset = SystemFileSystem.source(path).buffered().use { source ->
                var skipped = 0L
                if (size >= 22) {
                    skipped = size - minOf(size, 22L + 0xFFFFL)
                    source.skip(skipped)
                }
                val index = source.readByteString().lastIndexOf(ByteString(0x50u, 0x4Bu, 0x05u, 0x06u))
                if (index < 0) throw ZipException("Not a ZIP file (EOCD record not found)")
                skipped + index
            }
            val endRecord = SystemFileSystem.source(path).buffered().use { source ->
                source.skip(offset)
                EndRecord.read(source)
            }
            val fileHeaders = SystemFileSystem.source(path).buffered().use { source ->
                source.skip(endRecord.centralDirectoryOffset.toLong())
                (0..<endRecord.totalNumberOfEntries.toInt()).map { i ->
                    FileHeader.read(source)
                }
            }
            return CentralDirectory(
                fileHeaders = fileHeaders,
                endRecord = endRecord
            )
        }
    }
}

private infix fun Path.isEqualOrSubpathOf(other: Path): Boolean {
    if (this == other) return true
    return (parent ?: return false) isEqualOrSubpathOf other
}

internal sealed class InternalEntry : Zip.Entry {
    abstract val info: Info

    override val path get() = info.path
    override val isDirectory get() = info.isDirectory
    override val uncompressedSize: ULong get() = info.uncompressedSize.toULong()
    override val compressedSize: ULong get() = info.compressedSize.toULong()
    override val crc32: Long get() = info.crc32.toLong()

    data class Info(
        val path: Path,
        val isDirectory: Boolean,
        val isCompressed: Boolean,
        val crc32: UInt,
        val compressedSize: UInt,
        val uncompressedSize: UInt,

        val versionNeeded: UShort = 20u,
        val versionMadeBy: UShort = versionNeeded,
        val flags: UShort = 0u,
        val lastModificationTime: UShort = 0u,
        val lastModificationDate: UShort = 0u,
        val internalFileAttributes: UShort = 0u,
        val externalFileAttributes: UInt = 0u,
        val extraField: ByteString = ByteString(),
        val comment: ByteString = ByteString(),
    ) {
        val fileName by lazy {
            if (isDirectory) {
                Zip.pathToFolderEntryName(path)
            } else {
                Zip.pathToEntryName(path)
            }.encodeToByteString()
        }

        val compressionMethod get() = if (isCompressed) CompressionMethod.DEFLATED else CompressionMethod.NONE

        constructor(fileHeader: CentralDirectory.FileHeader) : this(
            path = Zip.entryNameToPath(fileHeader.decodedFileName),
            isDirectory = fileHeader.decodedFileName.endsWith('/') || fileHeader.decodedFileName.endsWith('\\'),
            isCompressed = fileHeader.compressionMethod != CompressionMethod.NONE,
            versionMadeBy = fileHeader.versionMadeBy,
            versionNeeded = fileHeader.versionNeeded,
            flags = fileHeader.flags,
            lastModificationTime = fileHeader.lastModificationTime,
            lastModificationDate = fileHeader.lastModificationDate,
            crc32 = fileHeader.crc32,
            compressedSize = fileHeader.compressedSize,
            uncompressedSize = fileHeader.uncompressedSize,
            internalFileAttributes = fileHeader.internalFileAttributes,
            externalFileAttributes = fileHeader.externalFileAttributes,
            extraField = fileHeader.extraField,
            comment = fileHeader.comment,
        )

        val localFileHeader by lazy {
            LocalFileHeader(
                versionNeeded = versionNeeded,
                flags = flags,
                compressionMethod = compressionMethod,
                lastModificationTime = lastModificationTime,
                lastModificationDate = lastModificationDate,
                crc32 = crc32,
                compressedSize = compressedSize,
                uncompressedSize = uncompressedSize,
                fileName = fileName,
                extraField = extraField
            )
        }

        fun fileHeader(startDisk: UShort, localFileHeaderOffset: UInt) = CentralDirectory.FileHeader(
            versionMadeBy = versionMadeBy,
            versionNeeded = versionNeeded,
            flags = flags,
            compressionMethod = compressionMethod,
            lastModificationTime = lastModificationTime,
            lastModificationDate = lastModificationDate,
            crc32 = crc32,
            compressedSize = compressedSize,
            uncompressedSize = uncompressedSize,
            startDisk = startDisk,
            internalFileAttributes = internalFileAttributes,
            externalFileAttributes = externalFileAttributes,
            localFileHeaderOffset = localFileHeaderOffset,
            fileName = fileName,
            extraField = extraField,
            comment = comment,
        )
    }
}

internal class KotlinZip(
    val path: Path,
    override val mode: Zip.Mode,
    val level: Zip.CompressionLevel
) : Zip {
    private val originalEntries by lazy {
        when (mode) {
            Zip.Mode.Read, Zip.Mode.Append -> {
                CentralDirectory.read(path).fileHeaders.mapTo(mutableListOf()) { it.entry(this) }
            }
            else -> mutableListOf()
        }
    }
    private val newEntries = mutableListOf<PreCompressedMemoryEntry>()

    private val entries by lazy { ConcatenatedList(originalEntries, newEntries) }

    override val numberOfEntries get() = entries.size

    init {
        if (mode == Zip.Mode.Read && !SystemFileSystem.exists(path)) {
            throw ZipException("File does not exist: $path")
        }
    }

    private var isClosed = false

    override fun entry(entry: Path, block: Zip.Entry.() -> Unit) {
        requireOpen()
        val zipEntry = entries.firstOrNull { it.path == entry }
            ?: throw ZipException("Entry not found: $entry")
        zipEntry.block()
    }

    override fun entry(index: Int, block: Zip.Entry.() -> Unit) {
        requireOpen()
        if (index !in entries.indices) throw ZipException("Invalid index: $index")
        entries[index].block()
    }

    override fun deleteEntries(paths: List<Path>) {
        requireOpen()
        requireWritable()
        fun Path.shouldRemove() = paths.any { this isEqualOrSubpathOf it }
        originalEntries.removeAll { it.path.shouldRemove() }
        newEntries.removeAll { it.path.shouldRemove() }
    }

    override fun deleteEntriesByIndex(indices: List<Int>) {
        requireOpen()
        requireWritable()
        deleteEntries(indices.map { entries[it].path })
    }

    private fun entryFromRawSource(entry: Path, data: RawSource) {
        newEntries += PreCompressedMemoryEntry.fileFromUncompressedSource(
            source = data,
            path = entry,
            targetCompressionLevel = level
        )
    }

    override fun entryFromSource(entry: Path, data: Source) {
        requireOpen()
        requireWritable()
        entryFromRawSource(entry, data)
    }

    override fun entryFromPath(entry: Path, file: Path) {
        requireOpen()
        requireWritable()
        file.requireFile()
        SystemFileSystem.source(file).use {
            entryFromRawSource(entry, it)
        }
    }

    override fun folderEntry(entry: Path) {
        requireOpen()
        requireWritable()
        newEntries += PreCompressedMemoryEntry.directory(
            path = entry,
            targetCompressionLevel = level
        )
    }

    private fun requireOpen() {
        if (isClosed) throw ZipException("Zip file is closed")
    }

    override fun close() {
        if (isClosed) return
        if (mode != Zip.Mode.Read) {
            writeToFile()
        }
        isClosed = true
    }

    private fun writeToFile() {
        // TODO replace with a more efficient system
        val entries = entries.map { entry ->
            when (entry) {
                is CentralDirectory.FileHeader.Entry -> PreCompressedMemoryEntry.fromEntry(
                    entry = entry,
                    targetCompressionLevel = level
                )
                is PreCompressedMemoryEntry -> entry
            }
        }

        // TODO load old end record

        var currentOffset = 0u
        val localFileHeaderOffsets = mutableListOf<UInt>()
        SystemFileSystem.sink(path, append = false).buffered().use { sink ->
            entries.forEach { entry ->
                localFileHeaderOffsets += currentOffset
                currentOffset += entry.info.localFileHeader.write(sink)
                currentOffset += entry.compressedBuffer.use { it.transferTo(sink) }.toUInt()
            }

            val centralDirectoryOffset = currentOffset
            localFileHeaderOffsets.forEachIndexed { i, offset ->
                currentOffset += entries[i].info.fileHeader(
                    startDisk = 0u,
                    localFileHeaderOffset = offset
                ).write(sink)
            }
            CentralDirectory.EndRecord(
                disk = 0u,
                centralDirectoryStartDisk = 0u,
                numberOfEntriesOnDisk = entries.size.toUShort(),
                totalNumberOfEntries = entries.size.toUShort(),
                centralDirectorySize = currentOffset - centralDirectoryOffset,
                centralDirectoryOffset = centralDirectoryOffset,
                comment = ByteString()
            ).write(sink)
        }
    }

    private class PreCompressedMemoryEntry(
        val compressedBuffer: Buffer,
        val targetCompressionLevel: Zip.CompressionLevel,
        baseInfo: Info
    ) : InternalEntry() {
        override val info = baseInfo.copy(
            isCompressed = targetCompressionLevel != Zip.CompressionLevel.NoCompression,
            compressedSize = compressedBuffer.size.toUInt(),
        )

        override fun readToSource() = compressedBuffer.copy().wrappedInflating(raw = true).buffered()

        companion object {
            fun fileFromUncompressedSource(
                source: RawSource,
                path: Path,
                targetCompressionLevel: Zip.CompressionLevel
            ): PreCompressedMemoryEntry {
                val isCompressed = targetCompressionLevel != Zip.CompressionLevel.NoCompression
                val uncompressedCounter = CountingSink()
                val uncompressedCrc32 = Crc32()
                val compressed = Buffer()
                source
                    .feed(uncompressedCounter, uncompressedCrc32)
                    .run {
                        if (isCompressed) wrappedDeflating(raw = true, level = targetCompressionLevel.zlibLevel) else this
                    }
                    .buffered()
                    .transferTo(compressed)
                return PreCompressedMemoryEntry(
                    compressedBuffer = compressed,
                    targetCompressionLevel = targetCompressionLevel,
                    baseInfo = Info(
                       path = path,
                       isDirectory = false,
                       isCompressed = isCompressed,
                       crc32 = uncompressedCrc32.crc32,
                       compressedSize = compressed.size.toUInt(),
                       uncompressedSize = uncompressedCounter.count.toUInt(),
                    )
                )
            }

            fun directory(
                path: Path,
                targetCompressionLevel: Zip.CompressionLevel
            ) = PreCompressedMemoryEntry(
                compressedBuffer = Buffer(),
                targetCompressionLevel = targetCompressionLevel,
                baseInfo = Info(
                    path = path,
                    isDirectory = true,
                    isCompressed = targetCompressionLevel != Zip.CompressionLevel.NoCompression,
                    crc32 = Crc32().crc32,
                    compressedSize = 0u,
                    uncompressedSize = 0u,
                )
            )

            fun fromEntry(
                entry: CentralDirectory.FileHeader.Entry,
                targetCompressionLevel: Zip.CompressionLevel
            ): PreCompressedMemoryEntry {
                val compressed = Buffer()
                entry.readToSource().use { source ->
                    source
                        .run {
                            if (targetCompressionLevel != Zip.CompressionLevel.NoCompression) {
                                wrappedDeflating(raw = true, level = targetCompressionLevel.zlibLevel)
                            } else {
                                this
                            }
                        }
                        .buffered()
                        .transferTo(compressed)
                }
                return PreCompressedMemoryEntry(
                    compressedBuffer = compressed,
                    targetCompressionLevel = targetCompressionLevel,
                    baseInfo = entry.info
                )
            }
        }
    }
}

public actual fun Zip.Companion.open(
    path: Path,
    mode: Zip.Mode,
    level: Zip.CompressionLevel
): Zip = KotlinZip(
    path = path,
    mode = mode,
    level = level
)
