package com.decimal128.bigint

import kotlin.test.Test
import kotlin.test.assertEquals

class TestPrintParse {

    val verbose = false

    data class ParseCase(val raw: String, val expected: String = raw)

    val parseCases = arrayOf(
        ParseCase("0x10", "16"),
        ParseCase("-0x20", "-32"),
        ParseCase("123_456", "123456"),
    )

    @Test
    fun testParseCases() {
        for (tc in parseCases) {
            val observed = tc.raw.toBigInt().toString()
            assertEquals(tc.expected, observed)
        }
    }

    val hexCases = arrayOf(
        ParseCase("0x10"),
        ParseCase("10", "0x10"),
        ParseCase("-10", "-0x10"),
        ParseCase("face", "0xFACE"),
        ParseCase("DEAD_beef_CAFE_babe", "0xDEADBEEFCAFEBABE"),
    )

    @Test
    fun testHexCases() {
        for (tc in hexCases) {
            val observed = BigInt.fromHex(tc.raw).toHexString()
            assertEquals(tc.expected, observed)
        }
    }

    val hexFormatUpper = HexFormat.UpperCase
    val hexFormatLower = HexFormat { upperCase = false; number { prefix = "0x"}}
    val hexFormatSharp8 = HexFormat { upperCase = true; number { prefix = "#"; minLength = 8} }
    val hexFormatIntel =
        HexFormat { upperCase = false; number { prefix = "["; minLength = 16; suffix = "]" }}

    data class HexFormatCase(val raw: String,
                             val hexFormat: HexFormat,
                             val expected: String)

    val hexFormatCases = arrayOf(
        HexFormatCase("Feed123", hexFormatSharp8, "#0FEED123"),
        HexFormatCase("Def", hexFormatLower, "0xdef"),
        HexFormatCase("abc", hexFormatUpper, "0xABC"),
        HexFormatCase("Def", hexFormatLower, "0xdef"),
        HexFormatCase("11112222", hexFormatIntel, "[0000000011112222]"),
    )

    @Test
    fun testHexFormatCases() {
        for (tc in hexFormatCases) {
            if (verbose)
                println(tc)
            val observed = BigInt.fromHex(tc.raw).toHexString(tc.hexFormat)
            assertEquals(tc.expected, observed)
        }
    }

}
