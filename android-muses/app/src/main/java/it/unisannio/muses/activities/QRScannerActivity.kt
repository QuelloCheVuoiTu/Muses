package it.unisannio.muses.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import it.unisannio.muses.R
import it.unisannio.muses.data.repositories.TaskRepository
import it.unisannio.muses.helpers.AuthTokenManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QRScannerActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Log.e("QRScanner", "Camera permission denied")
            Toast.makeText(this, "Camera permission is required to scan QR codes", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scanner)

        Log.d("QRScanner", "Starting QR scanner")

        previewView = findViewById(R.id.previewView)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Check camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, QRCodeAnalyzer { qrCode ->
                        Log.d("QRScanner", "QR Code detected: $qrCode")
                        handleQRCodeDetected(qrCode)
                    })
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e("QRScanner", "Use case binding failed", exc)
                Toast.makeText(this, "Failed to start camera", Toast.LENGTH_SHORT).show()
                finish()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun handleQRCodeDetected(qrCode: String) {
        // Stop camera to prevent multiple scans
        cameraProvider?.unbindAll()

        runOnUiThread {
            Toast.makeText(this, "QR Code scanned! Completing task...", Toast.LENGTH_SHORT).show()
        }

        // Complete the task
        lifecycleScope.launch {
            try {
                Log.d("QRScanner", "Starting task completion process...")
                val authTokenManager = AuthTokenManager(this@QRScannerActivity)
                val userId = authTokenManager.getEntityId()

                if (userId != null) {
                    Log.d("QRScanner", "User ID: $userId")
                    val taskRepository = TaskRepository()
                    
                    // Aggiungi un timeout per evitare operazioni troppo lunghe
                    val result = withContext(Dispatchers.IO) {
                        withTimeout(30000) { // 30 secondi di timeout
                            taskRepository.completeTaskWithQR(userId, qrCode)
                        }
                    }

                    // Controlla se l'activity è ancora attiva prima di procedere
                    if (!isFinishing && !isDestroyed) {
                        if (result.isSuccess) {
                            Log.d("QRScanner", "Task completed successfully")
                            runOnUiThread {
                                Toast.makeText(this@QRScannerActivity, "Task completed successfully!", Toast.LENGTH_SHORT).show()
                            }
                            
                            // Return success result
                            setResult(RESULT_OK)
                            finish()
                        } else {
                            val error = result.exceptionOrNull()
                            Log.e("QRScanner", "Failed to complete task: ${error?.message}")
                            runOnUiThread {
                                Toast.makeText(this@QRScannerActivity, 
                                    "Failed to complete task: ${error?.message}", Toast.LENGTH_LONG).show()
                            }
                            // Restart camera for retry
                            startCamera()
                        }
                    } else {
                        Log.d("QRScanner", "Activity is finishing, skipping UI updates")
                    }
                } else {
                    Log.e("QRScanner", "User ID not available")
                    if (!isFinishing && !isDestroyed) {
                        runOnUiThread {
                            Toast.makeText(this@QRScannerActivity, "User authentication error", Toast.LENGTH_SHORT).show()
                        }
                        finish()
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Log.e("QRScanner", "Task completion timed out", e)
                if (!isFinishing && !isDestroyed) {
                    runOnUiThread {
                        Toast.makeText(this@QRScannerActivity, "Task completion timed out. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                    startCamera()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.w("QRScanner", "Task completion was cancelled", e)
                // Non fare nulla se è stata cancellata - è normale quando l'activity si chiude
            } catch (e: Exception) {
                Log.e("QRScanner", "Exception completing task", e)
                if (!isFinishing && !isDestroyed) {
                    runOnUiThread {
                        Toast.makeText(this@QRScannerActivity, "Error completing task: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    // Restart camera for retry
                    startCamera()
                }
            }
        }
    }

    private inner class QRCodeAnalyzer(private val onQRCodeDetected: (String) -> Unit) : ImageAnalysis.Analyzer {
        private val scanner = BarcodeScanning.getClient()

        override fun analyze(image: ImageProxy) {
            val mediaImage = image.image
            if (mediaImage != null) {
                val inputImage = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
                
                scanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            when (barcode.valueType) {
                                Barcode.TYPE_TEXT, Barcode.TYPE_URL -> {
                                    barcode.displayValue?.let { value ->
                                        onQRCodeDetected(value)
                                    }
                                }
                            }
                        }
                    }
                    .addOnFailureListener {
                        Log.e("QRScanner", "Barcode scanning failed", it)
                    }
                    .addOnCompleteListener {
                        image.close()
                    }
            } else {
                image.close()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
