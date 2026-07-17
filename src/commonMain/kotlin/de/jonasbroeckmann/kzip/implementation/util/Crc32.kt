package de.jonasbroeckmann.kzip.implementation.util

import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.Sink
import kotlinx.io.buffered
import kotlinx.io.discardingSink
import kotlinx.io.readUByte

/**
 * Sink calculating CRC-32 code for all the data written to it and sending this data to the [sink] afterward.
 *
 * See sample in [RawSink]
 */
@OptIn(ExperimentalUnsignedTypes::class)
internal class Crc32(private val sink: RawSink = discardingSink()): RawSink {
    private val tempBuffer = Buffer()
    private var value: UInt = 0xFFFFFFFFu

    private fun update(value: UByte) {
        val index = (value.toUInt() xor this.value).toUByte()
        this.value = (this.value shr 8) xor table[index.toInt()]
    }

    val crc32: UInt get() = value xor 0xFFFFFFFFu

    override fun write(source: Buffer, byteCount: Long) {
        source.copyTo(tempBuffer, 0, byteCount)
        while (!tempBuffer.exhausted()) {
            update(tempBuffer.readUByte())
        }
        sink.write(source, byteCount)
    }

    override fun flush() = sink.flush()

    override fun close() = sink.close()

    companion object {
        private val table by lazy {
            UIntArray(256) {
                var v = it.toUInt()
                repeat(8) {
                    v = (if (v % 2u == 0u) v shr 1 else (v shr 1) xor 0xEDB88320U)
                }
                v
            }
        }

        fun compute(block: Sink.() -> Unit): UInt {
            val crc = Crc32()
            crc.buffered().use(block)
            return crc.crc32
        }

        fun of(bytes: ByteArray) = compute { write(bytes) }
    }
}
