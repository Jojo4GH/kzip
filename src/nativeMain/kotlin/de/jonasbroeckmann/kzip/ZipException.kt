package de.jonasbroeckmann.kzip

import kotlinx.io.IOException

actual class ZipException actual constructor(message: String) : IOException(message)