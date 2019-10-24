package org.futurerobotics.jargon.blocks

import org.futurerobotics.jargon.blocks.Block.Processing.IN_FIRST_LAZY
import org.futurerobotics.jargon.blocks.Block.Processing.OUT_FIRST_ALWAYS

/**
 * A block with one constant output [value].
 *
 * This is itself a [BlocksConfig.Output] representing this block's only output.
 *
 * @param value the constant value
 */
class Constant<T>(private val value: T) : SingleOutputBlock<T>(0, IN_FIRST_LAZY) {

    override fun initialValue(): T? = value
    override fun processOutput(inputs: List<Any?>, systemValues: SystemValues): T = value
    override fun toString(): String = "Constant($value)"
}

/**
 * A block with only one output [value], which can be changed externally.
 *
 * This is itself a [BlocksConfig.Output] representing this block's only output.
 *
 * @param value the value outputted
 */
class ExternalValue<T>(@Volatile var value: T) : SingleOutputBlock<T>(0, IN_FIRST_LAZY) {

    override fun initialValue(): T? = null
    override fun processOutput(inputs: List<Any?>, systemValues: SystemValues): T = value
    override fun toString(): String = "ExternalConstant($value)"
}

/**
 * A block with only one input, and stores the value inputted in [value]. Useful for extracting information
 * out of a system.
 *
 * This is itself a [BlocksConfig.Input] representing its only input.
 */
@Suppress("UNCHECKED_CAST")
class Monitor<T> : InputOnlyBlock<T>() {

    /**
     * The last value given to this monitor. Will be `null` if nothing has been received yet (or the given value
     * is null).
     */
    @Volatile
    var value: T? = null
        private set

    override fun init() {
        value = null
    }

    override fun processInput(input: T, systemValues: SystemValues) {
        value = input
    }

    override fun toString(): String = "Monitor($value)"
}

/**
 * A block that simply stores its input, and outputs it the next loop; so it is [OUT_FIRST_ALWAYS].
 * This is useful for breaking up loops.
 *
 * An [initialValue] must be given, which will be the first output given when the system first started, before
 * any inputs have been given.
 *
 * This is also creatable from [BlocksConfig.delay]
 */
class Delay<T>(private val initialValue: T) : PipeBlock<T, T>(OUT_FIRST_ALWAYS) {

    override fun initialValue(): T? = initialValue
    override fun pipe(input: T): T = input
}
