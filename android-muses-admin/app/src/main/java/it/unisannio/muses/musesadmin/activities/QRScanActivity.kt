package it.unisannio.muses.musesadmin.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import it.unisannio.muses.musesadmin.R
import it.unisannio.muses.musesadmin.api.RetrofitInstance
import it.unisannio.muses.musesadmin.data.repositories.RewardRepository
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QRScanActivity : AppCompatActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    private var isScanning = true

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required to scan QR codes", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scan)

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
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { qrCode ->
                        if (isScanning) {
                            isScanning = false
                            handleQRCode(qrCode)
                        }
                    })
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            } catch (exc: Exception) {
                Log.e("QRScanActivity", "Use case binding failed", exc)
                Toast.makeText(this, "Failed to start camera", Toast.LENGTH_SHORT).show()
                finish()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun handleQRCode(qrCode: String) {
        Log.d("QRScanActivity", "QR Code scanned: $qrCode")
        
        val viewModel = QRScanViewModel(RewardRepository(RetrofitInstance.api))
        viewModel.fetchRewardDetails(
            rewardId = qrCode,
            onSuccess = { rewardResponse ->
                // Navigate to reward details activity
                val intent = Intent(this, RewardDetailsActivity::class.java)
                intent.putExtra("REWARD_ID", qrCode)
                intent.putExtra("REWARD", rewardResponse.reward)
                startActivity(intent)
                finish()
            },
            onError = { error ->
                Toast.makeText(this, "Error: $error", Toast.LENGTH_LONG).show()
                isScanning = true // Allow scanning again on error
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private class BarcodeAnalyzer(private val onBarcodeDetected: (String) -> Unit) : ImageAnalysis.Analyzer {
        private val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        
        private val scanner = BarcodeScanning.getClient(options)

        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        if (barcodes.isNotEmpty()) {
                            val barcode = barcodes.first()
                            barcode.rawValue?.let { value ->
                                onBarcodeDetected(value)
                            }
                        }
                    }
                    .addOnFailureListener {
                        Log.e("BarcodeAnalyzer", "Barcode scanning failed", it)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }
}

class QRScanViewModel(private val rewardRepository: RewardRepository) : ViewModel() {
    
    fun fetchRewardDetails(
        rewardId: String,
        onSuccess: (it.unisannio.muses.musesadmin.data.models.RewardResponse) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = rewardRepository.getRewardDetails(rewardId)
            result.onSuccess { rewardResponse ->
                onSuccess(rewardResponse)
            }.onFailure { error ->
                onError(error.message ?: "Unknown error occurred")
            }
        }
    }
}