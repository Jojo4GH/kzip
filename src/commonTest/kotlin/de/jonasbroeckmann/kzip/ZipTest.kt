package de.jonasbroeckmann.kzip

import kotlinx.io.*
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.test.*

class ZipTest {

    @Test
    fun testWriteAndRead() = withTempDirectory { testDir ->
        val testZipPath = Path(testDir, "test.zip")
        Zip.open(testZipPath, Zip.Mode.Write, level = Zip.CompressionLevel.NoCompression).use { zip ->
            zip.entryFromSource(Path("test", "test-1.txt"), Buffer().apply { write(testData1) })
        }

        assertTrue(SystemFileSystem.exists(testZipPath))

        Zip.open(testZipPath, Zip.Mode.Read).use { zip ->
            assertEquals(1, zip.numberOfEntries)
            zip.entry(Path("test", "test-1.txt")) {
                assertEquals(Path("test", "test-1.txt"), path)
                assertFalse(isDirectory)
                assertEquals(testData1.size.toULong(), uncompressedSize)
                assertEquals(2220805626L, crc32)
                assertContentEquals(testData1, readToBytes())
            }
        }
    }

    @Test
    fun testAppend() = withTempDirectory { testDir ->
        val testZipPath = Path(testDir, "test.zip")
        Zip.open(testZipPath, Zip.Mode.Write).use { zip ->
            zip.entryFromSource(Path("test", "test-1.txt"), Buffer().apply { write(testData1) })
        }

        Zip.open(testZipPath, Zip.Mode.Append).use { zip ->
            zip.entryFromSource(Path("test", "test-2.txt"), Buffer().apply { write(testData2) })
        }

        Zip.open(testZipPath, Zip.Mode.Read).use { zip ->
            assertEquals(2, zip.numberOfEntries)
            zip.entry(Path("test", "test-1.txt")) {
                assertContentEquals(testData1, readToBytes())
            }
            zip.entry(Path("test", "test-2.txt")) {
                assertContentEquals(testData2, readToBytes())
                assertEquals(2532008468L, crc32)
            }
        }
    }

    @Test
    fun testEntryByIndex() = withTempDirectory { testDir ->
        val testZipPath = Path(testDir, "test.zip")
        Zip.open(testZipPath, Zip.Mode.Write).use { zip ->
            zip.entryFromSource(Path("test", "test-1.txt"), Buffer().apply { write(testData1) })
            zip.entryFromSource(Path("test", "test-2.txt"), Buffer().apply { write(testData2) })
        }

        Zip.open(testZipPath, Zip.Mode.Read).use { zip ->
            assertEquals(2, zip.numberOfEntries)
            zip.entry(0) {
                assertEquals(Path("test", "test-1.txt"), path)
            }
            zip.entry(1) {
                assertEquals(Path("test", "test-2.txt"), path)
            }
        }
    }

    @Test
    fun testWriteUtf8() = withTempDirectory { testDir ->
        val testZipPath = Path(testDir, "test.zip")
        val utf8Path = Path("тест", "Если-б-не-было-войны.txt")
        Zip.open(testZipPath, Zip.Mode.Write).use { zip ->
            zip.entryFromSource(utf8Path, Buffer().apply { write(testData1) })
        }

        Zip.open(testZipPath, Zip.Mode.Read).use { zip ->
            zip.entry(utf8Path) {
                assertEquals(utf8Path, path)
                assertContentEquals(testData1, readToBytes())
            }
        }
    }

    @Test
    fun testEntryFromPath() = withTempDirectory { testDir ->
        val testZipPath = Path(testDir, "test.zip")
        val testFile = Path(testDir, "test-file.txt")
        SystemFileSystem.sink(testFile).buffered().use { it.write(testData2) }

        Zip.open(testZipPath, Zip.Mode.Write).use { zip ->
            zip.entryFromPath(Path("from-path.txt"), testFile)
        }

        Zip.open(testZipPath, Zip.Mode.Read).use { zip ->
            zip.entry(Path("from-path.txt")) {
                assertContentEquals(testData2, readToBytes())
            }
        }
    }

    @Test
    fun testFolderEntry() = withTempDirectory { testDir ->
        val testZipPath = Path(testDir, "test.zip")
        Zip.open(testZipPath, Zip.Mode.Write).use { zip ->
            zip.folderEntry(Path("empty-folder"))
        }

        Zip.open(testZipPath, Zip.Mode.Read).use { zip ->
            assertEquals(1, zip.numberOfEntries)
            zip.entry(0) {
                assertEquals(Path("empty-folder"), path)
                assertTrue(isDirectory)
                assertEquals(0u, uncompressedSize)
            }
        }
    }

