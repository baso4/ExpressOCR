package com.oz.expressocr

import android.content.pm.PackageManager
import android.graphics.Rect
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.min

@androidx.camera.core.ExperimentalGetImage
class CameraActivity : AppCompatActivity() {

    // View 引用
    private lateinit var overlayView: OverlayView
    private lateinit var logTextView: TextView
    private lateinit var logScrollView: ScrollView
    private lateinit var cameraPreview: PreviewView
    private lateinit var highlightedCode: TextView

    // 资源和工具
    private lateinit var toneGenerator: ToneGenerator
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraExecutor: ExecutorService

    // 业务数据
    private var codesToScan: List<String> = emptyList()

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
        private const val LOG_MAX_LENGTH = 2000
        private const val TAG = "CameraActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        // 初始化 View
        cameraPreview = findViewById(R.id.camera_preview)
        highlightedCode = findViewById(R.id.highlighted_code)
        overlayView = findViewById(R.id.overlay_view)
        logTextView = findViewById(R.id.log_text_view)
        logScrollView = findViewById(R.id.log_scroll_view)

        // 初始化数据和工具
        codesToScan = intent.getStringArrayListExtra("codes") ?: arrayListOf()
        toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // 检查并请求相机权限
        checkCameraPermission()
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            // 权限已授予，开始设置相机
            startCamera()
        } else {
            // 请求权限
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Toast.makeText(this, "相机权限被拒绝", Toast.LENGTH_SHORT).show()
                finish() // 如果没有权限，关闭 Activity
            }
        }
    }

    private fun startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases(cameraProvider)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(cameraPreview.surfaceProvider)
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, this::analyzeImage) // 提取分析逻辑到独立方法
            }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
        } catch (exc: Exception) {
            Log.e(TAG, "相机绑定失败", exc)
        }
    }

    private fun analyzeImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    var matched = false
                    val logBuilder = StringBuilder()

                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            val processedLineText = line.text.replace("\\s".toRegex(), "")
                            val numericText = mapToNumeric(processedLineText)
                            if (numericText.isNotEmpty()) {
                                logBuilder.append("识别到: $numericText\n")
                            }

                            for (code in codesToScan) {
                                if (processedLineText.contains(code)) {
                                    Log.d(TAG, "匹配成功: $code")
                                    matched = true
                                    val boundingBox = line.boundingBox
                                    // 在主线程更新 UI
                                    runOnUiThread {
                                        highlightedCode.text = code
                                        highlightedCode.visibility = TextView.VISIBLE
                                        boundingBox?.let {
                                            val mappedBox = mapBoundingBoxToPreview(
                                                it,
                                                mediaImage.width,
                                                mediaImage.height,
                                                rotationDegrees,
                                                cameraPreview
                                            )
                                            overlayView.updateBoundingBoxWithText(mappedBox, code)
                                        }
                                    }
                                    break
                                }
                            }
                            if (matched) break
                        }
                        if (matched) break
                    }

                    updateUiOnResult(matched, logBuilder)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "文本识别失败", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun updateUiOnResult(matched: Boolean, logBuilder: StringBuilder) {
        runOnUiThread {
            // 更新日志
            if (logBuilder.isNotEmpty()) {
                if (logTextView.text.length > LOG_MAX_LENGTH) {
                    logTextView.text = ""
                }
                logTextView.append(logBuilder.toString())
                logScrollView.post { logScrollView.fullScroll(View.FOCUS_DOWN) }
            }

            // 处理匹配结果
            if (matched) {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP)
            } else {
                overlayView.updateBoundingBox(null) // 清除框选
            }
        }
    }

    private fun mapBoundingBoxToPreview(
        boundingBox: Rect, imageWidth: Int, imageHeight: Int, rotationDegrees: Int, previewView: PreviewView
    ): Rect {
        val rotatedImageWidth = if (rotationDegrees == 90 || rotationDegrees == 270) imageHeight else imageWidth
        val rotatedImageHeight = if (rotationDegrees == 90 || rotationDegrees == 270) imageWidth else imageHeight

        val scaleX = previewView.width.toFloat() / rotatedImageWidth
        val scaleY = previewView.height.toFloat() / rotatedImageHeight
        val scale = min(scaleX, scaleY)

        val offsetX = (previewView.width - rotatedImageWidth * scale) / 2
        val offsetY = (previewView.height - rotatedImageHeight * scale) / 2

        return Rect(
            (boundingBox.left * scale + offsetX).toInt(),
            (boundingBox.top * scale + offsetY).toInt(),
            (boundingBox.right * scale + offsetX).toInt(),
            (boundingBox.bottom * scale + offsetY).toInt()
        )
    }

    private fun mapToNumeric(text: String): String {
        val charMap = mapOf('z' to '2', 'Z' to '2', 'o' to '0', 'O' to '0', 'l' to '1', 'I' to '1')
        return text.map { charMap[it] ?: it }.filter { it.isDigit() }.joinToString("")
    }

    override fun onDestroy() {
        super.onDestroy()
        toneGenerator.release()
        cameraExecutor.shutdown()
    }
}