package com.share.app.ui.convert

import androidx.lifecycle.viewModelScope
import com.share.app.base.BaseViewModel
import com.share.app.base.UiEffect
import com.share.app.base.UiIntent
import com.share.app.base.UiState
import com.share.app.domain.analytics.ConversionSource
import com.share.app.domain.media.DetectedFileType
import com.share.app.domain.media.FileTypes
import com.share.app.domain.media.ImageFormat
import com.share.app.domain.media.ImagePreview
import com.share.app.domain.model.OutgoingFile
import com.share.app.domain.usecase.ConvertImageUseCase
import com.share.app.util.AppLog
import com.share.app.util.newId
import com.share.app.util.suspendRunCatching
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class PickedImage(
    /** Identifies this pick, so a conversion started for an earlier one cannot land on it. */
    val key: String,
    val name: String,
    val size: Long,
    val type: DetectedFileType,
    val preview: ImagePreview? = null,
    val previewPending: Boolean = true,
)

data class ConvertUiState(
    val picked: PickedImage? = null,
    val reading: Boolean = false,
    val error: String? = null,
    val conversion: ConversionState = ConversionState(),
    val isDraggingFile: Boolean = false,
) : UiState

sealed interface ConvertIntent : UiIntent {
    data object PickClicked : ConvertIntent
    data class FilesDropped(val files: List<OutgoingFile>) : ConvertIntent
    data class DragStateChanged(val dragging: Boolean) : ConvertIntent
    data class FormatClicked(val format: ImageFormat) : ConvertIntent
    data object SaveAgainClicked : ConvertIntent
}

sealed interface ConvertEffect : UiEffect

/**
 * Pick a picture, save it in another format. Everything happens on this
 * device: the file is read locally, redrawn and handed to the save dialog.
 * A port of the web client's standalone converter at /convert.
 */
class ConvertViewModel(
    private val useCase: ConvertImageUseCase,
) : BaseViewModel<ConvertUiState, ConvertIntent, ConvertEffect>(ConvertUiState()) {

    /** The picked file's bytes, held here rather than in the state. */
    private var bytes: ByteArray? = null
    private var pickJob: Job? = null

    private val conversions = ConversionController(viewModelScope, useCase) { conversion ->
        updateState { copy(conversion = conversion) }
    }

    override fun onIntent(intent: ConvertIntent) {
        when (intent) {
            ConvertIntent.PickClicked -> viewModelScope.launch {
                val picked = useCase.pickImage()
                    .onFailure { AppLog.w(it) { "The image picker failed" } }
                    .getOrNull() ?: return@launch
                accept(picked)
            }
            is ConvertIntent.FilesDropped -> {
                updateState { copy(isDraggingFile = false) }
                intent.files.firstOrNull()?.let(::accept)
            }
            is ConvertIntent.DragStateChanged -> updateState { copy(isDraggingFile = intent.dragging) }
            is ConvertIntent.FormatClicked -> convert(intent.format)
            ConvertIntent.SaveAgainClicked -> conversions.saveAgain()
        }
    }

    private fun accept(file: OutgoingFile) {
        if (file.size > MAX_SOURCE_SIZE) {
            updateState { copy(error = "Pick an image under 64 MB.") }
            return
        }

        // Two quick picks can finish out of order; only the newest may win, and
        // nothing started for the previous one may save itself.
        pickJob?.cancel()
        conversions.reset()
        pickJob = viewModelScope.launch {
            updateState { copy(reading = true, error = null) }
            val content = suspendRunCatching { file.readBytes() }.getOrElse { error ->
                AppLog.w(error) { "Reading the picked image failed" }
                updateState { copy(reading = false, error = "That file could not be read.") }
                return@launch
            }

            val type = FileTypes.detect(file.name, head = content, declaredType = file.contentType)
            val picked = PickedImage(key = newId("pick"), name = file.name, size = content.size.toLong(), type = type)
            bytes = content
            updateState { copy(picked = picked, reading = false) }

            val preview = useCase.preview(content, type.mimeType)
            updateState {
                if (this.picked?.key == picked.key) copy(picked = picked.copy(preview = preview, previewPending = false)) else this
            }
        }
    }

    private fun convert(format: ImageFormat) {
        val picked = currentState.picked ?: return
        val content = bytes ?: return
        conversions.convert(
            key = picked.key,
            source = ConversionSource.CONVERT_SCREEN,
            fileName = picked.name,
            mimeType = picked.type.mimeType,
            format = format,
            loadBytes = { content },
        )
    }

    private companion object {
        /**
         * The web converter takes anything the browser can hold. A phone decoding
         * a very large file can run out of memory, so this stops well short.
         */
        const val MAX_SOURCE_SIZE = 64L * 1024 * 1024
    }
}
