package org.futurerobotics.jargon.pathing.trajectory

import org.futurerobotics.jargon.linalg.*
import org.futurerobotics.jargon.math.Interval
import org.futurerobotics.jargon.math.Pose2d
import org.futurerobotics.jargon.model.*
import org.futurerobotics.jargon.pathing.PathPoint
import kotlin.math.*

/** A [VelocityConstraint] that limit's each motor's angular speed. */
open class MaxMotorSpeed protected constructor(
    private val interaction: MotorBotInteraction,
    private val maxes: Vec
) : VelocityConstraint {

    constructor(motorVelModel: MotorBotInteraction, maxes: List<Double>) :
            this(motorVelModel, maxes.toVec())

    constructor(motorVelModel: MotorBotInteraction, max: Double) :
            this(motorVelModel, genVec(motorVelModel.numMotors) { max })

    override fun maxVelocity(point: PathPoint): Double =
        maxSpeedFromBotVelTransform(point, interaction.motorVelFromBotVel, maxes)
}

/** A constraint that limit's each wheel's tangential speed. */
open class MaxWheelTangentialSpeed protected constructor(
    interaction: MotorBotInteraction,
    wheelInteraction: MotorWheelInteraction,
    maxes: Vec
) : MaxMotorSpeed(interaction, maxes.wheelToMotorVel(wheelInteraction)) {

    constructor(
        maxes: List<Double>,
        interaction: MotorBotInteraction,
        wheelInteraction: MotorWheelInteraction
    ) : this(interaction, wheelInteraction, maxes.toVec())

    constructor(
        max: Double,
        interaction: MotorBotInteraction,
        wheelInteraction: MotorWheelInteraction
    ) : this(interaction, wheelInteraction, genVec(interaction.numMotors) { max })
}

/** A constraint that limit's each motor's speed. */
open class MaxMotorAcceleration protected constructor(
    private val interaction: MotorBotInteraction,
    private val maxes: Vec
) : AccelerationConstraint {

    constructor(
        maxes: List<Double>,
        interaction: MotorBotInteraction
    ) : this(interaction, maxes.toVec())

    constructor(max: Double, interaction: MotorBotInteraction) :
            this(interaction, genVec(interaction.numMotors) { max })

    override fun accelRange(point: PathPoint, curVelocity: Double): Interval =
        accelRangeFromBotAccelTransform(point, curVelocity, interaction.motorAccelFromBotAccel, maxes)
}

/**
 * A constraint that limit's each wheel's tangential acceleration (output acceleration).
 */
open class MaxWheelTangentialAcceleration protected constructor(
    interaction: MotorBotInteraction,
    motorWheelInteraction: MotorWheelInteraction,
    maxes: Vec
) : MaxMotorAcceleration(interaction, maxes.wheelToMotorVel(motorWheelInteraction)) {

    constructor(
        maxes: List<Double>,
        interaction: MotorBotInteraction,
        motorWheelInteraction: MotorWheelInteraction
    ) : this(interaction, motorWheelInteraction, maxes.toVec())

    constructor(
        max: Double,
        interaction: MotorBotInteraction,
        motorWheelInteraction: MotorWheelInteraction
    ) : this(interaction, motorWheelInteraction, genVec(motorWheelInteraction.numMotors) { max })
}

/**
 * A constraint that limits the max motor voltages on each wheel.
 *
 * A lot of fun math going around.
 */
class MaxMotorVoltage private constructor(
    private val interaction: MotorBotInteraction,
    private val motorVelControllingModel: MotorVelocityControllingModel,
    private val motorFrictionModel: MotorFrictionModel,
    private val maxes: Vec
) : AccelerationConstraint {

    init {
        require(interaction.numMotors == motorVelControllingModel.numMotors) { "Num motors must match" }
        require(interaction.numMotors == maxes.size) { "Num motors must match" }
        require(interaction.numMotors == motorFrictionModel.numMotors) { "Num motors must match" }
    }

    constructor(
        maxes: List<Double>,
        interaction: MotorBotInteraction,
        motorVelControllingModel: MotorVelocityControllingModel,
        motorFrictionModel: MotorFrictionModel = ZeroMotorFrictionModel(interaction.numMotors)
    ) : this(interaction, motorVelControllingModel, motorFrictionModel, maxes.toVec())

    constructor(
        max: Double,
        interaction: MotorBotInteraction,
        motorVelControllingModel: MotorVelocityControllingModel,
        motorFrictionModel: MotorFrictionModel = ZeroMotorFrictionModel(interaction.numMotors)
    ) : this(interaction, motorVelControllingModel, motorFrictionModel, genVec(interaction.numMotors) { max })

    init {
        require(interaction.numMotors == motorVelControllingModel.numMotors) {
            "Number of motors in interaction ($interaction) != number of motors in " +
                    "motorVelModel(${motorVelControllingModel.numMotors})"
        }
    }

    override fun accelRange(point: PathPoint, curVelocity: Double): Interval {
        //lettuce assume that motorAccel = A*motorVel + B*volts + F*sign(motorVel)
        val maFmv = motorVelControllingModel.motorAccelFromMotorVel
        val vFma = motorVelControllingModel.voltsFromMotorAccel
        val maFba = interaction.motorAccelFromBotAccel
        val vFba = vFma * maFba
        val bv = (point.poseDeriv.vecRotated(-point.heading) * curVelocity).toVec()
        val mv = interaction.motorVelFromBotVel * bv
        val maFf = motorFrictionModel.motorAccelForMotorFriction
        val addend = vFma(maFmv * mv + maFf * sign(mv))

        return accelRangeFromBotAccelTransform(
            point, curVelocity, vFba, maxes, addend
        )
    }
}

