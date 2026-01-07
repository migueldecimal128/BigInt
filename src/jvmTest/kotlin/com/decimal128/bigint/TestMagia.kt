package com.decimal128.bigint

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.util.Random

class TestMagia {

    val verbose = false

    val random = Random()

    fun randJbi(maxBitLen: Int = 1024) : BigInteger {
        val bitLength = random.nextInt(0, maxBitLen)
        val jbi = BigInteger(bitLength, random)
        return jbi
    }

    @Test
    fun testProblemChild() {
        testDiv(BigInteger.ONE.shiftLeft(32), BigInteger.ONE.shiftLeft(32))
        testDiv(BigInteger.ONE, BigInteger.ONE)
        testDiv(BigInteger.TWO, BigInteger.ONE)
        testDiv(BigInteger.TEN, BigInteger.ONE)
    }

    @Test
    fun testProblem2() {
        val jbi = randJbi(1000)
        testRoundTripShift(jbi)
    }

    @Test
    fun testRoundTrip() {
        for (i in 0..1000) {
            val jbi = randJbi()
            testBitLen(jbi)
            testRoundTripJbi(jbi)
            testRoundTripStr(jbi.toString())
            testRoundTripShift(jbi)
        }
    }


    fun testRoundTripJbi(jbi: BigInteger) {
        val car = MagiaTransducer.magiaFromBi(jbi)
        val jbi2 = MagiaTransducer.magiaToBi(car)
        Assertions.assertEquals(jbi, jbi2)
    }

    fun testRoundTripStr(str: String) {

        if (verbose)
            println("testRoundTripStr($str)")
        val magia = MagiaTransducer.magiaFromString(str)
        val str2 = MagiaTransducer.magiaToString(magia)
        Assertions.assertEquals(str, str2)

        val magia3 = BigIntParsePrint.from(str)
        assert(Mago.EQ(magia, Mago.normLen(magia), magia3, Mago.normLen(magia3)))
        val str3 = BigIntParsePrint.toString(magia3)
        Assertions.assertEquals(str, str3)
    }

    fun testRoundTripShift(jbi: BigInteger) {
        val shift = random.nextInt(100)
        val magia = MagiaTransducer.magiaFromBi(jbi)
        val magiaNormLen = Mago.normLen(magia)

        val jbiLeft = jbi.shiftLeft(shift)
        val magiaShl = Mago.newShiftLeft(magia, magiaNormLen, shift)
        assert(MagiaTransducer.EQ(magiaShl, jbiLeft))

        //Mago.mutateShiftRight(magiaShl, Mago.normLen(magiaShl), shift)
        //assert(MagiaTransducer.EQ(magiaShl, jbi))

        //val jbiRight = jbi.shiftRight(shift)
        //Mago.mutateShiftRight(magia, Mago.normLen(magia), shift)
        //assert(MagiaTransducer.EQ(magia, jbiRight))
    }

    fun testBitLen(jbi: BigInteger) {
        val magia = MagiaTransducer.magiaFromBi(jbi)
        val bitLen = Mago.bitLen(magia)
        Assertions.assertEquals(jbi.bitLength(), bitLen)
    }

    @Test
    fun testArithmetic() {
        for (i in 0..<1000) {
            val jjbiA = randJbi()
            testDiv(jjbiA, jjbiA)

            val jjbiB = randJbi()
            testDiv(jjbiA, jjbiB)

            val jbiC = jjbiA.add(BigInteger.ONE)
            testDiv(jjbiA, jbiC)

        }
    }

    fun testDiv(jbiA: BigInteger, jbiB: BigInteger) {
        if (jbiB.signum() == 0)
            return
        val magiaA = MagiaTransducer.magiaFromBi(jbiA)
        val magiaB = MagiaTransducer.magiaFromBi(jbiB)
        val magiaQuot = Mago.newDiv(magiaA, Mago.normLen(magiaA), magiaB, Mago.normLen(magiaB))

        val jbiQuot = jbiA.divide(jbiB)

        assert(MagiaTransducer.EQ(magiaQuot, jbiQuot))
    }

}