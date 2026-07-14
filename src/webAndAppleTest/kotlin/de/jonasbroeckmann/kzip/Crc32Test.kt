package de.jonasbroeckmann.kzip

import kotlin.test.Test
import kotlin.test.assertEquals

class Crc32Test {

    @Test
    fun testEmpty() {
        assertEquals(0u, Crc32.of(byteArrayOf()))
    }

    @Test
    fun testStandardVector() {
        // Standard test vector for CRC32
        val data = "123456789".encodeToByteArray()
        assertEquals(0xCBF43926u, Crc32.of(data))
    }

    @Test
    fun testHelloWorld() {
        val data = "Hello World".encodeToByteArray()
        assertEquals(0x4A17B156u, Crc32.of(data))
    }

    @Test
    fun testIncrementalUpdate() {
        val data = "Hello World".encodeToByteArray()

        // Update byte by byte
        assertEquals(0x4A17B156u, Crc32.compute {
            for (b in data) {
                writeByte(b)
            }
        })

        // Update in two chunks
        assertEquals(0x4A17B156u, Crc32.compute {
            write(data, 0, 5)
            write(data, 5)
        })
    }

    @Test
    fun testOffsetAndLength() {
        val data = "ABC123456789XYZ".encodeToByteArray()
        val crc = Crc32()
        // Extract "123456789" from the middle
        assertEquals(0xCBF43926u, Crc32.compute {
            write(data, 3, 12)
        })
    }
}