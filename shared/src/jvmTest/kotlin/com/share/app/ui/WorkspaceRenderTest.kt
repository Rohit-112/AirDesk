package com.share.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.share.app.data.media.SkiaImageProcessor
import com.share.app.domain.media.ImageFormat
import com.share.app.domain.media.ImagePreview
import com.share.app.domain.model.AppSessionState
import com.share.app.domain.model.HistoryAction
import com.share.app.domain.model.HistoryItem
import com.share.app.domain.model.HistoryStatus
import com.share.app.domain.model.IncomingFile
import com.share.app.domain.model.SessionStatus
import com.share.app.domain.model.ThemePreference
import com.share.app.ui.convert.ConversionState
import com.share.app.ui.convert.ConversionStatus
import com.share.app.ui.home.HomeIntent
import com.share.app.ui.home.HomeUiState
import com.share.app.ui.home.components.ActivityCard
import com.share.app.ui.home.components.InboxCard
import com.share.app.ui.theme.KnoticTheme
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import kotlin.test.Test
import kotlin.test.assertEquals

/** Renders the new inbox and activity UI headlessly, with real decoded previews. */
@OptIn(ExperimentalTestApi::class)
class WorkspaceRenderTest {
    private val photo: ByteArray = Surface.makeRasterN32Premul(64, 48).run {
        canvas.clear(Color.BLUE)
        makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
    }
    private val thumbnail = ImagePreview(SkiaImageProcessor().preview(photo, "image/png", 192)!!)

    private val state = HomeUiState(
        session = AppSessionState(
            sessionStatus = SessionStatus.CONNECTED,
            peerOnline = true,
            incomingFile = IncomingFile("IMG_0042.jpg", photo.size.toLong(), "image/png", localFileId = "file-1", preview = thumbnail),
        ),
        history = listOf(
            HistoryItem("file-1", HistoryAction.RECEIVED_FILE, "IMG_0042.jpg", 0, photo.size.toLong(), HistoryStatus.SUCCESS, hasPayload = true, contentType = "image/png", thumbnail = thumbnail),
            HistoryItem("hist-2", HistoryAction.SENT_FILE, "report.pdf", 0, 2048, HistoryStatus.FAILED, contentType = "application/pdf"),
            HistoryItem("hist-3", HistoryAction.SENT_TEXT, "hello", 0, 5, HistoryStatus.SUCCESS, text = "hello"),
        ),
        convertPanelId = "file-1",
        conversion = ConversionState("file-1", ConversionStatus.Working(ImageFormat.JPEG)),
    )

    @Test
    fun rendersTheInboxWithItsTypeAndFormats() = runComposeUiTest {
        val intents = mutableListOf<HomeIntent>()
        setContent {
            KnoticTheme(preference = ThemePreference.DARK) {
                InboxCard(state) { intents += it }
            }
        }

        onNodeWithText("IMG_0042.jpg").assertExists()
        // The bytes said PNG, so that is what the label says - not the name's JPG.
        onNodeWithText("PNG · ", substring = true).assertExists()
        onNodeWithContentDescription("Preview of IMG_0042.jpg").assertExists()
        onNodeWithText("Converting to JPG on this device...").assertExists()
        // A PNG is not offered as PNG.
        onNodeWithContentDescription("Save as PNG").assertDoesNotExist()
        onNodeWithContentDescription("Save as WEBP").assertIsNotEnabled()
    }

    @Test
    fun rendersActivityRowsAndOpensTheConverter() = runComposeUiTest {
        val intents = mutableListOf<HomeIntent>()
        val done = state.copy(
            conversion = ConversionState("file-1", ConversionStatus.Done(ImageFormat.JPEG, "IMG_0042-converted.jpg", 1000, 4000, saved = true)),
        )
        setContent {
            KnoticTheme(preference = ThemePreference.LIGHT) {
                Column { ActivityCard(done) { intents += it } }
            }
        }

        onNodeWithText("Failed · PDF · ", substring = true).assertExists()
        onNodeWithText("Saved IMG_0042-converted.jpg", substring = true).assertExists()
        onNodeWithText("75% smaller", substring = true).assertExists()

        onNodeWithContentDescription("Save as WEBP").performClick()
        onNodeWithText("Save again").performClick()
        assertEquals(listOf(HomeIntent.ConvertHistoryFile("file-1", ImageFormat.WEBP), HomeIntent.SaveConvertedAgain), intents)
    }
}
