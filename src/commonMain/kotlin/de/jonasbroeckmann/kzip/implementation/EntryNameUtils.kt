package de.jonasbroeckmann.kzip.implementation

import kotlinx.io.files.Path

internal object EntryNameUtils {
    private const val ZipEntryNameSeparator = '/'

    fun pathToFileName(path: Path): String {
        if (path.isAbsolute) throw IllegalArgumentException("Path for zip entry must be relative: $path")
        path.parent?.let { parent ->
            return "${pathToFileName(parent)}$ZipEntryNameSeparator${path.name}"
        }
        return path.name
    }

    fun pathToFolderName(path: Path): String {
        return "${pathToFileName(path)}$ZipEntryNameSeparator"
    }

    fun entryNameToPath(name: String): Path {
        val elements = name.split(ZipEntryNameSeparator)
        return Path(
            elements.first(),
            *elements.drop(1).let {
                if (it.lastOrNull()?.isEmpty() == true) it.dropLast(1) else it
            }.toTypedArray()
        )
    }
}
