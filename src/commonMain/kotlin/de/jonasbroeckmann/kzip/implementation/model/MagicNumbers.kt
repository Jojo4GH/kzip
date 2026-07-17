package de.jonasbroeckmann.kzip.implementation.model

internal object MagicNumbers {
    const val LOCAL_FILE_HEADER = 0x04034b50u
    const val CENTRAL_DIRECTORY_FILE_HEADER = 0x02014b50u
    const val END_OF_CENTRAL_DIRECTORY_RECORD = 0x06054b50u
}
