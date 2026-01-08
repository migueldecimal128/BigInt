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
        assert(EQ(magia, normLen(magia), magia3, normLen(magia3)))
        val str3 = BigIntParsePrint.toString(magia3)
        Assertions.assertEquals(str, str3)
    }

    fun testRoundTripShift(jbi: BigInteger) {
        val shift = random.nextInt(100)
        val magia = MagiaTransducer.magiaFromBi(jbi)
        val magiaBitLen = bitLen(magia)

        val jbiLeft = jbi.shiftLeft(shift)
        val magiaShl = newWithBitLen(magiaBitLen + shift)
        setShiftLeft(magiaShl, magia, normLen(magia), shift)
        assert(MagiaTransducer.EQ(magiaShl, jbiLeft))

        //mutateShiftRight(magiaShl, normLen(magiaShl), shift)
        //assert(MagiaTransducer.EQ(magiaShl, jbi))

        //val jbiRight = jbi.shiftRight(shift)
        //mutateShiftRight(magia, normLen(magia), shift)
        //assert(MagiaTransducer.EQ(magia, jbiRight))
    }

    fun testBitLen(jbi: BigInteger) {
        val magia = MagiaTransducer.magiaFromBi(jbi)
        val bitLen = bitLen(magia)
        Assertions.assertEquals(jbi.bitLength(), bitLen)
    }

}