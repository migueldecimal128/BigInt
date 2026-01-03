
import kotlin.test.Test
import kotlin.time.measureTime

class IntArrayBenchmark {

    @Test
    fun benchmarkIntArrayOperations() {
        val size = 500
        var a = IntArray(size) { it }
        var b = IntArray(size) { it * 2 }
        var result = IntArray(size)

        var checksum1 = 0L // only serves to prevent code elimination
        run {
            // Warmup
            repeat(50000) {
                simpleArrayOps(a, b, result)
                checksum1 += result.sum().toLong()
                if ((it and 1) == 0) {
                    val swap = a; a = result; result = swap
                } else {
                    val swap = b; b = result; result = swap
                }
            }
        }

        var checksum2 = 0L // only serves to prevent code elimination
        // Benchmark
        val time = measureTime {
            repeat(50000) {
                simpleArrayOps(a, b, result)
                checksum2 += result.sum().toLong()
                if ((it and 1) == 0) {
                    val swap = a; a = result; result = swap
                } else {
                    val swap = b; b = result; result = swap
                }
            }
        }

        println("Time: $time")
        println("Checksums: $checksum1 $checksum2") // Prevent optimization
    }

    private fun simpleArrayOps(a: IntArray, b: IntArray, result: IntArray) {
        for (i in a.indices) {
            result[i] = a[i] + b[i]
        }
    }
}