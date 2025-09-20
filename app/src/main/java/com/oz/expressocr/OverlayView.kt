package com.oz.expressocr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val paint = Paint().apply {
        color = 0xFF00FF00.toInt() // 绿色
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    private var boundingBox: Rect? = null
    private var matchedText: String? = null // 修改类型为 String?

    fun updateBoundingBox(rect: Rect?) {
        boundingBox = rect
        invalidate() // 触发重绘
    }

    fun updateBoundingBoxWithText(boundingBox: Rect?, matchedText: String?) {
        this.boundingBox = boundingBox
        this.matchedText = matchedText
        invalidate()
    }

    override fun onDraw(canvas: Canvas) { // 修改 Canvas 为非空类型
        super.onDraw(canvas)
        boundingBox?.let {
            // 绘制边框
            paint.style = Paint.Style.STROKE
            canvas.drawRect(it, paint)

            // 绘制匹配的字符串
            matchedText?.let { text ->
                paint.style = Paint.Style.FILL
                paint.textSize = 40f
                canvas.drawText(text, it.left.toFloat(), it.top.toFloat() - 10, paint)
            }
        }
    }
}