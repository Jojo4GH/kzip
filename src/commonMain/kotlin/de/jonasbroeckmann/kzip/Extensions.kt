package de.jonasbroeckmann.kzip

import kotlinx.io.IOException
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

inline fun Zip.forEachEntryIndexed(crossinline block: (ULong, Zip.Entry) -> Unit) {
    for (i in 0uL until numberOfEntries) {
        entry(i) { block(i, this) }
    }
}

inline fun Zip.forEachEntry(crossinline block: (Zip.Entry) -> Unit) = forEachEntryIndexed { _, entry ->
    block(entry)
}

fun Zip.extractTo(directory: Path) {
    forEachEntry { entry ->
        val target = Path(directory, entry.path.toString())
        if (entry.isDirectory) {
            SystemFileSystem.createDirectories(target)
        } else {
            target.parent?.let { SystemFileSystem.createDirectories(it) }
            entry.readToPath(target)
        }
    }
}

fun Zip.compressFrom(path: Path, pathInZip: Path? = null) {
    val metadata = SystemFileSystem.metadataOrNull(path) ?: throw IOException("Cannot read metadata of $path")
    if (metadata.isDirectory) {
        println("Compressing directory $pathInZip")
        if (pathInZip != null) folderEntry(pathInZip)
        SystemFileSystem.list(path).forEach { child ->
            compressFrom(child, pathInZip?.let { Path(it, child.name) } ?: Path(child.name))
        }
    } else if (metadata.isRegularFile) {
        println("Compressing file $pathInZip")
        if (pathInZip != null) entryFromPath(pathInZip, path)
    } else {
        throw UnsupportedOperationException("Unsupported file type at $path")
    }
}