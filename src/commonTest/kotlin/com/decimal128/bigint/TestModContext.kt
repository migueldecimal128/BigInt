package com.decimal128.bigint

import com.decimal128.bigint.MutableBigInt
import kotlin.test.*
import com.decimal128.bigint.toBigInt

class TestModContext {

    private fun refMod(a: Long, m: Long) =
        ((a % m) + m) % m

    @Test
    fun testConstruction() {
        assertFailsWith<IllegalArgumentException> { ModContext(0.toBigInt()) }
        assertFailsWith<IllegalArgumentException> { ModContext((-5).toBigInt()) }

        val ctx = ModContext(17.toBigInt())
        assertEquals(17.toBigInt(), ctx.m)
    }

    @Test
    fun testModSetBigInt() {
        val ctx = ModContext(17.toBigInt())
        val out = MutableBigInt()

        ctx.modSet(5.toBigInt(), out)
        assertEquals(5.toBigInt(), out.toBigInt())

        ctx.modSet(25.toBigInt(), out)
        assertEquals(8.toBigInt(), out.toBigInt())

        ctx.modSet((-3).toBigInt(), out)
        assertEquals(14.toBigInt(), out.toBigInt())
    }

    @Test
    fun testModSetIntAndLong() {
        val ctx = ModContext(17.toBigInt())
        val out = MutableBigInt()

        ctx.modSet(3, out)
        assertEquals(3.toBigInt(), out.toBigInt())

        ctx.modSet(20, out)  // Int path
        assertEquals(3.toBigInt(), out.toBigInt())

        ctx.modSet(20L, out)
        assertEquals(3.toBigInt(), out.toBigInt())

        ctx.modSet(-5L, out)
        assertEquals(12.toBigInt(), out.toBigInt())
    }

    @Test
    fun testModAdd() {
        val ctx = ModContext(17.toBigInt())
        val out = MutableBigInt()

        ctx.modAdd(5.toBigInt(), 7.toBigInt(), out)
        assertEquals(12.toBigInt(), out.toBigInt())

        ctx.modAdd(15.toBigInt(), 5, out)
        assertEquals(3.toBigInt(), out.toBigInt()) // 20 mod 17 = 3

        ctx.modAdd(15.toBigInt(), 12L, out)
        assertEquals(10.toBigInt(), out.toBigInt()) // 27 mod 17 = 10
    }

    @Test
    fun testModSub() {
        val ctx = ModContext(17.toBigInt())
        val out = MutableBigInt()

        ctx.modSub(5.toBigInt(), 7.toBigInt(), out)
        assertEquals(15.toBigInt(), out.toBigInt()) // -2 -> 15

        ctx.modSub(4.toBigInt(), 8, out)
        assertEquals(13.toBigInt(), out.toBigInt())

        ctx.modSub(4.toBigInt(), 21L, out)
        assertEquals(refMod(4 - 21, 17).toBigInt(), out.toBigInt())
    }

    @Test
    fun testModMul() {
        val ctx = ModContext(17.toBigInt())
        val out = MutableBigInt()

        ctx.modMul(3.toBigInt(), 5.toBigInt(), out)
        assertEquals(15.toBigInt(), out.toBigInt())

        ctx.modMul(3.toBigInt(), 20, out)  // Int overload
        assertEquals(9.toBigInt(), out.toBigInt()) // 60 mod 17 = 9

        ctx.modMul(6.toBigInt(), 30L, out)
        assertEquals(refMod(6 * 30, 17).toBigInt(), out.toBigInt())
    }

    @Test
    fun testModSqr() {
        val ctx = ModContext(17.toBigInt())
        val out = MutableBigInt()

        ctx.modSqr(6.toBigInt(), out)
        assertEquals(2.toBigInt(), out.toBigInt()) // 36 mod 17 = 2
    }

    @Test
    fun testModPowOddUsesMontgomery() {
        val m = 97.toBigInt() // odd modulus
        val ctx = ModContext(m)
        val out = MutableBigInt()

        ctx.modPow(5.toBigInt(), 117.toBigInt(), out)
        val expected = (5.toBigInt().pow(117) % m)
        assertEquals(expected, out.toBigInt())
    }

    @Test
    fun testModPowEvenUsesBarrettFallback() {
        val m = 100.toBigInt() // even modulus
        val ctx = ModContext(m)
        val out = MutableBigInt()

        ctx.modPow(7.toBigInt(), 45.toBigInt(), out)
        val expected = (7.toBigInt().pow(45) % m)
        assertEquals(expected, out.toBigInt())
    }

    @Test
    fun testModInv() {
        val ctx = ModContext(17.toBigInt())
        val out = MutableBigInt()

        ctx.modInv(5.toBigInt(), out)
        assertEquals(7.toBigInt(), out.toBigInt()) // 5*7=35 mod 17 = 1
    }

    @Test
    fun testModInvNotInvertible() {
        val ctx = ModContext(21.toBigInt()) // composite modulus
        val out = MutableBigInt()

        assertFailsWith<ArithmeticException> {
            ctx.modInv(7.toBigInt(), out) // gcd(7,21)=7
        }
    }

    @Test
    fun testModHalfLucas() {
        val ctx = ModContext(17.toBigInt())
        val out = MutableBigInt()

        val a = MutableBigInt().set(5) // odd
        ctx.modHalfLucas(a, out)
        assertEquals(11.toBigInt(), out.toBigInt()) // (5+17)/2 = 11

        a.set(8) // even
        ctx.modHalfLucas(a, out)
        assertEquals(4.toBigInt(), out.toBigInt())
    }
}
