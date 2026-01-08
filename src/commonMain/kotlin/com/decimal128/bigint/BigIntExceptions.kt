package com.decimal128.bigint

object BigIntExceptions {
    private const val ERR_MSG_ADD_OVERFLOW = "add overflow ... destination too small"
    private const val ERR_MSG_SUB_UNDERFLOW = "sub underflow ... minuend too small for subtrahend"
    private const val ERR_MSG_MUL_OVERFLOW = "mul overflow ... destination too small"
    private const val ERR_MSG_SHL_OVERFLOW = "shl overflow ... destination too small"
    private const val ERR_MSG_DIV_BY_ZERO = "div by zero"
    private const val ERR_MSG_INVALID_ALLOCATION_LENGTH = "invalid allocation length"
    private const val ERR_MSG_NEGATIVE_INDEX = "negative index"
    private const val ERR_MSG_MOD_NEG_DIVISOR = "modulus with a negative divisor is undefined"
    private const val ERR_MSG_NEG_BITCOUNT = "negative bitCount"
    private const val ERR_MSG_BITLEN_LE_0 = "invalid bitLen <= 0"
    private const val ERR_MSG_INVALID_BITLEN_RANGE = "invalid bitLen range: 0 <= bitLenMin <= bitLenMax"
    private const val ERR_MSG_NEG_BITINDEX = "negative bitIndex"
    private const val ERR_MSG_BOUNDS_CHECK_VIOLATION = "bounds check violation"
    private const val ERR_MSG_BAD_KNUTH_ARGUMENT = "bad knuth argument"
    private const val ERR_MSG_HASH_CODE_UNSUPPORTED =
        "mutable MutableBigInt is an invalid key in collections"

    fun throwDivByZero(): Nothing {
        throw ArithmeticException(ERR_MSG_DIV_BY_ZERO)
    }

    fun throwAddOverflow(): Nothing {
        throw IllegalStateException(ERR_MSG_ADD_OVERFLOW)
    }

    fun throwSubUnderflow(): Nothing {
        throw IllegalStateException(ERR_MSG_SUB_UNDERFLOW)
    }

    fun throwMulOverflow(): Nothing {
        throw IllegalStateException(ERR_MSG_MUL_OVERFLOW)
    }

    fun throwShlOverflow(): Nothing {
        throw IllegalStateException(ERR_MSG_SHL_OVERFLOW)
    }

    fun throwInvalidAllocationLength(): Nothing {
        throw IllegalStateException(ERR_MSG_INVALID_ALLOCATION_LENGTH)
    }

    fun throwNegativeIndex(): Nothing {
        throw IllegalStateException(ERR_MSG_NEGATIVE_INDEX)
    }

    fun throwModNegDivisor(): Nothing {
        throw ArithmeticException(ERR_MSG_MOD_NEG_DIVISOR)
    }

    fun throwNegBitCount(): Nothing {
        throw IllegalArgumentException(ERR_MSG_NEG_BITCOUNT)
    }

    fun throwBitLenLE0(): Nothing {
        throw IllegalArgumentException(ERR_MSG_BITLEN_LE_0)
    }

    fun throwInvalidBitLenRange(): Nothing {
        throw IllegalArgumentException(ERR_MSG_INVALID_BITLEN_RANGE)
    }

    fun throwNegBitIndex(): Nothing {
        throw IllegalArgumentException(ERR_MSG_NEG_BITINDEX)
    }

    fun throwBoundsCheckViolation(): Nothing {
        throw IllegalArgumentException(ERR_MSG_BOUNDS_CHECK_VIOLATION)
    }

    fun throwBadKnuthArgument(): Nothing {
        throw IllegalArgumentException(ERR_MSG_BAD_KNUTH_ARGUMENT)
    }

    fun throwHashCodeUnsupported(): Nothing {
        throw UnsupportedOperationException(ERR_MSG_HASH_CODE_UNSUPPORTED)
    }
}
