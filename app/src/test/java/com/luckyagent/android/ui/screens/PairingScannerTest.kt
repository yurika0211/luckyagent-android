package com.luckyagent.android.ui.screens

import com.google.zxing.common.BitMatrix
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingScannerTest {
    private val payload = """{"v":1,"url":"http://192.168.1.8:9090","token":"lp_test","name":"desk"}"""

    @Test
    fun paddedPortraitFrameDecodesTheSamePairingJson() {
        val matrix = qrMatrix(payload)
        val scale = 8
        val width = matrix.width * scale
        val height = matrix.height * scale
        val rowStride = width + 17
        val y = ByteArray(rowStride * height)
        paintQr(y, matrix, scale, rowStride)
        val frame = rotateYPlane(y, width, height, rowStride, rotationDegrees = 0)
        requireNotNull(frame)
        assertEquals(width, frame.width)
        assertEquals(height, frame.height)
        assertEquals(payload, decodeQrLuminance(frame))
    }

    @Test
    fun rotation90And270SwapAxesAndStillDecode() {
        val matrix = qrMatrix(payload)
        val scale = 8
        val width = matrix.width * scale
        val height = matrix.height * scale
        val rowStride = width + 8
        val y = ByteArray(rowStride * height)
        paintQr(y, matrix, scale, rowStride)
        val upright = requireNotNull(rotateYPlane(y, width, height, rowStride, 0))
        val turned90 = requireNotNull(rotateYPlane(y, width, height, rowStride, 90))
        val turned270 = requireNotNull(rotateYPlane(y, width, height, rowStride, 270))
        assertEquals(height, turned90.width)
        assertEquals(width, turned90.height)
        assertEquals(height, turned270.width)
        assertEquals(width, turned270.height)
        assertNotEquals(upright.bytes.toList(), turned90.bytes.toList())
        assertEquals(payload, decodeQrLuminance(turned90))
        assertEquals(payload, decodeQrLuminance(turned270))
    }

    @Test
    fun cropWiderThanRowStrideIsRejected() {
        assertNull(rotateYPlane(ByteArray(8), width = 4, height = 2, rowStride = 3, rotationDegrees = 0))
    }

    private fun paintQr(y: ByteArray, matrix: BitMatrix, scale: Int, rowStride: Int) {
        val width = matrix.width * scale
        val height = matrix.height * scale
        for (row in 0 until height) {
            for (col in 0 until width) {
                val dark = matrix.get(col / scale, row / scale)
                y[row * rowStride + col] = if (dark) 0 else 255.toByte()
            }
        }
    }

    private fun qrMatrix(text: String) = QRCodeWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        41,
        41,
        mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 2,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        ),
    )
}
