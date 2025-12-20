package com.decimal128.bigint

import com.decimal128.bigint.crypto.ModContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestMbiMontgomeryRedc {

    @Test
    fun testMontgomeryRedc_smallModulus() {
        // Modulus n = 3
        val n = 3.toBigInt()
        val np = 0xAAAAAAABu  // -3^{-1} mod 2^32

        // Construct T = 2^32 + 2
        // i.e. [ low = 2 , high = 1 ]
        val t = 0x1_0000_0002uL.toMutableBigInt()

        // Perform REDC(T)
        t.montgomeryRedc(n, np)

        // Now t should equal (2^32 + 2) * R^{-1} mod 3 = 2 mod 3 = 2
        assertEquals(2.toBigInt(), t.toBigInt())

        // And should be normalized
        assertTrue(t.isNormalized())
    }

    @Test
    fun montgomeryRedc_roundTrip_smallX() {
        val n  = 0xFFFF_FFFF_FFFF_FFC5uL.toBigInt()
        val np = ModContext.Montgomery.computeNp(n.toUInt())

        val x  = 12345.toBigInt()

        // R = 2^(32*k), k = n.meta.normLen (here 2)
        val k  = n.meta.normLen
        val R  = BigInt.ONE shl (32 * k)

        // x in Montgomery domain: x̄ = x * R mod n
        val t  = ((x * R) % n).toMutableBigInt()

        t.montgomeryRedc(n, np)

        // Now REDC(x̄) == x mod n
        assertEquals(x, t.toBigInt())
        assertTrue(t.isNormalized())
    }

    @Test
    fun testMontgomeryRedc_anotherCase() {
        val modulus = 3.toBigInt()
        val np = ModContext.Montgomery.computeNp(3u)

        val t = 0x1_0000_0005uL.toMutableBigInt()

        t.montgomeryRedc(modulus, np)

        assertEquals(0.toBigInt(), t.toBigInt())
    }

    @Test
    fun testMontgomeryRedc_twoLimbModulus() {
        val n  = 0xFFFF_FFFF_FFFF_FFC5uL.toBigInt()  // 2 limbs
        val np = ModContext.Montgomery.computeNp(n.toUInt())
        val k  = n.meta.normLen

        val x = 12345.toBigInt()

        // Construct T = x * R mod n
        val R = BigInt.ONE shl (32 * k)
        val T = ((x * R) % n).toMutableBigInt()

        T.montgomeryRedc(n, np)

        assertEquals(x, T.toBigInt())
        assertTrue(T.isNormalized())
    }


}