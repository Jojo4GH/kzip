package de.jonasbroeckmann.kzip.implementation.util

import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource

private class FeedingSource(
    private val source: RawSource,
    private val autoClose: Boolean,
    private val sinks: List<RawSink>
): RawSource {
    private val buffer = Buffer()

    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        val read = source.readAtMostTo(buffer, byteCount)
        if (read > 0) sinks.forEach {
            buffer.copy().use { copy -> copy.transferTo(it) }
        }
        buffer.transferTo(sink)
        return read
    }

    override fun close() {
        buffer.close()
        source.close()
        if (autoClose) sinks.forEach { it.close() }
    }
}

internal fun RawSource.feed(vararg sinks: RawSink, autoClose: Boolean = true): RawSource = FeedingSource(
    source = this,
    autoClose = autoClose,
    sinks = listOf(*sinks)
)