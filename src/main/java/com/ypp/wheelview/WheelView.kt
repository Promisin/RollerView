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
     * the width can show the longest item
     */
    private var maxWidth = 0
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
     * By default, the farther item is from the middle line, the smaller text height ( notice just
     * height ). Which just make the text look like it's stick on a circle. When set to true,
     * there is no stretching for the text.
     */
    private var enableFlatDisplay = false

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
        // from the top of the first item and the bottom of the last item
        scrollRange = data.size * textHeight
        // if the size of data becomes smaller, the currentIndex may bigger than the size of data
        if (currentIndex >= data.size) {
            // scroll to the last item
            currentIndex = data.size - 1
        }
        currentScroll = getScrollByIndex(currentIndex)
    }

    /**
     * Get the scroll value when the index of item just show in the middle of this view.
     *
     * @param index the index of item which you want to be show in the middle
     * @return the scroll value
     */
    private fun getScrollByIndex(index: Int): Int {
        return ((index + 0.5) * textHeight).toInt()
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
                min(getVisibleHeight(), heightSize)
            }
            else -> {
                getVisibleHeight()
            }
        }
        setMeasuredDimension(width, height)
    }


    /**
     * Compute the text size scale according to the offset. The offset is the space between the
     * middle of this view ( view.height / 2 ) and the bottom ( if item is displayed above the
     * middle ) or the top (if item is displayed below the middle ) of the item. The specific
     * expression make the text looks like scrolling on a wheel.
     *
     * @param offset the space between the item and the central axis in pixels
     * @return the scale of the item
     */
    private fun getScale(offset: Float): Float {
        return sqrt(1 - (offset / (height / 2)).pow(2))
    }

    private fun getScale(offset: Int): Float {
        return getScale(offset.toFloat())
    }

    /**
     * Compute the theoretical height of this view
     */
    private fun getVisibleHeight(): Int {
//        val totalRatio = (0 until visibleItemNumber).fold(0F) { acc, cur ->
//            acc + sqrt(1 - ((cur + selectedTextHeight / 2 / textHeight) / visibleItemNumber).toFloat().pow(2))
//        }
//        return (textHeight * totalRatio * 2).toInt() + selectedTextHeight
        return if (enableFlatDisplay) {
            selectedTextHeight / 2 + textHeight * visibleItemNumber
        } else {
            ((selectedTextHeight / 2 + textHeight * visibleItemNumber) * 4 / (2 * 3.14)).toInt() * 2
        }
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
            currentIndex = currentScroll / textHeight
            if (enableCircle) {
                currentIndex %= data.size
            }
            Log.d(TAG, "onDraw: $currentScroll $textHeight")
            val text = data[currentIndex]
            // calculate the offset
            val offset = currentScroll % textHeight - textHeight / 2
            // calculate the scale by offset
            paint.textSize = (textSize - selectedTextSize) / (textHeight / 2) * abs(offset) + selectedTextSize
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


        // draw the other items on the both sides of selected item
        (1..visibleItemNumber).forEach { index ->
            paint.color = textColor
            canvas?.let {
                val scaleTop = getScale(abs(curTop))
                var upIndex = currentIndex - index
                if (upIndex < 0 && enableCircle) {
                    while (upIndex < 0) {
                        upIndex += data.size
                    }
                }

                if (upIndex >= 0) {
                    val text = data[upIndex]
                    if (enableFlatDisplay) {
                        paint.textSize = textSize * scaleTop
                        paint.textScaleX = 1F
                    } else {
                        // stretch the height of text
                        paint.textSize = textSize * scaleTop * scaleTop
                        paint.textScaleX = 1 / scaleTop
                    }

                    val fm = paint.fontMetrics
                    val textHeight = fm.descent - fm.ascent
                    val start = when(paint.textAlign) {
                        Paint.Align.LEFT -> { 0 }
                        Paint.Align.RIGHT -> { width }
                        Paint.Align.CENTER -> { width / 2 }
                        else -> { width / 2 }
                    }
                    it.drawText(text, start.toFloat(), height / 2 + curTop - fm.descent, paint)
                    curTop -= textHeight
                }


                val scaleBottom = getScale(abs(curBottom))
                var downIndex = currentIndex + index
                if (downIndex >= data.size && enableCircle) {
                    while (downIndex >= data.size) {
                        downIndex -= data.size
                    }
                }

                if (downIndex < data.size) {
                    val text = data[downIndex]
                    if (enableFlatDisplay) {
                        paint.textSize = textSize * scaleBottom
                        paint.textScaleX = 1F
                    } else {
                        // stretch the height of text
                        paint.textSize = textSize * scaleBottom * scaleBottom
                        paint.textScaleX = 1 / scaleBottom
                    }
                    val fm = paint.fontMetrics
                    val textHeight = fm.descent - fm.ascent
                    val start = when(paint.textAlign) {
                        Paint.Align.LEFT -> { 0 }
                        Paint.Align.RIGHT -> { width }
                        Paint.Align.CENTER -> { width / 2 }
                        else -> { width / 2 }
                    }
                    it.drawText(text, start.toFloat(), height / 2 + curBottom - fm.ascent, paint)
                    curBottom += textHeight
                }
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

    fun setEnableFlatDisplay(enable: Boolean) {
        if (enable != enableFlatDisplay) {
            enableFlatDisplay = enable
            requestLayout()
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
    }
}