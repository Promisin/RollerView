package com.ypp.rollerview

import android.view.animation.BaseInterpolator

class ComplexAccelerateInterpolator(private val startV: Float, endV: Float) : BaseInterpolator() {
    /**
     * the theoretical distance when a thing moves in a uniform variable motion from startV to endV
     * in 1 second
     */
    private var total: Float

    /**
     * the acceleration
     */
    private var a: Float
    constructor(startV: Float) : this(startV, 0F)

    init {
        if (startV + endV == 0F) {
            throw IllegalArgumentException("Can't produce the correct percent when endV is the opposite of starV")
        }
        total = (startV + endV) / 2
        a = endV - startV
    }

    override fun getInterpolation(input: Float): Float {
        return (a * input * input / 2 + startV * input) / total
    }
}