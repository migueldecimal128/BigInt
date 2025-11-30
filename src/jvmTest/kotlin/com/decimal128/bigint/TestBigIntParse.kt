package com.decimal128.bigint

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigInteger

class TestBigIntParse {
    val verbose = false

    class TC(val str: String, val isValid: Boolean = true) {
    }

    val tcs = arrayOf(
        TC("111111111_222222222_333333333_444444444_555555555_666666666_777777777"),
        TC("1_000_000_000"),
        TC("999_999_999"),
        TC("0xCAFE_BABE_DEAD_BEEF"),
        TC("1_", false),
        TC("0x0"),
        TC("0x_0", false),
        TC("0"),
        TC("1"),
        TC("-1"),
        TC("0x", false),
        TC("-00000000000000000099"),
        TC("-0_000_00000000000000_9_9"),
        TC("-0_000_00000000000000_9_9_", false),
        TC("-_00000000000000000099", false),
        TC("0xCAFE_BABE_DEAD_BEEF"),
        TC("1111111111_2222222222_3333333333_4444444444_5555555555" +
                "_6666666666_7777777777_8888888888_9999999999_0000000000"),
    )

    @Test
    fun testCases() {
        for (tc in tcs)
            test1(tc.str, tc.isValid)
    }

    fun test1(str: String, isValid: Boolean) {
        if (verbose)
            println("str:$str isValid:$isValid")
        try {
            val k = BigInt.from(str)
            assertTrue(isValid)
            val j = getBigInteger(str)

            val kStr = k.toString()
            val jStr = j.toString()
            if (verbose)
                println("kStr:$kStr jStr:$jStr")
            assertEquals(jStr, kStr)

            val sb = StringBuilder().append(str)
            val kCsq0 = BigInt.from(sb)
            assertEquals(k, kCsq0)

            sb.setLength(0)
            sb.append("<<").append(str).append('>')
            val kCsq1 = BigInt.from(sb, 2, str.length)
            assertEquals(k, kCsq1)

            val chars = str.toCharArray()
            val kChars0 = BigInt.from(chars)
            assertEquals(k, kChars0)

            val chars2 = CharArray(chars.size + 4)
            System.arraycopy(chars, 0, chars2, 2, chars.size)
            val kChars2 = BigInt.from(chars2, 2, chars.size)
            assertEquals(k, kChars2)

            val bytes = str.toByteArray()
            val kBytes0 = BigInt.fromAscii(bytes)
            assertEquals(k, kBytes0)

            val bytes2 = ByteArray(bytes.size + 4)
            System.arraycopy(bytes, 0, bytes2, 2, bytes.size)
            val kBytes2 = BigInt.fromAscii(bytes2, 2, bytes.size)
            assertEquals(k, kBytes2)


            if (str.contains("0x") || str.contains("0X"))
                testHex(str)
        } catch (e: IllegalArgumentException) {
            assertFalse(isValid)
        }

    }

    fun testHex(hexStr: String) {
        val hi0 = BigInt.fromHex(hexStr)
        val without0x = hexStr.replace("0x", "").replace("0X", "")
        val hi1 = BigInt.fromHex(without0x)
        assertEquals(hi0, hi1)
        val withoutUnderscores = without0x.replace("_", "")
        val hi2 = BigInt.fromHex(withoutUnderscores)

        assertEquals(hi0, hi1)
        assertEquals(hi0, hi2)

        val bi = BigInteger(withoutUnderscores, 16)
        val hiStr = hi0.toString()
        val biStr = bi.toString()
        assertEquals(biStr, hiStr)

        val sb = StringBuilder()
        sb.append(hexStr)
        val hi3 = BigInt.from(sb)
        assertEquals(hi0, hi3)

        sb.setLength(0)
        sb.append('[').append(hexStr).append(']')
        val hi4 = BigInt.from(sb, 1, sb.length - 2)
        assertEquals(hi0, hi4)

        val chars0 = hexStr.toCharArray()
        val hi5 = BigInt.from(chars0)
        assertEquals(hi0, hi5)

        val chars1 = CharArray(chars0.size + 20)
        System.arraycopy(chars0, 0, chars1, 10, chars0.size)
        val hi6 = BigInt.from(chars1, 10, chars1.size - 20)
        assertEquals(hi0, hi6)

        val bytes0 = hexStr.toByteArray()
        val hi7 = BigInt.fromAscii(bytes0)
        assertEquals(hi0, hi7)

        val bytes1 = ByteArray(bytes0.size + 200)
        System.arraycopy(bytes0, 0, bytes1, 100, bytes0.size)
        val hi8 = BigInt.fromAscii(bytes1, 100, bytes1.size - 200)
        assertEquals(hi0, hi8)

    }

    fun getBigInteger(str: String): BigInteger {
        val strNoUnderscores = str.replace("_", "")
        if (strNoUnderscores.startsWith("0x"))
            return BigInteger(strNoUnderscores.substring(2), 16)
        if (strNoUnderscores.startsWith("+0x") || strNoUnderscores.startsWith("-0x"))
            return BigInteger(strNoUnderscores.substring(3), 16)
        return BigInteger(strNoUnderscores)
    }
}
