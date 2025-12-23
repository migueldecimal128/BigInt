// SPDX-License-Identifier: MIT

@file:Suppress("NOTHING_TO_INLINE")

package com.decimal128.bigint

import com.decimal128.bigint.Mago.isNormalized
import kotlin.math.max
import kotlin.math.min

object Karatsuba {

    private const val DEFAULT_KARATSUBA_SQR_THRESHOLD = 2

    fun karatsubaSetSqr(
        z: Magia, zOff: Int,
        a: Magia, aOff: Int, aNormLen: Int,
        t: Magia,
        minLimbThreshold: Int = DEFAULT_KARATSUBA_SQR_THRESHOLD
    ): Int {
        if (aNormLen < minLimbThreshold)
            return schoolbookSetSqr(z, zOff, a, aOff, aNormLen)

        return karatsubaSqrRecurse(
            z, zOff,
            a, aOff, aNormLen,
            t,
            minLimbThreshold
        )
    }

    fun karatsubaSqrRecurse(
        z: Magia, zOff: Int,
        a: Magia, aOff: Int, aNormLen: Int,
        t: Magia,
        minLimbThreshold: Int
    ): Int {
        check (isNormalized(a, aOff, aNormLen))
        val n = aNormLen
        val k0 = n / 2
        val k1 = n - k0
        require (aNormLen >= 2)
        require (zOff >= 0 && z.size >= zOff + 2*n)
        require (t.size >= (3*k1 + 3))

        val a0 = a
        val a0Off = aOff
        val a0Len = k0

        val a1 = a
        val a1Off = a0Off + a0Len
        val a1Len = k1

        val z0Off = zOff
        val z0Len = karatsubaSetSqr(z, z0Off,
            a0, a0Off, a0Len, t, minLimbThreshold)

        val z2Off = zOff + 2*k0
        val z2Len = karatsubaSetSqr(z, z2Off,
            a1, a1Off, a1Len, t, minLimbThreshold)

        val s = t
        val sOff = 0
        val sLen = setAdd(s, sOff, a0, aOff, a0Len, a1, a1Off, a1Len)

        val s2 = t
        val s2Off = sOff + a1Len + 1
        val s2Len = schoolbookSetSqr(s2, s2Off, s, sOff, sLen)

        val z1 = s2
        val z1Off = s2Off
        var z1Len = setSub(z1, z1Off, s2, s2Off, s2Len, z, z0Off, z0Len)
        z1Len = setSub(z1, z1Off, z1, z1Off, z1Len, z, z2Off, z2Len)

        val z0z2Len = 2*k0 + z2Len
        val zNormLen = mutAddShifted(z, zOff, z0z2Len, z1, z1Off, z1Len, k0)

        check (isNormalized(z, zOff, zNormLen))
        return zNormLen
    }

    fun karatsubaSqr1(
        z: Magia, zOff: Int,
        a: Magia, aOff: Int, aNormLen: Int,
        t: Magia, tOff: Int
    ): Int {
        check (isNormalized(a, aOff, aNormLen))
        val n = aNormLen
        val k0 = n / 2
        val k1 = n - k0
        require (aNormLen >= 2)
        require (zOff >= 0 && z.size >= zOff + 2*n)
        require (tOff >= 0 && t.size >= tOff + (3*k1 + 3))

        val a0 = a
        val a0Off = aOff
        val a0Len = k0

        val a1 = a
        val a1Off = a0Off + a0Len
        val a1Len = k1

        val z0Off = zOff
        val z0Len = schoolbookSetSqr(z, z0Off, a0, a0Off, a0Len)

        val z2Off = zOff + 2*k0
        val z2Len = schoolbookSetSqr(z, z2Off, a1, a1Off, a1Len)

        val s = t
        val sOff = tOff
        val sLen = setAdd(s, sOff, a0, aOff, a0Len, a1, a1Off, a1Len)

        val s2 = t
        val s2Off = sOff + a1Len + 1
        val s2Len = schoolbookSetSqr(s2, s2Off, s, sOff, sLen)

        val z1 = s2
        val z1Off = s2Off
        var z1Len = setSub(z1, z1Off, s2, s2Off, s2Len, z, z0Off, z0Len)
        z1Len = setSub(z1, z1Off, z1, z1Off, z1Len, z, z2Off, z2Len)

        val z0z2Len = 2*k0 + z2Len
        val zNormLen = mutAddShifted(z, zOff, z0z2Len, z1, z1Off, z1Len, k0)

        check (isNormalized(z, zOff, zNormLen))
        return zNormLen
    }

    // <<<<<<<<<< PRIMITIVES >>>>>>>>>

    /**
     * Returns the 32-bit limb `n` zero-extended to a 64-bit `ULong`.
     */
    private inline fun dw32(n: Int) = n.toUInt().toULong()


