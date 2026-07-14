package de.jonasbroeckmann.kzip.implementation.model

import de.jonasbroeckmann.kzip.ZipException
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.decodeToString
import kotlinx.io.bytestring.lastIndexOf
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteString
import kotlinx.io.readUIntLe
import kotlinx.io.readUShortLe
import kotlinx.io.write
import kotlinx.io.writeUIntLe
import kotlinx.io.writeUShortLe

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
                (0..<endRecord.totalNumberOfEntries.toInt()).map {
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
