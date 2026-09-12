package com.share.app.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.share.app.config.Brand
import com.share.app.domain.model.AppSessionState
import com.share.app.domain.model.AuthStatus
import com.share.app.domain.model.SessionStatus
import com.share.app.domain.model.ThemePreference
import com.share.app.domain.policy.PairingCode
import com.share.app.ui.components.BrandLogo
import com.share.app.ui.components.ChipTone
import com.share.app.ui.components.KnoticIconButton
import com.share.app.ui.components.StatusChip
import com.share.app.ui.home.HomeIntent
import com.share.app.ui.platform.QrScannerView
import com.share.app.ui.theme.KnoticTheme

@Composable
fun HomeTopBar(
    session: AppSessionState,
    themePreference: ThemePreference,
    onCycleTheme: () -> Unit,
    onIntent: (HomeIntent) -> Unit,
) {
    val colors = KnoticTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandLogo(size = 28.dp)
        Text(
            text = Brand.NAME,
            color = colors.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 10.dp).weight(1f),
        )

        ConnectionBadge(session, onRetry = { onIntent(HomeIntent.RetrySignIn) })

        // Says what pressing it will do next, not what is currently set.
        val (icon, label) = when (themePreference) {
            ThemePreference.SYSTEM -> Icons.Rounded.BrightnessAuto to "Switch to light theme"
            ThemePreference.LIGHT -> Icons.Rounded.LightMode to "Switch to dark theme"
            ThemePreference.DARK -> Icons.Rounded.DarkMode to "Follow the system theme"
        }
        KnoticIconButton(icon, label, onCycleTheme, modifier = Modifier.padding(start = 4.dp))
        KnoticIconButton(Icons.Rounded.Info, "How it works", { onIntent(HomeIntent.AboutClicked) })
    }
}

@Composable
private fun ConnectionBadge(session: AppSessionState, onRetry: () -> Unit) {
    val (label, tone) = when {
        session.authStatus == AuthStatus.ERROR -> "Tap to retry" to ChipTone.DANGER
        session.authStatus != AuthStatus.READY -> "Starting" to ChipTone.IDLE
        session.sessionStatus == SessionStatus.CONNECTING -> "Connecting" to ChipTone.WAITING
        session.isLinked -> "Connected" to ChipTone.LIVE
        session.sessionStatus == SessionStatus.CONNECTED -> "Waiting" to ChipTone.WAITING
        else -> "Offline" to ChipTone.IDLE
    }
    val modifier = if (session.authStatus == AuthStatus.ERROR) {
        Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onRetry)
    } else {
        Modifier
    }
    StatusChip(label, tone, modifier)
}

@Composable
fun ScannerDialog(onCode: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = KnoticTheme.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Point at the other screen",
                    color = colors.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                KnoticIconButton(Icons.Rounded.Close, "Close scanner", onDismiss)
            }

            Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color.Black)) {
                QrScannerView(
                    onScanned = { raw ->
                        // Only a real pairing code ends the scan; anything else keeps looking.
                        val code = PairingCode.extract(raw)
                        if (code != null) onCode(code)
                        code != null
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(56.dp)
                        .border(2.dp, colors.brandTo, RoundedCornerShape(18.dp)),
                )
            }

            Text(
                "The camera stays on this device and is only read for a code.",
                color = colors.textMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

/** Dropping a file anywhere on the window sends it. */
@Composable
fun DropOverlay(linked: Boolean) {
    val colors = KnoticTheme.colors
    Box(
        Modifier.fillMaxSize().background(Color(0xB808090C)).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface)
                .drawBehind {
                    drawRoundRect(
                        color = colors.accent,
                        style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))),
                        cornerRadius = CornerRadius(20.dp.toPx()),
                    )
                }
                .padding(horizontal = 40.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.CloudUpload, null, tint = colors.accent, modifier = Modifier.size(36.dp))
            Text(if (linked) "Drop to send" else "Not linked yet", color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (linked) "Up to 20 MB, one file at a time" else "Link the other device first",
                color = colors.textMuted,
                fontSize = 13.sp,
            )
        }
    }
}
