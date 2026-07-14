package de.jonasbroeckmann.kzip

import kotlinx.io.IOException

public actual class ZipException actual constructor(message: String) : IOException(message)
