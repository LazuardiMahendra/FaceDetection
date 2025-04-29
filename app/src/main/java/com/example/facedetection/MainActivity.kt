package com.example.facedetection

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
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
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var faceDetector: FaceDetector

    private var currentPhase = 1
    private var phaseStartTime: Long = 0L
    private var challengePassed = false

    private var isSmile = false
    private var isLeftEyeClosed = false
    private var isRightEyeClosed = false

    private val challengeList: MutableList<String> = mutableListOf()
    private var currentChallenge = ""

    private var imageUri: Uri? = null


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

                val faceViewModel = FaceViewModel(face)
                val faceDrawable = FaceDrawable(faceViewModel)

                binding.viewFinder.setOnTouchListener(faceViewModel.faceTouchCallback)
                binding.viewFinder.overlay.clear()
                binding.viewFinder.overlay.add(faceDrawable)
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

        if (phaseStartTime == 0L) phaseStartTime = System.currentTimeMillis()
        val elapsed = System.currentTimeMillis() - phaseStartTime
        binding.statusText.text = "Hold position.... ${(1000 - elapsed).coerceAtLeast(0) / 1000}s"

        if (elapsed >= 1000) {
            if (isValid) {
                if ((face.smilingProbability ?: 0F) > 0.7) isSmile = true
                if ((face.leftEyeOpenProbability ?: 0F) < 0.2) isLeftEyeClosed = true
                if ((face.rightEyeOpenProbability ?: 0F) < 0.2) isRightEyeClosed = true

                currentPhase = 2
                phaseStartTime = 0L
                challengePassed = false
                currentChallenge = generateChallenges()

            } else {
                resetPhases()
                binding.statusText.text = "Invalid face ❌"
                Log.d("CHALLENGE", "Challenge List: $challengeList")

            }
        }
    }

    private fun challengeVerification(face: Face) {
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
        if (!isValid) resetPhases()

        binding.statusText.text = "Challenge $currentChallenge"
        checkingChallengeVerification(face)
        if (challengePassed) {
            binding.statusText.text = "Verifikasi BERHASIL ✅"
        }
    }

    private fun generateChallenges(): String {
        if (!isSmile) challengeList.add("smile")
        if (!isLeftEyeClosed) challengeList.add("closed left eye")
        if (!isRightEyeClosed) challengeList.add("closed right eye")
        if (!isRightEyeClosed && !isLeftEyeClosed) challengeList.add("blink")

        return challengeList.random()
    }

    private fun checkingChallengeVerification(face: Face) {
        when (currentChallenge) {
            "smile" -> {
                if ((face.smilingProbability ?: 0F) > 0.7) {
                    challengePassed = true
                }
            }

            "closed left eye" -> {
                if ((face.leftEyeOpenProbability ?: 0F) > 0.2 && (face.rightEyeOpenProbability
                        ?: 0F) < 0.4
                ) {
                    challengePassed = true
                }
            }

            "closed right eye" -> {
                if ((face.rightEyeOpenProbability ?: 0F) > 0.2 && (face.leftEyeOpenProbability
                        ?: 0F) < 0.4
                ) {
                    challengePassed = true
                }
            }

            "blink" -> {
                if ((face.rightEyeOpenProbability ?: 0F) < 0.2 && (face.leftEyeOpenProbability
                        ?: 0F) < 0.2
                ) {
                    challengePassed = true
                }
            }

            else -> {
                resetPhases()
            }
        }
    }

    private fun resetPhases() {
        currentPhase = 1
        phaseStartTime = 0L
        currentChallenge = ""
        challengePassed = false
        isSmile = false
        isLeftEyeClosed = false
        isRightEyeClosed = false
        challengeList.clear()

    }

    private fun saveToGallery(photoPath: String?) {
        if (photoPath == null) return

        val file = File(photoPath)
        if (!file.exists()) {
            Log.e("ERROR", "File not found in path: $photoPath")
            return
        }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GlobalExtreme")
        }
        val uri =
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        uri?.let {
            try {
                contentResolver.openOutputStream(it)?.use { outputStream ->
                    FileInputStream(file).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.d("RESULT", "Failed load image to gallery $e")
            }
        }
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