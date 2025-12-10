// SPDX-License-Identifier: MIT

package com.decimal128.bigint

import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.max

object MagiaTransducer {

    @Suppress("NOTHING_TO_INLINE")
    private inline fun U32(n: Int) = n.toLong() and 0xFFFF_FFFFL

    fun magiaToBi(x: IntArray): BigInteger {
        var bi = BigInteger.valueOf(U32(x[0]))
        for (i in x.size-1 downTo 1) {
            val t = BigInteger.valueOf(U32(x[i])).shiftLeft(i * 32)
            bi = bi.or(t)
        }
        return bi
    }

    fun magiaFromBi(bi: BigInteger): IntArray {
        val bitLen = bi.bitLength()
        val wordLen = (bitLen + 0x1F) ushr 5
        val magia = IntArray(max(1, wordLen))
        for (i in 0..<wordLen)
            magia[i] = bi.shiftRight(i * 32).toInt()
        return magia
    }

    fun magiaToString(magia: IntArray): String {
        return magiaToBi(magia).toString()
    }

    fun magiaFromString(str: String): IntArray {
        return magiaFromBi(BigInteger(str))
    }

    fun compare(magia: IntArray, bi: BigInteger): Int {
        val magiaBitLen = Magian.bitLen(magia)
        val cmpBitLen = magiaBitLen.compareTo(bi.bitLength())
        if (cmpBitLen != 0)
            return cmpBitLen
        for (i in magia.size - 1 downTo 0) {
            val xLimb = magia[i]
            val yLimb = bi.shiftRight(i * 32).toInt()
            if (xLimb != yLimb)
                return xLimb.toUInt().compareTo(yLimb.toUInt())
        }
        return 0
    }

    fun EQ(magia: IntArray, bi:BigInteger) = compare(magia, bi) == 0

    fun calcDigitCount(magia: IntArray): Int {
        val bi = magiaToBi(magia)
        val bd = BigDecimal(bi)
        val precision = bd.precision()
        return precision
    }
}
