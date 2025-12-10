package com.decimal128.bigint

import com.decimal128.bigint.BigIntExtensions.EQ
import com.decimal128.bigint.BigIntExtensions.NE
import com.decimal128.bigint.BigIntExtensions.toBigInteger
import com.decimal128.bigint.BigIntExtensions.toBigInt
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.math.BigInteger.*
import java.util.*

class TestBigInt {
    val verbose = false

    val random = Random()

    fun randBi(maxBitLen: Int = 500) : BigInteger {
        val bitLength = random.nextInt(0, maxBitLen)
        val bi = BigInteger(bitLength, random)
        return if (random.nextBoolean()) bi.negate() else bi
    }

    @Test
    fun testProblemChild() {
        testSqr(BigInteger("12345678901234567890123456789012345678901234567890"))
        testBigEndianByteArray(BigInteger("-11593"))
        testBigEndianByteArray(BigInteger("851028216441256846883624947"))
        testBigEndianByteArray(BigInteger("851028216441256846883624947"))
        testBigEndianByteArray(BigInteger("80", 16))
        testBigEndianByteArray(BigInteger("AA", 16))
        testBigEndianByteArray(BigInteger("-851028216441256846883624947"))
        testBigEndianByteArray(BigInteger("-1"))
        testBigEndianByteArray(BigInteger("-1000000000000000"))
        testHexStr(BigInteger("123"))
        testHexStr(BigInteger("12345678901234567890123456789012345678901234567890"))
        testWithIndexedBitMask(3, 0)
        testSqr(BigInteger("99999999999"))
        testAdd(BigInteger.ZERO, BigInteger.ZERO)
        testAdd(BigInteger("-4418548"), BigInteger.ZERO)
        testSub(BigInteger("-4418548"), BigInteger.ZERO)
        testRoundTripShift(BigInteger("-485"))
        testRoundTripShift(BigInteger("20000000000000000000000000"))
        testRoundTripStr("-999999999")
        testRoundTripStr("-1234567890")
        testRoundTripStr("-9999999999")
        testRoundTripStr("-1")
        testRoundTripBi(BigInteger("-1"))
        testDiv(BigInteger("1234567890123456789012"), BigInteger("1234567890123456789013"))
        testMod(ONE, ONE)
        testDiv(ONE.shiftLeft(32), ONE.shiftLeft(32))
        testDiv(ONE, ONE)
        testDiv(TWO, ONE)
        testDiv(TEN, ONE)
        testMod(TEN, ONE)
    }

    @Test
    fun testProblemChildHex() {
        testHexStr(BigInteger("1234ABCD", 16))
    }

    @Test
    fun testRoundTrip() {
        for (i in 0..10000) {
            val bi = randBi()
            testBitLen(bi)
            testRoundTripBi(bi)
            testRoundTripStr(bi.toString())
            testRoundTripShift(bi)
            testBigEndianByteArray(bi)
            testHexStr(bi)
        }
    }


    fun testRoundTripBi(bi: BigInteger) {
        if (verbose)
            println("testRoundTripBi: $bi")
        val hi = bi.toBigInt()
        val bi2 = hi.toBigInteger()
        assertEquals(bi, bi2)
    }

    fun testRoundTripStr(str: String) {
        if (verbose)
            println("testRoundTripStr($str)")
        val hi = BigInt.from(str)
        val str2 = hi.toString()
        assertEquals(str, str2)
    }

    fun testHexStr(bi: BigInteger) {
        val biSign = bi.signum() < 0
        val biAbs = bi.abs()
        val biHexStr = (if (biSign) "-0x" else "0x") + biAbs.toString(16).uppercase()
        if (verbose)
            println("testHexStr($biHexStr)")
        val hi = bi.toBigInt()
        val hiHexStr = hi.toHexString()
        assertEquals(biHexStr, hiHexStr)

        val roundTrip1 = BigInt.fromHex(hiHexStr)
        assertEquals(hi, roundTrip1)

        val roundTrip2 = BigInt.from(hiHexStr)
        assertEquals(hi, roundTrip2)

        val roundTrip3 = BigInt.from(hiHexStr.uppercase())
        assertEquals(hi, roundTrip3)

        val roundTrip4 = BigInt.from(hiHexStr.lowercase())
        assertEquals(hi, roundTrip4)

        var with_ = hiHexStr
        repeat (5) {
            val i = random.nextInt(with_.length - 1)
            if (i >= 4)
                with_ = with_.substring(0, i) + '_' + with_.substring(i)
        }
        if (verbose)
            println("with_:$with_")
        val roundTrip5 = BigInt.from(with_)
        assertEquals(hi, roundTrip5)

    }

