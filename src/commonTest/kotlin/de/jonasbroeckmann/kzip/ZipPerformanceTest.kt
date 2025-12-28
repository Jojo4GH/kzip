package de.jonasbroeckmann.kzip

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.measureTime

class ZipPerformanceTest {

    private val benchmarkDir = Path("benchmark-data")
    private val zipNative = Path("benchmark-native.zip")
    private val zipKotlin = Path("benchmark-kotlin.zip")
    private val extractNative = Path("extract-native")
    private val extractKotlin = Path("extract-kotlin")

    @BeforeTest
    fun setup() {
        cleanup()
        SystemFileSystem.createDirectories(benchmarkDir)
    }

    @AfterTest
    fun teardown() {
        cleanup()
    }

    private fun cleanup() {
        if (SystemFileSystem.exists(benchmarkDir)) deleteRecursively(benchmarkDir)
        if (SystemFileSystem.exists(zipNative)) SystemFileSystem.delete(zipNative)
        if (SystemFileSystem.exists(zipKotlin)) SystemFileSystem.delete(zipKotlin)
        if (SystemFileSystem.exists(extractNative)) deleteRecursively(extractNative)
        if (SystemFileSystem.exists(extractKotlin)) deleteRecursively(extractKotlin)
    }

    private fun deleteRecursively(path: Path) {
        val metadata = SystemFileSystem.metadataOrNull(path) ?: return
        if (metadata.isDirectory) {
            SystemFileSystem.list(path).forEach { deleteRecursively(it) }
        }
        SystemFileSystem.delete(path)
    }

    private fun generateTestData(numFiles: Int, maxDepth: Int, fileSize: Int) {
        val random = Random(42)
        for (i in 1..numFiles) {
            val depth = random.nextInt(maxDepth + 1)
            var currentDir = benchmarkDir
            for (d in 0 until depth) {
                currentDir = Path(currentDir, "dir_$d")
                if (!SystemFileSystem.exists(currentDir)) {
                    SystemFileSystem.createDirectories(currentDir)
                }
            }
            val file = Path(currentDir, "file_$i.txt")
            SystemFileSystem.sink(file).buffered().use { sink ->
                val bytes = ByteArray(fileSize)
                random.nextBytes(bytes)
                sink.write(bytes)
            }
        }
    }

    @Test
    fun runBenchmarks() {
        val configs = listOf(
            BenchmarkConfig(numFiles = 100, maxDepth = 2, fileSize = 10 * 1024), // Medium
            BenchmarkConfig(numFiles = 500, maxDepth = 5, fileSize = 50 * 1024), // Heavy
            BenchmarkConfig(numFiles = 100, maxDepth = 15, fileSize = 500 * 1024) // Deep and large
        )

        for (config in configs) {
            println("\n--- Benchmarking: $config ---")
            setup() // Clear and prepare
            generateTestData(config.numFiles, config.maxDepth, config.fileSize)

            // 1. Benchmark Compression
            val timeNativeCompress = measureTime {
                Zip.open(zipNative, Zip.Mode.Write).use { it.compressFrom(benchmarkDir) }
            }
            println("Native Compression: $timeNativeCompress")

            val timeKotlinCompress = measureTime {
                Zip.openKotlin(zipKotlin, Zip.Mode.Write).use { it.compressFrom(benchmarkDir) }
            }
            println("Kotlin Compression: $timeKotlinCompress")

            // 2. Benchmark Extraction
            SystemFileSystem.createDirectories(extractNative)
            val timeNativeExtract = measureTime {
                Zip.open(zipNative, Zip.Mode.Read).use { it.extractTo(extractNative) }
            }
            println("Native Extraction:  $timeNativeExtract")

            SystemFileSystem.createDirectories(extractKotlin)
            val timeKotlinExtract = measureTime {
                Zip.openKotlin(zipKotlin, Zip.Mode.Read).use { it.extractTo(extractKotlin) }
            }
            println("Kotlin Extraction:  $timeKotlinExtract")

            // Verification (optional but good for benchmarks to ensure correctness)
            Zip.open(zipNative, Zip.Mode.Read).use { nativeZip ->
                Zip.openKotlin(zipKotlin, Zip.Mode.Read).use { kotlinZip ->
                    assertEquals(nativeZip.numberOfEntries, kotlinZip.numberOfEntries, "Number of entries should match")
                }
            }
        }
    }

    data class BenchmarkConfig(val numFiles: Int, val maxDepth: Int, val fileSize: Int) {
        override fun toString(): String = "files=$numFiles, depth=$maxDepth, size=${fileSize / 1024}KB"
    }
}
