package com.dtpos.salonmanager.presentation.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.presentation.messages.WhatsAppGreen
import com.dtpos.salonmanager.presentation.theme.SalonTheme
import com.dtpos.salonmanager.services.export.ExternalApps
import com.dtpos.salonmanager.services.export.ShareHelper
import com.dtpos.salonmanager.services.export.WhatsAppResult
import com.dtpos.salonmanager.services.printer.PaperWidth
import com.dtpos.salonmanager.services.printer.PrintLine
import com.dtpos.salonmanager.services.printer.PrintResult
import com.dtpos.salonmanager.services.printer.ReceiptPrinter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** A slip shown before printing or sending (Close Day report, staff payment, udhaar payment). */
class SlipPreview(
    val title: String,
    /** What the printer gets. */
    val lines: List<PrintLine>,
    /** The same slip at the paper width (58 / 80 mm), with a white margin. */
    val bitmap: Bitmap,
    /** That picture as a PNG for WhatsApp / share (null if it could not be saved). */
    val file: File?,
    /** Text for WhatsApp / share. */
    val message: String,
    /** WhatsApp opens this number's chat; without one the owner picks the chat. */
    val phone: String?,
    val paper: PaperWidth,
)

/**
 * Builds, shows and prints slip previews for a screen. The picture is the one the printer
 * prints, so what the owner sees, prints and sends is the same slip.
 */
class SlipPreviews(private val printer: ReceiptPrinter, private val app: Context, private val scope: CoroutineScope) {
    val current = MutableStateFlow<SlipPreview?>(null)
    val printing = MutableStateFlow(false)

    /** After Print: the result shown in the dialog (success, or why it did not print). */
    val printResult = MutableStateFlow<PrintResult?>(null)

    suspend fun build(title: String, lines: List<PrintLine>, message: String, phone: String?, fileName: String): SlipPreview {
        val slip = printer.slipBitmap(lines)
        val paper = printer.paper()
        return withContext(Dispatchers.IO) {
            val pad = MARGIN_PX
            val bitmap = Bitmap.createBitmap(slip.width + pad * 2, slip.height + pad * 2, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply {
                drawColor(Color.WHITE)
                drawBitmap(slip, pad.toFloat(), pad.toFloat(), null)
            }
            val file = try {
                val dir = File(app.cacheDir, "shared/slips").apply { mkdirs() }
                File(dir, "$fileName.png").also { f -> FileOutputStream(f).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            } catch (e: Exception) {
                null
            }
            SlipPreview(title, lines, bitmap, file, message, phone?.takeIf { it.isNotBlank() }, paper)
        }
    }

    suspend fun show(title: String, lines: List<PrintLine>, message: String, phone: String?, fileName: String) {
        val preview = build(title, lines, message, phone, fileName)
        printResult.value = null
        current.value = preview
    }

    fun print() {
        val preview = current.value ?: return
        if (printing.value) return
        printing.value = true
        printResult.value = null
        scope.launch {
            try {
                printResult.value = printer.printLines(preview.lines)
            } finally {
                printing.value = false
            }
        }
    }

    fun close() {
        current.value = null
        printResult.value = null
    }

    companion object {
        const val MARGIN_PX = 20
    }
}

/**
 * The slip as it prints, with Print, WhatsApp (picture or message, to the saved number) and
 * Share. Print errors (no printer set up, printer off) show here, not behind the dialog.
 */
@Composable
fun SlipPreviewDialog(previews: SlipPreviews) {
    val preview by previews.current.collectAsStateWithLifecycle()
    val p = preview ?: return
    val context = LocalContext.current
    val printing by previews.printing.collectAsStateWithLifecycle()
    val printResult by previews.printResult.collectAsStateWithLifecycle()
    val whatsApp = rememberWhatsAppLauncher()
    val uri = remember(p.file) { p.file?.let { ShareHelper.uriFor(context, it) } }
    val title = p.title
    AlertDialog(
        onDismissRequest = previews::close,
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.slip_preview_hint, if (p.paper == PaperWidth.MM58) "58 mm" else "80 mm"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)).padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = p.bitmap.asImageBitmap(),
                        contentDescription = title,
                        modifier = Modifier.fillMaxWidth(if (p.paper == PaperWidth.MM58) 0.86f else 1f),
                        contentScale = ContentScale.FillWidth,
                    )
                }
                Button(onClick = previews::print, enabled = !printing, modifier = Modifier.fillMaxWidth()) {
                    if (printing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.slip_print))
                    }
                }
                when (val r = printResult) {
                    PrintResult.Success -> Text(stringResource(R.string.slip_printed), color = SalonTheme.extended.positive, style = MaterialTheme.typography.bodySmall)
                    is PrintResult.Failure -> Text(stringResource(r.error.messageRes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    null -> Unit
                }
                Button(
                    onClick = { whatsApp.image(uri, p.phone, p.message, title) },
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen, contentColor = androidx.compose.ui.graphics.Color.White),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.slip_wa_picture))
                }
                OutlinedButton(onClick = { whatsApp.text(p.phone, p.message, title) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Sms, contentDescription = null, modifier = Modifier.size(18.dp), tint = WhatsAppGreen)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.slip_wa_text), color = WhatsAppGreen)
                }
                OutlinedButton(
                    onClick = {
                        val ok = if (uri != null) ExternalApps.shareImage(context, uri, "image/png", p.message, title) else ExternalApps.shareText(context, p.message, title)
                        if (!ok) context.showWhatsAppResult(WhatsAppResult.FAILED, p.message)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.slip_share))
                }
                Text(
                    if (p.phone == null) stringResource(R.string.slip_no_phone) else stringResource(R.string.slip_phone, p.phone),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = previews::close) { Text(stringResource(R.string.action_close)) } },
    )
}
