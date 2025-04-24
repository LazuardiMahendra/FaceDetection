package com.example.facedetection

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.facedetection.databinding.ActivityMainBinding
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var faceDetector: FaceDetector

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA
        ).toTypedArray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (allPermissionsGranted()) {
            Log.d("PERMISSION", "PERMISSION GRANTED")
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCamera() {
        val cameraController = LifecycleCameraController(baseContext)
        val previewView: PreviewView = binding.viewFinder

        cameraController.cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL).build()

        faceDetector = FaceDetection.getClient(options)

        cameraController.setImageAnalysisAnalyzer(
            ContextCompat.getMainExecutor(this), MlKitAnalyzer(
                listOf(faceDetector),
                COORDINATE_SYSTEM_VIEW_REFERENCED,
                ContextCompat.getMainExecutor(this)
            ) { result: MlKitAnalyzer.Result? ->
                val faces = result?.getValue(faceDetector)

                if (faces.isNullOrEmpty()) {
                    previewView.overlay.clear()
                    "No face detected ❌".also { binding.statusText.text = it }
                    return@MlKitAnalyzer
                }

                val result = processFaceList(faces)
                binding.statusText.text = result.joinToString("\n\n")

                val faceViewModel = FaceViewModel(faces[0])
                val faceDrawable = FaceDrawable(faceViewModel)

                previewView.setOnTouchListener(faceViewModel.faceTouchCallback)
                previewView.overlay.clear()
                previewView.overlay.add(faceDrawable)
            })

        cameraController.bindToLifecycle(this)
        previewView.controller = cameraController
    }

    private fun processFaceList(faces: List<Face>): List<String> {
        val results = mutableListOf<String>()
        for ((i, face) in faces.withIndex()) {
            val id = face.trackingId ?: -1

            val rotX = face.headEulerAngleX
            val rotY = face.headEulerAngleY
            val rotZ = face.headEulerAngleZ

            val smileProb = face.smilingProbability ?: 0F

            val leftEyeProb = face.leftEyeOpenProbability ?: 0F
            val rightEyeProb = face.rightEyeOpenProbability ?: 0F

            val smileApprove = smileProb > 0.7
            val eyesOpen = (leftEyeProb > 0.7 && rightEyeProb > 0.7)
            val facingApprove = (rotX in -10f..10f && rotY in -15f..15f && rotZ in -10f..10f)
            val hasContours = face.allContours.isNotEmpty()
            val hasLandmarks = listOf(
                FaceLandmark.LEFT_EAR, FaceLandmark.RIGHT_EAR, FaceLandmark
                    .RIGHT_EYE, FaceLandmark.LEFT_EYE, FaceLandmark.NOSE_BASE, FaceLandmark
                    .MOUTH_BOTTOM, FaceLandmark.MOUTH_LEFT, FaceLandmark.MOUTH_RIGHT
            ).all { face.getLandmark(it) != null }

            val isFaceValid = smileApprove && eyesOpen && facingApprove && hasContours &&
                    hasLandmarks

            val result = buildString {
                append("- Face #$1 (ID : $id) :\n")
                append("- Smile: %.2f %s\n".format(smileProb, if (smileApprove) "✅" else "❌"))
                append(
                    "- Eyes Left Open: %.2f || Eyes Right Open: %.2f =  %s\n".format
                        (leftEyeProb, rightEyeProb, if (eyesOpen) "✅" else "❌")
                )
                append(
                    "- Pose Straight Ahead: Y: %.1f || Z: %.1f = %s\n".format(
                        rotY,
                        rotZ,
                        if (facingApprove) "✅" else "❌"
                    )
                )
                append("- Landmarks: ${if (hasLandmarks) "✅" else "❌"}\n")
                append("- Contours: ${if (hasContours) "✅" else "❌"}\n")
                append("→ VALID FACE: ${if (isFaceValid) "YES ✅" else "NO ❌"}")
            }
            results.add(result)
        }
        return results
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this, "Permissions not granted by the user.", Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }
}