    fun testRoundTripShift(bi: BigInteger) {
        val shift = 1 // random.nextInt(200)
        if (verbose)
            println("testRoundTripShift($bi) shift:$shift")
        val hi = bi.toBigInt()

        val biLeft = bi shl shift
        val hiLeft = hi shl shift
        if (verbose)
            println(" biLeft:$biLeft hiLeft:$hiLeft")
        assert(hiLeft.EQ(biLeft))

        val hiRight = hiLeft shr shift
        if (verbose)
            println(" hiRight:$hiRight")
        assert(hiRight.EQ(bi))

        val hiRight2 = hiRight shr shift
        val biRight2 = bi shr shift
        if (verbose)
            println(" hiRight2:$hiRight2 biRight2:$biRight2")
        assert(hiRight2.EQ(biRight2))
    }

    fun testBitLen(bi: BigInteger) {
        val hi = bi.toBigInt()
        val hiBitLen = hi.magnitudeBitLen()
        val biBitLen = bi.bitLength()
        val hiBitLenBI = hi.bitLengthBigIntegerStyle()
        if (verbose)
            println("testBitLen:$bi biBitLen:$biBitLen hiBitLenBI:$hiBitLen")
        assertEquals(biBitLen, hiBitLenBI)
    }

    @Test
    fun testArithmetic() {
        for (i in 0..<10000) {
            val biA = randBi()
            val absA = biA.abs()
            testAdd(biA, biA)
            testSub(biA, biA)
            testMul(biA, biA)
            testDiv(biA, biA)
            testMod(biA, biA)
            testAnd(absA, absA)
            testOr(absA, absA)
            testXor(absA, absA)
            testSqr(biA)
            testPow(biA)

            val biB = randBi()
            val absB = biB.abs()
            testAdd(biA, biB)
            testSub(biA, biB)
            testMul(biA, biB)
            testDiv(biA, biB)
            testMod(biA, biB)
            testAnd(absA, absB)
            testOr(absA, absB)
            testXor(absA, absB)
            testSqr(biB)
            testPow(biB)

            val biC = biA.add(ONE)
            val absC = biC.abs()
            testAdd(biA, biC)
            testSub(biA, biC)
            testMul(biA, biC)
            testDiv(biA, biC)
            testMod(biA, biC)
            testAnd(absA, absC)
            testOr(absA, absC)
            testXor(absA, absC)
            testSqr(biC)
            testPow(biC)

        }
    }

    fun testAdd(biA: BigInteger, biB: BigInteger) {
        val hiA = biA.toBigInt()
        val hiB = biB.toBigInt()
        if (verbose)
            println("biA:$biA hiA:$hiA   biB:$biB hiB:$hiB")

        val hiSum = hiA + hiB
        val biSum = biA + biB
        if (! hiSum.EQ(biSum))
            println("biSum:$biSum hiSum:$hiSum")

        assert(hiSum.EQ(biSum))
        if (biSum.signum() == 0)
            assert(hiSum === BigInt.ZERO)
    }

    fun testSub(biA: BigInteger, biB: BigInteger) {

        val hiA = biA.toBigInt()
        val hiB = biB.toBigInt()
        if (verbose)
            println("biA:$biA hiA:$hiA   biB:$biB hiB:$hiB")
        val hiDiff = hiA - hiB
        val biDiff = biA - biB
        if (! hiDiff.EQ(biDiff))
            println("biDiff:$biDiff hiDiff:$hiDiff")
        assert(hiDiff.EQ(biDiff))
        if (biDiff.signum() == 0)
            assert(hiDiff === BigInt.ZERO)
    }

