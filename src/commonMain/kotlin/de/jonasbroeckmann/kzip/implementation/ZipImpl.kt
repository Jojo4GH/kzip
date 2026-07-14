package de.jonasbroeckmann.kzip.implementation

import de.jonasbroeckmann.kzip.Zip
import de.jonasbroeckmann.kzip.ZipException
import de.jonasbroeckmann.kzip.implementation.model.CentralDirectory
import de.jonasbroeckmann.kzip.implementation.util.ConcatenatedList
import kotlinx.io.*
import kotlinx.io.bytestring.ByteString
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.Boolean
import kotlin.use

internal class ZipImpl(
    val path: Path,
    override val mode: Zip.Mode,
    val level: Zip.CompressionLevel
) : Zip {
    private val originalEntries by lazy {
        when (mode) {
            Zip.Mode.Read, Zip.Mode.Append -> {
                CentralDirectory.read(path).fileHeaders.mapTo(mutableListOf()) { HeaderBasedEntry(this, it) }
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
        requireReadable()
        val zipEntry = entries.firstOrNull { it.path == entry }
            ?: throw ZipException("Entry not found: $entry")
        zipEntry.block()
    }

    override fun entry(index: Int, block: Zip.Entry.() -> Unit) {
        requireReadable()
        if (index !in entries.indices) throw ZipException("Invalid index: $index")
        entries[index].block()
    }

    override fun deleteEntries(paths: List<Path>) {
        requireWritable()
        fun Path.shouldRemove() = paths.any { this isEqualOrSubpathOf it }
        originalEntries.removeAll { it.path.shouldRemove() }
        newEntries.removeAll { it.path.shouldRemove() }
    }

    override fun deleteEntriesByIndex(indices: List<Int>) {
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
        requireWritable()
        entryFromRawSource(entry, data)
    }

    override fun entryFromPath(entry: Path, file: Path) {
        requireWritable()
        if (SystemFileSystem.metadataOrNull(file)?.isRegularFile != true) {
            throw IllegalArgumentException("File does not exist or is not a regular file: $this")
        }
        SystemFileSystem.source(file).use {
            entryFromRawSource(entry, it)
        }
    }

    override fun folderEntry(entry: Path) {
        requireWritable()
        newEntries += PreCompressedMemoryEntry.directory(
            path = entry,
            targetCompressionLevel = level
        )
    }

    private fun requireOpen() {
        if (isClosed) throw ZipException("Zip file is closed")
    }

    private fun requireWritable() {
        requireOpen()
        if (mode !in listOf(Zip.Mode.Write, Zip.Mode.Append)) throw ZipException("Zip is not opened writable")
    }

    private fun requireReadable() {
        requireOpen()
        if (mode !in listOf(Zip.Mode.Read, Zip.Mode.Append)) throw ZipException("Zip is not opened readable")
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
                is HeaderBasedEntry -> PreCompressedMemoryEntry.fromEntry(
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
}

private tailrec infix fun Path.isEqualOrSubpathOf(other: Path): Boolean {
    return this == other || (parent ?: return false) isEqualOrSubpathOf other
}
