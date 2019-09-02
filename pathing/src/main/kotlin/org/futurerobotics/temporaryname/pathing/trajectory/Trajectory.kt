package org.futurerobotics.temporaryname.pathing.trajectory

import org.futurerobotics.temporaryname.math.epsEq
import org.futurerobotics.temporaryname.mechanics.LinearMotionState
import org.futurerobotics.temporaryname.mechanics.PoseMotionState
import org.futurerobotics.temporaryname.pathing.Path
import org.futurerobotics.temporaryname.pathing.PathPoint
import org.futurerobotics.temporaryname.pathing.pose
import org.futurerobotics.temporaryname.pathing.poseDeriv
import org.futurerobotics.temporaryname.profile.MotionProfile
import org.futurerobotics.temporaryname.profile.MotionProfiled
import org.futurerobotics.temporaryname.util.Stepper

/**
 * Represents a trajectory; that is a Path paired with time/velocity info on traversal (a [MotionProfiled]).
 *
 * @see TrajectoryGenerator
 */
class Trajectory(private val path: Path, private val profile: MotionProfile) : MotionProfiled<PoseMotionState> {

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
     * Gets the [PoseMotionState] after the specified [time] traversing this trajectory.
     */
    override fun atTime(time: Double): PoseMotionState {
        val state = profile.atTime(time)
        val point = path.atLength(state.s)
        return getState(state, point)
    }

    override fun stepper(): Stepper<Double, PoseMotionState> {
        val pathStepper = path.stepper()
        val profileStepper = profile.stepper()
        return Stepper {
            val state = profileStepper.stepTo(it)
            val point = pathStepper.stepTo(state.s)
            getState(state, point)
        }
    }

    private fun getState(state: LinearMotionState, point: PathPoint): PoseMotionState {
        return PoseMotionState(point.pose, point.poseDeriv * state.v) //second derivative probably not necessary?
    }
}