    fun testMul(biA: BigInteger, biB: BigInteger) {
        val hiA = biA.toBigInt()
        val hiB = biB.toBigInt()
        val hiProd = hiA * hiB
        val biProd = biA.multiply(biB)
        assert(hiProd.EQ(biProd))
        if (biProd.signum() == 0)
            assert(hiProd === BigInt.ZERO)
    }

    fun testDiv(biA: BigInteger, biB: BigInteger) {
        if (verbose)
            println("testDiv: $biA / $biB")
        if (biB.signum() == 0)
            return
        val hiA = biA.toBigInt()
        val hiB = biB.toBigInt()
        val hiQuot = hiA / hiB

        val biQuot = biA.divide(biB)
        assert(hiQuot.EQ(biQuot))
        if (biQuot.signum() == 0)
            assert(hiQuot === BigInt.ZERO)
    }

    fun testMod(biA: BigInteger, biB: BigInteger) {
        if (biB.signum() == 0)
            return
        val hiA = biA.toBigInt()
        val hiB = biB.toBigInt()
        val hiRem = hiA % hiB

        val biRem = biA.remainder(biB)
        assert(hiRem.EQ(biRem))
        if (biRem.signum() == 0)
            assert(hiRem === BigInt.ZERO)
    }

    fun testSqr(bi: BigInteger) {
        val hi = bi.toBigInt()
        val biSqr = bi * bi
        val hiSqr = hi.sqr()
        val hiSqrMul = hi * hi
        if (hiSqr NE biSqr)
            println("hi:$hi hiSqr:$hiSqr biSqr:$biSqr")
        assert(hiSqr EQ biSqr)
        if (hiSqr != hiSqrMul)
            println("hi:$hi hiSqr:$hiSqr hiSqr2:$hiSqrMul")
        assertEquals(hiSqr, hiSqrMul)
    }

    fun testPow(bi: BigInteger) {
        val hi = bi.toBigInt()
        val n = random.nextInt(10)
        val biPow = bi.pow(n)
        val hiPow = hi.pow(n)
        assertEquals(hiPow.toString(), biPow.toString())
    }

    fun testBigEndianByteArray(bi: BigInteger) {
        val hi = bi.toBigInt()
        val biBytes = bi.toByteArray()
        val hiBytes = hi.toTwosComplementBigEndianByteArray()

        val biHex = java.util.HexFormat.ofDelimiter(" ").withUpperCase().formatHex(biBytes)
        val hiHex = java.util.HexFormat.ofDelimiter(" ").withUpperCase().formatHex(hiBytes)

        if (verbose)
            println("biHex:$biHex hiHex:$hiHex")
        assertArrayEquals(biBytes, hiBytes)

        val hi2 = BigInt.fromTwosComplementBigEndianBytes(hiBytes)
        if (verbose)
            println("hi:$hi hi2:$hi2")
        assertEquals(hi, hi2)

        val bytesPadded = ByteArray(biBytes.size + 4)
        System.arraycopy(biBytes, 0, bytesPadded, 2, biBytes.size)
        val signPrefix = (biBytes[0].toInt() shr 7).toByte()
        bytesPadded[1] = signPrefix

        val hi3 = BigInt.fromTwosComplementBigEndianBytes(bytesPadded, 1, 1 + biBytes.size)
        assertEquals(hi, hi3)

        val bytesReversed = reverseBytes(biBytes)
        val hi4 = BigInt.fromBinaryBytes(isTwosComplement = true, isBigEndian = false, bytesReversed)
        assertEquals(hi, hi4)


        val bytesPaddedReversed = reverseBytes(bytesPadded)
        val hi5 = BigInt.fromBinaryBytes(isTwosComplement = true, isBigEndian = false,
                                          bytesPaddedReversed, 2, biBytes.size)
        assertEquals(hi, hi5)

        val hi6 = BigInt.fromBinaryBytes(isTwosComplement = true, isBigEndian = false,
                                          bytesPaddedReversed, 2, biBytes.size + 1)
        assertEquals(hi, hi6)


    }

    fun reverseBytes(bytes: ByteArray): ByteArray {
        val reverse = ByteArray(bytes.size)
        for (i in bytes.indices)
            reverse[reverse.size - i - 1] = bytes[i]
        return reverse
    }

