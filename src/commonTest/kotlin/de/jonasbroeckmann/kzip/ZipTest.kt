package de.jonasbroeckmann.kzip

import kotlinx.io.*
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.test.*

class ZipTest {
    private val testZipPath = Path("test.zip")
    private val testData1 = "Some test data 1...".encodeToByteArray()
    private val testData2 = "Some test data 2...".encodeToByteArray()

    @BeforeTest
    fun setup() {
        if (SystemFileSystem.exists(testZipPath)) {
            SystemFileSystem.delete(testZipPath)
        }
    }

    @AfterTest
    fun teardown() {
        if (SystemFileSystem.exists(testZipPath)) {
            SystemFileSystem.delete(testZipPath)
        }
    }

    @Test
    fun testWriteAndRead() {
        Zip.open(testZipPath, Zip.Mode.Write).use { zip ->
            zip.entryFromSource(Path("test/test-1.txt"), Buffer().apply { write(testData1) })
        }

        assertTrue(SystemFileSystem.exists(testZipPath))

        Zip.open(testZipPath, Zip.Mode.Read).use { zip ->
            assertEquals(1, zip.numberOfEntries)
            zip.entry(Path("test/test-1.txt")) {
                assertEquals(Path("test/test-1.txt"), path)
                assertFalse(isDirectory)
                assertEquals(testData1.size.toULong(), uncompressedSize)
                assertEquals(2220805626L, crc32)
                assertContentEquals(testData1, readToBytes())
            }
        }
    }

    @Test
    fun testAppend() {
        Zip.open(testZipPath, Zip.Mode.Write).use { zip ->
            zip.entryFromSource(Path("test/test-1.txt"), Buffer().apply { write(testData1) })
        }

        Zip.open(testZipPath, Zip.Mode.Append).use { zip ->
            zip.entryFromSource(Path("test/test-2.txt"), Buffer().apply { write(testData2) })
        }

        Zip.open(testZipPath, Zip.Mode.Read).use { zip ->
            assertEquals(2, zip.numberOfEntries)
            zip.entry(Path("test/test-1.txt")) {
                assertContentEquals(testData1, readToBytes())
            }
            zip.entry(Path("test/test-2.txt")) {
                assertContentEquals(testData2, readToBytes())
                assertEquals(2532008468L, crc32)
            }
        }
    }

    @Test
    fun testEntryByIndex() {
        Zip.open(testZipPath, Zip.Mode.Write).use { zip ->
            zip.entryFromSource(Path("test/test-1.txt"), Buffer().apply { write(testData1) })
            zip.entryFromSource(Path("test/test-2.txt"), Buffer().apply { write(testData2) })
        }

        Zip.open(testZipPath, Zip.Mode.Read).use { zip ->
            assertEquals(2, zip.numberOfEntries)
            zip.entry(0) {
                assertEquals(Path("test/test-1.txt"), path)
            }
            zip.entry(1) {
                assertEquals(Path("test/test-2.txt"), path)
            }
        }
    }

    @Test
    fun testWriteUtf8() {
        val utf8Name = "тест/Если-б-не-было-войны.txt"
        Zip.open(testZipPath, Zip.Mode.Write).use { zip ->
            zip.entryFromSource(Path(utf8Name), Buffer().apply { write(testData1) })
        }

        Zip.open(testZipPath, Zip.Mode.Read).use { zip ->
            zip.entry(Path(utf8Name)) {
                assertEquals(Path(utf8Name), path)
                assertContentEquals(testData1, readToBytes())
            }
        }
    }

    @Test
    fun testEntryFromPath() {
        val testFile = Path("test-file.txt")
        try {
            SystemFileSystem.sink(testFile).buffered().use { it.write(testData2) }

            Zip.open(testZipPath, Zip.Mode.Write).use { zip ->
                zip.entryFromPath(Path("from-path.txt"), testFile)
            }

            Zip.open(testZipPath, Zip.Mode.Read).use { zip ->
                zip.entry(Path("from-path.txt")) {
                    assertContentEquals(testData2, readToBytes())
                }
            }
        } finally {
            if (SystemFileSystem.exists(testFile)) {
                SystemFileSystem.delete(testFile)
            }
        }
    }

