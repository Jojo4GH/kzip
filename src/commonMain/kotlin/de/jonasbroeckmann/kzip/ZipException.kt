package de.jonasbroeckmann.kzip

import kotlinx.io.IOException

expect class ZipException(message: String) : IOException