    fun testAnd(biA: BigInteger, biB: BigInteger) {
        if (verbose)
            println("testAnd(biA:$biA biB:$biB)")
        val hiA = biA.toBigInt()
        val hiB = biB.toBigInt()

        val biAnd = biA and biB
        val hiAnd = hiA and hiB
        if (hiAnd NE biAnd)
            println("biAnd:$biAnd hiAnd:$hiAnd")
        assert(hiAnd EQ biAnd)
    }

    fun testOr(biA: BigInteger, biB: BigInteger) {
        if (verbose)
            println("testOr(biA:$biA biB:$biB)")
        val hiA = biA.toBigInt()
        val hiB = biB.toBigInt()

        val biOr = biA or biB
        val hiOr = hiA or hiB

        if (hiOr NE biOr)
            println("biOr:$biOr hiOr:$hiOr")
        assert(hiOr EQ biOr)
    }

    @Test
    fun testXor() {
        for (i in 0..<100) {
            val biA = randBi(200).abs()
            val absA = biA.abs()
            val biB = randBi(200).abs()
            val absB = biB.abs()

            if (verbose)
                println("biaA:$biA biB:$biB")

            testXor(absA, absB)
        }
    }

    fun testXor(biA: BigInteger, biB: BigInteger) {
        if (verbose)
            println("testXor(biA:$biA biB:$biB)")
        val hiA = biA.toBigInt()
        val hiB = biB.toBigInt()

        val biXor = biA xor biB
        val hiXor = hiA xor hiB

        if (hiXor NE biXor)
            println("biXor:$biXor hiXor:$hiXor")
        assert(hiXor EQ biXor)
    }

    @Test
    fun testWithBitMask() {
        for (i in 0..<10000) {
            val bitIndex = random.nextInt(5)
            val bitWidth = random.nextInt(1)
            testWithIndexedBitMask(bitIndex, bitWidth)
            testWithSetBit(bitIndex)
            testWithBitMask(bitWidth)
        }
    }

    fun testWithIndexedBitMask(bitIndex: Int, bitWidth: Int) {
        if (verbose)
            println("bitIndex:$bitIndex bitWidth:$bitWidth")
        val bi = ((BigInteger.ONE shl bitWidth) - BigInteger.ONE) shl bitIndex
        val hi1 = ((BigInt.ONE shl bitWidth) - 1) shl bitIndex
        val hi2 = BigInt.withBitMask(bitWidth, bitIndex)
        if (hi1 NE bi) {
            println("bitIndex:$bitIndex bitWidth:$bitWidth")
            println("bi:$bi hi1:$hi1")
        }
        assert(hi1 EQ bi)
        if (hi2 NE bi) {
            println("bitIndex:$bitIndex bitWidth:$bitWidth")
            println("bi:$bi hi2:$hi2")
        }
        assert(hi2 EQ bi)
    }

    fun testWithSetBit(bitIndex: Int) {
        if (verbose)
            println("testWithSetBit(bitIndex:$bitIndex)")
        val bi = BigInteger.ONE shl bitIndex
        val hi1 = BigInt.ONE shl bitIndex
        val hi2 = BigInt.withSetBit(bitIndex)
        if (hi1 NE bi) {
            println("bitIndex:$bitIndex")
            println("bi:$bi hi1:$hi1")
        }
        assert(hi1 EQ bi)
        if (hi2 NE bi) {
            println("bitIndex:$bitIndex")
            println("bi:$bi hi2:$hi2")
        }
        assert(hi2 EQ bi)
    }

    fun testWithBitMask(bitWidth: Int) {
        if (verbose)
            println("bitWidth:$bitWidth")
        val bi = (BigInteger.ONE shl bitWidth) - BigInteger.ONE
        val hi1 = (BigInt.ONE shl bitWidth) - 1
        val hi2 = BigInt.withBitMask(bitWidth)
        if (hi1 NE bi) {
            println("bitWidth:$bitWidth")
            println("bi:$bi hi1:$hi1")
        }
        assert(hi1 EQ bi)
        if (hi2 NE bi) {
            println("bitWidth:$bitWidth")
            println("bi:$bi hi2:$hi2")
        }
        assert(hi2 EQ bi)
    }

}
