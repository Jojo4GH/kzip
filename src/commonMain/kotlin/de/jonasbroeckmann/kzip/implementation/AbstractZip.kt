package de.jonasbroeckmann.kzip.implementation

import de.jonasbroeckmann.kzip.Zip
import de.jonasbroeckmann.kzip.ZipException
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

internal abstract class AbstractZip : Zip {
    private var isClosed = false

    protected fun requireWritable() {
        requireOpen()
        if (mode !in listOf(Zip.Mode.Write, Zip.Mode.Append)) throw ZipException("Zip is not opened writable")
    }

    protected fun requireReadable() {
        requireOpen()
        if (mode !in listOf(Zip.Mode.Read, Zip.Mode.Append)) throw ZipException("Zip is not opened readable")
    }

    protected fun requireValidFile(path: Path): Path {
        if (SystemFileSystem.metadataOrNull(path)?.isRegularFile != true) {
            throw IllegalArgumentException("File does not exist or is not a regular file: $this")
        }
        return path
    }

    private fun requireOpen() {
        if (isClosed) throw ZipException("Zip file is closed")
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        closeImpl()
    }

    protected abstract fun closeImpl()
}
