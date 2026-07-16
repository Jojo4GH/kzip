package de.jonasbroeckmann.kzip.implementation.util

import okio.Path.Companion.toPath
import kotlinx.io.files.Path as KotlinxIoPath
import okio.Path as OkioPath

internal fun KotlinxIoPath.toOkioPath(): OkioPath = toString().toPath()

internal fun OkioPath.toKotlinxIoPath(): KotlinxIoPath = KotlinxIoPath(toString())
