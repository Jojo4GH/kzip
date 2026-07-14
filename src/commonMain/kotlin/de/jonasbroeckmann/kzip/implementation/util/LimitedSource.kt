package de.jonasbroeckmann.kzip.implementation.util

import kotlinx.io.Buffer
import kotlinx.io.RawSource

private class LimitedSource(
    private val upstream: RawSource,
    private var remaining: Long
) : RawSource {
    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        if (remaining <= 0L) return -1
        val toRead = minOf(byteCount, remaining)
        val read = upstream.readAtMostTo(sink, toRead)
        if (read > 0) remaining -= read
        return read
    }

    override fun close() = upstream.close()
}

internal fun RawSource.limited(byteCount: Long): RawSource = LimitedSource(upstream = this, remaining = byteCount)