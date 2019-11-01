package org.futurerobotics.jargon.mechanics

import org.futurerobotics.jargon.linalg.*
import org.futurerobotics.jargon.math.Pose2d
import org.futurerobotics.jargon.math.Vector2d
import org.futurerobotics.jargon.math.convert.*
import org.futurerobotics.jargon.util.toImmutableList
import org.futurerobotics.jargon.util.zipForEachIndexed

/**
 * Represents a drive model that is framed analyzing how motor voltages affects the motor's velocity, with possibly
 * some interactions between separate motors (applying a voltage to one motor may also move another).
 */
interface MotorVelocityModel {
    // motor <-> motor
    /**
     * The number of motors in this model.
     */
    val numMotors: Int
    /**
     * Transforms motor velocities into the voltages needed to keep it at that speed.
     *
     * This can be due to the motors acting as generators, hence producing a voltage, and/or the
     * contribution of other frictional forces.
     *
     * Non-diagonal entries may or may not be 0 (there are interactions between motors).
     *
     * Adding this with voltages obtained from [voltsFromMotorAccel] and [voltsForMotorFriction] gets the modeled
     * voltage signal.
     */
    val voltsFromMotorVel: Mat
    /**
     * Transforms motor acceleration into the voltages needed to do so, given no initial velocity.
     *
     * This may factor in possible frictional forces related to friction.
     *
     * Non-diagonal entries may or may not be 0 (there are interactions between motors).
     *
     * Adding this with voltagess obtained from [voltsFromMotorVel] and [voltsForMotorFriction] gets the modeled
     * voltage signal.
     * @see motorAccelFromVolts
     */
    val voltsFromMotorAccel: Mat
    /**
     * Transforms motor voltage into expected motor acceleration, given no initial velocity.
     *
     * This is used for observation/prediction.
     *
     * Non-diagonal entries may or may not be 0 (there are interactions between motors).
     *
     * This should be the inverse of [voltsFromMotorAccel].
     * @see voltsFromMotorAccel
     */
    val motorAccelFromVolts: Mat
    /**
     * Gets the expected motor (de)acceleration due to existing motor velocities; caused by the motor
     * acting as a generator hence producing a voltage that slows it down, and/or other frictional/friction-like
     * forces that are dependent on the bot's velocity.
     *
     * This should not be used for _constant_ friction that depends only on motor's direction, for that, see
     * [motorAccelForMotorFriction].
     *
     * This should (usually) have ***negative*** diagonal entries.
     *
     * Non-diagonal entries may or may not be 0 (there are interactions between motors).
     */
    val motorAccelFromMotorVel: Mat
    /**
     * Represents the _constant_ components of frictional forces; Transforms a vector of _signs_ of the motor's
     * velocities into how that affects the motor's acceleration.
     *
     * For non_constant components that depend on the motor velocity, see [motorAccelFromMotorVel].
     *
     * Should (usually) have ***negative*** diagonal entries.
     *
     * May be 0 to assume no friction.
     *
     * This should (usually) have negative diagonal entries.
     *
     * @see voltsForMotorFriction
     */
    val motorAccelForMotorFriction: Mat
    /**
     * Represents the _constant_ components of frictional forces; Transforms a vector of _signs_ of the motor's
     * velocities into the amount of volts needed to add to compensate for it.
     *
     * For non_constant components that depend on the motor velocity, see [motorAccelFromMotorVel].
     *
     * Should (usually) have ***positive*** diagonal entries.
     *
     * May be 0 to assume no friction.
     *
     * @see voltsForMotorFriction
     */
    val voltsForMotorFriction: Mat
}

/**
 * Represents a drive model that is framed analyzing how motor voltages affects the bot's velocity, without regard
 * for the motor's velocities.
 *
 * This is a tightly coupled model and there is no easy way to account for some types of friction unless at the motor
 * level, and so almost all cases we suggest using `MotorVelocityModel` and bridging with `MotorBotInteractions`
 * instead.
 *
 * @see MotorVelocityModel
 */
interface BotVelocityModel {

    /**
     * If this model is the fancy math word that means this can move in any direction AND rotate independently of each
     * other.
     */
    val isHolonomic: Boolean

