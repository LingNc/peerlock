package com.peerlock.ui.common

import android.Manifest
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    onResult: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var zoomLevel by remember { mutableFloatStateOf(1f) }
    var hasResult by remember { mutableStateOf(false) }
    var hasCameraPermission by remember { mutableStateOf(false) }

    // 请求摄像头权限
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            Toast.makeText(context, "需要摄像头权限才能扫描二维码", Toast.LENGTH_SHORT).show()
        }
    }

    // 首次进入时请求权限
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            hasCameraPermission = true
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // 从相册选择
    val galleryLauncher = rememberLauncherForActivityResult(
        PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val bitmap = context.contentResolver.openInputStream(it)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
                if (bitmap == null) {
                    Toast.makeText(context, "无法读取图片", Toast.LENGTH_SHORT).show()
                    return@let
                }
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
                val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                try {
                    val result = MultiFormatReader().decode(binaryBitmap)
                    if (!hasResult) {
                        hasResult = true
                        onResult(result.text)
                    }
                } catch (_: Exception) {
                    Toast.makeText(context, "未识别到二维码", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "读取图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("扫描二维码") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        val newZoom = (zoomLevel * zoom).coerceIn(1f, 10f)
                        zoomLevel = newZoom
                        cameraControl?.setZoomRatio(newZoom)
                    }
                },
        ) {
            if (hasCameraPermission) {
                // 相机预览
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }
                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also { analysis ->
                                        analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                            if (hasResult) {
                                                imageProxy.close()
                                                return@setAnalyzer
                                            }
                                            val buffer = imageProxy.planes[0].buffer
                                            val bytes = ByteArray(buffer.remaining())
                                            buffer.get(bytes)
                                            val source = PlanarYUVLuminanceSource(
                                                bytes,
                                                imageProxy.width,
                                                imageProxy.height,
                                                0, 0,
                                                imageProxy.width,
                                                imageProxy.height,
                                                false,
                                            )
                                            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                                            try {
                                                val result = MultiFormatReader().decode(binaryBitmap)
                                                if (!hasResult) {
                                                    hasResult = true
                                                    onResult(result.text)
                                                }
                                            } catch (_: Exception) {
                                            } finally {
                                                imageProxy.close()
                                            }
                                        }
                                    }
                                cameraProvider.unbindAll()
                                val camera = cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageAnalysis,
                                )
                                cameraControl = camera.cameraControl
                            } catch (_: Exception) {}
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                // 扫描框
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(250.dp)
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
                    )
                }

                // 底部控制栏
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "缩放: ${"%.1f".format(zoomLevel)}x",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "双指缩放调整大小",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // 底部栏：取消（左）+ 相册（右下角）
                    Box(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            onClick = onClose,
                            modifier = Modifier.align(Alignment.CenterStart),
                        ) {
                            Text("取消", color = Color.White)
                        }
                        IconButton(
                            onClick = { galleryLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                        ) {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = "从相册选择",
                                tint = Color.White,
                            )
                        }
                    }
                }
            } else {
                // 无权限提示
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "需要摄像头权限才能扫描二维码",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    ) {
                        Text("授予权限")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = { galleryLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }) {
                        Text("从相册选择")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onClose) {
                        Text("取消")
                    }
                }
            }
        }
    }
}
