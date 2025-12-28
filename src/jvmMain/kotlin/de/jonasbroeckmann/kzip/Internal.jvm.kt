package de.jonasbroeckmann.kzip

import kotlinx.io.RawSource
import kotlinx.io.asSource
import kotlinx.io.files.Path
import java.io.FileInputStream
import java.io.RandomAccessFile

internal actual fun openSourceAt(path: Path, offset: Long): RawSource {
    val raf = RandomAccessFile(path.toString(), "r")
    raf.seek(offset)
    val fis = FileInputStream(raf.getFD())
    val source = fis.asSource()
    return object : RawSource by source {
        override fun close() {
            source.close()
            raf.close()
        }
    }
}