    fun setAdd(z: Magia, zOff: Int,
               x: Magia, xOff: Int, xNormLen: Int,
               y: Magia, yOff: Int, yNormLen: Int): Int {
        require (isNormalized(x, xOff, xNormLen))
        require (isNormalized(y, yOff, yNormLen))
        if (xNormLen >= 0 && xOff >= 0 && xOff + xNormLen <= x.size &&
            yNormLen >= 0 && yOff >= 0 && yOff + yNormLen <= y.size) {
            val maxNormLen = max(xNormLen, yNormLen)
            val minNormLen = min(xNormLen, yNormLen)
            if (zOff + maxNormLen < z.size) {
                var carry = 0uL
                var i = 0
                while (i < minNormLen) {
                    val t = dw32(x[xOff + i]) + dw32(y[yOff + i]) + carry
                    z[zOff + i] = t.toInt()
                    carry = t shr 32
                    ++i
                }
                val longer = if (xNormLen > yNormLen) x else y
                val longerOff = if (xNormLen > yNormLen) xOff else yOff
                while (i < maxNormLen) {
                    val t = dw32(longer[longerOff + i]) + carry
                    z[zOff + i] = t.toInt()
                    carry = t shr 32
                    ++i
                }
                val finalCarryOrZero = carry.toInt()
                z[zOff + i] = finalCarryOrZero
                val zNormLen = i + (-finalCarryOrZero ushr 31)
                check (isNormalized(z, zOff, zNormLen))
                return zNormLen
            }
        }
        throw IllegalArgumentException()
    }

    /**
     * @return the updated xNormLen
     */
    fun mutAddShifted(x: Magia, xOff: Int, xNormLen: Int,
                      y: Magia, yOff: Int, yNormLen: Int, yLimbShift: Int): Int {
        require (isNormalized(x, xOff, xNormLen))
        require (isNormalized(y, yOff, yNormLen))
        require (xNormLen >= 0 && xOff >= 0 && xOff + xNormLen <= x.size)
        require (yNormLen >= 0 && yOff >= 0 && yOff + yNormLen <= y.size)
        require (max(xNormLen, yNormLen + yLimbShift) + 1 <= x.size)
        require (yLimbShift >= 0)

        if (yNormLen == 0)
            return xNormLen
        val overlap = xNormLen - yLimbShift
        if (overlap <= 0) {
            // no overlap
            x.fill(0, xOff + xNormLen, xOff + yLimbShift)
            y.copyInto(x, xOff + yLimbShift, yOff, yOff + yNormLen)
            val retNormLen = yLimbShift + yNormLen
            // always write to max (xLen, yLimbShift + yLen)
            // as though there were a carry out.
            x[xOff + retNormLen] = 0
            return retNormLen
        }
        val xOffShifted = xOff + (xNormLen - overlap)
        val xLenShifted = overlap
        var carry = 0uL
        var i = 0
        val minLen = min(xLenShifted, yNormLen)
        while (i < minLen) {
            val t = dw32(x[xOffShifted + i]) + dw32(y[yOff + i]) + carry
            x[xOffShifted + i] = t.toInt()
            carry = t shr 32
            ++i
        }
        if (i < yNormLen) {
            do {
                val t = dw32(y[yOff + i]) + carry
                x[xOffShifted + i] = t.toInt()
                carry = t shr 32
                ++i
            } while (i < yNormLen)
            val finalCarryOrZero = carry.toInt()
            check (finalCarryOrZero == 1 || finalCarryOrZero == 0)
            x[xOffShifted + yNormLen] = finalCarryOrZero
            return yNormLen + yLimbShift + (-finalCarryOrZero ushr 31)
        }
        while (i < xLenShifted && carry != 0uL) {
            val t = dw32(x[xOffShifted + i]) + carry
            x[xOffShifted + i] = t.toInt()
            carry = t shr 32
            ++i
        }
        val finalCarryOrZero = carry.toInt()
        check (xOffShifted + xLenShifted == xOff + xNormLen)
        x[xOff + xNormLen] = finalCarryOrZero
        return xNormLen + (-finalCarryOrZero ushr 31)
    }

    fun setSub(z: Magia, zOff: Int,
               x: Magia, xOff: Int, xNormLen: Int,
               y: Magia, yOff: Int, yNormLen: Int): Int {
        if (xOff >= 0 && xNormLen >= 0 && xOff + xNormLen <= x.size &&
            yOff >= 0 && yNormLen >= 0 && yOff + yNormLen <= y.size) {
            if (zOff + xNormLen <= z.size) {
                if (xNormLen >= yNormLen) {
                    var borrow = 0uL
                    var lastNonZeroIndex = -1
                    var i = 0
                    while (i < yNormLen) {
                        val t = dw32(x[xOff + i]) - dw32(y[yOff + i]) - borrow
                        val zi = t.toInt()
                        z[zOff + i] = zi
                        val nonZeroMask = (zi or -zi) shr 31
                        lastNonZeroIndex = (lastNonZeroIndex and nonZeroMask.inv()) or (i and nonZeroMask)
                        borrow = t shr 63
                        ++i
                    }
                    while (i < xNormLen) {
                        val t = dw32(x[xOff + i]) - borrow
                        val zi = t.toInt()
                        z[zOff + i] = zi
                        val nonZeroMask = (zi or -zi) shr 31
                        lastNonZeroIndex = (lastNonZeroIndex and nonZeroMask.inv()) or (i and nonZeroMask)
                        borrow = t shr 63
                        ++i
                    }
                    if (borrow == 0uL) {
                        val zNormLen = lastNonZeroIndex + 1
                        check(isNormalized(z, zOff, zNormLen))
                        return zNormLen
                    }
                }
                throw ArithmeticException()
            }
        }
        throw IllegalArgumentException()
    }

