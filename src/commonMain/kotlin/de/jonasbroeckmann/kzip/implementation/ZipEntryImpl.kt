package de.jonasbroeckmann.kzip.implementation

import de.jonasbroeckmann.kzip.Zip
import de.jonasbroeckmann.kzip.implementation.model.CentralDirectory
import de.jonasbroeckmann.kzip.implementation.model.CompressionMethod
import de.jonasbroeckmann.kzip.implementation.model.LocalFileHeader
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encodeToByteString
import kotlinx.io.files.Path

internal sealed class ZipEntryImpl : Zip.Entry {
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
        val extraField: ByteString = kotlinx.io.bytestring.ByteString(),
        val comment: ByteString = kotlinx.io.bytestring.ByteString(),
    ) {
        val fileName by lazy {
            if (isDirectory) {
                EntryNameUtils.pathToFolderName(path)
            } else {
                EntryNameUtils.pathToFileName(path)
            }.encodeToByteString()
        }

        val compressionMethod get() = if (isCompressed) CompressionMethod.DEFLATED else CompressionMethod.NONE

        constructor(fileHeader: CentralDirectory.FileHeader) : this(
            path = EntryNameUtils.entryNameToPath(fileHeader.decodedFileName),
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
