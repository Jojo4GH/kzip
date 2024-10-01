package de.jonasbroeckmann.kzip

import kotlinx.cinterop.*
import kotlinx.io.files.Path
import kuba.zip.*
import cnames.structs.zip_t
import kotlinx.io.IOException


//var done = 0
//@OptIn(ExperimentalForeignApi::class)
//fun extractZip(zip: Path, dest: Path) = memScoped {
//    val counter = alloc<IntVar>()
//    counter.value = 0
//    val result = zip_extract(
//        zipname = zip.toString(),
//        dir = dest.toString(),
////        on_extract_entry = null,
//        on_extract_entry = staticCFunction<CPointer<ByteVar>?, COpaquePointer?, Int> { name, arg ->
//            val counter = arg!!.reinterpret<IntVar>().pointed
//            counter.value = counter.value + 1
//            println("${counter.value} Extracting ${name?.toKString()}")
//            return@staticCFunction 0
//        },
//        arg = counter.ptr
//    )
//    println(result)
//}



@OptIn(ExperimentalForeignApi::class)
private typealias ZipHandle = CPointer<zip_t>



@OptIn(ExperimentalForeignApi::class)
private class KubaZip(private val handle: ZipHandle) : WritableZip {
    override val numberOfEntries: ULong by lazy { zip_entries_total(handle).orZipError() }

    override fun writableEntry(path: Path, block: WritableZip.Entry.() -> Unit) {
        zip_entry_open(handle, Zip.pathToEntryName(path)).zeroOrZipError()
        try {
            Entry(handle).block()
        } finally {
            zip_entry_close(handle).zeroOrZipError()
        }
    }
    override fun writeFolder(path: Path) {
        zip_entry_open(handle, "${Zip.pathToEntryName(path)}$ZipEntryNameSeparator").zeroOrZipError()
        zip_entry_close(handle).zeroOrZipError()
    }
    override fun entry(index: ULong, block: Zip.Entry.() -> Unit) {
        zip_entry_openbyindex(handle, index).zeroOrZipError()
        try {
            Entry(handle).block()
        } finally {
            zip_entry_close(handle).zeroOrZipError()
        }
    }
    override fun deleteEntries(paths: List<Path>) = memScoped {
        val names = paths.map { Zip.pathToEntryName(it) }
        val result = zip_entries_delete(handle, names.toCStringArray(this), names.size.toULong())
        if (result <= 0) throwZipError("Failed to delete entries", result)
    }
    override fun deleteEntries(vararg indices: ULong) = memScoped {
        val result = zip_entries_deletebyindex(handle, indices.toCValues().ptr, indices.size.toULong())
        if (result <= 0) throwZipError("Failed to delete entries", result)
    }

    @OptIn(ExperimentalForeignApi::class)
    private class Entry(private val zip: ZipHandle) : WritableZip.Entry {
        override val path: Path by lazy {
            Zip.entryNameToPath(zip_entry_name(zip)!!.toKString())
        }
        override val isDirectory: Boolean by lazy { zip_entry_isdir(zip) != 0 }
        override val uncompressedSize: ULong by lazy { zip_entry_uncomp_size(zip) }
        override val compressedSize: ULong by lazy { zip_entry_comp_size(zip) }
        override val crc32: Long by lazy { zip_entry_crc32(zip).toLong() }

        override fun read(): ByteArray {
            val buffer = ByteArray(uncompressedSize.toInt())
            val result = zip_entry_noallocread(zip, buffer.refTo(0), uncompressedSize)
            if (result <= 0) throwZipError("Error while reading", result)
            return buffer
        }
        override fun readToPath(path: Path) {

            zip_entry_fread(zip, path.toString())
                .zeroOrZipError { "Error while reading to path" }
        }

        override fun write(data: ByteArray) {
            zip_entry_write(zip, data.refTo(0), data.size.toULong())
                .zeroOrZipError { "Error while writing" }
        }
        override fun writeFromPath(path: Path) {
            zip_entry_fwrite(zip, path.toString())
                .zeroOrZipError { "Error while writing from path" }
        }
    }

    override fun close() {
        zip_close(handle)
    }
}

private val WritableZip.Mode.char get() = when (this) {
    WritableZip.Mode.Write -> 'w'
    WritableZip.Mode.Append -> 'a'
}

private val Zip.CompressionLevel.number get() = level


@OptIn(ExperimentalForeignApi::class)
actual fun Zip.Companion.open(path: Path): Zip = memScoped {
    val err = alloc<IntVar>()
    val handle = zip_openwitherror(path.toString(), 0, 'r'.code.toByte(), err.ptr)
        ?: throwZipError("Failed to open zip file", err.value)
    KubaZip(handle)
}
@OptIn(ExperimentalForeignApi::class)
actual fun WritableZip.Companion.open(path: Path, level: Zip.CompressionLevel, mode: WritableZip.Mode): WritableZip = memScoped {
    val err = alloc<IntVar>()
    val handle = zip_openwitherror(path.toString(), level.number, mode.char.code.toByte(), err.ptr)
        ?: throwZipError("Failed to open zip file", err.value)
    KubaZip(handle)
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

actual class ZipException actual constructor(message: String) : IOException(message)
