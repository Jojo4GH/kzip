package de.jonasbroeckmann.kzip.implementation.util

import kotlinx.io.RawSource
import kotlinx.io.files.Path

internal expect fun fileSourceWithOffset(
    path: Path,
    startOffset: Long
): RawSource
