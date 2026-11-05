package com.luckyagent.android.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@Composable
fun PairingScannerDialog(
    onDismiss: () -> Unit,
    onScanned: (String) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        PairingScanner(onDismiss = onDismiss, onScanned = onScanned)
    }
}

@Composable
private fun PairingScanner(
    onDismiss: () -> Unit,
    onScanned: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val delivered = remember { AtomicBoolean(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val missedFrames = remember { AtomicInteger(0) }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            val provider = try {
                future.get()
            } catch (error: Exception) {
                failure = error.message ?: "相机打不开"
                return@Runnable
            }
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(1280, 720),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                            ),
                        )
                        .build(),
                )
                .build()
            analysis.setAnalyzer(executor) { image ->
                if (delivered.get()) {
                    image.close()
                    return@setAnalyzer
                }
                val text = decodeQr(image)
                image.close()
                if (text.isNullOrBlank()) {
                    val misses = missedFrames.incrementAndGet()
                    if (misses == CAMERA_QR_MISS_LIMIT) {
                        previewView.post {
                            if (!delivered.get()) {
                                failure = "画面里还没认出配对码。可以对准 lh qr 再试，或改用相册和手动填写。"
                            }
                        }
                    }
                    return@setAnalyzer
                }
                if (!delivered.compareAndSet(false, true)) return@setAnalyzer
                previewView.post { onScanned(text) }
            }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (error: Exception) {
                failure = error.message ?: "相机打不开"
            }
        }
        future.addListener(listener, ContextCompat.getMainExecutor(context))
        onDispose {
            delivered.set(true)
            if (future.isDone) {
                runCatching { future.get().unbindAll() }
            }
            executor.shutdown()
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Text(
            failure ?: "把 lh qr 的二维码放进画面",
            color = Color.White,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp),
        )
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) {
            Text("取消", color = Color.White)
        }
    }
}

/** Frames analyzed with no QR before the hint becomes a readable failure. */
internal const val CAMERA_QR_MISS_LIMIT = 45

/**
 * Packed Y after rotation. 90 and 270 swap width and height.
 * Crop width never exceeds the source row stride.
 */
internal data class YPlaneFrame(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
)

internal fun rotateYPlane(
    y: ByteArray,
    width: Int,
    height: Int,
    rowStride: Int,
    rotationDegrees: Int,
): YPlaneFrame? {
    if (width <= 0 || height <= 0 || rowStride < width) return null
    val cropWidth = minOf(width, rowStride)
    val needed = rowStride * (height - 1) + cropWidth
    if (y.size < needed) return null
    val packed = ByteArray(cropWidth * height)
    var index = 0
    for (row in 0 until height) {
        val start = row * rowStride
        y.copyInto(packed, index, start, start + cropWidth)
        index += cropWidth
    }
    return when (rotationDegrees.mod(360)) {
        0 -> YPlaneFrame(packed, cropWidth, height)
        90 -> YPlaneFrame(rotatePacked(packed, cropWidth, height, 90), height, cropWidth)
        180 -> YPlaneFrame(rotatePacked(packed, cropWidth, height, 180), cropWidth, height)
        270 -> YPlaneFrame(rotatePacked(packed, cropWidth, height, 270), height, cropWidth)
        else -> null
    }
}

private fun rotatePacked(source: ByteArray, width: Int, height: Int, degrees: Int): ByteArray {
    val out = ByteArray(width * height)
    when (degrees) {
        90 -> {
            var index = 0
            for (x in 0 until width) {
                for (y in height - 1 downTo 0) {
                    out[index++] = source[y * width + x]
                }
            }
        }
        180 -> {
            var index = 0
            for (i in source.lastIndex downTo 0) out[index++] = source[i]
        }
        270 -> {
            var index = 0
            for (x in width - 1 downTo 0) {
                for (y in 0 until height) {
                    out[index++] = source[y * width + x]
                }
            }
        }
        else -> source.copyInto(out)
    }
    return out
}

internal fun decodeQrLuminance(frame: YPlaneFrame): String? {
    if (frame.width <= 0 || frame.height <= 0 || frame.bytes.size < frame.width * frame.height) return null
    val source = PlanarYUVLuminanceSource(
        frame.bytes,
        frame.width,
        frame.height,
        0,
        0,
        frame.width,
        frame.height,
        false,
    )
    val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.CHARACTER_SET to "UTF-8",
            ),
        )
    }
    return try {
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
    } catch (_: NotFoundException) {
        null
    } catch (_: Exception) {
        null
    } finally {
        reader.reset()
    }
}

internal fun decodeQr(image: androidx.camera.core.ImageProxy): String? {
    val plane = image.planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val frame = rotateYPlane(
        y = bytes,
        width = image.width,
        height = image.height,
        rowStride = plane.rowStride,
        rotationDegrees = image.imageInfo.rotationDegrees,
    ) ?: return null
    return decodeQrLuminance(frame)
}

internal fun decodeQrBitmap(bitmap: Bitmap): String? {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return null
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.CHARACTER_SET to "UTF-8",
            ),
        )
    }
    return try {
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels)))).text
    } catch (_: NotFoundException) {
        null
    } catch (_: Exception) {
        null
    } finally {
        reader.reset()
    }
}

internal fun decodeQrUri(context: android.content.Context, uri: Uri): String? {
    val bitmap = if (Build.VERSION.SDK_INT >= 28) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
        }
    } else {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    } ?: return null
    return try {
        decodeQrBitmap(bitmap)
    } finally {
        bitmap.recycle()
    }
}
