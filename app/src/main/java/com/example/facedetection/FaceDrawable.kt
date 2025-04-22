package com.example.facedetection

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

class FaceDrawable(private val viewModel: FaceViewModel) : Drawable() {

    private val paint = Paint().apply {
        style = Paint.Style.FILL_AND_STROKE
        color = Color.RED
        strokeWidth = 10f
        alpha = 200
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRect(viewModel.boundingRect, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

}