///**
// * A constraint that limit's each motor's torque.
// */
//class MaxMotorTorque private constructor(
//    motorModels: List<MotorModel>,
//    private val interaction: MotorBotInteraction,
//    private val motorVelControllingModel: MotorVelocityControllingModel,
//    private val maxes: Vec
//) : AccelerationConstraint {
//
//    constructor(
//        maxes: List<Double>,
//        motorModels: List<MotorModel>,
//        interaction: MotorBotInteraction,
//        motorVelControllingModel: MotorVelocityControllingModel
//    ) : this(motorModels, interaction, motorVelControllingModel, maxes.toVec())
//
//    constructor(
//        max: Double,
//        motorModels: List<MotorModel>,
//        interaction: MotorBotInteraction,
//        motorVelControllingModel: MotorVelocityControllingModel
//    ) : this(motorModels, interaction, motorVelControllingModel, genVec(interaction.numMotors) { max })
//
//    init {
//        require(interaction.numMotors == motorVelControllingModel.numMotors) {
//            "Number of motors in interaction ($interaction) != number of motors in " +
//                    "motorVelModel(${motorVelControllingModel.numMotors})"
//        }
//    }
//
//    private val torqueFromVolts = diagMat(motorModels.map { 1 / it.voltsPerTorque })
//    override fun accelRange(point: PathPoint, curVelocity: Double): Interval {
//        val tFma = torqueFromVolts * motorVelControllingModel.voltsFromMotorAccel
//        val tFba = tFma * interaction.motorAccelFromBotAccel
//        val bv = (point.poseDeriv.vecRotated(-point.heading)).toVec()
//        val mv = interaction.motorVelFromBotVel * bv
//        val maFf = motorVelControllingModel.motorAccelForMotorFriction
//        val addend = tFma(maFf * sign(mv))
//
//        return accelRangeFromBotAccelTransform(
//            point, curVelocity, tFba, maxes, addend
//        )
//    }
//}

private fun rotationMatrix(angle: Double): Mat {
    val c = cos(angle)
    val s = sin(angle)
    return zeroMat(3, 3).apply {
        this[0, 0] = c
        this[0, 1] = -s
        this[1, 0] = s
        this[1, 1] = c
        this[2, 2] = 1.0
    }
}

private fun rotationMatrixDeriv(angle: Double, angleDeriv: Double): Mat {
    val c = cos(angle) * angleDeriv
    val s = sin(angle) * angleDeriv
    return zeroMat(3, 3).apply {
        this[0, 0] = -s
        this[0, 1] = -c
        this[1, 0] = c
        this[1, 1] = -s
    }
}

/**
 * Calculates the maximum allowable ds/dt given that
 * ```
 * mat * bot_vel << +/-maxes
 * ```
 * where:
 * - `<<` indicates that the individual elements of the left-hand vector is within the ranges specified by the
 *  right-hand side.
 * - [mat] is a given matrix that transforms bot velocity into the constrained value
 * - `bot_vel` is the bot's velocity vector (see [Pose2d.toVec])
 *
 * This assumes all maximums are positive.
 */
fun maxSpeedFromBotVelTransform(
    point: PathPoint,
    mat: Mat,
    maxes: Vec
): Double {
    val factors = mat * point.poseDeriv.vecRotated(-point.heading).toVec()
    var res = Double.POSITIVE_INFINITY
    repeat(maxes.size) { i ->
        val max = maxes[i]
        val factor = factors[i]
        val curMax = abs(max / factor)
        res = min(res, curMax)
    }
    return res
}

/**
 * Calculates the maximum allowable ds/dt given that
 * ```
 * mat * bot_accel << +/-maxes + addend
 * ```
 * where:
 * - `<<` indicates that all the individual elements of the left-hand vector is within the ranges specified by the
 *  right-hand side.
 * - [mat] is a given matrix that transforms bot acceleration into the constrained value
 * - `bot_accel` is the bot's acceleration vector (see [Pose2d.toVec])
 * - [maxes] is a vector of allowable maximums of each of the components, which should all be positive
 * - [addend] is an optional vector of addends.
 *
 */
fun accelRangeFromBotAccelTransform(
    point: PathPoint,
    curVelocity: Double,
    mat: Mat,
    maxes: Vec,
    addend: Vec = zeroVec(maxes.size)
): Interval {
    val rot = rotationMatrix(-point.heading)
    val rotDeriv = rotationMatrixDeriv(-point.heading, -point.headingDeriv)
    val mults = mat * rot * point.poseDeriv.toVec()
    // Additional acceleration due to just motion (rotation and centripetal)
    // Similar to GlobalToBot.motion
    val fullAddend = addend - mat *
            (rot * (point.poseSecondDeriv * curVelocity.pow(2)).toVec() +
                    rotDeriv * (point.poseDeriv * curVelocity).toVec())
    var res = Interval.REAL
    repeat(maxes.size) { i ->
        val max = maxes[i]
        val mult = mults[i]
        val add = fullAddend[i]
        val interval = Interval.symmetricRegular(max / mult, add / mult)
        res = res.intersect(interval)
        if (res.isEmpty()) return@repeat
    }
    return res
}

private fun Vec.wheelToMotorVel(interaction: MotorWheelInteraction): Vec =
    interaction.motorVelFromWheelVel * this
