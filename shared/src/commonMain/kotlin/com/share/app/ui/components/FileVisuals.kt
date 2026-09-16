package com.share.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.share.app.domain.media.FileKind
import com.share.app.domain.media.FileTypes
import com.share.app.domain.media.ImageFormat
import com.share.app.domain.media.ImageFormats
import com.share.app.domain.media.ImagePreview
import com.share.app.ui.convert.ConversionStatus
import com.share.app.ui.platform.decodePreview
import com.share.app.ui.theme.KnoticColors
import com.share.app.ui.theme.KnoticTheme
import com.share.app.util.humanFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decoded off the main thread; null until then, and for good if it cannot be read. */
@Composable
fun rememberPreviewBitmap(preview: ImagePreview?): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, preview) {
        value = preview?.let { withContext(Dispatchers.Default) { decodePreview(it.bytes) } }
    }
    return bitmap
}

private data class TileStyle(val icon: ImageVector, val background: Color, val tint: Color)

/** One hue per kind, so a PDF and a photo are told apart before either name is read. */
private fun tileStyle(kind: FileKind?, colors: KnoticColors): TileStyle = when (kind) {
    null -> TileStyle(Icons.Rounded.ChatBubbleOutline, colors.accentSoft, colors.accent)
    FileKind.IMAGE -> TileStyle(Icons.Rounded.Image, colors.violetSoft, colors.violet)
    FileKind.VIDEO -> TileStyle(Icons.Rounded.VideoFile, colors.pinkSoft, colors.pink)
    FileKind.AUDIO -> TileStyle(Icons.Rounded.AudioFile, colors.cyanSoft, colors.cyan)
    FileKind.PDF -> TileStyle(Icons.Rounded.PictureAsPdf, colors.dangerSoft, colors.danger)
    FileKind.DOCUMENT -> TileStyle(Icons.Rounded.Description, colors.accentSoft, colors.accent)
    FileKind.SPREADSHEET -> TileStyle(Icons.Rounded.TableChart, colors.emeraldSoft, colors.emerald)
    FileKind.PRESENTATION -> TileStyle(Icons.Rounded.Slideshow, colors.warnSoft, colors.warn)
    FileKind.ARCHIVE -> TileStyle(Icons.Rounded.FolderZip, colors.warnSoft, colors.warn)
    FileKind.CODE -> TileStyle(Icons.Rounded.Code, colors.cyanSoft, colors.cyan)
    FileKind.TEXT -> TileStyle(Icons.AutoMirrored.Rounded.Article, colors.surface2, colors.textMuted)
    FileKind.OTHER -> TileStyle(Icons.AutoMirrored.Rounded.InsertDriveFile, colors.surface2, colors.textMuted)
}

enum class TransferDirection { SENT, RECEIVED }

/**
 * A file at a glance: its own picture when there is one, otherwise an icon in
 * the colour of its kind with the format underneath. The corner badge keeps the
 * direction readable now that the tile is the file rather than an arrow.
 */
@Composable
fun FileThumbnail(
    name: String,
    contentType: String?,
    modifier: Modifier = Modifier,
    preview: ImagePreview? = null,
    message: Boolean = false,
    direction: TransferDirection? = null,
    failed: Boolean = false,
    size: Dp = 40.dp,
) {
    val colors = KnoticTheme.colors
    val type = if (message) null else FileTypes.describe(name, contentType)
    val style = tileStyle(type?.kind, colors)
    val bitmap = rememberPreviewBitmap(preview)
    val shape = RoundedCornerShape(size * 0.28f)

    Box(modifier.size(size)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(if (bitmap != null) colors.surface2 else style.background)
                .border(1.dp, colors.border, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(style.icon, contentDescription = null, tint = style.tint, modifier = Modifier.size(size * 0.42f))
                    if (type != null) {
                        Text(
                            type.label,
                            color = style.tint,
                            fontSize = if (size >= 48.dp) 8.sp else 7.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            letterSpacing = 0.3.sp,
                        )
                    }
                }
            }
        }

        if (direction != null) {
            val badge = when {
                failed -> colors.danger
                direction == TransferDirection.SENT -> colors.accent
                else -> colors.emerald
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 3.dp, y = 3.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(badge)
                    .border(1.5.dp, colors.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (direction == TransferDirection.SENT) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
    }
}

/**
 * The file itself, whenever the platform could draw it. A photo that just
 * arrived is recognised faster than its file name ever is. A video shows a
 * still frame; anything that could not be drawn shows nothing at all.
 */
@Composable
fun MediaPreview(preview: ImagePreview?, name: String, kind: FileKind, modifier: Modifier = Modifier) {
    val bitmap = rememberPreviewBitmap(preview) ?: return
    val colors = KnoticTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val ratio = (bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)).coerceIn(0.5f, 2.5f)

    Box(
        modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .clip(shape)
            .border(1.dp, colors.border, shape)
            .then(if (kind == FileKind.IMAGE) Modifier.checkerboard(colors) else Modifier.background(Color.Black))
            .semantics { contentDescription = "Preview of $name" },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().aspectRatio(ratio),
        )
        if (kind == FileKind.VIDEO) {
            Icon(
                Icons.Rounded.PlayCircle,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(44.dp),
            )
        }
    }
}

