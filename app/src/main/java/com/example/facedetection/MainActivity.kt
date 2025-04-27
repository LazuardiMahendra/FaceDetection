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

    private var currentPhase = 1
    private var phaseStartTime: Long = 0L
    private var currentChallenge: Pair<String, Boolean>? = null
    private var challengePassed = false

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
                    resetPhases()
                    binding.statusText.text = "No face detected ❌"
                    return@MlKitAnalyzer
                }

                val face = faces[0]
                when (currentPhase) {
                    1 -> requiredVerification(face)
                    2 -> challengeVerification(face)
                }

                // Additional drawing or logic
                try {
                    val faceViewModel = FaceViewModel(face)
                    val faceDrawable = FaceDrawable(faceViewModel)

                    binding.viewFinder.setOnTouchListener(faceViewModel.faceTouchCallback)
                    binding.viewFinder.overlay.clear()
                    binding.viewFinder.overlay.add(faceDrawable)
                } catch (e: Exception) {
                    Log.e("Camera", "Face overlay error: ${e.message}")
                }
            })

        cameraController.bindToLifecycle(this)
        previewView.controller = cameraController
    }

    private fun requiredVerification(face: Face) {
        val rotX = face.headEulerAngleX
        val rotY = face.headEulerAngleY
        val rotZ = face.headEulerAngleZ

        val facingApprove = (rotX in -10f..10f && rotY in -15f..15f && rotZ in -10f..10f)
        val hasContours = face.allContours.isNotEmpty()
        val hasLandmarks = listOf(
            FaceLandmark.LEFT_EAR,
            FaceLandmark.RIGHT_EAR,
            FaceLandmark.RIGHT_EYE,
            FaceLandmark.LEFT_EYE,
            FaceLandmark.NOSE_BASE,
            FaceLandmark.MOUTH_BOTTOM,
            FaceLandmark.MOUTH_LEFT,
            FaceLandmark.MOUTH_RIGHT
        ).all { face.getLandmark(it) != null }

        val isValid = facingApprove && hasContours && hasLandmarks

        if (isValid) {
            if (phaseStartTime == 0L) phaseStartTime = System.currentTimeMillis()

            val elapsed = System.currentTimeMillis() - phaseStartTime
            binding.statusText.text =
                "Hold position.... ${(5000 - elapsed).coerceAtLeast(0) / 1000}s"

            if (elapsed >= 5000) {
                currentPhase = 2
                phaseStartTime = 0L
                currentChallenge = generateChallenge(face)
                challengePassed = false
            }
        } else {
            resetPhases()
            binding.statusText.text = "Wajah tidak valid ❌"
        }
    }

    private fun challengeVerification(face: Face) {
        val (challengeName, expectedResult) = currentChallenge ?: return
        val currentResult = evaluateChallenge(face, challengeName)

        if (currentResult == expectedResult) {
            challengePassed = true

            if (phaseStartTime == 0L) phaseStartTime = System.currentTimeMillis()

            val elapsed = System.currentTimeMillis() - phaseStartTime
            binding.statusText.text =
                "Challenge \"$challengeName\" → ${(3000 - elapsed).coerceAtLeast(0) / 1000}s"

            if (elapsed >= 3000) {
                binding.statusText.text = "Verifikasi BERHASIL ✅"

                // Jeda 7 detik sebelum lanjutkan proses berikutnya
                binding.statusText.postDelayed({
                    resetPhases()
                    binding.statusText.text = ""
                }, 7000) // Delay 7 detik
            }
        } else {
            challengePassed = false

            // Langsung ulangi challenge jika gagal
            binding.statusText.text = "Challenge gagal. Coba lagi ❌"
            currentChallenge = generateChallenge(face)
            phaseStartTime = 0L
        }
    }

    private fun evaluateChallenge(face: Face, challenge: String): Boolean {
        val smileProb = face.smilingProbability ?: 0F
        val eyeLeftProb = face.leftEyeOpenProbability ?: 0F
        val eyeRightProb = face.rightEyeOpenProbability ?: 0F

        return when (challenge) {
            "smile" -> smileProb > 0.7 // Smile condition
            "leftEyeClosed" -> eyeLeftProb < 0.3 // Left eye closed condition
            "rightEyeClosed" -> eyeRightProb < 0.3 // Right eye closed condition
            "blinkEye" -> eyeLeftProb < 0.3 && eyeRightProb < 0.3 // Blink condition (both eyes closed)
            else -> false
        }
    }

    private fun generateChallenge(face: Face): Pair<String, Boolean> {
        val smileProb = face.smilingProbability ?: 0F
        val eyeLeftProb = face.leftEyeOpenProbability ?: 0F
        val eyeRightProb = face.rightEyeOpenProbability ?: 0F

        val challengeConditions = mapOf(
            "smile" to (smileProb > 0.7),
            "leftEyeClosed" to (eyeLeftProb < 0.3),
            "rightEyeClosed" to (eyeRightProb < 0.3),
            "blinkEye" to (eyeLeftProb < 0.3 && eyeRightProb < 0.3)
        )

        return challengeConditions.entries.random().toPair()
    }

    private fun resetPhases() {
        currentPhase = 1
        phaseStartTime = 0L
        currentChallenge = null
        challengePassed = false
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