    @Test
    fun testExtractTo() = withTempDirectory { testDir ->
        val testZipPath = Path(testDir, "test.zip")
        Zip.open(testZipPath, Zip.Mode.Write).use { zip ->
            zip.entryFromSource(Path("test", "test-1.txt"), Buffer().apply { write(testData1) })
            zip.folderEntry(Path("empty-folder"))
        }

        val extractDir = Path(testDir, "extract")
        Zip.open(testZipPath, Zip.Mode.Read).use { zip ->
            zip.extractTo(extractDir)
        }

        assertEquals(true, SystemFileSystem.metadataOrNull(Path(extractDir, "test", "test-1.txt"))?.isRegularFile)
        assertEquals(true, SystemFileSystem.metadataOrNull(Path(extractDir, "empty-folder"))?.isDirectory)

        SystemFileSystem.source(Path(extractDir, "test", "test-1.txt")).buffered().use {
            assertContentEquals(testData1, it.readByteArray())
        }
    }

    @Test
    fun testCompressFrom() = withTempDirectory { testDir ->
        val testZipPath = Path(testDir, "test.zip")
        val sourceDir = Path(testDir, "source")
        val file1 = Path(sourceDir, "file1.txt")
        val subDir = Path(sourceDir, "subdir")
        val file2 = Path(subDir, "file2.txt")

        SystemFileSystem.createDirectories(subDir)
        SystemFileSystem.sink(file1).buffered().use { it.write(testData1) }
        SystemFileSystem.sink(file2).buffered().use { it.write(testData2) }

        Zip.open(testZipPath, Zip.Mode.Write).use { zip ->
            zip.compressFrom(sourceDir)
        }

        Zip.open(testZipPath, Zip.Mode.Read).use { zip ->
            assertContainsEntry(zip, Path("file1.txt"))
            assertContainsEntry(zip, Path("subdir"))
            assertContainsEntry(zip, Path("subdir", "file2.txt"))
        }
    }

    @Test
    fun testOpenNonExistentRead() = withTempDirectory { testDir ->
        assertFails {
            Zip.open(Path(testDir, "non-existent.zip"), Zip.Mode.Read)
        }
    }

    @Test
    fun testDeleteEntries() = withTempDirectory { testDir ->
        val testZipPath = Path(testDir, "test.zip")
        Zip.open(testZipPath, Zip.Mode.Write).use { zip ->
            zip.entryFromSource(Path("file1.txt"), Buffer().apply { write(testData1) })
            zip.entryFromSource(Path("file2.txt"), Buffer().apply { write(testData2) })
            zip.entryFromSource(Path("file3.txt"), Buffer().apply { write(testData1) })
        }

        Zip.open(testZipPath, Zip.Mode.Append).use { zip ->
            zip.deleteEntries(listOf(Path("file2.txt")))
        }

        Zip.open(testZipPath, Zip.Mode.Read).use { zip ->
            assertEquals(2, zip.numberOfEntries)
            assertContainsEntry(zip, Path("file1.txt"))
            assertContainsEntry(zip, Path("file3.txt"))
            assertFalse(zip.containsEntry(Path("file2.txt")))
        }
    }

    @Test
    fun testDeleteEntriesByIndex() = withTempDirectory { testDir ->
        val testZipPath = Path(testDir, "test.zip")
        Zip.open(testZipPath, Zip.Mode.Write).use { zip ->
            zip.entryFromSource(Path("file1.txt"), Buffer().apply { write(testData1) })
            zip.entryFromSource(Path("file2.txt"), Buffer().apply { write(testData2) })
        }

        Zip.open(testZipPath, Zip.Mode.Append).use { zip ->
            zip.deleteEntriesByIndex(listOf(0))
        }

        Zip.open(testZipPath, Zip.Mode.Read).use { zip ->
            assertEquals(1, zip.numberOfEntries)
            zip.entry(0) {
                assertEquals(Path("file2.txt"), path)
            }
        }
    }

    private companion object {
        private val testData1 = "Some test data 1...".encodeToByteArray()
        private val testData2 = "Some test data 2...".encodeToByteArray()
    }
}

private fun assertContainsEntry(zip: Zip, path: Path) {
    assertTrue(zip.containsEntry(path), "$path not found")
}

private fun Zip.containsEntry(path: Path): Boolean {
    for (i in 0 until numberOfEntries) {
        var found = false
        entry(i) {
            found = this.path == path
        }
        if (found) return true
    }
    return false
}
