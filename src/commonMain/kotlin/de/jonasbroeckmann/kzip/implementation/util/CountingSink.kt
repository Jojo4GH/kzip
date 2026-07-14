package de.jonasbroeckmann.kzip.implementation.util

import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.discardingSink

internal class CountingSink(
    private val sink: RawSink = discardingSink()
) : RawSink {
    var count = 0L
        private set

    override fun write(source: Buffer, byteCount: Long) {
        sink.write(source, byteCount)
        count += byteCount
    }

    override fun flush() = sink.flush()

    override fun close() = sink.close()
}