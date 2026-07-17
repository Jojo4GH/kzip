package de.jonasbroeckmann.kzip.implementation

import de.jonasbroeckmann.kzip.Zip
import de.jonasbroeckmann.kzip.implementation.util.CountingSink
import de.jonasbroeckmann.kzip.implementation.util.Crc32
import de.jonasbroeckmann.kzip.implementation.util.feed
import de.jonasbroeckmann.kzip.implementation.util.wrapped
import dev.karmakrafts.kompress.deflating
import dev.karmakrafts.kompress.inflating
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

    override fun readToSource() = compressedBuffer.copy().wrapped { inflating(raw = true) }.buffered()

    companion object {
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

        private fun fileFromUncompressedSource(
            source: RawSource,
            targetCompressionLevel: Zip.CompressionLevel,
            baseInfo: Info
        ): PreCompressedMemoryEntry {
            val isCompressed = targetCompressionLevel != Zip.CompressionLevel.NoCompression
            val uncompressedCounter = CountingSink()
            val uncompressedCrc32 = Crc32()
            val compressed = Buffer()
            source
                .feed(uncompressedCounter, uncompressedCrc32)
                .run {
                    when {
                        isCompressed -> wrapped { deflating(raw = true, level = targetCompressionLevel.zlibLevel) }
                        else -> this
                    }
                }
                .buffered()
                .use { it.transferTo(compressed) }
            return PreCompressedMemoryEntry(
                compressedBuffer = compressed,
                targetCompressionLevel = targetCompressionLevel,
                baseInfo = baseInfo.copy(
                    isCompressed = isCompressed,
                    crc32 = uncompressedCrc32.crc32,
                    compressedSize = compressed.size.toUInt(),
                    uncompressedSize = uncompressedCounter.count.toUInt(),
                )
            )
        }

        fun fileFromUncompressedSource(
            source: RawSource,
            path: Path,
            targetCompressionLevel: Zip.CompressionLevel
        ) = fileFromUncompressedSource(
            source = source,
            targetCompressionLevel = targetCompressionLevel,
            baseInfo = Info(
                path = path,
                isDirectory = false,
                // These will be overwritten
                isCompressed = false,
                crc32 = 0u,
                compressedSize = 0u,
                uncompressedSize = 0u,
            )
        )

        fun fromEntry(
            entry: ZipEntryImpl,
            targetCompressionLevel: Zip.CompressionLevel
        ) = fileFromUncompressedSource(
            source = entry.readToSource(),
            targetCompressionLevel = targetCompressionLevel,
            baseInfo = entry.info
        )
    }
}
