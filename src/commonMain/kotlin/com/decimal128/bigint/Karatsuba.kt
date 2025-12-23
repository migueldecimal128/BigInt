package com.decimal128.bigint

import com.decimal128.bigint.Mago.isNormalized
import com.decimal128.bigint.Mago.setAdd
import com.decimal128.bigint.Mago.setSqr
import com.decimal128.bigint.Mago.setSub

object Karatsuba {

    fun karatsubaSquare1(
        z: Magia, zOff: Int,
        a: Magia, aOff: Int, aNormLen: Int,
        t: Magia, tOff: Int
    ): Int {
        check (isNormalized(a, aOff, aNormLen))
        val n = aNormLen
        val k0 = n / 2
        val k1 = n - k0
        require (zOff >= 0 && z.size >= zOff + 2*n)
        require (tOff >= 0 && t.size >= tOff + (3*k1 + 3))

        val a0 = a
        val a0Off = aOff
        val a0Len = k0

        val a1 = a
        val a1Off = a0Off + a0Len
        val a1Len = k1

        val z0Off = zOff
        val z0Len = setSqr(z, z0Off, a0, a0Off, a0Len)

        val z2Off = zOff + 2*k0
        val z2Len = setSqr(z, z2Off, a1, a1Off, a1Len)

        val s = t
        val sOff = tOff
        val sLen = setAdd(s, sOff, a0, aOff, a0Len, a1, a1Off, a1Len)

        val s2 = t
        val s2Off = sOff + a1Len + 1
        val s2Len = setSqr(s2, s2Off, s, sOff, sLen)

        val z1 = s2
        val z1Off = s2Off
        var z1Len = setSub(z1, z1Off, s2, s2Off, s2Len, z, z0Off, z0Len)
        z1Len = setSub(z1, z1Off, z1, z1Off, z1Len, z, z2Off, z2Len)

        val zNormLen = Mago.mutAddShifted(z, zOff, 2*n, z1, z1Off, z1Len, k0)

        check (isNormalized(z, zOff, zNormLen))
        return zNormLen
    }
}