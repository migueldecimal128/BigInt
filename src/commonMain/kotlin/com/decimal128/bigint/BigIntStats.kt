package com.decimal128.bigint

object BigIntStats {

    internal val BI_OP_COUNTS = LongArray(BigIntOp.CARDINALITY)

    enum class BigIntOp {
        BI_CONSTRUCT_32,
        BI_CONSTRUCT_64,
        BI_CONSTRUCT_DBL,
        BI_CONSTRUCT_TEXT,
        BI_CONSTRUCT_RANDOM,
        BI_CONSTRUCT_BINARY_ARRAY,
        BI_CONSTRUCT_BITWISE,
        BI_CONSTRUCT_COPY,
        BI_NEGATE,
        BI_ADD_SUB_BI,
        BI_ADD_SUB_PRIMITIVE,
        BI_MUL_BI,
        BI_MUL_PRIMITIVE,
        BI_DIV_BI,
        BI_DIV_PRIMITIVE,
        BI_REM_BI,
        BI_REM_PRIMITIVE,
        BI_MOD_BI,
        BI_MOD_PRIMITIVE,
        BI_DIV_INVERSE_PRIMITIVE,
        BI_REM_INVERSE_PRIMITIVE,
        BI_MOD_INVERSE_PRIMITIVE,
        BI_SQR,
        BI_POW,
        BI_BITWISE_OP,

        MBI_CONSTRUCT_EMPTY,
        MBI_CONSTRUCT_PRIMITIVE,
        MBI_CONSTRUCT_BI,
        MBI_CONSTRUCT_CAPACITY_HINT,
        ;

        companion object {
            val values = BigIntOp.values()
            val CARDINALITY = values.size
        }



    }

    internal val MBI_RESIZE_COUNTS =
        IntArray(MutableResizeOperation.CARDINALITY * MutableResizeEvent.CARDINALITY)

    enum class MutableResizeOperation {
        RESIZE_MAGIA,
        RESIZE_TMP1_MUL,
        RESIZE_TMP1_SQR,
        RESIZE_TMP1_KNUTH_DIVIDEND,
        RESIZE_TMP1_KARATSUBA_SQR,
        RESIZE_TMP2_KNUTH_DIVISOR,
        RESIZE_TMP2_KARATSUBA_Z1;

        companion object {
            val values = MutableResizeOperation.values()
            val CARDINALITY = values.size
        }
    }

    enum class MutableResizeEvent {
        INITIAL_UNHINTED,
        INITIAL_HINTED,
        REPEAT_UNHINTED,
        REPEAT_HINTED;

        companion object {
            val values = MutableResizeEvent.values()
            val CARDINALITY = values.size
        }
    }
}

