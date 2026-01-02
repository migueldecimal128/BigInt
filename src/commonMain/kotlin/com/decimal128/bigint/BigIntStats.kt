package com.decimal128.bigint

import kotlin.time.TimeSource.Monotonic

/**
 * Statistics collection system for BigInt operations.
 *
 * Tracks operation counts across all BigInt and MutableBigInt operations including:
 * - Construction operations
 * - Arithmetic operations (add, subtract, multiply, divide, etc.)
 * - Bitwise operations
 * - Memory resize events
 *
 * Usage:
 * ```
 * val snapshot1 = BigIntStats.snapshot()
 * // ... perform operations ...
 * val snapshot2 = BigIntStats.snapshot()
 * val interval = snapshot2.delta(snapshot1)
 * println(interval.toString("MBI_".toRegex()) { it > 0 })
 * ```
 */
object BigIntStats {

    internal val BI_OP_COUNTS = LongArray(StatsOp.CARDINALITY)

    /**
     * Captures the current state of all operation counters along with a timestamp.
     *
     * @return A [Snapshot] containing the current timestamp and a copy of all counter values
     */
    fun snapshot(): Snapshot = Snapshot(Monotonic.markNow(), BI_OP_COUNTS.copyOf())

    /**
     * Enumeration of all tracked BigInt operations.
     *
     * Operations are categorized by prefix:
     * - `BI_*`: Immutable BigInt operations
     * - `MBI_*`: MutableBigInt operations
     * - `MBI_RESIZE_*`: Memory allocation/resize events with metadata:
     *   - Target buffer (MAGIA, TMP1_*, TMP2_*)
     *   - Operation context (MUL, SQR, KNUTH_*, KARATSUBA_*)
     *   - Event type (INITIAL/REPEAT, HINTED/UNHINTED)
     */
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

            /** Base ordinal for MAGIA buffer resize operations */
            val MBI_RESIZE_MAGIA = MBI_RESIZE_MAGIA_INITIAL_UNHINTED
            /** Base ordinal for TMP1 multiply buffer resize operations */
            val MBI_RESIZE_TMP1_MUL = MBI_RESIZE_TMP1_MUL_INITIAL_UNHINTED
            /** Base ordinal for TMP1 square buffer resize operations */
            val MBI_RESIZE_TMP1_SQR = MBI_RESIZE_TMP1_SQR_INITIAL_UNHINTED
            /** Base ordinal for TMP1 Knuth dividend buffer resize operations */
            val MBI_RESIZE_TMP1_KNUTH_DIVIDEND = MBI_RESIZE_TMP1_KNUTH_DIVIDEND_INITIAL_UNHINTED
            /** Base ordinal for TMP1 Karatsuba square buffer resize operations */
            val MBI_RESIZE_TMP1_KARATSUBA_SQR = MBI_RESIZE_TMP1_KARATSUBA_SQR_INITIAL_UNHINTED
            /** Base ordinal for TMP2 Knuth divisor buffer resize operations */
            val MBI_RESIZE_TMP2_KNUTH_DIVISOR = MBI_RESIZE_TMP2_KNUTH_DIVISOR_INITIAL_UNHINTED
            /** Base ordinal for TMP2 Karatsuba Z1 buffer resize operations */
            val MBI_RESIZE_TMP2_KARATSUBA_Z1 = MBI_RESIZE_TMP2_KARATSUBA_Z1_INITIAL_UNHINTED
        }

    }

    /**
     * A point-in-time capture of operation statistics.
     *
     * Snapshots capture both a monotonic timestamp and copies of all operation counters,
     * allowing measurement of operations performed between two snapshots.
     *
     * @property mark Monotonic timestamp when this snapshot was captured
     * @property counts Copy of all operation counters at the time of snapshot
     */
    class Snapshot internal constructor(val mark: Monotonic.ValueTimeMark, val counts: LongArray) {
        /**
         * Computes the difference between this snapshot and a previous snapshot.
         *
         * @param prevSnapshot An earlier snapshot to compare against
         * @return An [Interval] containing the elapsed time and operation count deltas
         * @throws IllegalArgumentException if this snapshot is not newer than [prevSnapshot]
         */
        fun delta(prevSnapshot: Snapshot): Interval {
            require (mark > prevSnapshot.mark)
            val deltas = LongArray(counts.size) { i -> counts[i] - prevSnapshot.counts[i] }
            return Interval(mark - prevSnapshot.mark, deltas)
        }

        /**
         * Returns a string representation showing the timestamp and all operation counts.
         */
        override fun toString(): String = buildString {
            appendLine("mark: $mark")
            for (e in StatsOp.values) {
                appendLine("${e.name}: ${counts[e.ordinal]}")
            }
        }
    }

    /**
     * Represents operations performed during a time interval between two snapshots.
     *
     * An interval contains the elapsed duration and the delta (difference) in operation counts
     * between two snapshots. This allows analysis of which operations were performed and how
     * frequently during a specific time period.
     *
     * @property duration Elapsed time between the two snapshots
     * @property counts Delta in operation counts (later snapshot - earlier snapshot)
     */
    class Interval internal constructor(val duration: kotlin.time.Duration, val counts: LongArray) {
        /**
         * Returns a string representation of the interval with all operation deltas.
         */
        override fun toString(): String = toString(null, null)

        /**
         * Returns a filtered string representation of the interval.
         *
         * @param nameFilter Optional regex to filter operations by name. Only operations whose
         *                   names match this regex will be included.
         * @param valuePredicate Optional predicate to filter operations by count. Only operations
         *                       where this predicate returns true will be included.
         * @return Formatted string showing duration and matching operation deltas
         *
         * Example:
         * ```
         * // Show only MBI operations with non-zero counts
         * interval.toString("^MBI_".toRegex()) { it > 0 }
         *
         * // Show only operations that occurred more than 100 times
         * interval.toString(null, valuePredicate = { it > 100 })
         * ```
         */
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
