package org.futurerobotics.temporaryname.math

import kotlin.math.abs

/**
 * Represents an Interval on the number line, represented by two endpoints
 * If the interval is empty, both the endpoints will be NaN.
 *
 * See factory methods.
 *
 * @param a The lower bound of this interval.
 * @param b The upper bound of this interval. Can be [Double.POSITIVE_INFINITY]
 */
class Interval private constructor(val a: Double, val b: Double) {
    init {
        assert(isEmpty || a <= b) { "Invalid interval!! No cookie for the developer!" }
    }

    /** If this interval is empty (contains no values) */
    val isEmpty: Boolean get() = a.isNaN() || b.isNaN()

    /** If this interval consists of a single point. */
    val isPoint: Boolean get() = a == b

    override fun hashCode(): Nothing = throw UnsupportedOperationException()
    /**
     * Returns the lower bound [a]
     */
    operator fun component1(): Double = a

    /**
     * Returns the upper bound [b]
     */
    operator fun component2(): Double = b

    /**
     * Gets a bound by [index], which must be 0 or 1.
     *
     * An index of 0 will return the lower bound
     * An index of 1 will return the upper bound.
     */
    operator fun get(index: Int): Double = when (index) {
        0 -> a
        1 -> b
        else -> throw IndexOutOfBoundsException("Interval index must be 0 or 1, got $index")
    }

    /** @return if [v] is contained in the interval. */
    operator fun contains(v: Double): Boolean = v in a..b //includes empty case

    /** @return if [v] is contained in this interval, with leniency at the endpoints. */
    fun epsContains(v: Double): Boolean {
        return !isEmpty && v in (a - EPSILON)..(b + EPSILON)
    }

    override fun equals(other: Any?): Boolean = (this === other) || (other is Interval && a == other.a && b == other.b)
    /** @return if this interval epsilon equals the other via endpoints */
    infix fun epsEq(other: Interval): Boolean = this.isEmpty && other.isEmpty || a epsEq other.a && b epsEq other.b

    /** @return the intersection of this interval with another. */
    fun intersect(that: Interval): Interval {
        if (this.isEmpty || that.isEmpty || that.a > this.b || this.a > that.b) return EMPTY
        val gta: Interval
        val lta: Interval
        if (this.a < that.a) {
            gta = that
            lta = this
        } else {
            gta = this
            lta = that
        }
        if (gta.b <= lta.b) return gta //lta[ gta(--) ]
        return gta.a intervalTo lta.b  //lta[ gta(--] )
    }

    companion object {
        /** An empty interval */
        @JvmField
        val EMPTY: Interval = Interval(Double.NaN, Double.NaN)
        /** An interval spanning all real numbers. */
        @JvmField
        val REAL: Interval = Interval(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)

        /**
         * Returns a interval by endpoints [a] and [b].
         *
         * Will return an empty interval if b < a, or either endpoint is `NaN`
         */
        @JvmStatic
        fun of(a: Double, b: Double): Interval {
            if (a.isNaN() || b.isNaN() || b < a) return EMPTY
            return Interval(a, b)
        }

        /**
         * Returns an interval by endpoints [a] and [b].
         *
         * Will swap endpoints if b < a.
         *
         * Will return empty interval if either endpoint is `NaN`
         */
        @JvmStatic
        fun ofRegular(a: Double, b: Double): Interval {
            if (a.isNaN() || b.isNaN()) return EMPTY
            return if (b > a) Interval(a, b) else Interval(b, a)
        }

        /**
         * Returns an interval with a [center] and a [radius].
         *
         * Will return an empty interval if radius < 0,
         *
         * or `center` or `radius` is `NaN`
         */
        @JvmOverloads
        @JvmStatic
        fun symmetric(radius: Double, center: Double = 0.0): Interval {
            if (radius.isNaN() || center.isNaN() || radius < 0) return EMPTY
            if (radius == Double.POSITIVE_INFINITY) return REAL
            return Interval(center - radius, center + radius)
        }

        /**
         * Returns an interval with a [center] and a [radius].
         *
         * Radius will be interpreted as absolute value.
         *
         * Will return an empty interval if `center` or `radius` is `NaN`
         */
        @JvmOverloads
        @JvmStatic
        fun symmetricRegular(radius: Double, center: Double = 0.0): Interval = symmetric(abs(radius), center)
    }
}

/**
 * Constructs an [Interval] from [this] to [b].
 * @see Interval.of
 */
inline infix fun Double.intervalTo(b: Double): Interval = Interval.of(this, b)

/**
 * Constructs an regular [Interval] from [this] to [b].
 * @see Interval.ofRegular
 */
inline infix fun Double.regularIntervalTo(b: Double): Interval = Interval.ofRegular(this, b)

/**
 * @return if [this] is contained in this interval, with leniency at the endpoints.
 * @see Interval.epsContains
 */
inline infix fun Double.epsIn(i: Interval): Boolean = i.epsContains(this)

/**
 * Ensures that this value lies in the specified [Interval] i.
 * Will return [Double.NaN] if the interval is empty.
 */
inline infix fun Double.coerceIn(i: Interval) = coerceIn(i.a, i.b)