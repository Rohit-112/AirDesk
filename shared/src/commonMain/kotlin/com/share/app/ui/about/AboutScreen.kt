package com.share.app.ui.about

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.share.app.config.AboutContent
import com.share.app.config.Brand
import com.share.app.ui.components.Eyebrow
import com.share.app.ui.components.KnoticCard
import com.share.app.ui.components.KnoticIconButton
import com.share.app.ui.components.ToneBadge
import com.share.app.ui.theme.KnoticTheme

/** Static reference content: how it works, what happens to your data, FAQ. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val colors = KnoticTheme.colors

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KnoticIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, tint = colors.text)
            Text("About ${Brand.NAME}", color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(36.dp),
            ) {
                Section(title = "How to send files with ${Brand.NAME}", subtitle = "No account and no upload step.") {
                    val tones = listOf(
                        Triple(Icons.Rounded.Key, colors.violetSoft, colors.violet),
                        Triple(Icons.AutoMirrored.Rounded.Send, colors.cyanSoft, colors.cyan),
                        Triple(Icons.Rounded.VerifiedUser, colors.emeraldSoft, colors.emerald),
                    )
                    AboutContent.steps.forEachIndexed { index, step ->
                        val (icon, background, tint) = tones[index % tones.size]
                        KnoticCard {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ToneBadge(icon, background, tint, rounded = false)
                                Eyebrow("Step ${index + 1}")
                            }
                            Text(step.title, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                            Text(step.body, color = colors.textMuted, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }

                Section(title = "What ${Brand.NAME} does with your data", subtitle = "The short, honest version.") {
                    AboutContent.privacy.forEach { point ->
                        KnoticCard {
                            Text(point.title, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(point.body, color = colors.textMuted, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }

                Section(title = "Frequently asked questions", subtitle = "Sending files and text between your phone and computer.") {
                    AboutContent.faq.forEach { item -> FaqItem(item.title, item.body) }
                }

                Text(
                    text = "${Brand.LEGAL_NAME} · v${Brand.VERSION}",
                    color = colors.textFaint,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, subtitle: String, content: @Composable () -> Unit) {
    val colors = KnoticTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text(title, color = colors.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = colors.textMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
        content()
    }
}

@Composable
private fun FaqItem(question: String, answer: String) {
    val colors = KnoticTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    KnoticCard(modifier = Modifier.clickable { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(question, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ExpandMore, null, tint = colors.textFaint, modifier = Modifier.size(18.dp).rotate(rotation))
        }
        AnimatedVisibility(expanded) {
            Text(answer, color = colors.textMuted, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