    fun setMul(z: Magia, zOff: Int,
               x: Magia, xOff: Int, xNormLen: Int,
               y: Magia, yOff: Int, yNormLen: Int): Int {
        if (xOff >= 0 && xNormLen >= 0 && xOff + xNormLen <= x.size &&
            yOff >= 0 && yNormLen >= 0 && yOff + yNormLen <= y.size &&
            zOff >= 0 && zOff + xNormLen + yNormLen <= z.size) {
            check (isNormalized(x, xOff, xNormLen))
            check (isNormalized(y, yOff, yNormLen))

            if (xNormLen == 0 || yNormLen == 0)
                return 0

            // karatsuba produces balanced operands,
            // so no need to check/swap longer/shorter

            // zero out the minimum amount needed
            // higher limbs will be written before read
            z.fill(0, zOff, zOff + yNormLen)
            for (i in 0..<xNormLen) {
                val xLimb = dw32(x[xOff + i])
                var carry = 0uL
                for (j in 0..<yNormLen) {
                    val yLimb = dw32(y[yOff + j])
                    val t = xLimb * yLimb + dw32(z[zOff + i + j]) + carry
                    z[zOff + i + j] = t.toInt()
                    carry = t shr 32
                }
                z[zOff + i + yNormLen] = carry.toInt()
            }
            val lastIndex = xNormLen + yNormLen - 1
            val zNormLen = lastIndex +
                    if (z[zOff + lastIndex] == 0) 0 else 1
            check (isNormalized(z, zOff, zNormLen))
            return zNormLen
        }
        throw IllegalArgumentException()
    }

    fun schoolbookSetSqr(z: Magia, zOff: Int, x: Magia, xOff: Int, xNormLen: Int) : Int {
        val zMaxLen = 2 * xNormLen
        require (xOff >= 0)
        require (xOff + xNormLen <= x.size)
        require (zOff >= 0)
        require (zOff + zMaxLen <= z.size)

        if (xNormLen == 0)
            return 0

        z.fill(0, zOff, zOff + zMaxLen)

        // 1) Cross terms: for i<j, add (x[i]*x[j]) twice into p[i+j]
        // these terms are doubled
        for (i in 0..<xNormLen) {
            val xi = dw32(x[xOff + i])
            var carry = 0uL
            var j = i + 1
            var k = 2*i + 1
            while (j < xNormLen) {
                val prod = xi * dw32(x[xOff + j])        // 32x32 -> 64
                // add once
                val t1 = prod + dw32(z[zOff + k]) + carry
                val p1 = t1 and 0xFFFF_FFFFuL
                carry = t1 shr 32
                // add second time (doubling) — avoids (prod << 1) overflow
                val t2 = prod + p1
                z[zOff + k] = t2.toInt()
                carry += t2 shr 32
                ++j
                ++k
            }
            // flush carry to the next limb(s)
            if (carry != 0uL) {
                val t = dw32(z[zOff + k]) + carry
                z[zOff + k] = t.toInt()
                carry = t shr 32
                if (carry != 0uL)
                    ++z[zOff + k + 1]
            }
        }

        // 2) Diagonals: add x[i]**2 into columns 2*i and 2*i+1
        // terms on the diagonal are not doubled
        for (i in 0..<xNormLen) {
            val xi = x[xOff + i].toUInt().toULong()
            val sq = xi * xi
            // add low 32 to p[2*i]
            var t = dw32(z[zOff + 2 * i]) + (sq and 0xFFFF_FFFFuL)
            z[zOff + 2 * i] = t.toInt()
            var carry = t shr 32
            // add high 32 (and carry) to p[2*i+1]
            val s = (sq shr 32) + carry
            if (s != 0uL) {
                t = dw32(z[zOff + 2 * i + 1]) + s
                z[zOff + 2 * i + 1] = t.toInt()
                carry = t shr 32
                // propagate any remaining carry
                var k = 2 * i + 2
                while (carry != 0uL) {
                    t = dw32(z[zOff + k]) + carry
                    z[zOff + k] = t.toInt()
                    carry = t shr 32
                    k++
                }
            }
        }
        val lastIndex = 2 * xNormLen - 1
        val zNormLen = lastIndex +
                if (z[zOff + lastIndex] == 0) 0 else 1
        check (isNormalized(z, zOff, zNormLen))
        return zNormLen
    }
}