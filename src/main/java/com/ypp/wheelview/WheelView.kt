package com.ypp.wheelview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class WheelView: View {
    companion object {
        private const val TAG = "WheelView"
        private const val DEFAULT_TEXT_COLOR = Color.BLACK
        private const val DEFAULT_SELECTED_TEXT_COLOR = DEFAULT_TEXT_COLOR
        private const val DEFAULT_TEXT_SIZE = 24F
        private const val DEFAULT_SELECTED_TEXT_SIZE = (DEFAULT_TEXT_SIZE * 1.5).toFloat()
        private const val DEFAULT_VISIBLE_ITEM_NUMBER = 3
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    /**
     * the color of normal items
     */
    private var textColor = DEFAULT_TEXT_COLOR
    /**
     * the color if the selected item, on the other word, the item show in the middle of this view
     */
    private var selectedTextColor = DEFAULT_SELECTED_TEXT_COLOR
    /**
     * the base textSize of the normal item, not the actual size
     */
    private var textSize = DEFAULT_TEXT_SIZE
    /**
     * the base textSize of the selected item, not the actual size
     */
    private var selectedTextSize = DEFAULT_SELECTED_TEXT_SIZE
    /**
     * the number of the visible item on one side, the total number of visible item should be
     * 2 * visibleItemNumber + 1
     */
    private var visibleItemNumber = DEFAULT_VISIBLE_ITEM_NUMBER
    /**
     * the base height of the normal item, not the actual height
     */
    private var textHeight = 0
    /**
     * the base height of the selected item, not the actual height
     */
    private var selectedTextHeight = 0
    /**
     * the height required for scrolling across an item
     */
    private var itemHeight = 0
    /**
     * the width required for showing the longest item
     */
    private var maxWidth = 0

    /**
     * the height required for showing all visible items
     */
    private var maxHeight = 0
    /**
     * the index of the selected item
     */
    private var currentIndex = 0
    /**
     * scroll from the top of the first item and the bottom of the last item
     */
    private var scrollRange = 0
    private var currentScroll = 0
    private var lastY = 0

    /**
     * If true, scroll to bottom will back to top and scroll to top will go down to bottom, and the
     * last item show above the first item.
     * If false, this view cannot scroll to out of the scroll range.
     */
    private var enableCircle = true

    /**
     * By default, the farther the item is from the middle line, the stronger the text height is
     * scaled ( square of scale ). Which just make the text look like it's stick on a circle.
     * When set to true, there is no stretching for the text height.
     */
    private var enableLinearScale = false

    /**
     * When set to true, this view only show 2 * {@link #visibleItemNumber} + 1 items ( one selected item and
     * visibleItemNumber items for each side ). In the meantime, the height of view is measured to
     * display items of the specific quantity when the layout param is set to wrap_content.
     * Otherwise, this view shows as more items as it can according to the height. In this case,
     * need to be careful about custom scale {@link #setCustomScale}, the extremely small scale may
     * cause wheel view draw too many items.
     */
    private var useVisibleNumber = true

    /**
     * The text scale ( except selected text ) can be customized by setting this param. It's a
     * formula return the scale using given params. The offset is the margin between item and the
     * middle line. The total is half the height of this view. The scale shouldn't be negative at
     * anytime, and it can only be zero when the offset is bigger than total.
     *
     * It's very possibly that you need to set height of this view when using a custom scale, and
     * wrap_content can't work properly.
     */
    private var customScale: ((offset: Float, total: Int) -> Float)? = null

    private var data = listOf<String>()


    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs, defStyleAttr, 0)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(it, R.styleable.WheelView)
            textColor = typedArray.getColor(R.styleable.WheelView_textColor, DEFAULT_TEXT_COLOR)
            selectedTextColor = typedArray.getColor(R.styleable.WheelView_textColorSelected, DEFAULT_SELECTED_TEXT_COLOR)
            textSize = typedArray.getDimension(R.styleable.WheelView_textSize, DEFAULT_TEXT_SIZE)
            selectedTextSize = typedArray.getDimension(R.styleable.WheelView_textSizeSelected, textSize * 1.5F)
            visibleItemNumber = typedArray.getInteger(R.styleable.WheelView_visibleItemNumber, DEFAULT_VISIBLE_ITEM_NUMBER)
            typedArray.recycle()
        }
    }

    /**
     * Measure some useful dimensions. This method should be invoke every time when either textSize
     * or selectedTextSize is changed which affect the value of scrollRange. So dose the size of
     * data.
     */
    private fun measureSelf() {
        paint.textSize = this.textSize
        var fm = paint.fontMetrics
        textHeight = (fm.descent - fm.ascent).toInt()
        paint.textSize = selectedTextSize
        fm = paint.fontMetrics
        selectedTextHeight = (fm.descent - fm.ascent).toInt()
        // length of the longest item or 0 if data is empty
        maxWidth = data.maxOfOrNull { str ->
            paint.textSize = max(selectedTextSize, textSize)
            paint.measureText(str).toInt()
        } ?: 0
        // measure visible height
        measureVisibleHeight()
    }

    /**
     * Get the scroll value when the index of item just show in the middle of this view.
     *
     * @param index the index of item which you want to be show in the middle
     * @return the scroll value
     */
    private fun getScrollByIndex(index: Int): Int {
        return ((index + 0.5) * itemHeight).toInt()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        // measure some dimension will be used below
        measureSelf()

        val width = when(widthMode) {
            MeasureSpec.EXACTLY -> {
                widthSize
            }
            MeasureSpec.AT_MOST -> {
                min(maxWidth, widthSize)
            }
            else -> {
                maxWidth
            }
        }

        val height = when(heightMode) {
            MeasureSpec.EXACTLY -> {
                heightSize
            }
            MeasureSpec.AT_MOST -> {
                min(maxHeight, heightSize)
            }
            else -> {
                maxHeight
            }
        }
        setMeasuredDimension(width, height)
        // get view height at first, then getScale() can take effect
        itemHeight = (textHeight * getScale(0F, height)).toInt()
        // from the top of the first item and the bottom of the last item
        scrollRange = data.size * itemHeight
        // if the size of data becomes smaller, the currentIndex may bigger than the size of data
        if (currentIndex >= data.size) {
            // scroll to the last item
            currentIndex = data.size - 1
        }
        currentScroll = getScrollByIndex(currentIndex)
    }



    private fun getScale(offset: Float): Float {
        return customScale?.invoke(offset, height / 2) ?: getDefaultScale(offset, height / 2)
    }

    private fun getScale(offset: Float, total: Int): Float {
        return customScale?.invoke(offset, total) ?: getDefaultScale(offset, total)
    }

    /**
     * Compute the text size scale according to the offset in the default way. The offset is the
     * space between the middle of this view ( view.height / 2 ) and the bottom ( if item is
     * displayed above the middle ) or the top (if item is displayed below the middle ) of the item.
     * The specific expression make the text looks like scrolling on a wheel.
     *
     * @param offset the space between the item and the central axis in pixels
     * @param total the biggest value that the offset can be
     * @return the scale of the item
     */
    private fun getDefaultScale(offset: Float, total: Int): Float {
        return sqrt(1 - (offset / total).pow(2))
    }

    /**
     * Estimate the height of this view according to the visible number and scale.
     *
     * @return the height of this view for showing all visible item
     */
    private fun measureVisibleHeight() {
        var offset = 0
        var height = selectedTextHeight / 2 + textHeight * visibleItemNumber

        // this function is invoked when measure, so it shouldn't take too long
        for ( i in 0..visibleItemNumber * 2) {
            // compute the actual height if the height is current estimated value
            offset = selectedTextHeight / 2
            var scale = 1F

            repeat(visibleItemNumber) {
                scale = getScale(offset.toFloat(), height)
                if (!enableLinearScale) {
                    scale *= scale
                }
                Log.d(TAG, "$offset $scale")
                offset += (textHeight * scale).toInt()
            }

            scale = getScale(offset.toFloat(), height)
            if (!enableLinearScale) {
                scale *= scale
            }
            paint.textSize = textSize * scale
            paint.textScaleX = 1F
            val fm = paint.fontMetrics
            if (abs(height - offset) > fm.descent && fm.descent > 1) {
                // Let the height be the offset of this iteration, so estimated result will be
                // closer to the theoretical value in the next iteration.
                height = offset
            } else {
                // The difference between height and offset is good enough when it is smaller than
                // the descent. Or it is acceptable if the descent is smaller than 1px.
                Log.d(TAG, "measureVisibleHeight: ${abs(height - offset)}")
                break
            }
        }

        maxHeight = height * 2
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        val y = event!!.y.toInt()
        when(event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastY = y
            }
            MotionEvent.ACTION_MOVE -> {
                // scroll with finger
                val offsetY = y - lastY
                currentScroll -= offsetY

                if (enableCircle) {
                    // make the item show in a circle
                    while (currentScroll > scrollRange) {
                        currentScroll -= scrollRange
                    }
                    while (currentScroll < 0) {
                        currentScroll += scrollRange
                    }
                } else {
                    // cannot scroll to out of the scroll range
                    currentScroll = min(currentScroll, scrollRange - 1)
                    currentScroll = max(currentScroll, 0)
                }

                lastY = y
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                // scroll to the middle of current index item when user take up the finger
                currentScroll = getScrollByIndex(currentIndex)
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        if (data.isEmpty()) {
            return
        }

        var curTop = height / 2F
        var curBottom = height / 2F

        canvas?.let {
            paint.color = selectedTextColor
            // compute the index of selected item
            currentIndex = currentScroll / itemHeight
            if (enableCircle) {
                currentIndex %= data.size
            }
            val text = data[currentIndex]
            // calculate the offset
            val offset = currentScroll % itemHeight - itemHeight / 2
            // calculate the scale by offset
            paint.textSize = (textSize * getScale(0F) - selectedTextSize) / (itemHeight / 2) * abs(offset) + selectedTextSize
            Log.d(TAG, "onDraw: $textSize $textHeight")
            // no horizontal scale for selected item
            paint.textScaleX = 1F
            val fm = paint.fontMetrics
            // get the text height after stretching
            val textHeight = fm.descent - fm.ascent
            // text horizontal alignment, default is center
            val start = when(paint.textAlign) {
                Paint.Align.LEFT -> { 0 }
                Paint.Align.RIGHT -> { width }
                Paint.Align.CENTER -> { width / 2 }
                else -> { width / 2 }
            }
            // draw on the baseline
            it.drawText(text, start.toFloat(), height / 2 - (fm.descent + fm.ascent) / 2 - offset, paint)
            // the top of current item equals the bottom of the item above current item
            curTop =  - (textHeight / 2 + offset)
            // the bottom of current item equals the top of the item below current item
            curBottom = textHeight / 2 - offset
        }


        // draw above items
        var count = 0
        while (height / 2 + curTop > 0) {
            paint.color = textColor
            count += 1
            if (useVisibleNumber && count > visibleItemNumber) {
                break
            }
            val scaleTop = getScale(abs(curTop))
            var upIndex = currentIndex - count
            if (upIndex < 0 && enableCircle) {
                while (upIndex < 0) {
                    upIndex += data.size
                }
            }

            if (upIndex >= 0) {
                val text = data[upIndex]
                if (enableLinearScale) {
                    paint.textSize = textSize * scaleTop
                    paint.textScaleX = 1F
                } else {
                    // stretch the height of text
                    paint.textSize = textSize * scaleTop * scaleTop
                    paint.textScaleX = 1 / scaleTop
                }

                val fm = paint.fontMetrics
                val textHeight = fm.descent - fm.ascent
                if (textHeight < 1) {
                    break
                }
                val start = when(paint.textAlign) {
                    Paint.Align.LEFT -> { 0 }
                    Paint.Align.RIGHT -> { width }
                    Paint.Align.CENTER -> { width / 2 }
                    else -> { width / 2 }
                }
                canvas?.drawText(text, start.toFloat(), height / 2 + curTop - fm.descent, paint)
                curTop -= textHeight
            }
        }

        // draw below items
        count = 0
        while (height / 2 - curBottom > 0) {
            paint.color = textColor
            count += 1
            if (useVisibleNumber && count > visibleItemNumber) {
                break
            }
            val scaleBottom = getScale(abs(curBottom))
            var downIndex = currentIndex + count
            if (downIndex >= data.size && enableCircle) {
                while (downIndex >= data.size) {
                    downIndex -= data.size
                }
            }

            if (downIndex < data.size) {
                val text = data[downIndex]
                if (enableLinearScale) {
                    paint.textSize = textSize * scaleBottom
                    paint.textScaleX = 1F
                } else {
                    // stretch the height of text
                    paint.textSize = textSize * scaleBottom * scaleBottom
                    paint.textScaleX = 1 / scaleBottom
                }
                val fm = paint.fontMetrics
                val textHeight = fm.descent - fm.ascent
                if (textHeight < 1) {
                    break
                }
                val start = when(paint.textAlign) {
                    Paint.Align.LEFT -> { 0 }
                    Paint.Align.RIGHT -> { width }
                    Paint.Align.CENTER -> { width / 2 }
                    else -> { width / 2 }
                }
                canvas?.drawText(text, start.toFloat(), height / 2 + curBottom - fm.ascent, paint)
                curBottom += textHeight
            }
        }
    }

    fun setTextSize(size: Float) {
        textSize = size
        requestLayout()
        invalidate()
    }

    fun setSelectedTextSize(size: Float) {
        selectedTextSize = size
        requestLayout()
        invalidate()
    }

    fun setVisibleItemNumber(number: Int) {
        visibleItemNumber = number
        requestLayout()
        invalidate()
    }

    fun setData(data: List<String>) {
        this.data = data
        requestLayout()
        invalidate()
    }

    fun setEnableCircle(enable: Boolean) {
        if (enable != enableCircle) {
            enableCircle = enable
            requestLayout()
            invalidate()
        }
    }

    fun setEnableLinearScale(enable: Boolean) {
        if (enable != enableLinearScale) {
            enableLinearScale = enable
            requestLayout()
            invalidate()
        }
    }

    fun setCustomScale(scale: (offset: Float, total: Int) -> Float) {
        customScale = scale
        if (getScale(0F) == 0F) {
            throw IllegalArgumentException("The scale value when offset is 0 should never be zero")
        }
        requestLayout()
        invalidate()
    }

    fun setUseVisibleNumber(enable: Boolean) {
        if (enable != useVisibleNumber) {
            useVisibleNumber = enable
            invalidate()
        }
    }

    fun setTextColor(color: Int) {
        textColor = color
        invalidate()
    }

    fun setSelectedTextColor(color: Int) {
        selectedTextColor = color
        invalidate()
    }

    fun setTextAlign(align: Paint.Align) {
        paint.textAlign = align
        invalidate()
    }
}