package com.share.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.share.app.config.Brand
import com.share.app.domain.model.ThemePreference
import com.share.app.ui.components.AlertBanner
import com.share.app.ui.components.HeroGraphic
import com.share.app.ui.components.KnoticCard
import com.share.app.ui.components.ToneBadge
import com.share.app.ui.home.components.ActivityCard
import com.share.app.ui.home.components.AdvancedPanel
import com.share.app.ui.home.components.Composer
import com.share.app.ui.home.components.DropOverlay
import com.share.app.ui.home.components.HomeTopBar
import com.share.app.ui.home.components.InboxCard
import com.share.app.ui.home.components.PairingCard
import com.share.app.ui.home.components.ScannerDialog
import com.share.app.ui.home.components.TransferStatusCard
import com.share.app.ui.platform.fileDropTarget
import com.share.app.ui.theme.KnoticTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(
    themePreference: ThemePreference,
    onCycleTheme: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToConvert: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onIntent = viewModel::onIntent

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                HomeEffect.NavigateToAbout -> onNavigateToAbout()
                HomeEffect.NavigateToConvert -> onNavigateToConvert()
            }
        }
    }

    // Returning to the app counts as activity, like a tab becoming visible.
    LifecycleResumeEffect(Unit) {
        onIntent(HomeIntent.UserActivity)
        onPauseOrDispose { }
    }

    HomeContent(
        state = state,
        themePreference = themePreference,
        onCycleTheme = onCycleTheme,
        onIntent = onIntent,
    )
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    themePreference: ThemePreference,
    onCycleTheme: () -> Unit,
    onIntent: (HomeIntent) -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .fileDropTarget(
                onDragStateChange = { onIntent(HomeIntent.DragStateChanged(it)) },
                onFilesDropped = { onIntent(HomeIntent.FilesDropped(it)) },
            )
            // Any touch or key keeps a live session from timing out.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        onIntent(HomeIntent.UserActivity)
                    }
                }
            }
            .onPreviewKeyEvent {
                onIntent(HomeIntent.UserActivity)
                false
            },
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            HomeTopBar(
                session = state.session,
                themePreference = themePreference,
                onCycleTheme = onCycleTheme,
                onIntent = onIntent,
            )

            Box(
                Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Hero()

                    ModeChoice(onConvertOnly = { onIntent(HomeIntent.ConvertOnlyClicked) })

                    AnimatedVisibility(
                        visible = state.session.error != null,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut(),
                    ) {
                        AlertBanner(
                            message = state.session.error.orEmpty(),
                            onDismiss = { onIntent(HomeIntent.DismissError) },
                        )
                    }

                    // Once the devices are linked, sending is the job and the code
                    // becomes reference material - so the two swap places. They
                    // are moved rather than rebuilt: otherwise every time the peer
                    // blinked in or out, the field being typed in lost its focus
                    // and the keyboard closed.
                    val currentState by rememberUpdatedState(state)
                    val pairing = remember { movableContentOf { PairingCard(currentState, onIntent) } }
                    val workspace = remember { movableContentOf { Workspace(currentState, onIntent) } }
                    if (state.linked) {
                        workspace()
                        pairing()
                    } else {
                        pairing()
                        workspace()
                    }

                    AdvancedPanel(state, onIntent)
                    ActivityCard(state, onIntent)
                    Footer(onAbout = { onIntent(HomeIntent.AboutClicked) })
                }
            }
        }

        if (state.isDraggingFile) {
            DropOverlay(linked = state.linked)
        }

        if (state.isScannerOpen) {
            ScannerDialog(
                onCode = { onIntent(HomeIntent.CodeScanned(it)) },
                onDismiss = { onIntent(HomeIntent.ScannerDismissed) },
            )
        }
    }
}

@Composable
private fun Workspace(state: HomeUiState, onIntent: (HomeIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TransferStatusCard(state)
        InboxCard(state, onIntent)
        Composer(state, onIntent)
    }
}

/**
 * The two things the app does, stated once at the top. Someone who only wants
 * a HEIC turned into a JPG should not have to pair two devices to find out
 * that they can.
 */
@Composable
private fun ModeChoice(onConvertOnly: () -> Unit) {
    val colors = KnoticTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KnoticCard(
            modifier = Modifier.weight(1f).semantics { selected = true },
            contentPadding = PaddingValues(12.dp),
        ) {
            ModeLabel(Icons.Rounded.Share, colors.accentSoft, colors.accent, "Send & receive", "Between two devices")
        }
        KnoticCard(
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).clickable(onClick = onConvertOnly),
            contentPadding = PaddingValues(12.dp),
        ) {
            ModeLabel(Icons.Rounded.Transform, colors.violetSoft, colors.violet, "Convert only", "One device, no pairing", showArrow = true)
        }
    }
}

@Composable
private fun ModeLabel(
    icon: ImageVector,
    background: Color,
    tint: Color,
    title: String,
    subtitle: String,
    showArrow: Boolean = false,
) {
    val colors = KnoticTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ToneBadge(icon, background, tint, size = 34.dp, rounded = false)
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(subtitle, color = colors.textMuted, fontSize = 11.sp, maxLines = 1)
        }
        if (showArrow) {
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = colors.textFaint, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun Hero() {
    val colors = KnoticTheme.colors
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth > 520.dp
        val copy: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier) {
                Text(
                    text = Brand.EYEBROW.uppercase(),
                    color = colors.brandTo,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                )
                Text(
                    text = Brand.TAGLINE,
                    color = colors.text,
                    fontSize = if (wide) 22.sp else 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = if (wide) 28.sp else 26.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    text = Brand.INTRO,
                    color = colors.textMuted,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        if (wide) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                copy(Modifier.weight(1f))
                HeroGraphic(Modifier.widthIn(max = 280.dp).weight(0.8f))
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                HeroGraphic(Modifier.widthIn(max = 260.dp))
                copy(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Footer(onAbout: () -> Unit) {
    val colors = KnoticTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "How it works, privacy & FAQ",
            color = colors.accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onAbout).padding(8.dp),
        )
        Text("${Brand.LEGAL_NAME} · v${Brand.VERSION}", color = colors.textFaint, fontSize = 11.sp)
    }
}
