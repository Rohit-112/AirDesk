package com.share.app.ui.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.share.app.domain.model.SessionStatus
import com.share.app.domain.policy.PairingCode
import com.share.app.ui.components.KnoticButton
import com.share.app.ui.components.KnoticButtonStyle
import com.share.app.ui.components.KnoticCard
import com.share.app.ui.components.KnoticIconButton
import com.share.app.ui.home.HomeIntent
import com.share.app.ui.home.HomeUiState
import com.share.app.ui.theme.CodeFontFamily
import com.share.app.ui.theme.KnoticTheme
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import kotlinx.coroutines.delay

private val DigitWidth = 32.dp
private val DigitHeight = 42.dp
private val DigitGap = 5.dp
private val QrSize = 156.dp

/**
 * Your code, its copy button and the field for theirs on one side; the QR and
 * its scan button on the other. Both stay on screen together.
 */
@Composable
fun PairingCard(state: HomeUiState, onIntent: (HomeIntent) -> Unit) {
    val colors = KnoticTheme.colors
    val session = state.session

    // The moment the other device appears is the payoff, so the card lights up.
    val borderColor by animateColorAsState(
        targetValue = if (state.justLinked) colors.accent else colors.border,
        animationSpec = tween(400),
        label = "linked-border",
    )

    KnoticCard(borderColor = borderColor) {
        Row(Modifier.fillMaxWidth().heightIn(min = 34.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when {
                    state.joining -> "Enter their code"
                    session.peerOnline -> "Paired"
                    session.sessionCode.isNotEmpty() -> "Your code"
                    else -> "No active code"
                },
                color = colors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (state.connected) {
                    KnoticButton(
                        text = "Disconnect",
                        onClick = { onIntent(HomeIntent.DisconnectClicked) },
                        icon = Icons.Rounded.LinkOff,
                        style = KnoticButtonStyle.SECONDARY,
                        compact = true,
                    )
                }
                if (session.sessionCode.isNotEmpty() && !state.joining) {
                    KnoticButton(
                        text = "New code",
                        onClick = { onIntent(HomeIntent.NewCodeClicked) },
                        icon = Icons.Rounded.Refresh,
                        style = KnoticButtonStyle.SECONDARY,
                        enabled = !state.busy,
                        compact = true,
                    )
                }
            }
        }
        Text(
            text = when {
                state.joining -> "Your own code was released."
                session.peerOnline -> "Both devices are linked."
                session.sessionCode.isNotEmpty() -> "Type it on the other device, or let it scan the square."
                else -> "Generate one so another device can join you."
            },
            color = colors.textMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp),
        )

        val showQr = session.sessionCode.isNotEmpty() || (state.busy && !state.joining)
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            if (maxWidth >= 440.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CodeColumn(state, onIntent, Modifier.weight(1f))
                    if (showQr) QrColumn(state, onIntent)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CodeColumn(state, onIntent, Modifier.fillMaxWidth())
                    if (showQr) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { QrColumn(state, onIntent) }
                    }
                }
            }
        }

        session.sessionError?.let { message ->
            Text(
                text = message,
                color = colors.danger,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.dangerSoft)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun CodeColumn(state: HomeUiState, onIntent: (HomeIntent) -> Unit, modifier: Modifier) {
    val colors = KnoticTheme.colors
    val session = state.session

    Column(modifier) {
        // Reserves the tallest of the things that trade places here, so the card
        // does not jump as they swap.
        Box(Modifier.heightIn(min = 46.dp), contentAlignment = Alignment.CenterStart) {
            when {
                !state.joining && session.sessionCode.isEmpty() -> KnoticButton(
                    text = if (state.busy) "Generating..." else "Generate new code",
                    onClick = { onIntent(HomeIntent.NewCodeClicked) },
                    icon = Icons.Rounded.Refresh,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )

                !state.joining -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CodeDigits(session.sessionCode)
                    KnoticIconButton(
                        icon = if (state.codeCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = if (state.codeCopied) "Code copied" else "Copy code",
                        onClick = { onIntent(HomeIntent.CopyCodeClicked) },
                        tint = if (state.codeCopied) colors.success else colors.textMuted,
                        size = 38.dp,
                    )
                }
            }
        }

        Text(
            text = if (state.joining) "Enter their code" else "Got a code from the other device?",
            color = colors.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = if (state.joining) 0.dp else 12.dp),
        )
        Row(
            modifier = Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CodeInput(
                value = state.joinCode,
                onValueChange = { onIntent(HomeIntent.JoinCodeChanged(it)) },
                onFocused = { onIntent(HomeIntent.JoinFieldFocused) },
            )
            if (state.joining) {
                KnoticButton(
                    text = "Cancel",
                    onClick = { onIntent(HomeIntent.CancelJoin) },
                    style = KnoticButtonStyle.GHOST,
                    compact = true,
                )
            }
        }
        Text(
            text = when {
                session.sessionStatus == SessionStatus.CONNECTING -> "Connecting..."
                state.joining -> "Your own code was released. Cancel to get a new one."
                else -> "Connects on its own at six digits."
            },
            color = colors.textFaint,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Digits land one after another, as if the code were being dealt out. */
@Composable
private fun CodeDigits(code: String) {
    val colors = KnoticTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(DigitGap)) {
        code.padEnd(PairingCode.LENGTH, '-').forEachIndexed { index, digit ->
            val progress = remember(code) { Animatable(0f) }
            LaunchedEffect(code) {
                delay(index * 35L)
                progress.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
            }
            Box(
                modifier = Modifier
                    .size(DigitWidth, DigitHeight)
                    .graphicsLayer {
                        alpha = progress.value
                        translationY = (1f - progress.value) * 5.dp.toPx()
                    }
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surface2)
                    .border(1.dp, colors.border, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(digit.toString(), color = colors.text, fontSize = 21.sp, fontWeight = FontWeight.Bold, fontFamily = CodeFontFamily)
            }
        }
    }
}

/** Exactly the width of the six boxes above it, so your code and theirs read as one control. */
@Composable
private fun CodeInput(value: String, onValueChange: (String) -> Unit, onFocused: () -> Unit) {
    val colors = KnoticTheme.colors
    val width = DigitWidth * PairingCode.LENGTH + DigitGap * (PairingCode.LENGTH - 1)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        cursorBrush = SolidColor(colors.accent),
        textStyle = TextStyle(
            color = colors.text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = CodeFontFamily,
            letterSpacing = 6.sp,
            textAlign = TextAlign.Center,
        ),
        modifier = Modifier
            .width(width)
            .height(DigitHeight)
            .onFocusChanged { if (it.isFocused) onFocused() },
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surface2)
                    .border(1.dp, colors.border, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (value.isEmpty()) {
                    Text("------", color = colors.textFaint, fontSize = 20.sp, fontFamily = CodeFontFamily, letterSpacing = 6.sp)
                }
                inner()
            }
        },
    )
}

