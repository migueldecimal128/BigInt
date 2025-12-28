// SPDX-License-Identifier: MIT

@file:Suppress("NOTHING_TO_INLINE")

package com.decimal128.bigint

import com.decimal128.bigint.Mago.setMulSchoolbook
import com.decimal128.bigint.intrinsic.unsignedMulHi

internal object MagoSqr {

    /**
     * Returns the 32-bit limb `n` zero-extended to a 64-bit `ULong`.
     */
    inline fun dw32(n: Int) = n.toUInt().toULong()


    /**
     * Squares the first [xLen] limbs of [x], storing the result in [z].
     *
     * Requirements:
     * - [z.size] must be 2 * [xLen] ... allows higher performance
     *   at very low cost
     *
     * @return the normalized limb length of the result.
     */

    fun setSqr(z: Magia, x: Magia, xNormLen: Int): Int {
        return when {
            xNormLen == 0 -> 0
            xNormLen == 1 -> setSqr1Limb(z, x)
            xNormLen == 2 -> setSqr2Limbs(z, x)
            xNormLen == 3 -> setSqr3Limbs(z, x)
            xNormLen == 4 -> setSqr4Limbs(z, x)
            xNormLen <= 16 -> setMulSchoolbook(z, x, xNormLen, x, xNormLen)
            else -> setSqrSchoolbook(z, 0, x, 0, xNormLen)
        }
    }

    inline fun setSqrSchoolbook(z: Magia, x: Magia, xNormLen: Int): Int =
        setSqrSchoolbook(z, 0, x, 0, xNormLen)

    fun setSqrSchoolbook(z: Magia, zOff: Int, x: Magia, xOff: Int, xNormLen: Int): Int {
        if (xNormLen == 0)
            return 0
        val zLen = 2 * xNormLen
        check(zOff >= 0 && zOff + zLen <= z.size)
        z.fill(0, zOff, zOff + zLen)

        check(xOff >= 0 && xOff + xNormLen <= x.size)
        // 1) Cross terms: i < j
        // We compute the sum of all a[i]*a[j] where i < j
        for (i in 0 until xNormLen - 1) {
            val ai = dw32(x[xOff + i])
            var carry = 0uL
            for (j in i + 1 until xNormLen) {
                val k = i + j
                // Standard row-multiply accumulation
                val t = ai * dw32(x[xOff + j]) + dw32(z[zOff + k]) + carry
                z[zOff + k] = t.toInt()
                carry = t shr 32
            }
            z[zOff + i + xNormLen] = carry.toInt()
        }

        // 2) Double the cross terms: z = z * 2
        // This is much faster than doubling inside the loop because it's a linear scan
        var shiftCarry = 0uL
        for (i in 0 until zLen) {
            val zi = dw32(z[zOff + i])
            val t = (zi shl 1) or shiftCarry
            z[zOff + i] = t.toInt()
            shiftCarry = t shr 32
        }

        // 3) Diagonals: add a[i]^2 into column 2*i
        // We add these directly into the doubled cross-terms
        for (i in 0 until xNormLen) {
            var k = 2 * i
            val zk = dw32(z[zOff + k])
            val ai = dw32(x[xOff + i])
            val sqa = ai * ai + zk

            // Add low 32 bits
            z[zOff + k] = sqa.toInt()
            ++k
            // Add high 32 bits + carry
            var carry = dw32(z[zOff + k]) + (sqa shr 32)
            z[zOff + k] = carry.toInt()
            carry = carry shr 32
            ++k

            while (carry != 0uL && k < zLen) {
                carry = dw32(z[zOff + k]) + carry
                z[zOff + k] = carry.toInt()
                carry = carry shr 32
                ++k
            }
        }

        // Normalization
        val lastIndex = zLen - 1
        val lastLimb = z[zOff + lastIndex]
        val zNormLen = lastIndex + ((lastLimb or -lastLimb) ushr 31)
        return zNormLen
    }

    private inline fun setSqr1Limb(z: Magia, a: Magia): Int {
        val dw = a[0].toUInt().toULong()
        val sq = dw * dw
        val hi = sq shr 32
        z[1] = hi.toInt()
        z[0] = sq.toInt()
        return (-hi.toLong() ushr 63).toInt() + 1
    }

    private inline fun setSqr2Limbs(z: Magia, a: Magia): Int {
        val dw = (a[1].toULong() shl 32) or a[0].toUInt().toULong()
        val sqLo = dw * dw
        val sqHi = unsignedMulHi(dw, dw)
        val hiLimb = (sqHi shr 32).toInt()
        z[3] = hiLimb
        z[2] = sqHi.toInt()
        z[1] = (sqLo shr 32).toInt()
        z[0] = sqLo.toInt()
        return if (hiLimb == 0) 3 else 4
    }

