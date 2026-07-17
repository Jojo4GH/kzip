package de.jonasbroeckmann.kzip.implementation.util

import kotlinx.io.Buffer
import kotlinx.io.RawSource

private class WrappedSource<Upstream : RawSource>(
    private val upstream: Upstream,
    wrapped: Upstream.() -> RawSource
) : RawSource {
    private val wrapped = upstream.wrapped()

    override fun readAtMostTo(sink: Buffer, byteCount: Long) = wrapped.readAtMostTo(sink, byteCount)

    override fun close() {
        wrapped.close()
        upstream.close()
    }
}

internal fun <T : RawSource> T.wrapped(wrapped: T.() -> RawSource): RawSource = WrappedSource(this, wrapped)
