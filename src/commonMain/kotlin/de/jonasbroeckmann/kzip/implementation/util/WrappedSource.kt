package de.jonasbroeckmann.kzip.implementation.util

import dev.karmakrafts.kompress.Deflater
import dev.karmakrafts.kompress.Inflater
import dev.karmakrafts.kompress.deflating
import dev.karmakrafts.kompress.inflating
import kotlinx.io.Buffer
import kotlinx.io.RawSource

private class WrappedSource(
    private val upstream: RawSource,
    wrapped: RawSource.() -> RawSource
) : RawSource {
    private val wrapped = upstream.wrapped()

    override fun readAtMostTo(sink: Buffer, byteCount: Long) = wrapped.readAtMostTo(sink, byteCount)

    override fun close() {
        wrapped.close()
        upstream.close()
    }
}

internal fun RawSource.wrappedInflating(
    raw: Boolean = true,
    bufferSize: Int = Inflater.DEFAULT_BUFFER_SIZE
): RawSource = WrappedSource(this) { inflating(raw = raw, bufferSize = bufferSize) }

internal fun RawSource.wrappedDeflating(
    raw: Boolean = true,
    level: Int = Deflater.DEFAULT_LEVEL,
    bufferSize: Int = Deflater.DEFAULT_BUFFER_SIZE
): RawSource = WrappedSource(this) { deflating(raw = raw, level = level, bufferSize = bufferSize) }