    /** The number of wheels. */
    val numWheels: Int
    //volts <-> bot
    /**
     * Transforms a bot velocity vector (see [Pose2d.toVec]) into the expected motor volts to sustain that speed
     * with no acceleration.
     *
     * This can be due to motors acting as generators, hence producing a voltage; and/or the contribution of
     * other frictional forces.
     *
     *  This can then be added with [voltsFromBotVel] to get the actual volts.
     */
    val voltsFromBotVel: Mat
    /**
     * Transform a bot acceleration vector (see [Pose2d.toVec]) into needed motor volts, assuming no friction and
     * no initial speed.
     *
     * This can then be added with [voltsFromBotVel] to get the actual volts.
     * @see botAccelFromVolts
     */
    val voltsFromBotAccel: Mat
    /**
     * Transform motor volts into a bot acceleration vector (see [Pose2d.toVec]), assuming no
     * friction and no initial speed.
     *
     * This should be the inverse of [voltsFromBotAccel]
     *
     * @see voltsFromBotAccel
     */
    val botAccelFromVolts: Mat
    /**
     * Transform the currents bot velocity into the expected bot deceleration.
     *
     * This can be due to motors acting as generators and hence producing a voltage, or the contribution
     * of other frictional forces.
     *
     * Should (usually) have ***negative*** diagonal entries.
     * @see voltsFromBotAccel
     */
    val botAccelFromBotVel: Mat

//    /**
//     * Gets the expected constant component of the bot acceleration, from transforming a vector of the _signs_ of the
//     * bot velocity (since constant friction is usually related to the direction of motion).
//     *
//     * For _non_ constant friction/friction-like forces, see [botAccelFromBotVel]
//     * @see voltsForBotFriction
//     */
//    val botAccelForBotFriction: Mat
//    /**
//     * Gets the expected amount of volts needed to be added to compensate for _constant_ friction, from transforming a
//     * vector of the _signs_ of the bot velocity (since constant friction is usually related to the direction of
//     * motion).
//     *
//     * For _non_ constant friction/friction-like forces, see [botAccelFromBotVel] and [voltsFromBotAccel]
//     * @see botAccelForBotFriction
//     */
//    val voltsForBotFriction: Mat
}

/**
 * Represents how to transforming of motor velocities/accelerations into bot velocities/accelerations, and vice versa.
 */
interface MotorBotVelInteraction {

    /** The number of motors/wheels. */
    val numMotors: Int
    //bot <-> motor
    /**
     * Transforms a bot velocity vector (see [Pose2d.toVec]) into motor angular velocities.
     * @see botVelFromMotorVel
     */
    val motorVelFromBotVel: Mat
    /**
     * Transforms motor angular velocities into a bot velocity vector (see [Pose2d.toVec]), with least squares error.
     * @see motorVelFromBotVel
     */
    val botVelFromMotorVel: Mat
    /**
     * Transforms a bot acceleration vector (see [Pose2d.toVec]) into motor angular acceleration.
     * @see botAccelFromMotorAccel
     * @see botVelFromMotorVel
     */
    val motorAccelFromBotAccel: Mat
    /**
     * Transforms motor angular acceleration into a bot acceleration vector (see [Pose2d.toVec]), with least squares
     * error.
     * @see motorAccelFromBotAccel
     * @see motorVelFromBotVel
     */
    val botAccelFromMotorAccel: Mat

    // wheel <-> motor
    /**
     * Transforms motor velocities into wheel (tangential) velocities
     */
    val wheelVelFromMotorVel: Mat

    /**
     * Transforms a wheel (tangential) velocities into motor velocities
     */
    val motorVelFromWheelVel: Mat
}

/**
 * An implementation of all of [MotorVelocityModel], [BotVelocityModel], and [MotorBotVelInteraction]
 * that represents a body that uses ([FixedWheelModel])s and bot mass and moment of inertia.
 *
 * **More importantly**, This model relies on an assumption tha the wheels have perfect traction and are perfectly
 * fixed to the drive body (so moving one wheel may affect all the others). One should consider using the values produced
 * by this model as only a starting point in creating a real model; although sometimes it may
 * perform well enough (for drives with few wheels).
 *
 * @param wheels the list of [FixedWheelModel]s
 * @param mass the mass of the bot
 * @param moi the moment of inertia of the bot
 */
