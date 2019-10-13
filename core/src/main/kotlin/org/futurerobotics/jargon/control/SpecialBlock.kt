package org.futurerobotics.jargon.control

import org.futurerobotics.jargon.control.Block.Processing.IN_FIRST_ALWAYS


/**
 * Special blocks tap into the life of [BlocksSystem]s themselves.
 * If a [BlocksSystem] sees a special block, it must do something with it.
 *
 * There can only be up to one of each kind of special block per blo
 *
 * All special blocks must implement this interface.
 */
sealed class SpecialBlock(numInputs: Int, numOutputs: Int, processing: Block.Processing) :
    AbstractBlock(numInputs, numOutputs, processing)


/**
 * This [SpecialBlock], when inputted a value of `true`, will tell the system to shutdown.
 */
class Shutdown : SpecialBlock(1, 0, IN_FIRST_ALWAYS), BlockInput<Boolean?> {
    /** If this block received  shutdown signal or not. */
    var shutDownSignal: Boolean = false
        private set

    override fun init() {
        shutDownSignal = false
    }

    override fun process(inputs: List<Any?>, systemValues: SystemValues) {
        shutDownSignal = inputs[0] as Boolean? == true
    }

    override fun getOutput(index: Int): Any? = throw IndexOutOfBoundsException(index)

    override val block: Block get() = this
    override val inputIndex: Int get() = 0
}