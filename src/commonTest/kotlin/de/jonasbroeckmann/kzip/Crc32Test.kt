package de.jonasbroeckmann.kzip

import kotlin.test.Test
import kotlin.test.assertEquals

class Crc32Test {

    @Test
    fun testEmpty() {
        assertEquals(0L, Crc32.calculate(ByteArray(0)))
    }

    @Test
    fun testStandardVector() {
        // Standard test vector for CRC32
        val data = "123456789".encodeToByteArray()
        assertEquals(0xCBF43926L, Crc32.calculate(data))
    }

    @Test
    fun testHelloWorld() {
        val data = "Hello World".encodeToByteArray()
        assertEquals(0x4A17B156L, Crc32.calculate(data))
    }

    @Test
    fun testIncrementalUpdate() {
        val data = "Hello World".encodeToByteArray()
        val crc = Crc32()

        // Update byte by byte
        for (b in data) {
            crc.update(b.toInt())
        }
        assertEquals(0x4A17B156L, crc.getValue())

        crc.reset()

        // Update in two chunks
        crc.update(data, 0, 5)
        crc.update(data, 5, 6)
        assertEquals(0x4A17B156L, crc.getValue())
    }

    @Test
    fun testReset() {
        val data = "123456789".encodeToByteArray()
        val crc = Crc32()
        crc.update(data)
        assertEquals(0xCBF43926L, crc.getValue())

        crc.reset()
        assertEquals(0L, crc.getValue())

        crc.update(data)
        assertEquals(0xCBF43926L, crc.getValue())
    }

    @Test
    fun testOffsetAndLength() {
        val data = "ABC123456789XYZ".encodeToByteArray()
        val crc = Crc32()
        // Extract "123456789" from the middle
        crc.update(data, 3, 9)
        assertEquals(0xCBF43926L, crc.getValue())
    }
}
