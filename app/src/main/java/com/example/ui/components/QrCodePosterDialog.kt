package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Patterns
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.R
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream

private const val DEFAULT_SHARED_URL =
    "https://ais-pre-i3xjerikdrxpqtxqliyu3x-26812080681.europe-west2.run.app"

@Composable
fun QrCodePosterDialog(
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    var downloadUrl by remember { mutableStateOf(DEFAULT_SHARED_URL) }

    val cleanUrl = downloadUrl.trim()
    val isValidUrl = remember(cleanUrl) {
        (cleanUrl.startsWith("https://", ignoreCase = true) ||
            cleanUrl.startsWith("http://", ignoreCase = true)) &&
            Patterns.WEB_URL.matcher(cleanUrl).matches()
    }

    val espressoArgb = MaterialTheme.colorScheme.primary.toArgb()
    val creamArgb = Color(0xFFFFFCF8).toArgb()

    val qrBitmap = remember(cleanUrl, isValidUrl) {
        if (isValidUrl) generateQrBitmap(cleanUrl, 540, espressoArgb, creamArgb) else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.QrCode2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "باركود تحميل تطبيق بن يزن",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "استخرج ملف APK من إعدادات المشروع (Generate APK) ثم ارفعه على Google Drive أو Google Play وضع رابط التحميل المباشر هنا لطباعة الباركود وتعليقه في المحل.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }

                OutlinedTextField(
                    value = downloadUrl,
                    onValueChange = { downloadUrl = it },
                    label = { Text("رابط تحميل التطبيق (APK / Google Play)") },
                    singleLine = true,
                    isError = cleanUrl.isNotEmpty() && !isValidUrl,
                    supportingText = {
                        if (cleanUrl.isNotEmpty() && !isValidUrl) {
                            Text("يرجى إدخال رابط يبدأ بـ https://")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("qr_url_input"),
                )

                if (qrBitmap != null) {
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFFFFCF8))
                            .border(
                                2.dp,
                                MaterialTheme.colorScheme.tertiary,
                                RoundedCornerShape(20.dp),
                            )
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "رمز QR لتحميل تطبيق بن يزن",
                            modifier = Modifier.size(196.dp),
                        )
                    }
                    Text(
                        text = "امسح الكود لتحميل تطبيق بن يزن",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (qrBitmap != null) {
                        shareQrPoster(
                            context = context,
                            qrBitmap = qrBitmap,
                            onError = { onMessage("تعذر مشاركة صورة الباركود") },
                        )
                    }
                },
                enabled = qrBitmap != null,
                modifier = Modifier.testTag("share_qr_poster_button"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("مشاركة / طباعة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق")
            }
        },
    )
}

private fun generateQrBitmap(
    content: String,
    sizePx: Int,
    darkColor: Int,
    lightColor: Int,
): Bitmap? {
    return try {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.MARGIN to 1,
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            val offset = y * sizePx
            for (x in 0 until sizePx) {
                pixels[offset + x] = if (bitMatrix[x, y]) darkColor else lightColor
            }
        }
        Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
        }
    } catch (_: Exception) {
        null
    }
}

private fun shareQrPoster(
    context: Context,
    qrBitmap: Bitmap,
    onError: () -> Unit,
) {
    try {
        val poster = createBrandedPosterBitmap(context, qrBitmap)
        val dir = File(context.cacheDir, "qr_posters").apply { mkdirs() }
        val file = File(dir, "bon_yazan_qr_poster.png")
        FileOutputStream(file).use { out ->
            poster.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "باركود تطبيق بن يزن")
            putExtra(Intent.EXTRA_TEXT, "امسح الكود لتحميل تطبيق بن يزن وطلب قهوتك بسهولة")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "مشاركة أو طباعة باركود بن يزن"))
    } catch (_: Exception) {
        onError()
    }
}

private fun createBrandedPosterBitmap(context: Context, qrBitmap: Bitmap): Bitmap {
    val width = 900
    val height = 1220
    val poster = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(poster)

    // Warm cream background
    canvas.drawColor(0xFFFAF6F0.toInt())

    // Top Espresso Header Banner
    val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4E2A1E.toInt() }
    canvas.drawRoundRect(RectF(48f, 48f, width - 48f, 290f), 40f, 40f, headerPaint)

    // Circular logo inside header
    val rawLogo = BitmapFactory.decodeResource(context.resources, R.drawable.bon_yazan_logo)
    if (rawLogo != null) {
        val logoSize = 180
        val circularLogo = cropCircle(rawLogo, logoSize)
        val goldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFB8862D.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }
        val logoLeft = (width - logoSize) / 2f
        val logoTop = 190f
        canvas.drawBitmap(circularLogo, logoLeft, logoTop, null)
        canvas.drawCircle(width / 2f, logoTop + logoSize / 2f, logoSize / 2f + 2f, goldPaint)
    }

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 56f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText("بن يزن", width / 2f, 130f, titlePaint)

    val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFF0C7.toInt()
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText("مطحنة بن ومشاريب طازجة", width / 2f, 172f, subtitlePaint)

    // Card behind QR code
    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFCF8.toInt() }
    val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB8862D.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    val cardRect = RectF(130f, 420f, width - 130f, 1020f)
    canvas.drawRoundRect(cardRect, 42f, 42f, cardPaint)
    canvas.drawRoundRect(cardRect, 42f, 42f, cardBorderPaint)

    val qrDest = Rect(185, 455, width - 185, 985)
    canvas.drawBitmap(qrBitmap, null, qrDest, null)

    val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4E2A1E.toInt()
        textSize = 36f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText("امسح الكود لتحميل تطبيق بن يزن", width / 2f, 1100f, footerPaint)

    val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF7B5038.toInt()
        textSize = 26f
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText("اطلب قهوتك واستلمها جاهزة بدون انتظار", width / 2f, 1150f, hintPaint)

    return poster
}

private fun cropCircle(source: Bitmap, size: Int): Bitmap {
    val scaled = Bitmap.createScaledBitmap(source, size, size, true)
    val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val rect = Rect(0, 0, size, size)
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(scaled, rect, rect, paint)
    return output
}
