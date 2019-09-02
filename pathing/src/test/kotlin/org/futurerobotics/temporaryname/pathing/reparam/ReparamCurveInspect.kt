package org.futurerobotics.temporaryname.pathing.reparam

import org.futurerobotics.temporaryname.GraphUtil
import org.futurerobotics.temporaryname.math.*
import org.futurerobotics.temporaryname.math.function.QuinticSpline
import org.futurerobotics.temporaryname.math.function.VectorFunction
import org.futurerobotics.temporaryname.reportError
import org.futurerobotics.temporaryname.saveTest
import org.futurerobotics.temporaryname.util.getCallerStackFrame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import kotlin.math.PI
import kotlin.random.Random

@RunWith(Parameterized::class)
internal class ReparamCurveInspect(private val func: VectorFunction, private val curve: ReparamCurve) {

    @Test
    fun `report samples`() {
        println("Num samples: ${(curve.mapping as SamplesReparamMapping).numSamples}")
    }

    @Test
    fun `reparameterization inspect`() {
        testValue({ it }, curve::tOfS, 0.001, 0.0)
    }

    @Test
    fun `position inspect`() {
        testVector({ func.vec(it) }, { curve.atLength(it).position }, 0.001, 0.0)
    }

    @Test
    fun `positionDeriv inspect`() {
        testVector({ func.vecDeriv(it).normalized() }, { curve.atLength(it).positionDeriv }, 0.002, 0.001)
    }

    @Test
    fun `positionSecondDeriv inspect`() {
        testVector({
            val deriv = func.vecDeriv(it)
            val secondDeriv = func.vecSecondDeriv(it)
            val z = (secondDeriv cross deriv) / deriv.lengthSquared.squared()
            Vector2d(deriv.y * z, -deriv.x * z)
        }, { curve.atLength(it).positionSecondDeriv }, 0.005, 0.001)
    }

    @Test
    fun `tanAngle inspect`() {
        testAngle({ func.vecDeriv(it).angle }, { curve.atLength(it).tanAngle }, 0.001, 0.0)
    }

    @Test
    fun `tanAngleDeriv inspect`() {
        testValue({ func.curvature(it) }, { curve.atLength(it).tanAngleDeriv }, 0.002, 0.001)
    }

    @Test
    fun `tanAngleSecondDeriv inspect`() {
        testValue(
            { func.curvatureDeriv(it) / func.vecDeriv(it).length },
            { curve.atLength(it).tanAngleSecondDeriv },
            0.05,
            0.001
        )
    }

    private fun testVector(
        trueValT: (Double) -> Vector2d, testValS: (Double) -> Vector2d, maxError: Double, offset: Double
    ) {
        testStep(trueValT, testValS, { this distTo it }, offset, maxError)
    }

    private fun testValue(
        trueValT: (Double) -> Double, testValS: (Double) -> Double, maxError: Double, offset: Double
    ) {
        testStep(trueValT, testValS, { this distTo it }, offset, maxError)
    }

    private fun testAngle(
        trueValT: (Double) -> Double, testValS: (Double) -> Double, maxError: Double, offset: Double
    ) {
        testStep(trueValT, testValS, { angleNorm(distTo(it)) }, offset, maxError)
    }

    private fun <T> testStep(
        trueValT: (Double) -> T, testValS: (Double) -> T, getError: T.(T) -> Double, offset: Double, maxError: Double
    ) {
        val offsetInt = (steps * offset).toInt()

        reportError {
            var s = 0.0
            for (i in 0..(steps - offsetInt)) {
                val t = i.toDouble() / steps
                if (i >= offsetInt) {
                    val trueVal = trueValT(t)
                    val testVal = testValS(s)
                    val err = trueVal.getError(testVal)
                    addError(err) { "$t, true is $trueVal, got $testVal" }
                }
                s += func.vecDeriv((i + 0.5) / steps).length / steps
            }
        }.let {
            println(getCallerStackFrame(3).methodName + ":")
            println(it.report())
            println()
            assertTrue(it.averageError <= maxError)
        }
    }

    companion object {
        const val steps = 100_000
        private const val seed = 34226
        private const val minDiff = 2.0
        private const val maxDiff = 8.0
        private val random = Random(seed)
        @JvmStatic
        @Parameters
        fun curves(): List<Array<Any>> {
            val list = mutableListOf<Array<Any>>()

            repeat(30) {
                val p0 = random.nextVector2d(
                    minDiff
                )
                val diff = random.nextDouble(
                    minDiff,
                    maxDiff
                )
                val angle = random.nextDouble() * TAU
                val p5 = p0 + Vector2d.polar(diff, angle)
                val p0Deriv =
                    Vector2d.polar(diff * random.nextDouble(0.2, 1.0), angle + random.nextDouble(-PI / 3, PI / 3))
                val p0SecondDeriv = Vector2d.ZERO
                val p5Deriv =
                    Vector2d.polar(diff * random.nextDouble(0.2, 1.0), angle + random.nextDouble(-PI / 3, PI / 3))
                val p5SecondDeriv = Vector2d.ZERO
                val p1 = p0 + p0Deriv / 5
                val p2 = p0SecondDeriv / 20 + 2 * p1 - p0
                val p4 = p5 - p5Deriv / 5
                val p3 = p5SecondDeriv / 20 + 2 * p4 - p5

                GraphUtil.getSplineGraph(30, p0, p1, p2, p3, p4, p5).saveTest("RandomSpline/$it")
                val func = QuinticSpline.fromControlPoints(p0, p1, p2, p3, p4, p5)

                list.add(arrayOf(func, func.reparamByIntegration()))
            }
            return list
        }
    }
}

