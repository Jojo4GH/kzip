package de.jonasbroeckmann.kzip.implementation.model

import de.jonasbroeckmann.kzip.ZipException
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.decodeToString
import kotlinx.io.readByteString
import kotlinx.io.readUIntLe
import kotlinx.io.readUShortLe
import kotlinx.io.write
import kotlinx.io.writeUIntLe
import kotlinx.io.writeUShortLe

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
