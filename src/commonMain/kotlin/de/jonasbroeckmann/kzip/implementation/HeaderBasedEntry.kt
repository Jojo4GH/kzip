package de.jonasbroeckmann.kzip.implementation

import de.jonasbroeckmann.kzip.ZipException
import de.jonasbroeckmann.kzip.implementation.model.CentralDirectory
import de.jonasbroeckmann.kzip.implementation.model.CompressionMethod
import de.jonasbroeckmann.kzip.implementation.model.LocalFileHeader
import de.jonasbroeckmann.kzip.implementation.util.fileSourceWithOffset
import de.jonasbroeckmann.kzip.implementation.util.limited
import de.jonasbroeckmann.kzip.implementation.util.wrappedInflating
import kotlinx.io.buffered

internal data class HeaderBasedEntry(
    private val zip: ZipImpl,
    val fileHeader: CentralDirectory.FileHeader
) : ZipEntryImpl() {
    override val info by lazy { Info(fileHeader) }

    override fun readToSource() = fileSourceWithOffset(
        path = zip.path,
        startOffset = fileHeader.localFileHeaderOffset.toLong()
    )
        .buffered()
        .apply { LocalFileHeader.read(this) }
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
