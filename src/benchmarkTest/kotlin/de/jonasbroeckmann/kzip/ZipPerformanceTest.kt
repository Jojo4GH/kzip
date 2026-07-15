package de.jonasbroeckmann.kzip

import kotlinx.benchmark.*
import kotlinx.io.Buffer
import kotlinx.io.files.Path
import kotlin.random.Random
import kotlin.test.assertContentEquals

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 2, time = 1, batchSize = 1)
@Measurement(iterations = 4, time = 1, batchSize = 1)
open class ZipBenchmark {
    @Param("true", "false")
    var useKotlinImplementation = true

    private lateinit var tempDir: Path
    private val compressZipPath get() = Path(tempDir, "benchmark-compress.zip")
    private val extractZipPath get() = Path(tempDir, "benchmark-extract.zip")

    private lateinit var benchmarkEntries: List<BenchmarkEntry>

    private class BenchmarkEntry(
        val path: Path,
        val content: ByteArray?
    )

    @Setup
    fun setup() {
        tempDir = createTempDirectory("benchmark")
        benchmarkEntries = generateEntries(100, 5, 500 * 1024)
        Zip.open(extractZipPath, Zip.Mode.Write).use { zip ->
            benchmarkEntries.forEach { entry ->
                zip.writeEntry(entry)
            }
        }
    }

    private fun generateEntries(numFiles: Int, maxDepth: Int, contentSize: Int) = buildList {
        val random = Random(42)
        for (i in 0 until numFiles) {
            val depth = random.nextInt(maxDepth + 1)
            var currentDir: Path? = null
            for (d in 0 until depth) {
                currentDir = Path(currentDir, "dir_$d")
                this += BenchmarkEntry(currentDir, null)
            }
            this += BenchmarkEntry(
                path = Path(currentDir, "file_$i.txt"),
                content = random.nextBytes(contentSize)
            )
        }
    }

    @TearDown
    fun teardown() {
        deleteTempDirectory(tempDir)
    }

    private fun openZip(
        path: Path,
        mode: Zip.Mode = Zip.Mode.Read,
        level: Zip.CompressionLevel = Zip.CompressionLevel.Default
    ): Zip = if (useKotlinImplementation) Zip.open(path, mode, level) else Zip.openNative(path, mode, level)

    private fun Zip.writeEntry(entry: BenchmarkEntry) {
        if (entry.content == null) {
            folderEntry(entry.path)
        } else {
            entryFromSource(entry.path, Buffer().apply { write(entry.content) })
        }
    }

    @Benchmark
    fun benchmarkCompression() {
        openZip(compressZipPath, Zip.Mode.Write).use { zip ->
            benchmarkEntries.forEach { entry ->
                zip.writeEntry(entry)
            }
        }
    }

    @Benchmark
    fun benchmarkExtraction() {
        openZip(extractZipPath, Zip.Mode.Read).use { zip ->
            benchmarkEntries.forEach { entry ->
                if (entry.content != null) zip.entry(entry.path) {
                    assertContentEquals(entry.content, readToBytes())
                }
            }
        }
    }

    private companion object {
        private fun Path(base: Path?, segment: String): Path = when (base) {
            null -> Path(segment)
            else -> kotlinx.io.files.Path(base, segment)
        }
    }
}

expect fun Zip.Companion.openNative(
    path: Path,
    mode: Zip.Mode = Zip.Mode.Read,
    level: Zip.CompressionLevel = Zip.CompressionLevel.Default
): Zip
