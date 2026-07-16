package de.jonasbroeckmann.kzip

import cnames.structs.zip_t
import de.jonasbroeckmann.kzip.implementation.AbstractZip
import de.jonasbroeckmann.kzip.implementation.EntryNameUtils
import kotlinx.cinterop.*
import kotlinx.io.files.Path
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.readByteArray
import libzip.*

@OptIn(ExperimentalForeignApi::class)
actual fun Zip.Companion.openNative(
    path: Path,
    mode: Zip.Mode,
    level: Zip.CompressionLevel
): Zip = memScoped {
    val err = alloc<IntVar>()
    val handle = zip_openwitherror(path.toString(), level.zlibLevel, mode.char.code.toByte(), err.ptr)
        ?: throwZipError("Failed to open zip file $path", err.value)
    KubaZip(handle, mode)
}

@OptIn(ExperimentalForeignApi::class)
private class KubaZip(
    private val handle: CPointer<zip_t>,
    override val mode: Zip.Mode
) : AbstractZip() {
    override val numberOfEntries: Int by lazy { zip_entries_total(handle).orZipError() }

    private fun <R> withZipEntry(name: String, block: () -> R): R {
        zip_entry_open(handle, name).zeroOrZipError()
        return try {
            block()
        } finally {
            zip_entry_close(handle).zeroOrZipError()
        }
    }

    override fun <R> entry(entry: Path, block: Zip.Entry.() -> R): R {
        return withZipEntry(EntryNameUtils.pathToFileName(entry)) {
            Entry().block()
        }
    }
    override fun <R> entry(index: Int, block: Zip.Entry.() -> R): R {
        zip_entry_openbyindex(handle, index.toULong()).zeroOrZipError()
        return try {
            Entry().block()
        } finally {
            zip_entry_close(handle).zeroOrZipError()
        }
    }

    override fun deleteEntries(paths: List<Path>) = memScoped {
        requireWritable()
        val names = paths.map { EntryNameUtils.pathToFileName(it) }
        val result = zip_entries_delete(handle, names.toCStringArray(this), names.size.toULong())
        if (result <= 0) throwZipError("Failed to delete entries", result)
    }
    override fun deleteEntriesByIndex(indices: List<Int>) = memScoped {
        requireWritable()
        val result = zip_entries_deletebyindex(handle, indices.map { it.toULong() }.toULongArray().toCValues().ptr, indices.size.toULong())
        if (result <= 0) throwZipError("Failed to delete entries", result)
    }

    override fun entryFromSource(entry: Path, data: Source) {
        requireWritable()
        withZipEntry(EntryNameUtils.pathToFileName(entry)) {
            val bytes = data.readByteArray()
            zip_entry_write(handle, bytes.refTo(0), bytes.size.toULong())
                .zeroOrZipError { "Error while writing from source" }
        }
    }
    override fun entryFromPath(entry: Path, file: Path) {
        requireWritable()
        requireValidFile(file)
        withZipEntry(EntryNameUtils.pathToFileName(entry)) {
            zip_entry_fwrite(handle, file.toString())
                .zeroOrZipError { "Error while writing from path" }
        }
    }
    override fun folderEntry(entry: Path) {
        requireWritable()
        withZipEntry(EntryNameUtils.pathToFolderName(entry)) { }
    }

    override fun closeImpl() {
        zip_close(handle)
    }

    private inner class Entry : Zip.Entry {
        override val path: Path by lazy {
            EntryNameUtils.entryNameToPath(zip_entry_name(handle)!!.toKString())
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

private val Zip.Mode.char get() = when (this) {
    Zip.Mode.Read -> 'r'
    Zip.Mode.Write -> 'w'
    Zip.Mode.Append -> 'a' // same as 'd'
}

private fun Int.zeroOrZipError(msg: () -> String = { "Error" }) {
    if (this != 0) throwZipError(msg(), this)
}

private fun Long.orZipError(msg: () -> String = { "Error" }): Int {
    if (this < 0) throwZipError(msg(), this)
    if (this > Int.MAX_VALUE.toLong()) throwZipError("Value too large ($this)")
    return toInt()
}

private fun throwZipError(msg: String, err: Number? = null): Nothing {
    throw ZipException(
        listOfNotNull(
            msg,
            err?.let { zipError(it.toInt()) }
        ).joinToString(": ")
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun zipError(err: Int): String {
    return zip_strerror(err)?.toKString() ?: "unknown error $err"
}
