package com.decimal128.hugeint

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigInteger

class TestHugeIntParse {
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
            val hi = HugeInt.from(str)
            assertTrue(isValid)
            val bi = getBigInteger(str)

            val hiStr = hi.toString()
            val biStr = bi.toString()
            if (verbose)
                println("hiStr:$hiStr biStr:$biStr")
            assertEquals(biStr, hiStr)

            val sb = StringBuilder().append(str)
            val hiCsq0 = HugeInt.from(sb)
            assertEquals(hi, hiCsq0)

            sb.setLength(0)
            sb.append("<<").append(str).append('>')
            val hiCsq1 = HugeInt.from(sb, 2, str.length)
            assertEquals(hi, hiCsq1)

            val chars = str.toCharArray()
            val hiChars0 = HugeInt.from(chars)
            assertEquals(hi, hiChars0)

            val chars2 = CharArray(chars.size + 4)
            System.arraycopy(chars, 0, chars2, 2, chars.size)
            val hiChars2 = HugeInt.from(chars2, 2, chars.size)
            assertEquals(hi, hiChars2)

            val bytes = str.toByteArray()
            val hiBytes0 = HugeInt.fromAscii(bytes)
            assertEquals(hi, hiBytes0)

            val bytes2 = ByteArray(bytes.size + 4)
            System.arraycopy(bytes, 0, bytes2, 2, bytes.size)
            val hiBytes2 = HugeInt.fromAscii(bytes2, 2, bytes.size)
            assertEquals(hi, hiBytes2)


            if (str.contains("0x") || str.contains("0X"))
                testHex(str)
        } catch (e: IllegalArgumentException) {
            assertFalse(isValid)
        }

    }

    fun testHex(hexStr: String) {
        val hi0 = HugeInt.fromHex(hexStr)
        val without0x = hexStr.replace("0x", "").replace("0X", "")
        val hi1 = HugeInt.fromHex(without0x)
        assertEquals(hi0, hi1)
        val withoutUnderscores = without0x.replace("_", "")
        val hi2 = HugeInt.fromHex(withoutUnderscores)

        assertEquals(hi0, hi1)
        assertEquals(hi0, hi2)

        val bi = BigInteger(withoutUnderscores, 16)
        val hiStr = hi0.toString()
        val biStr = bi.toString()
        assertEquals(biStr, hiStr)

        val sb = StringBuilder()
        sb.append(hexStr)
        val hi3 = HugeInt.from(sb)
        assertEquals(hi0, hi3)

        sb.setLength(0)
        sb.append('[').append(hexStr).append(']')
        val hi4 = HugeInt.from(sb, 1, sb.length - 2)
        assertEquals(hi0, hi4)

        val chars0 = hexStr.toCharArray()
        val hi5 = HugeInt.from(chars0)
        assertEquals(hi0, hi5)

        val chars1 = CharArray(chars0.size + 20)
        System.arraycopy(chars0, 0, chars1, 10, chars0.size)
        val hi6 = HugeInt.from(chars1, 10, chars1.size - 20)
        assertEquals(hi0, hi6)

        val bytes0 = hexStr.toByteArray()
        val hi7 = HugeInt.fromAscii(bytes0)
        assertEquals(hi0, hi7)

        val bytes1 = ByteArray(bytes0.size + 200)
        System.arraycopy(bytes0, 0, bytes1, 100, bytes0.size)
        val hi8 = HugeInt.fromAscii(bytes1, 100, bytes1.size - 200)
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
