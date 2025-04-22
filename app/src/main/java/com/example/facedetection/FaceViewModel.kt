package com.example.facedetection

import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import com.google.mlkit.vision.face.Face

class FaceViewModel(face: Face) {
    val boundingRect: Rect = face.boundingBox
    val faceTouchCallback = View.OnTouchListener { v, event ->
        if (event.action == MotionEvent.ACTION_DOWN && boundingRect.contains(
                event.x.toInt(),
                event.y.toInt()
            )
        ) {
            Toast.makeText(v.context, "Face tapped!", Toast.LENGTH_SHORT).show()
        }
        true
    }

}