open class NominalFixedWheelDriveModel(
    val mass: Double,
    val moi: Double,
    wheels: List<FixedWheelModel>,
    override val isHolonomic: Boolean
) : MotorVelocityModel, BotVelocityModel, MotorBotVelInteraction {

    /**
     * The [FixedWheelModel]s that this uses.
     */
    val wheels: List<FixedWheelModel> = wheels.toImmutableList()

    init {
        require(wheels.isNotEmpty()) { "Drive model needs to have at least one wheel" }
        require(mass >= 0) { "mass ($mass) should be >= 0" }
        require(moi >= 0) { "moi ($moi) should be >= 0" }
    }

    //private
    private val wheelBotDynamicsMatrix: Mat = run {
        val turnContributionVector = wheels.map { it.position cross it.orientation }
        zeroMat(3, wheels.size).also {
            wheels.zipForEachIndexed(turnContributionVector) { i, wheel, c ->
                val (fx, fy) = wheel.orientation
                it[0, i] = fx
                it[1, i] = fy
                it[2, i] = c
            }
        }
    }
    private val botForceFromWheelForce: Mat get() = wheelBotDynamicsMatrix
    private val wheelVelFromBotVel: Mat by lazy { wheelBotDynamicsMatrix.T } //turns out to be the same
    //motor velocity
    override val numMotors: Int get() = wheels.size
    override val voltsFromMotorVel: Mat by lazy { diagMat(wheels.map { it.transmission.motor.voltsPerAngVel }) }
    override val voltsFromMotorAccel: Mat by lazy { motorAccelFromVolts.inv() }
    override val motorAccelFromVolts: Mat by lazy { motorAccelFromBotAccel * botAccelFromVolts }
    override val motorAccelFromMotorVel: Mat by lazy { -motorAccelFromVolts * voltsFromMotorVel }
    override val motorAccelForMotorFriction: Mat by lazy {
        -motorAccelFromVolts * diagMat(wheels.map { it.transmission.voltsForFriction })
    }
    override val voltsForMotorFriction: Mat by lazy { -voltsFromMotorAccel * motorAccelForMotorFriction }
    //bot velocity
    override val numWheels: Int get() = wheels.size
    override val voltsFromBotVel: Mat by lazy { voltsFromMotorVel * motorVelFromBotVel }
    override val voltsFromBotAccel: Mat by lazy { botAccelFromVolts.pinv() }
    override val botAccelFromVolts: Mat by lazy {
        val botAccelFromBotForce = diagMat(1 / mass, 1 / mass, 1 / moi)
        val wheelForceFromVolts = diagMat(wheels.map { 1 / it.voltsPerOutputForce })
        botAccelFromBotForce * botForceFromWheelForce * wheelForceFromVolts
    }
    override val botAccelFromBotVel: Mat by lazy { -botAccelFromVolts * voltsFromBotVel }
//    override val botAccelForBotFriction: Mat get() =
//    override val voltsForBotFriction: Mat get() =

    override val motorVelFromBotVel: Mat by lazy {
        diagMat(wheels.map { it.motorVelPerOutputVel }) * wheelVelFromBotVel
    }
    override val botVelFromMotorVel: Mat by lazy { motorVelFromBotVel.pinv() }
    override val motorAccelFromBotAccel: Mat
        get() = motorVelFromBotVel
    override val botAccelFromMotorAccel: Mat
        get() = botVelFromMotorVel

    override val motorVelFromWheelVel: Mat by lazy { diagMat(wheels.map { it.motorVelPerOutputVel }) }
    override val wheelVelFromMotorVel: Mat by lazy { diagMat(wheels.map { 1 / it.motorVelPerOutputVel }) }
}

/**
 * Utilities for creating common drive models.
 */
object NominalDriveModels {

    /**
     * Creates a drive model for a mecanum-like drive, with wheels in
     * [front left, front right, back left, back right] order, using NWU orientation.
     */
    @JvmStatic
    fun mecanumLike(
        mass: Double,
        moi: Double,
        transmission: TransmissionModel,
        wheelRadius: Double,
        horizontalRadius: Double,
        verticalRadius: Double
    ): NominalFixedWheelDriveModel {
        val wheels = listOf(
            FixedWheelModel.fromWheelAngle(
                transmission,
                Vector2d(verticalRadius, horizontalRadius),
                wheelRadius,
                -44.99 * degrees
                //deviate to prevent problems with singular matrices. Simulates pseudo inverse kinda
            ),
            FixedWheelModel.fromWheelAngle(
                transmission,
                Vector2d(verticalRadius, -horizontalRadius),
                wheelRadius,
                45 * degrees
            ),
            FixedWheelModel.fromWheelAngle(
                transmission,
                Vector2d(-verticalRadius, horizontalRadius),
                wheelRadius,
                45 * degrees
            ),
            FixedWheelModel.fromWheelAngle(
                transmission,
                Vector2d(-verticalRadius, -horizontalRadius),
                wheelRadius,
                -45 * degrees
            )
        )
        return NominalFixedWheelDriveModel(mass, moi, wheels, true)
    }

    /**
     * Creates a drive model for a differential supplied, wheels in [left, right] order, using NWU orientation.
     */
    @JvmStatic
    fun differential(
        mass: Double,
        moi: Double,
        transmission: TransmissionModel,
        wheelRadius: Double,
        horizontalRadius: Double
    ): NominalFixedWheelDriveModel {
        val wheels = listOf(
            FixedWheelModel.fromWheelAngle(transmission, Vector2d(0.0, horizontalRadius), wheelRadius, 0.0),
            FixedWheelModel.fromWheelAngle(transmission, Vector2d(0.0, -horizontalRadius), wheelRadius, 0.0)
        )
        return NominalFixedWheelDriveModel(mass, moi, wheels, false)
    }
}
