package com.decimal128.bigint.crypto

import com.decimal128.bigint.BigInt
import com.decimal128.bigint.BigIntExtensions.toBigInteger
import com.decimal128.bigint.toBigInt
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class TestIsProbablePrimeVsBigInteger {

    val verbose = true

    @Test
    fun testIsProbablePrime_againstJavaBigInteger() {
        repeat(300) {
            val n = BigInt.randomWithMaxBitLen(2048) or BigInt.ONE  // odd
            val ours = BigIntPrime.isProbablePrime(n)

            val java = n.toBigInteger().isProbablePrime(50)

            assertEquals(java, ours, "mismatch on $n")
        }
    }


    @Test
    fun testProblemA() {
        val strVal = "11365424011101675333969644835356771403652944204" +
                "6010798868088013717331652876454661148406546006611" +
                "4840318238386502787767953862275163180548847288028" +
                "4619221959554279742269262376910331749773659333018" +
                "5953757372613494429855677211277452522260441124775" +
                "8634554123540112365870760045827430858186786950545" +
                "6838417082394064586188174210087743045350728709931" +
                "6478429918991301727940676749765335163612549842940" +
                "6520398553749682177715682771323081333829930105950" +
                "8978548002318524987502538592531943122250854129386" +
                "5387330416942979452927435423492500379166102288699" +
                "7308002837533078519163606236270818438672296645910" +
                "0182437683317769693995946274821"

        val bi = strVal.toBigInt()
        val java = BigInteger(strVal)

        val myOpinion = BigIntPrime.isProbablePrime(bi)
        if (verbose)
            println("testProblemA isProbablePrime() myOpinion:$myOpinion")
        for (certainty in 25..200 step 25) {
            val javaOpinion = java.isProbablePrime(certainty)
            println("  java certainty:$certainty javaOpinion:$javaOpinion")
        }
    }


}