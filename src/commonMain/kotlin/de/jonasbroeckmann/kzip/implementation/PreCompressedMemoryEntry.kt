package de.jonasbroeckmann.kzip.implementation

import de.jonasbroeckmann.kzip.Zip
import de.jonasbroeckmann.kzip.implementation.util.CountingSink
import de.jonasbroeckmann.kzip.implementation.util.Crc32
import de.jonasbroeckmann.kzip.implementation.util.feed
import de.jonasbroeckmann.kzip.implementation.util.wrappedDeflating
import de.jonasbroeckmann.kzip.implementation.util.wrappedInflating
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.io.files.Path

internal class PreCompressedMemoryEntry(
    val compressedBuffer: Buffer,
    val targetCompressionLevel: Zip.CompressionLevel,
    baseInfo: Info
) : ZipEntryImpl() {
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
            entry: ZipEntryImpl,
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