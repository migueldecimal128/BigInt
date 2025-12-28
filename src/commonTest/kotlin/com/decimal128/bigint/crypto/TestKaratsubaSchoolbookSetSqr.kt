package com.decimal128.bigint.crypto

import com.decimal128.bigint.BigInt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class TestKaratsubaSchoolbookSetSqr {

    @Test
    fun square_len1() {
        val a = intArrayOf(0xFFFF_FFFF.toInt())
        val z = IntArray(4)

        Karatsuba.setSqrSchoolbookK(z, 0, a, 0, 1)

        val ref = BigInt.Companion.fromLittleEndianIntArray(false, a).sqr()
        val got = BigInt.Companion.fromLittleEndianIntArray(false, z)

        assertEquals(ref, got)
    }

    @Test
    fun square_len2() {
        val a = intArrayOf(
            0x0123_4567,
            0x89AB_CDEF.toInt()
        )
        val z = IntArray(8)

        Karatsuba.setSqrSchoolbookK(z, 1, a, 0, 2)

        val ref = BigInt.Companion.fromLittleEndianIntArray(false, a).sqr()
        val got = BigInt.Companion.fromLittleEndianIntArray(false, z, 1, a.size * 2)

        assertEquals(ref, got)
    }

    @Test
    fun square_len3_offsets() {
        val a = IntArray(10)
        a[3] = 0xDEAD_BEEF.toInt()
        a[4] = 0x1234_5678
        a[5] = 0xCAFEBABE.toInt()

        val z = IntArray(20)

        Karatsuba.setSqrSchoolbookK(z, 7, a, 3, 3)

        val ref = BigInt.Companion.fromLittleEndianIntArray(false, a, 3, 3).sqr()
        val got = BigInt.Companion.fromLittleEndianIntArray(false, z, 7, 6)

        assertEquals(ref, got)
    }

    @Test
    fun square_allOnes_carryStress() {
        val n = 8
        val a = IntArray(n) { 0xFFFF_FFFF.toInt() }
        val z = IntArray(16)

        Karatsuba.setSqrSchoolbookK(z, 0, a, 0, a.size)

        val ref = BigInt.Companion.fromLittleEndianIntArray(false, a)
        val ref2 = ref*ref
        val got = BigInt.Companion.fromLittleEndianIntArray(false, z, 0, 2 * a.size)

        assertEquals(ref2, got)
    }

    @Test
    fun square_len4_randomish() {
        val a = intArrayOf(
            0x1111_1111,
            0x2222_2222,
            0x3333_3333,
            0x4444_4444
        )
        val z = IntArray(12)

        Karatsuba.setSqrSchoolbookK(z, 2, a, 0, 4)

        val ref = BigInt.Companion.fromLittleEndianIntArray(false, a, 0, 4).sqr()
        val got = BigInt.Companion.fromLittleEndianIntArray(false, z, 2, 8)

        assertEquals(ref, got)
    }

    @Test
    fun testRandom() {
        repeat (100) {
            val bi = BigInt.Companion.randomWithBitLen(2048)
            val ax = bi.magnitudeToLittleEndianIntArray()
            if (ax.size < 1)
                return@repeat
            val a = IntArray(ax.size + 10) { 0xDEAD }
            val aOff = Random.Default.nextInt(10)
            val aNormLen = ax.size
            ax.copyInto(a, aOff)

            val zLen = 2 * aNormLen
            val z = IntArray(zLen + 10) { 0xFFFF }
            val zOff = Random.Default.nextInt(10)

            z.fill(0, zOff, zOff + zLen)
            Karatsuba.setSqrSchoolbookK(z, zOff, a, aOff, aNormLen)

            val observed = BigInt.Companion.fromLittleEndianIntArray(false, z, zOff, zLen)
            val expected = bi.sqr()

            assertEquals(expected, observed)
        }
    }

    @Test
    fun square_FFFF() {
        val a = intArrayOf( -1, -1, -1, -1)

        val z = IntArray(2*a.size)

        Karatsuba.setSqrSchoolbookK(z, 0, a, 0, a.size)

        val ref = BigInt.Companion.fromLittleEndianIntArray(false, a, 0, a.size)
        val ref2 = ref * ref
        val got = BigInt.Companion.fromLittleEndianIntArray(false, z)

        println("z=[${z.joinToString()}]")

        println("ref2.magia=[${ref2.magia.joinToString()}]")

        assertEquals(ref2, got)

    }


}