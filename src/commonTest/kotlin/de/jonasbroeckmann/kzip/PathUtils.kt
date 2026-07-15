package de.jonasbroeckmann.kzip

import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem


private var nextTempId = 1

internal fun createTempDirectory(label: String? = null): Path {
    val base = SystemFileSystem.resolve(Path("."))
    val path = Path(base, listOf("tmp", "${nextTempId++}", label).joinToString("-"))
//    SystemFileSystem.deleteRecursively(path, mustExist = false)
    SystemFileSystem.createDirectories(path)
    return path
}

internal fun deleteTempDirectory(path: Path) {
    try {
        SystemFileSystem.deleteRecursively(path, mustExist = false)
    } catch (e: Exception) {
        throw RuntimeException("Failed to delete temp directory: $path", e)
    }
}

internal inline fun withTempDirectory(label: String? = null, block: (Path) -> Unit) {
    val path = createTempDirectory(label)
    try {
        block(path)
    } finally {
        deleteTempDirectory(path)
    }
}

internal fun FileSystem.deleteRecursively(path: Path, mustExist: Boolean = true) {
    metadataOrNull(path)?.let { metadata ->
        if (metadata.isDirectory) {
            list(path).forEach { deleteRecursively(it, mustExist = mustExist) }
        }
    }
    try {
        delete(path, mustExist = mustExist)
    } catch (e: Exception) {
        throw RuntimeException("Failed to delete: $path", e)
    }
}