    private inline fun setSqr3Limbs(z: Magia, a: Magia): Int {
        val a0 = dw32(a[0])   // ULong, 0..2^32-1
        val a1 = dw32(a[1])
        val a2 = dw32(a[2])

        val s0 = a0 * a0
        val s1 = a1 * a1
        val s2 = a2 * a2
        val c01 = a0 * a1
        val c02 = a0 * a2
        val c12 = a1 * a2

        // carry is in "32-bit limbs" units: carry == next inbound value to add into column
        var carry = s0 shr 32

        // z[0]
        z[0] = s0.toInt()

        // ---- column 1: 2*c01 + carry ----
        run {
            var lo = carry                      // fits in ULong
            var hi = 0uL                        // counts 2^64 units

            // add (c01 << 1) with overflow bit (c01 >>> 63)
            val dLo = c01 shl 1
            val dHi = c01 shr 63               // 0 or 1

            val old = lo
            lo += dLo
            if (lo < old) hi++                  // 64-bit add overflow
            hi += dHi

            z[1] = lo.toInt()
            carry = (lo shr 32) + (hi shl 32)
        }

        // ---- column 2: s1 + 2*c02 + carry ----
        run {
            var lo = carry
            var hi = 0uL

            // + s1
            var old = lo
            lo += s1
            if (lo < old) hi++

            // + (c02 << 1) with overflow bit
            val dLo = c02 shl 1
            val dHi = c02 shr 63

            old = lo
            lo += dLo
            if (lo < old) hi++
            hi += dHi

            z[2] = lo.toInt()
            carry = (lo shr 32) + (hi shl 32)
        }

        // ---- column 3: 2*c12 + carry ----
        run {
            var lo = carry
            var hi = 0uL

            val dLo = c12 shl 1
            val dHi = c12 shr 63

            val old = lo
            lo += dLo
            if (lo < old) hi++
            hi += dHi

            z[3] = lo.toInt()
            carry = (lo shr 32) + (hi shl 32)
        }

        // ---- column 4: s2 + carry ----
        run {
            val t = carry + s2                  // this sum fits in <= 66 bits; still safe in ULong
            z[4] = t.toInt()
            z[5] = (t shr 32).toInt()
        }

        return if (z[5] == 0) 5 else 6
    }

    private inline fun setSqr4Limbs(z: Magia, a: Magia): Int {
        val a0 = dw32(a[0]);
        val a1 = dw32(a[1])
        val a2 = dw32(a[2]);
        val a3 = dw32(a[3])

        val s0 = a0 * a0;
        val s1 = a1 * a1;
        val s2 = a2 * a2;
        val s3 = a3 * a3
        val c01 = a0 * a1;
        val c02 = a0 * a2;
        val c03 = a0 * a3
        val c12 = a1 * a2;
        val c13 = a1 * a3;
        val c23 = a2 * a3

        z[0] = s0.toInt()
        var carry = s0 shr 32

        // Column 1: 2*c01 + carry
        run {
            var lo = carry;
            var hi = 0uL
            val dLo = c01 shl 1;
            val dHi = c01 shr 63
            val old = lo; lo += dLo
            if (lo < old) hi++; hi += dHi
            z[1] = lo.toInt(); carry = (lo shr 32) + (hi shl 32)
        }

        // Column 2: s1 + 2*c02 + carry
        run {
            var lo = carry;
            var hi = 0uL
            var old = lo; lo += s1
            if (lo < old) hi++
            val dLo = c02 shl 1;
            val dHi = c02 shr 63
            old = lo; lo += dLo
            if (lo < old) hi++; hi += dHi
            z[2] = lo.toInt(); carry = (lo shr 32) + (hi shl 32)
        }

        // Column 3: 2*c03 + 2*c12 + carry (The n=4 Peak)
        run {
            var lo = carry;
            var hi = 0uL
            // + 2*c03
            var dLo = c03 shl 1;
            var dHi = c03 shr 63
            var old = lo; lo += dLo
            if (lo < old) hi++; hi += dHi
            // + 2*c12
            dLo = c12 shl 1; dHi = c12 shr 63
            old = lo; lo += dLo
            if (lo < old) hi++; hi += dHi
            z[3] = lo.toInt(); carry = (lo shr 32) + (hi shl 32)
        }

        // Column 4: s2 + 2*c13 + carry
        run {
            var lo = carry;
            var hi = 0uL
            var old = lo; lo += s2
            if (lo < old) hi++
            val dLo = c13 shl 1;
            val dHi = c13 shr 63
            old = lo; lo += dLo
            if (lo < old) hi++; hi += dHi
            z[4] = lo.toInt(); carry = (lo shr 32) + (hi shl 32)
        }

        // Column 5: 2*c23 + carry
        run {
            var lo = carry;
            var hi = 0uL
            val dLo = c23 shl 1;
            val dHi = c23 shr 63
            val old = lo; lo += dLo
            if (lo < old) hi++; hi += dHi
            z[5] = lo.toInt(); carry = (lo shr 32) + (hi shl 32)
        }

        // Column 6 & 7: s3 + carry
        run {
            val t = carry + s3
            z[6] = t.toInt()
            val z7 = (t shr 32).toInt()
            z[7] = z7
            return if (z7 == 0) 7 else 8
        }
    }

}