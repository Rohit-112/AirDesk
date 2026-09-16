package com.share.app.ui.convert

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.share.app.domain.media.ImageFormats
import com.share.app.ui.components.ConvertOptions
import com.share.app.ui.components.FileThumbnail
import com.share.app.ui.components.KnoticButton
import com.share.app.ui.components.KnoticButtonStyle
import com.share.app.ui.components.KnoticCard
import com.share.app.ui.components.KnoticIconButton
import com.share.app.ui.components.MediaPreview
import com.share.app.ui.platform.fileDropTarget
import com.share.app.ui.theme.KnoticTheme
import com.share.app.util.humanFileSize
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ConvertScreen(onBack: () -> Unit, viewModel: ConvertViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onIntent = viewModel::onIntent
    val colors = KnoticTheme.colors

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .fileDropTarget(
                onDragStateChange = { onIntent(ConvertIntent.DragStateChanged(it)) },
                onFilesDropped = { onIntent(ConvertIntent.FilesDropped(it)) },
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KnoticIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back to send and receive", onBack, tint = colors.text)
            Text("Convert only", color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Convert an image on your device", color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "HEIC, PNG, JPG, WEBP, GIF, BMP or AVIF in - JPG, PNG or WEBP out. The picture is redrawn on this device and never uploaded.",
                    color = colors.textMuted,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )

                val picked = state.picked
                if (picked == null) {
                    PickZone(dragging = state.isDraggingFile, reading = state.reading, onPick = { onIntent(ConvertIntent.PickClicked) })
                } else {
                    PickedCard(state, picked, onIntent)
                }

                state.error?.let { message ->
                    Text(
                        message,
                        color = colors.danger,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.dangerSoft)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }

                Text(
                    "Your file is never uploaded. It is read, redrawn and saved by this device alone. " +
                        "HEIC opens on Android 9 and later and on iPhone; the desktop app cannot read it yet.",
                    color = colors.textFaint,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun PickZone(dragging: Boolean, reading: Boolean, onPick: () -> Unit) {
    val colors = KnoticTheme.colors
    val stroke = if (dragging) colors.accent else colors.borderStrong
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface)
            .drawBehind {
                drawRoundRect(
                    color = stroke,
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))),
                    cornerRadius = CornerRadius(20.dp.toPx()),
                )
            }
            .clickable(enabled = !reading, onClick = onPick)
            .padding(horizontal = 24.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (reading) {
            CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        } else {
            Icon(Icons.Rounded.FileUpload, contentDescription = null, tint = colors.accent, modifier = Modifier.size(30.dp))
        }
        Text(
            when {
                reading -> "Reading..."
                dragging -> "Drop it here"
                else -> "Choose an image"
            },
            color = colors.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Nothing leaves this device",
            color = colors.textMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PickedCard(state: ConvertUiState, picked: PickedImage, onIntent: (ConvertIntent) -> Unit) {
    val colors = KnoticTheme.colors
    val convertible = ImageFormats.conversionTargets(picked.type.mimeType).isNotEmpty()

    KnoticCard(contentPadding = PaddingValues(16.dp)) {
        when {
            picked.preview != null -> MediaPreview(picked.preview, picked.name, picked.type.kind)
            picked.previewPending -> Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FileThumbnail(picked.name, picked.type.mimeType, size = 48.dp)
                Text(
                    if (convertible) {
                        "No preview for this ${picked.type.label} file on this device."
                    } else {
                        "This is a ${picked.type.label} file."
                    },
                    color = colors.textMuted,
                    fontSize = 13.sp,
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(picked.name, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${picked.type.label} · ${humanFileSize(picked.size)}", color = colors.textMuted, fontSize = 11.sp)
            }
            KnoticButton(
                text = "Choose another",
                onClick = { onIntent(ConvertIntent.PickClicked) },
                style = KnoticButtonStyle.SECONDARY,
                enabled = !state.reading,
                compact = true,
            )
        }

        if (convertible) {
            ConvertOptions(
                fileName = picked.name,
                mimeType = picked.type.mimeType,
                status = state.conversion.statusFor(picked.key),
                onConvert = { onIntent(ConvertIntent.FormatClicked(it)) },
                onSaveAgain = { onIntent(ConvertIntent.SaveAgainClicked) },
                modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            Text(
                "Conversion here is for images. To move this file to another device, go back and pair the two devices instead.",
                color = colors.textMuted,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