    @Test
    fun testFolderEntry() {
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
    fun testExtractTo() {
        Zip.open(testZipPath, Zip.Mode.Write).use { zip ->
            zip.entryFromSource(Path("test/test-1.txt"), Buffer().apply { write(testData1) })
            zip.folderEntry(Path("empty-folder"))
        }

        val extractDir = Path("extract")
        try {
            Zip.open(testZipPath, Zip.Mode.Read).use { zip ->
                zip.extractTo(extractDir)
            }

            assertTrue(SystemFileSystem.metadataOrNull(Path(extractDir, "test/test-1.txt"))?.isRegularFile == true)
            assertTrue(SystemFileSystem.metadataOrNull(Path(extractDir, "empty-folder"))?.isDirectory == true)
            
            SystemFileSystem.source(Path(extractDir, "test/test-1.txt")).buffered().use {
                assertContentEquals(testData1, it.readByteArray())
            }
        } finally {
            if (SystemFileSystem.exists(Path(extractDir, "test/test-1.txt"))) SystemFileSystem.delete(Path(extractDir, "test/test-1.txt"))
            if (SystemFileSystem.exists(Path(extractDir, "test"))) SystemFileSystem.delete(Path(extractDir, "test"))
            if (SystemFileSystem.exists(Path(extractDir, "empty-folder"))) SystemFileSystem.delete(Path(extractDir, "empty-folder"))
            if (SystemFileSystem.exists(extractDir)) SystemFileSystem.delete(extractDir)
        }
    }

    @Test
    fun testCompressFrom() {
        val sourceDir = Path("source")
        val file1 = Path(sourceDir, "file1.txt")
        val subDir = Path(sourceDir, "subdir")
        val file2 = Path(subDir, "file2.txt")
        
        try {
            SystemFileSystem.createDirectories(subDir)
            SystemFileSystem.sink(file1).buffered().use { it.write(testData1) }
            SystemFileSystem.sink(file2).buffered().use { it.write(testData2) }

            Zip.open(testZipPath, Zip.Mode.Write).use { zip ->
                zip.compressFrom(sourceDir)
            }

            Zip.open(testZipPath, Zip.Mode.Read).use { zip ->
                var foundFile1 = false
                var foundFile2 = false
                var foundSubdir = false
                
                zip.forEachEntry { entry ->
                    when (entry.path) {
                        Path("file1.txt") -> foundFile1 = true
                        Path("subdir") -> foundSubdir = true
                        Path("subdir/file2.txt") -> foundFile2 = true
                    }
                }
                
                assertTrue(foundFile1, "file1.txt not found")
                assertTrue(foundSubdir, "subdir not found")
                assertTrue(foundFile2, "subdir/file2.txt not found")
            }
        } finally {
            if (SystemFileSystem.exists(file2)) SystemFileSystem.delete(file2)
            if (SystemFileSystem.exists(subDir)) SystemFileSystem.delete(subDir)
            if (SystemFileSystem.exists(file1)) SystemFileSystem.delete(file1)
            if (SystemFileSystem.exists(sourceDir)) SystemFileSystem.delete(sourceDir)
        }
    }

    @Test
    fun testOpenNonExistentRead() {
        assertFails {
            Zip.open(Path("non-existent.zip"), Zip.Mode.Read)
        }
    }

    @Test
    fun testDeleteEntries() {
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
            var foundFile1 = false
            var foundFile3 = false
            zip.forEachEntry { entry ->
                if (entry.path == Path("file1.txt")) foundFile1 = true
                if (entry.path == Path("file3.txt")) foundFile3 = true
                assertNotEquals(Path("file2.txt"), entry.path)
            }
            assertTrue(foundFile1)
            assertTrue(foundFile3)
        }
    }

    @Test
    fun testDeleteEntriesByIndex() {
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
}
