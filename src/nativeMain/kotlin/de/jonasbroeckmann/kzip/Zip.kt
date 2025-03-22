package de.jonasbroeckmann.kzip

import cnames.structs.zip_t
import kotlinx.cinterop.*
import kotlinx.io.files.Path
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.readByteArray
import libzip.*


@OptIn(ExperimentalForeignApi::class)
private class KubaZip(
    private val handle: CPointer<zip_t>,
    override val mode: Zip.Mode
) : Zip {
    override val numberOfEntries: ULong by lazy { zip_entries_total(handle).orZipError() }

    private fun withZipEntry(name: String, block: () -> Unit) {
        zip_entry_open(handle, name).zeroOrZipError()
        try {
            block()
        } finally {
            zip_entry_close(handle).zeroOrZipError()
        }
    }

    override fun entry(entry: Path, block: Zip.Entry.() -> Unit) {
        withZipEntry(Zip.pathToEntryName(entry)) {
            Entry().block()
        }
    }
    override fun entry(index: ULong, block: Zip.Entry.() -> Unit) {
        zip_entry_openbyindex(handle, index).zeroOrZipError()
        try {
            Entry().block()
        } finally {
            zip_entry_close(handle).zeroOrZipError()
        }
    }

//    override fun deleteEntries(paths: List<Path>) = memScoped {
//        requireWritable()
//        val names = paths.map { Zip.pathToEntryName(it) }
//        val result = zip_entries_delete(handle, names.toCStringArray(this), names.size.toULong())
//        if (result <= 0) throwZipError("Failed to delete entries", result)
//    }
//    override fun deleteEntries(indices: List<ULong>) = memScoped {
//        requireWritable()
//        val result = zip_entries_deletebyindex(handle, indices.toULongArray().toCValues().ptr, indices.size.toULong())
//        if (result <= 0) throwZipError("Failed to delete entries", result)
//    }

    override fun entryFromSource(entry: Path, data: Source) {
        requireWritable()
        withZipEntry(Zip.pathToEntryName(entry)) {
            val bytes = data.readByteArray()
            zip_entry_write(handle, bytes.refTo(0), bytes.size.toULong())
                .zeroOrZipError { "Error while writing from source" }
        }
    }
    override fun entryFromPath(entry: Path, file: Path) {
        requireWritable()
        file.requireFile()
        withZipEntry(Zip.pathToEntryName(entry)) {
            zip_entry_fwrite(handle, file.toString())
                .zeroOrZipError { "Error while writing from path" }
        }
    }
    override fun folderEntry(entry: Path) {
        requireWritable()
        withZipEntry(Zip.pathToFolderEntryName(entry)) { }
    }

    override fun close() {
        zip_close(handle)
    }

    private inner class Entry : Zip.Entry {
        override val path: Path by lazy {
            Zip.entryNameToPath(zip_entry_name(handle)!!.toKString())
        }
        override val isDirectory: Boolean by lazy { zip_entry_isdir(handle) != 0 }
        override val uncompressedSize: ULong by lazy { zip_entry_uncomp_size(handle) }
        override val compressedSize: ULong by lazy { zip_entry_comp_size(handle) }
        override val crc32: Long by lazy { zip_entry_crc32(handle).toLong() }

        override fun readToSource(): Source {
            return Buffer().apply { write(readToBytes()) }
        }
        override fun readToBytes(): ByteArray {
            val bytes = ByteArray(uncompressedSize.toInt())
            val result = zip_entry_noallocread(handle, bytes.refTo(0), uncompressedSize)
            if (result <= 0) throwZipError("Error while reading bytes", result)
            return bytes
        }
        override fun readToPath(path: Path) {
            zip_entry_fread(handle, path.toString())
                .zeroOrZipError { "Error while reading to path" }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
public actual fun Zip.Companion.open(
    path: Path,
    mode: Zip.Mode,
    level: Zip.CompressionLevel
): Zip = memScoped {
    val err = alloc<IntVar>()
    val handle = zip_openwitherror(path.toString(), level.zlibLevel, mode.char.code.toByte(), err.ptr)
        ?: throwZipError("Failed to open zip file $path", err.value)
    KubaZip(handle, mode)
}

private val Zip.Mode.char get() = when (this) {
    Zip.Mode.Read -> 'r'
    Zip.Mode.Write -> 'w'
    Zip.Mode.Append -> 'a' // same as 'd'
}

private fun Int.zeroOrZipError(msg: () -> String = { "Error" }) {
    if (this != 0) throwZipError(msg(), this)
}

private fun Long.orZipError(msg: () -> String = { "Error" }): ULong {
    if (this < 0) throwZipError(msg(), this)
    return toULong()
}

private fun throwZipError(msg: String, err: Number? = null): Nothing {
    throw ZipException(listOfNotNull(
        msg,
        err?.let { zipError(it.toInt()) }
    ).joinToString(": "))
}

@OptIn(ExperimentalForeignApi::class)
private fun zipError(err: Int): String {
    return zip_strerror(err)?.toKString() ?: "unknown error $err"
}
