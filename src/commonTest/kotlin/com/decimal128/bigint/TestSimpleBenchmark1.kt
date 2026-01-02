package com.decimal128.bigint

import com.decimal128.bigint.intrinsic.platformName
import kotlin.time.measureTime
import kotlin.test.Test

class TestSimpleBenchmark1 {

    var foo = BigInt.ZERO
    val a = "987654321098765432109876543210".toBigInt()
    val b = "123456789012345678901234567890".toBigInt()
    val mbiFoo = MutableBigInt()


    @Test
    fun runBenchmark() {
        benchmarkOperations()
        benchmarkOperationsMbi()
    }

    fun benchmarkOperations() {
        // Warm up
        repeat(10000) {
            runOperations()
        }

        foo = 0.toBigInt()
        // Actual benchmark
        val time = measureTime {
            repeat(1000) {
                runOperations()
            }
        }

        println("Platform: ${platformName()}")
        println("Time for 1000 iterations: $time foo:$foo")
    }

    private fun runOperations() {
        // Your actual BigInt operations

        val sum = a + b
        foo += sum
        val diff = a - b
        foo += diff
        val product = a * b
        foo += product

        foo += b.sqr()

        val quotient = product / a
        foo += quotient
    }

    fun benchmarkOperationsMbi() {
        // Warm up
        repeat(10000) {
            runOperationsMbi()
        }

        mbiFoo.setZero()
        // Actual benchmark
        val time = measureTime {
            repeat(1000) {
                runOperationsMbi()
            }
        }

        println("Platform: ${platformName()}")
        println("Time for 1000 mutable iterations: $time foo:$foo")
    }

    val mbiTmp = MutableBigInt()

    private fun runOperationsMbi() {
        // Your actual BigInt operations
        val a = "987654321098765432109876543210".toBigInt()
        val b = "123456789012345678901234567890".toBigInt()

        mbiTmp.setAdd(a, b)
        mbiFoo += mbiTmp

        mbiTmp.setSub(a, b)
        mbiFoo += mbiTmp

        mbiTmp.setMul(a, b)
        mbiFoo += mbiTmp

        mbiFoo.addSquareOf(b)

        mbiTmp.setDiv(a, b)
        mbiFoo += mbiTmp
    }
}