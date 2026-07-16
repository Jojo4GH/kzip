package de.jonasbroeckmann.kzip.implementation.util

import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.files.Path
import kotlinx.io.okio.asKotlinxIoRawSource
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

internal actual fun fileSourceWithOffset(path: Path, startOffset: Long): RawSource = FileSource(path, startOffset)

private class FileSource(
    path: Path,
    startOffset: Long
) : RawSource {
    private val lazyHandle = lazy {
        FileSystem.SYSTEM.openReadOnly(path.toOkioPath())
    }
    private val lazySource = lazy {
        lazyHandle.value.source(fileOffset = startOffset).asKotlinxIoRawSource()
    }

    override fun readAtMostTo(sink: Buffer, byteCount: Long) = lazySource.value.readAtMostTo(sink, byteCount)

    override fun close() {
        if (lazySource.isInitialized()) lazySource.value.close()
        if (lazyHandle.isInitialized()) lazyHandle.value.close()
    }
}
