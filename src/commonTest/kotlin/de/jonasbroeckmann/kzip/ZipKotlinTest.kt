package de.jonasbroeckmann.kzip

import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlin.test.*

class ZipKotlinTest {
    private val testZip = Path("test-kotlin.zip")
    private val testFile = Path("test.txt")
    private val testContent = "Hello, Kzip Kotlin!"

    @BeforeTest
    fun setup() {
        if (SystemFileSystem.exists(testZip)) SystemFileSystem.delete(testZip)
        if (SystemFileSystem.exists(testFile)) SystemFileSystem.delete(testFile)
        SystemFileSystem.sink(testFile).buffered().use { it.writeString(testContent) }
    }

    @AfterTest
    fun teardown() {
        if (SystemFileSystem.exists(testZip)) SystemFileSystem.delete(testZip)
        if (SystemFileSystem.exists(testFile)) SystemFileSystem.delete(testFile)
    }

    @Test
    fun testWriteAndRead() {
        Zip.openKotlin(testZip, Zip.Mode.Write).use { zip ->
            zip.entryFromPath(Path("test.txt"), testFile)
            val buffer = Buffer().apply { writeString("Source content") }
            zip.entryFromSource(Path("source.txt"), buffer)
        }

        assertTrue(SystemFileSystem.exists(testZip))

        Zip.openKotlin(testZip, Zip.Mode.Read).use { zip ->
            assertEquals(2, zip.numberOfEntries)
            zip.entry(Path("test.txt")) {
                assertEquals(testContent, readToSource().readString())
                assertEquals(testContent.length.toULong(), uncompressedSize)
            }
            zip.entry(Path("source.txt")) {
                assertEquals("Source content", readToSource().readString())
            }
        }
    }

    @Test
    fun testAppend() {
        Zip.openKotlin(testZip, Zip.Mode.Write).use { zip ->
            zip.entryFromPath(Path("test.txt"), testFile)
        }

        Zip.openKotlin(testZip, Zip.Mode.Append).use { zip ->
            zip.entryFromSource(Path("append.txt"), Buffer().apply { writeString("Appended") })
        }

        Zip.openKotlin(testZip, Zip.Mode.Read).use { zip ->
            assertEquals(2, zip.numberOfEntries)
            zip.entry(Path("test.txt")) {
                assertEquals(testContent, readToSource().readString())
            }
            zip.entry(Path("append.txt")) {
                assertEquals("Appended", readToSource().readString())
            }
        }
    }

    @Test
    fun testDelete() {
        Zip.openKotlin(testZip, Zip.Mode.Write).use { zip ->
            zip.entryFromSource(Path("f1.txt"), Buffer().apply { writeString("1") })
            zip.entryFromSource(Path("f2.txt"), Buffer().apply { writeString("2") })
            zip.entryFromSource(Path("f3.txt"), Buffer().apply { writeString("3") })
        }

        Zip.openKotlin(testZip, Zip.Mode.Append).use { zip ->
            zip.deleteEntries(listOf(Path("f2.txt")))
        }

        Zip.openKotlin(testZip, Zip.Mode.Read).use { zip ->
            assertEquals(2, zip.numberOfEntries)
            assertFailsWith<ZipException> {
                zip.entry(Path("f2.txt")) { }
            }
            zip.entry(Path("f1.txt")) { assertEquals("1", readToSource().readString()) }
            zip.entry(Path("f3.txt")) { assertEquals("3", readToSource().readString()) }
        }
    }

    @Test
    fun testFolderEntry() {
        Zip.openKotlin(testZip, Zip.Mode.Write).use { zip ->
            zip.folderEntry(Path("folder"))
            zip.entryFromSource(Path("folder/file.txt"), Buffer().apply { writeString("file") })
        }

        Zip.openKotlin(testZip, Zip.Mode.Read).use { zip ->
            assertEquals(2, zip.numberOfEntries)
            zip.entry(Path("folder")) {
                assertTrue(isDirectory)
            }
            zip.entry(Path("folder/file.txt")) {
                assertFalse(isDirectory)
                assertEquals("file", readToSource().readString())
            }
        }
    }
}
