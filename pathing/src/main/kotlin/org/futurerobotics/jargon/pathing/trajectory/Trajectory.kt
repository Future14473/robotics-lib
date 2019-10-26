package org.futurerobotics.jargon.pathing.trajectory

import org.futurerobotics.jargon.math.Pose2d
import org.futurerobotics.jargon.math.epsEq
import org.futurerobotics.jargon.mechanics.LinearMotionState
import org.futurerobotics.jargon.mechanics.MotionState
import org.futurerobotics.jargon.mechanics.ValueMotionState
import org.futurerobotics.jargon.pathing.Path
import org.futurerobotics.jargon.pathing.PathPoint
import org.futurerobotics.jargon.profile.MotionProfile
import org.futurerobotics.jargon.profile.MotionProfiled
import org.futurerobotics.jargon.util.Stepper
import kotlin.math.pow

/**
 * Represents a trajectory; that is a Path paired with time/velocity info on traversal (a [MotionProfiled]).
 *
 * @see generateTrajectory
 */
class Trajectory(private val path: Path, private val profile: MotionProfile) : MotionProfiled<MotionState<Pose2d>> {

    /**
     * The duration of time to traverse this [Trajectory] (ideally)
     *
     * _in a perfect world where friction and entropy and floating-
     * point errors and capacitance and noise and delay and approximation errors and internal resistance and
     * dampening and time and space don't exist._
     * */
    override val duration: Double get() = profile.duration
    /**
     * The total length of this Trajectory
     * @see [Path]
     */
    val distance: Double get() = path.length

    init {
        require(path.length epsEq profile.distance) {
            "Path length ${path.length} and profile length ${profile.distance} must match"
        }
    }

    /**
     * Gets the [MotionState] of Poses after the specified [time] traversing this trajectory.
     */
    override fun atTime(time: Double): MotionState<Pose2d> {
        val state = profile.atTime(time)
        val point = path.pointAt(state.s)
        return getState(state, point)
    }

    override fun stepper(): Stepper<Double, MotionState<Pose2d>> {
        val pathStepper = path.stepper()
        val profileStepper = profile.stepper()
        return Stepper {
            val state = profileStepper.stepTo(it)
            val point = pathStepper.stepTo(state.s)
            getState(state, point)
        }
    }

    private fun getState(state: LinearMotionState, point: PathPoint): MotionState<Pose2d> {
        val pose = point.pose
        val poseDeriv = point.poseDeriv
        val poseSecondDeriv = point.poseSecondDeriv
        return ValueMotionState(
            pose,
            poseDeriv * state.v,
            poseSecondDeriv * state.v.pow(2) + poseDeriv * state.a
        )
    }
}
