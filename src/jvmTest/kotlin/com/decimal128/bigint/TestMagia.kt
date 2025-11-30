package com.decimal128.bigint

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.util.Random

class TestMagia {

    val verbose = false

    val random = Random()

    fun randBi() : BigInteger {
        val bitLength = random.nextInt(0, 1024)
        val bi = BigInteger(bitLength, random)
        return bi
    }

    @Test
    fun testProblemChild() {
        testDiv(BigInteger.ONE.shiftLeft(32), BigInteger.ONE.shiftLeft(32))
        testDiv(BigInteger.ONE, BigInteger.ONE)
        testDiv(BigInteger.TWO, BigInteger.ONE)
        testDiv(BigInteger.TEN, BigInteger.ONE)
    }

    @Test
    fun testRoundTrip() {
        for (i in 0..1000) {
            val bi = randBi()
            testBitLen(bi)
            testRoundTripBi(bi)
            testRoundTripStr(bi.toString())
            testRoundTripShift(bi)
        }
    }


    fun testRoundTripBi(bi: BigInteger) {
        val car = MagiaTransducer.magiaFromBi(bi)
        val bi2 = MagiaTransducer.magiaToBi(car)
        Assertions.assertEquals(bi, bi2)
    }

    fun testRoundTripStr(str: String) {

        if (verbose)
            println("testRoundTripStr($str)")
        val car = MagiaTransducer.magiaFromString(str)
        val str2 = MagiaTransducer.magiaToString(car)
        Assertions.assertEquals(str, str2)

        val car3 = Magia.from(str)
        assert(Magia.EQ(car, car3))
        val str3 = Magia.toString(car3)
        Assertions.assertEquals(str, str3)
    }

    fun testRoundTripShift(bi: BigInteger) {
        val shift = random.nextInt(100)
        val magia = MagiaTransducer.magiaFromBi(bi)

        val biLeft = bi.shiftLeft(shift)
        val carLeft = Magia.newShiftLeft(magia, shift)
        assert(MagiaTransducer.EQ(carLeft, biLeft))

        Magia.mutateShiftRight(carLeft, carLeft.size, shift)
        assert(MagiaTransducer.EQ(carLeft, bi))

        val biRight = bi.shiftRight(shift)
        Magia.mutateShiftRight(magia, magia.size, shift)
        assert(MagiaTransducer.EQ(magia, biRight))
    }

    fun testBitLen(bi: BigInteger) {
        val car = MagiaTransducer.magiaFromBi(bi)
        val bitLen = Magia.bitLen(car)
        Assertions.assertEquals(bi.bitLength(), bitLen)
    }

    @Test
    fun testArithmetic() {
        for (i in 0..<1000) {
            val biA = randBi()
            testAdd(biA, biA)
            testSub(biA, biA)
            testMul(biA, biA)
            testDiv(biA, biA)

            val biB = randBi()
            testAdd(biA, biB)
            testSub(biA, biB)
            testMul(biA, biB)
            testDiv(biA, biB)

            val biC = biA.add(BigInteger.ONE)
            testAdd(biA, biC)
            testSub(biA, biC)
            testMul(biA, biC)
            testDiv(biA, biC)

        }
    }

    fun testAdd(biA: BigInteger, biB: BigInteger) {
        val carA = MagiaTransducer.magiaFromBi(biA)
        val carB = MagiaTransducer.magiaFromBi(biB)
        val carSum = Magia.newAdd(carA, carB)

        val biSum = biA.add(biB)

        assert(MagiaTransducer.EQ(carSum, biSum))
    }

    fun testSub(biA: BigInteger, biB: BigInteger) {
        var biX = biA
        var biY = biB
        if (biA < biB) {
            biX = biB
            biY = biA
        }
        val carX = MagiaTransducer.magiaFromBi(biX)
        val carY = MagiaTransducer.magiaFromBi(biY)
        val magiaDiff = Magia.newSub(carX, carY)

        val biDiff = biX.subtract(biY)

        assert(MagiaTransducer.EQ(magiaDiff, biDiff))
    }

    fun testMul(biA: BigInteger, biB: BigInteger) {
        val carA = MagiaTransducer.magiaFromBi(biA)
        val carB = MagiaTransducer.magiaFromBi(biB)
        val carProd = Magia.newMul(carA, carB)

        val biProd = biA.multiply(biB)

        assert(MagiaTransducer.EQ(carProd, biProd))
    }

    fun testDiv(biA: BigInteger, biB: BigInteger) {
        if (biB.signum() == 0)
            return
        val carA = MagiaTransducer.magiaFromBi(biA)
        val carB = MagiaTransducer.magiaFromBi(biB)
        val carQuot = Magia.newDiv(carA, carB)

        val biQuot = biA.divide(biB)

        assert(MagiaTransducer.EQ(carQuot, biQuot))
    }

}