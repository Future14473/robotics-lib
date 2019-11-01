package org.futurerobotics.jargon.simulation

import org.futurerobotics.jargon.blocks.AbstractBlock
import org.futurerobotics.jargon.blocks.Block
import org.futurerobotics.jargon.blocks.Block.Processing.OUT_FIRST_ALWAYS
import org.futurerobotics.jargon.blocks.BlocksConfig
import org.futurerobotics.jargon.blocks.SystemValues
import org.futurerobotics.jargon.blocks.control.MotorsBlock
import org.futurerobotics.jargon.hardware.Gyro
import org.futurerobotics.jargon.linalg.*
import org.futurerobotics.jargon.math.Pose2d
import org.futurerobotics.jargon.mechanics.BotVelocityModel
import org.futurerobotics.jargon.mechanics.GlobalToBot
import org.futurerobotics.jargon.mechanics.NominalFixedWheelDriveModel
import org.futurerobotics.jargon.statespace.DriveStateSpaceModels
import java.util.*
import kotlin.math.roundToInt

/**
 * Represents a simulated drive interface.
 *
 * Right now only [BotVelocityModel]s are supported.
 */
interface SimulatedDrive {

    /**
     * Gets the current positions of the wheels.
     */
    val curMotorPositions: Vec
    /**
     * Gets the current velocities of the wheels.
     */
    val curMotorVelocities: Vec
    /**
     * Gets the current pose of the bot.
     */
    val curGlobalPose: Pose2d

    /**
     * Updates the simulation, using the given [volts] vector and the loop [time] elapsed.
     */
    fun update(volts: Vec, time: Double)
}

/**
 * Base class for a simulated drive.
 *
 * @param driveModel the model to use
 * @param voltageNoise the voltage signal noise covariance
 * @param measurementNoise the measurement noise covariance
 * @param timeStep the minimum amount of time passed between samples. Because I don't like euler approximations.
 */
class SimulatedFixedDrive(
    private val driveModel: NominalFixedWheelDriveModel,
    private val random: Random = Random(),
    private val voltageNoise: Mat,
    private val measurementNoise: Mat,
    private val timeStep: Double = 0.01,
    private val maxVolts: Double = 12.0
) : SimulatedDrive {

    constructor(
        model: NominalFixedWheelDriveModel,
        perturb: Perturber<NominalFixedWheelDriveModel>,
        random: Random,
        voltageNoise: Mat,
        measurementNoise: Mat,
        timeStep: Double = 0.01
    ) : this(perturb.perturb(model, random), random, voltageNoise, measurementNoise, timeStep)

    init {
        require(voltageNoise.isSquare) { "Voltage noise $voltageNoise must be square" }
        require(measurementNoise.isSquare) { "Measurement noise $measurementNoise must be square" }
        require(timeStep > 0) { "Time step $timeStep must be > 0" }
        require(maxVolts > 0) { "Time step $maxVolts must be > 0" }
    }

    /** The number of motors/wheels in this drive. */
    val numMotors: Int get() = driveModel.numWheels

    override var curMotorVelocities: Vec = zeroVec(driveModel.numWheels)
    override val curMotorPositions: Vec = zeroVec(driveModel.numWheels)

    override var curGlobalPose: Pose2d = Pose2d.ZERO
    //wheel velocity controller
    private val wheelSSModel = DriveStateSpaceModels.motorVelocityController(driveModel).discretize(timeStep)

    override fun update(volts: Vec, time: Double) {
//        require(!volts.isNaN) { "Received NaN volts" }
        repeat((time / timeStep).roundToInt()) {
            singleStep(volts)
        }
    }

    private fun singleStep(volts: Vec) {
        val realVolts = volts.map { it.coerceIn(-maxVolts, maxVolts) } + getVoltageNoise()
        val pastMotorVelocities = curMotorVelocities
        curMotorVelocities = wheelSSModel.processState(pastMotorVelocities, realVolts)
        val motorDelta = (pastMotorVelocities + curMotorVelocities) * (timeStep / 2)
        curMotorPositions += motorDelta
        val botPoseDelta = Pose2d(driveModel.botVelFromMotorVel(motorDelta))
        curGlobalPose = GlobalToBot.trackGlobalPose(botPoseDelta, curGlobalPose)
    }

    /** Gets a measurement noise vector. */
    fun getMeasurementNoise(): Vec = measurementNoise * normRandVec(measurementNoise.rows)

    /** Gets a voltage noise vector. */
    private fun getVoltageNoise(): Vec = voltageNoise * normRandVec(voltageNoise.rows)
}

/**
 * Simulated drive input block.
 *
 * Inputs:
 * 1. Voltage inputs; either a [Vec], List<Double> or DoubleArray/double[]
 *
 * Outputs:
 * 1. A List<Double> of motor positions in radians
 * 2. A List<Double> of motor velocities in radians
 */
class SimulatedDriveBlock(private val drive: SimulatedFixedDrive) :
    AbstractBlock(1, 3, OUT_FIRST_ALWAYS),
    MotorsBlock {

    override val numMotors: Int
        get() = drive.numMotors

    override fun init() {
    }

    override fun process(inputs: List<Any?>, systemValues: SystemValues) {
        @Suppress("UNCHECKED_CAST")
        drive.update(createVec(inputs[0] as List<Double>), systemValues.loopTime)
    }

    override fun getOutput(index: Int): Any? = when (index) {
        0 -> (drive.curMotorPositions + drive.getMeasurementNoise()).toList()
        1 -> (drive.curMotorVelocities + drive.getMeasurementNoise()).toList()
        2 -> drive.curGlobalPose
        else -> throw IndexOutOfBoundsException(index)
    }

    /** Motor positions [BlocksConfig.Output] */
    override val motorPositions: BlocksConfig.Output<List<Double>> get() = configOutput(0)
    /** Motor velocities [BlocksConfig.Output] */
    override val motorVelocities: BlocksConfig.Output<List<Double>> get() = configOutput(1)

    /** The actual pose as monitored by the simulated drive. Usually will not have direct info about this; used for testing. */
    val actualPose: BlocksConfig.Output<Pose2d> get() = configOutput(2)

    override val block: Block get() = this
    override val index: Int get() = 0
}

/**
 * Simulated gyroscope measurement.
 *
 * This currently does not simulate drift.
 */
class SimulatedGyro(
    private val drive: SimulatedFixedDrive,
    private val noiseStd: Double = 0.0,
    private val random: Random = Random()
) : Gyro {

    override val currentAngle: Double
        get() = drive.curGlobalPose.heading + random.nextGaussian() * noiseStd
}