/** Shows through wherever a picture is transparent, so a transparent PNG does not look blank. */
private fun Modifier.checkerboard(colors: KnoticColors): Modifier = drawBehind {
    val cell = 8.dp.toPx()
    drawRect(colors.surface)
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = if (row % 2 == 0) 0f else cell
        while (x < size.width) {
            drawRect(colors.surface2, topLeft = Offset(x, y), size = Size(cell, cell))
            x += cell * 2
        }
        y += cell
        row += 1
    }
}

/**
 * "Save as" another format, done entirely on this device. Renders nothing for
 * a file that cannot be converted.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConvertOptions(
    fileName: String,
    mimeType: String,
    status: ConversionStatus,
    onConvert: (ImageFormat) -> Unit,
    onSaveAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val targets = ImageFormats.conversionTargets(mimeType)
    if (targets.isEmpty()) return
    val colors = KnoticTheme.colors
    val working = status is ConversionStatus.Working

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface2)
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { contentDescription = "Save $fileName as another format" },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = colors.violet, modifier = Modifier.size(14.dp))
                Text("Save as", color = colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            targets.forEach { format ->
                FormatChip(
                    label = format.label,
                    busy = status is ConversionStatus.Working && status.format == format,
                    done = status is ConversionStatus.Done && status.format == format && status.saved,
                    enabled = !working,
                    onClick = { onConvert(format) },
                )
            }
        }

        val line = conversionLine(status)
        if (line != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    line.text,
                    color = if (line.failed) colors.danger else colors.textMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (status is ConversionStatus.Done) {
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onSaveAgain)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, tint = colors.accent, modifier = Modifier.size(13.dp))
                        Text(
                            if (status.saved) "Save again" else "Save",
                            color = colors.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

private class StatusLine(val text: String, val failed: Boolean)

private fun conversionLine(status: ConversionStatus): StatusLine? = when (status) {
    ConversionStatus.Idle -> null
    is ConversionStatus.Working -> StatusLine("Converting to ${status.format.label} on this device...", failed = false)
    is ConversionStatus.Failed -> StatusLine(status.message, failed = true)
    is ConversionStatus.Done -> {
        val change = ImageFormats.describeSizeChange(status.originalSize, status.size)
        val lead = when {
            status.saveFailed -> "Converted ${status.fileName}, but it couldn't be saved"
            status.saved -> "Saved ${status.fileName}"
            else -> "Converted ${status.fileName}"
        }
        StatusLine(
            buildString {
                append(lead)
                append(" · ").append(humanFileSize(status.size))
                if (change.isNotEmpty()) append(" · ").append(change)
            },
            failed = status.saveFailed,
        )
    }
}

@Composable
private fun FormatChip(label: String, busy: Boolean, done: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = KnoticTheme.colors
    val border = when {
        done -> colors.success
        busy -> colors.violet
        else -> colors.border
    }
    Row(
        Modifier
            .clip(CircleShape)
            .background(colors.surface)
            .border(1.dp, border, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics { contentDescription = "Save as $label" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        when {
            busy -> CircularProgressIndicator(color = colors.violet, strokeWidth = 1.5.dp, modifier = Modifier.size(11.dp))
            done -> Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.success, modifier = Modifier.size(12.dp))
        }
        Text(
            label,
            color = if (enabled || busy) colors.text else colors.textFaint,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
