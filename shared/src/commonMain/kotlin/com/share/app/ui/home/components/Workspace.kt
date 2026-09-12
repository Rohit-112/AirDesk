package com.share.app.ui.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.share.app.domain.model.HistoryItem
import com.share.app.domain.model.HistoryStatus
import com.share.app.domain.model.SessionStatus
import com.share.app.domain.model.TransportMode
import com.share.app.domain.model.WebRtcStatus
import com.share.app.domain.policy.SessionLimits
import com.share.app.ui.components.Eyebrow
import com.share.app.ui.components.KnoticButton
import com.share.app.ui.components.KnoticButtonStyle
import com.share.app.ui.components.KnoticCard
import com.share.app.ui.components.KnoticIconButton
import com.share.app.ui.components.ProgressTrack
import com.share.app.ui.components.QuietCard
import com.share.app.ui.components.ToneBadge
import com.share.app.ui.components.WaitingGraphic
import com.share.app.ui.home.HomeIntent
import com.share.app.ui.home.HomeUiState
import com.share.app.ui.platform.sendShortcutHint
import com.share.app.ui.theme.KnoticTheme
import com.share.app.util.humanFileSize
import com.share.app.util.relativeTime

private const val VISIBLE_HISTORY_BY_DEFAULT = 4

/** Progress only exists while something is moving. */
@Composable
fun TransferStatusCard(state: HomeUiState) {
    val colors = KnoticTheme.colors
    val session = state.session
    val active = session.outgoingTransfer ?: session.incomingTransfer ?: return
    val isOutgoing = session.outgoingTransfer != null
    // The receiving side is not told the total up front, so it reports bytes.
    val percent = if (isOutgoing) active.progress else null

    KnoticCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToneBadge(
                icon = if (isOutgoing) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                background = if (isOutgoing) colors.accentSoft else colors.emeraldSoft,
                tint = if (isOutgoing) colors.accent else colors.emerald,
            )
            Column(Modifier.weight(1f)) {
                Text(active.name, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val detail = buildString {
                    append(if (isOutgoing) "Sending" else "Receiving")
                    if (percent != null) append(" $percent%")
                    append(" · ").append(humanFileSize(active.transferredBytes))
                    active.size?.let { append(" of ").append(humanFileSize(it)) }
                    if (session.transportMode == TransportMode.RELAY) append(" · via relay")
                }
                Text(detail, color = colors.textMuted, fontSize = 11.sp)
            }
        }
        ProgressTrack(
            fraction = percent?.let { it / 100f },
            brush = if (isOutgoing) Brush.horizontalGradient(listOf(colors.brandFrom, colors.brandTo)) else SolidColor(colors.emerald),
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** Whatever just arrived, front and centre with its one obvious action. */
@Composable
fun InboxCard(state: HomeUiState, onIntent: (HomeIntent) -> Unit) {
    val colors = KnoticTheme.colors
    val session = state.session
    val connected = session.sessionStatus == SessionStatus.CONNECTED

    if (session.incomingText == null && session.incomingFile == null) {
        QuietCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 26.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WaitingGraphic(active = connected)
                Text(
                    if (connected) "Nothing received yet. Send something from the other device." else "Link a device to start receiving.",
                    color = colors.textFaint,
                    fontSize = 13.sp,
                )
            }
        }
        return
    }

    AnimatedVisibility(visible = true, enter = fadeIn() + scaleIn(initialScale = 0.98f)) {
        KnoticCard(borderColor = colors.emerald) {
            Eyebrow("Just received", color = colors.success)

            session.incomingText?.let { text ->
                Text(text, color = colors.text, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 10.dp))
                KnoticButton(
                    text = if (state.inboxCopied) "Copied" else "Copy text",
                    onClick = { onIntent(HomeIntent.CopyIncomingText) },
                    icon = if (state.inboxCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                    style = if (state.inboxCopied) KnoticButtonStyle.SUCCESS else KnoticButtonStyle.PRIMARY,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            session.incomingFile?.let { file ->
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToneBadge(Icons.Rounded.FileDownload, colors.emeraldSoft, colors.emerald, size = 44.dp, rounded = false)
                    Column(Modifier.weight(1f)) {
                        Text(file.name, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(humanFileSize(file.size), color = colors.textMuted, fontSize = 11.sp)
                    }
                }
                KnoticButton(
                    text = "Save file",
                    onClick = { onIntent(HomeIntent.SaveIncomingFile) },
                    icon = Icons.Rounded.Download,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

/** One box, one button. Ctrl/Cmd+Enter sends on a keyboard. */
@Composable
fun Composer(state: HomeUiState, onIntent: (HomeIntent) -> Unit) {
    val colors = KnoticTheme.colors
    val remaining = SessionLimits.MAX_TEXT_CHARS - state.composerText.length

    KnoticCard {
        BasicTextField(
            value = state.composerText,
            onValueChange = { onIntent(HomeIntent.ComposerTextChanged(it)) },
            enabled = state.secureReady,
            minLines = 3,
            maxLines = 8,
            cursorBrush = SolidColor(colors.accent),
            textStyle = TextStyle(color = colors.text, fontSize = 13.sp, lineHeight = 20.sp, fontFamily = FontFamily.Default),
            modifier = Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    val send = event.type == KeyEventType.KeyDown && event.key == Key.Enter && (event.isCtrlPressed || event.isMetaPressed)
                    if (send) onIntent(HomeIntent.SendTextClicked)
                    send
                },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface2)
                        .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                ) {
                    if (state.composerText.isEmpty()) {
                        Text(
                            text = when {
                                state.secureReady -> "Type or paste something to send..."
                                state.linked -> "Securing the connection..."
                                else -> "Link a device to start sending"
                            },
                            color = colors.textFaint,
                            fontSize = 13.sp,
                        )
                    }
                    inner()
                }
            },
        )

        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            KnoticIconButton(Icons.Rounded.AttachFile, "Attach a file", { onIntent(HomeIntent.AttachFileClicked) }, enabled = state.linked)
            KnoticIconButton(Icons.Rounded.ContentPaste, "Paste from clipboard", { onIntent(HomeIntent.PasteClicked) }, enabled = state.secureReady)

            if (state.secureReady) {
                Row(
                    Modifier.padding(start = 4.dp).clip(CircleShape).background(colors.emeraldSoft).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Rounded.Lock, null, tint = colors.emerald, modifier = Modifier.size(11.dp))
                    Text("ENCRYPTED", color = colors.emerald, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
                }
            }

            Text(
                text = when {
                    remaining < 120 -> "$remaining left"
                    state.session.transportMode == TransportMode.RELAY -> "Via relay"
                    else -> sendShortcutHint.orEmpty()
                },
                color = colors.textFaint,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )

            KnoticButton(
                text = if (state.justSent) "Sent" else "Send",
                onClick = { onIntent(HomeIntent.SendTextClicked) },
                icon = if (state.justSent) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.Send,
                style = if (state.justSent) KnoticButtonStyle.SUCCESS else KnoticButtonStyle.PRIMARY,
                enabled = state.canSendText || state.justSent,
            )
        }
    }
}

/** Route detail, a manual retry and raw connection state, folded away. */
@Composable
fun AdvancedPanel(state: HomeUiState, onIntent: (HomeIntent) -> Unit) {
    val colors = KnoticTheme.colors
    val session = state.session
    val chevron by animateFloatAsState(if (state.isAdvancedOpen) 180f else 0f, label = "advanced-chevron")

    QuietCard {
        Row(
            Modifier.fillMaxWidth().clickable { onIntent(HomeIntent.ToggleAdvanced) }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Cable, null, tint = colors.textFaint, modifier = Modifier.size(16.dp))
            Text(
                text = when (session.transportMode) {
                    TransportMode.P2P -> "Direct connection"
                    TransportMode.RELAY -> "Cloud relay"
                    TransportMode.UNAVAILABLE -> "Not connected"
                },
                color = colors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            Icon(Icons.Rounded.ExpandMore, null, tint = colors.textFaint, modifier = Modifier.size(18.dp).rotate(chevron))
        }

        AnimatedVisibility(state.isAdvancedOpen) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                session.webrtcError?.let { message ->
                    val failed = session.webrtcStatus == WebRtcStatus.FAILED
                    Text(
                        message,
                        color = if (failed) colors.warn else colors.textMuted,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (failed) colors.warnSoft else colors.surface)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }

                KnoticButton(
                    text = "Retry direct connection",
                    onClick = { onIntent(HomeIntent.RetryDirectConnection) },
                    icon = Icons.Rounded.Refresh,
                    style = KnoticButtonStyle.SECONDARY,
                    enabled = state.connected && session.peerOnline,
                    compact = true,
                )

                Text(
                    text = (if (state.showConnectionDetail) "Hide" else "Show") + " connection detail",
                    color = colors.textFaint,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onIntent(HomeIntent.ToggleConnectionDetail) }.padding(vertical = 4.dp),
                )

                if (state.showConnectionDetail) {
                    val d = session.webrtcDiagnostics
                    val rows = listOf(
                        "Connection" to d.connectionState,
                        "ICE" to d.iceConnectionState,
                        "Gathering" to d.iceGatheringState,
                        "Channel" to d.dataChannelState,
                        "Local candidates" to d.localCandidateCount.toString(),
                        "Remote candidates" to d.remoteCandidateCount.toString(),
                        "Candidate errors" to d.candidateErrorCount.toString(),
                        "Relay server" to when (d.hasTurn) {
                            null -> "Unknown"
                            true -> "Configured"
                            false -> "None"
                        },
                        "Last failure" to (d.lastFailureReason ?: "None"),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        rows.forEach { (label, value) ->
                            Row(Modifier.fillMaxWidth()) {
                                Text(label, color = colors.textFaint, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Text(
                                    value,
                                    color = colors.textMuted,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1.4f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A short record of what moved, and a way to act on it again while it is still held. */
@Composable
fun ActivityCard(state: HomeUiState, onIntent: (HomeIntent) -> Unit) {
    val colors = KnoticTheme.colors
    if (state.history.isEmpty()) return

    val visible = if (state.isActivityExpanded) state.history else state.history.take(VISIBLE_HISTORY_BY_DEFAULT)

    KnoticCard {
        Eyebrow("Activity")
        Column(Modifier.padding(top = 8.dp)) {
            visible.forEach { item -> ActivityRow(item, copied = state.copiedHistoryId == item.id, onIntent = onIntent) }
        }
        if (state.history.size > VISIBLE_HISTORY_BY_DEFAULT) {
            KnoticButton(
                text = if (state.isActivityExpanded) "Show less" else "Show all ${state.history.size}",
                onClick = { onIntent(HomeIntent.ToggleActivityExpanded) },
                icon = Icons.Rounded.ExpandMore,
                style = KnoticButtonStyle.GHOST,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ActivityRow(item: HistoryItem, copied: Boolean, onIntent: (HomeIntent) -> Unit) {
    val colors = KnoticTheme.colors
    val failed = item.status == HistoryStatus.FAILED
    val sent = item.action.isOutgoing

    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        ToneBadge(
            icon = if (sent) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
            background = when {
                failed -> colors.dangerSoft
                sent -> colors.accentSoft
                else -> colors.emeraldSoft
            },
            tint = when {
                failed -> colors.danger
                sent -> colors.accent
                else -> colors.emerald
            },
            size = 28.dp,
        )
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(item.title, color = colors.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = buildString {
                if (failed) append("Failed · ")
                if (item.size > 0) append(humanFileSize(item.size)).append(" · ")
                append(relativeTime(item.timestampMillis))
            }
            Text(meta, color = colors.textFaint, fontSize = 11.sp)
        }
        if (item.text != null) {
            KnoticIconButton(
                icon = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                contentDescription = if (copied) "Copied" else "Copy text",
                onClick = { onIntent(HomeIntent.CopyHistoryText(item.id)) },
                tint = if (copied) colors.success else colors.textMuted,
                size = 34.dp,
            )
        }
        if (item.hasPayload) {
            KnoticIconButton(
                icon = Icons.Rounded.Download,
                contentDescription = "Save ${item.title}",
                onClick = { onIntent(HomeIntent.SaveHistoryFile(item.id)) },
                size = 34.dp,
            )
        }
    }
}
