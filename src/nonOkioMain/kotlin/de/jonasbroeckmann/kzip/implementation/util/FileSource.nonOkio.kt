package de.jonasbroeckmann.kzip.implementation.util

import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

internal actual fun fileSourceWithOffset(path: Path, startOffset: Long): RawSource = SystemFileSystem.source(path)
    .buffered()
    .apply { skip(startOffset) }
