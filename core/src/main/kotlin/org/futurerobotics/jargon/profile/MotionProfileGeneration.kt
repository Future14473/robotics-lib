@file:JvmName("GenerateMotionProfile")

package org.futurerobotics.jargon.profile

import org.futurerobotics.jargon.math.DoubleProgression
import org.futurerobotics.jargon.math.EPSILON
import org.futurerobotics.jargon.math.distTo
import org.futurerobotics.jargon.math.notNaNOrElse
import org.futurerobotics.jargon.util.extendingDownDoubleSearch
import org.futurerobotics.jargon.util.mapToSelf
import org.futurerobotics.jargon.util.stepToAll
import kotlin.math.*

private const val MAX_VEL = 10000.0
private const val BINARY_SEARCH_INITIAL_STEP_RATIO = 2
/**
 * Calculates an approximately optimal [MotionProfile], given a [MotionProfileConstrainer] and other options.
 *
 * [targetStartVel] and [targetEndVel] specify the target start and end velocities, respectively, However, if this
 * results in a profile not within constraints, the actual start and end velocities may be lower.
 *
 * [segmentSize] specifies the size of the segments used in the profile generation.
 *
 * [maxVelSearchTolerance] specifies the tolerance in which the maximum velocities due to
 * satisfying acceleration constraints will be searched for, if needed (heavy heuristic
 * binary search). This will not happen unless need be.
 *
 * This uses a modified version of the algorithm described in section 3.2 of:
 *  [http://www2.informatik.uni-freiburg.de/~lau/students/Sprunk2008.pdf].
 *
 *  The differences include: this method only considers the maximum velocity and acceleration at the point at one
 *  endpoint of a segment (or the other endpoint when doing a backwards pass), and uses that to determine the
 *  maximum velocities and acceleration of the entire segment. This changes the formulas used to determine the
 *  constraints to use, but makes it much simpler. This does introduce some approximation error, and requires some
 *  binary search at some point, but it _greatly_ simplifies calculations,  More importantly, this allows for a more
 *  modular and a wider variety of combination of constraints. The error introduced is negligible or tiny when segments
 *  are sufficiently small, and binary search has shown to not significantly impact generation times, and can possibly
 *  be avoided altogether with "non-demanding" constraints.
 *
 *  I will write a short paper about this someday.
 */
@JvmOverloads
fun generateDynamicProfile(
    constrainer: MotionProfileConstrainer,
    distance: Double,
    targetStartVel: Double = 0.0,
    targetEndVel: Double = 0.0,
    segmentSize: Double = 0.01,
    maxVelSearchTolerance: Double = 0.01
    //may introduce a parameters class if start to have too many parameters
): MotionProfile {
    require(distance > 0) { "distance ($distance) must be > 0" }
    require(targetStartVel >= 0) { "targetStartVel ($targetStartVel) must be >= 0" }
    require(targetEndVel >= 0) { "targetEndVel ($targetEndVel) must be >= 0" }
    require(segmentSize > 0) { "segmentSize ($segmentSize) must be > 0" }
    require(segmentSize <= distance) { "segmentSize ($$segmentSize) must be <= dist ($distance)" }
    require(maxVelSearchTolerance > 0) { "maxVelSearchTolerance ($maxVelSearchTolerance) must be > 0" }
    val segments = ceil(distance / segmentSize).toInt()
    val points = DoubleProgression.fromNumSegments(0.0, distance, segments).toList()
    val pointConstraints: List<PointConstraint> = constrainer.stepToAll(points)
    val maxVels = pointConstraints.mapIndexedTo(ArrayList(points.size)) { i, it ->
        it.maxVelocity.also {
            require(it >= 0) { "All maximum velocities given by constrainer should be >= 0, got $it at segment $i" }
        }
    }


    maxVels[0] = min(maxVels[0], targetStartVel)
    maxVels.lastIndex.let {
        maxVels[it] = min(maxVels[it], targetEndVel)
    }
    maxVels.mapToSelf { min(it, MAX_VEL) }

    accelerationPass(maxVels, pointConstraints, points, maxVelSearchTolerance, false)
    accelerationPass( //reverse
        maxVels.asReversed(),
        pointConstraints.asReversed(),
        points.asReversed(),
        maxVelSearchTolerance,
        true
    )
    val pointVelPairs = points.zip(maxVels)
    return SegmentsMotionProfile.fromPointVelPairs(pointVelPairs)
}

private fun accelerationPass(
    maxVels: MutableList<Double>,
    accelGetters: List<PointConstraint>,
    points: List<Double>,
    binarySearchTolerance: Double,
    reversed: Boolean
) {
    val tolerance = max(binarySearchTolerance, EPSILON)
    repeat(points.size - 1) {
        var v0 = maxVels[it]
        val accelGetter = accelGetters[it]
        val dx = points[it] distTo points[it + 1] //works for backwards
        var aMax = getAMaxOrNaN(dx, v0, accelGetter, reversed)
        if (aMax.isNaN()) {
            if (v0 == 0.0) throwBadAccelAtZeroVel()
            //OH NO, ITS BINARY SEARCH!
            // heuristic search, typically < 10 iterations, and only occurs when necessary,
            // and typically happens < 1% of the time
            val newV0 = extendingDownDoubleSearch(
                0.0, v0, tolerance, searchingFor = false
            ) { v -> getAMaxOrNaN(dx, v, accelGetter, reversed).isNaN() }
            aMax = getAMaxOrNaN(dx, newV0, accelGetter, reversed).notNaNOrElse(::throwBadAccelAtZeroVel)
            v0 = newV0
            maxVels[it] = newV0
        }
        val v1 = sqrt(v0.pow(2) + 2 * aMax * dx).notNaNOrElse { 0.0 }
        val actualV1 = min(v1, maxVels[it + 1])
        maxVels[it + 1] = actualV1
    }
}

private fun getAMaxOrNaN(dx: Double, v: Double, accelGetter: PointConstraint, reversed: Boolean): Double {
    val aMin = -v.pow(2) / 2 / dx
    val interval = accelGetter.accelRange(v)
    val aMaxMaybe = if (reversed) -interval.a else interval.b
    return if (aMaxMaybe > aMin) aMaxMaybe else Double.NaN
}

private fun throwBadAccelAtZeroVel(): Nothing =
    throw RuntimeException(
        "Unsatisfiable constraints: The current constraint's did not return a non-empty interval for acceleration even" +
                " with a current velocity of 0.0."
    )
