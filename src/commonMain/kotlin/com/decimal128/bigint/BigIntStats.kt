package com.decimal128.bigint

import kotlin.time.TimeSource.Monotonic

object BigIntStats {

    internal val BI_OP_COUNTS = LongArray(StatsOp.CARDINALITY)

    fun snapshot(): Snapshot = Snapshot(Monotonic.markNow(), BI_OP_COUNTS.copyOf())

    enum class StatsOp {
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

        MBI_SET_ADD_SUB_PRIMITIVE,
        MBI_SET_ADD_SUB_BI,
        MBI_SET_MUL_PRIMITIVE,
        MBI_SET_MUL_BI,
        MBI_SET_SQR_PRIMITIVE,
        MBI_SET_SQR_SCHOOLBOOK,
        MBI_SET_SQR_KARATSUBA,
        MBI_SET_POW,
        MBI_SET_DIV_PRIMITIVE,
        MBI_SET_DIV_BI_FASTPATH,
        MBI_SET_DIV_BI_KNUTH,
        MBI_SET_REM_PRIMITIVE,
        MBI_SET_REM_BI_FASTPATH,
        MBI_SET_REM_BI_KNUTH,
        MBI_SET_MOD_PRIMITIVE,
        MBI_SET_MOD_BI_FASTPATH,
        MBI_SET_MOD_BI_KNUTH,
        MBI_ADD_SQR_PRIMITIVE,
        MBI_ADD_SQR_BI,
        MBI_SET_BITWISE_OP,
        MBI_MONTGOMERY_REDC,

        MBI_RESIZE_MAGIA_INITIAL_UNHINTED,
        MBI_RESIZE_MAGIA_INITIAL_HINTED,
        MBI_RESIZE_MAGIA_REPEAT_UNHINTED,
        MBI_RESIZE_MAGIA_REPEAT_HINTED,

        MBI_RESIZE_TMP1_MUL_INITIAL_UNHINTED,
        MBI_RESIZE_TMP1_MUL_INITIAL_HINTED,
        MBI_RESIZE_TMP1_MUL_REPEAT_UNHINTED,
        MBI_RESIZE_TMP1_MUL_REPEAT_HINTED,

        MBI_RESIZE_TMP1_SQR_INITIAL_UNHINTED,
        MBI_RESIZE_TMP1_SQR_INITIAL_HINTED,
        MBI_RESIZE_TMP1_SQR_REPEAT_UNHINTED,
        MBI_RESIZE_TMP1_SQR_REPEAT_HINTED,

        MBI_RESIZE_TMP1_KNUTH_DIVIDEND_INITIAL_UNHINTED,
        MBI_RESIZE_TMP1_KNUTH_DIVIDEND_INITIAL_HINTED,
        MBI_RESIZE_TMP1_KNUTH_DIVIDEND_REPEAT_UNHINTED,
        MBI_RESIZE_TMP1_KNUTH_DIVIDEND_REPEAT_HINTED,

        MBI_RESIZE_TMP1_KARATSUBA_SQR_INITIAL_UNHINTED,
        MBI_RESIZE_TMP1_KARATSUBA_SQR_INITIAL_HINTED,
        MBI_RESIZE_TMP1_KARATSUBA_SQR_REPEAT_UNHINTED,
        MBI_RESIZE_TMP1_KARATSUBA_SQR_REPEAT_HINTED,

        MBI_RESIZE_TMP2_KNUTH_DIVISOR_INITIAL_UNHINTED,
        MBI_RESIZE_TMP2_KNUTH_DIVISOR_INITIAL_HINTED,
        MBI_RESIZE_TMP2_KNUTH_DIVISOR_REPEAT_UNHINTED,
        MBI_RESIZE_TMP2_KNUTH_DIVISOR_REPEAT_HINTED,

        MBI_RESIZE_TMP2_KARATSUBA_Z1_INITIAL_UNHINTED,
        MBI_RESIZE_TMP2_KARATSUBA_Z1_INITIAL_HINTED,
        MBI_RESIZE_TMP2_KARATSUBA_Z1_REPEAT_UNHINTED,
        MBI_RESIZE_TMP2_KARATSUBA_Z1_REPEAT_HINTED,

        ;

        companion object {
            val values = StatsOp.values()
            val CARDINALITY = values.size

            val MBI_RESIZE_MAGIA = MBI_RESIZE_MAGIA_INITIAL_UNHINTED
            val MBI_RESIZE_TMP1_MUL = MBI_RESIZE_TMP1_MUL_INITIAL_UNHINTED
            val MBI_RESIZE_TMP1_SQR = MBI_RESIZE_TMP1_SQR_INITIAL_UNHINTED
            val MBI_RESIZE_TMP1_KNUTH_DIVIDEND = MBI_RESIZE_TMP1_KNUTH_DIVIDEND_INITIAL_UNHINTED
            val MBI_RESIZE_TMP1_KARATSUBA_SQR = MBI_RESIZE_TMP1_KARATSUBA_SQR_INITIAL_UNHINTED
            val MBI_RESIZE_TMP2_KNUTH_DIVISOR = MBI_RESIZE_TMP2_KNUTH_DIVISOR_INITIAL_UNHINTED
            val MBI_RESIZE_TMP2_KARATSUBA_Z1 = MBI_RESIZE_TMP2_KARATSUBA_Z1_INITIAL_UNHINTED
        }

    }

    class Snapshot internal constructor(val mark: Monotonic.ValueTimeMark, val counts: LongArray) {
        fun delta(prevSnapshot: Snapshot): Interval {
            require (mark > prevSnapshot.mark)
            val deltas = LongArray(counts.size) { i -> counts[i] - prevSnapshot.counts[i] }
            return Interval(mark - prevSnapshot.mark, deltas)
        }

        override fun toString(): String = buildString {
            appendLine("mark: $mark")
            for (e in StatsOp.values) {
                appendLine("${e.name}: ${counts[e.ordinal]}")
            }
        }
    }

    class Interval internal constructor(val duration: kotlin.time.Duration, val counts: LongArray) {
        override fun toString(): String = toString(null, null)

        fun toString(nameFilter: Regex?, valuePredicate: ((Long) -> Boolean)? = null): String = buildString {
            appendLine("duration: $duration")
            for (e in StatsOp.values) {
                val name = e.name
                val count = counts[e.ordinal]
                val nameMatch = nameFilter == null || name.contains(nameFilter)
                val valueMatch = valuePredicate == null || valuePredicate(count)
                if (nameMatch && valueMatch)
                    appendLine("$name: $count")
            }
        }
    }

}