@Composable
private fun QrColumn(state: HomeUiState, onIntent: (HomeIntent) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Always a white card, in both themes: the one polarity every scanner accepts.
        Box(
            Modifier
                .size(QrSize)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .border(1.dp, KnoticTheme.colors.border, RoundedCornerShape(14.dp))
                .padding(7.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (state.joinUrl.isEmpty()) {
                QrSkeleton()
            } else {
                Image(
                    painter = rememberQrCodePainter(state.joinUrl),
                    contentDescription = "QR code for pairing code ${state.session.sessionCode}",
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (state.canScan) {
            KnoticButton(
                text = "Scan",
                onClick = { onIntent(HomeIntent.ScanClicked) },
                icon = Icons.Rounded.QrCodeScanner,
                style = KnoticButtonStyle.SECONDARY,
                enabled = state.session.sessionCode.isNotEmpty(),
                compact = true,
                modifier = Modifier.width(QrSize),
            )
        }
    }
}

@Composable
private fun QrSkeleton() {
    val colors = KnoticTheme.colors
    val shimmer by rememberInfiniteTransition(label = "qr-skeleton").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Restart),
        label = "shimmer",
    )
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.horizontalGradient(
                    0f to colors.surface2,
                    shimmer to colors.border,
                    1f to colors.surface2,
                ),
            ),
    )
}
