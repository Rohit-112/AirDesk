package com.share.app.data.local

import com.share.app.domain.media.ImagePreview
import com.share.app.domain.model.HistoryAction
import com.share.app.domain.model.HistoryItem
import com.share.app.domain.model.HistoryStatus
import com.share.app.domain.policy.SessionLimits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HistoryRepositoryTest {
    private fun row(id: String, hasPayload: Boolean = false) = HistoryItem(
        id = id,
        action = HistoryAction.RECEIVED_FILE,
        title = "$id.png",
        timestampMillis = 0,
        size = 1,
        status = HistoryStatus.SUCCESS,
        hasPayload = hasPayload,
    )

    @Test
    fun attachesAThumbnailToARowLoggedBeforeItWasDrawn() {
        val repository = InMemoryHistoryRepository()
        repository.add(row("a"))
        val thumbnail = ImagePreview(byteArrayOf(1))
        repository.setThumbnail("a", thumbnail)
        assertSame(thumbnail, repository.history.value.single().thumbnail)
    }

    @Test
    fun thumbnailsOutliveTheFileItself() {
        val repository = InMemoryHistoryRepository()
        repeat(SessionLimits.MAX_DOWNLOADABLE + 3) { index ->
            val id = "f$index"
            repository.add(row(id, hasPayload = true), payload = byteArrayOf(index.toByte()))
            repository.setThumbnail(id, ImagePreview(byteArrayOf(index.toByte())))
        }

        val rows = repository.history.value
        assertTrue(rows.all { it.thumbnail != null })
        assertEquals(SessionLimits.MAX_DOWNLOADABLE, rows.count { it.hasPayload })
        assertNull(repository.payload("f0"))
        assertNotNull(repository.payload("f${SessionLimits.MAX_DOWNLOADABLE + 2}"))
    }

    @Test
    fun ignoresAThumbnailForARowThatIsAlreadyGone() {
        val repository = InMemoryHistoryRepository()
        repository.add(row("a"))
        repository.setThumbnail("missing", ImagePreview(byteArrayOf(1)))
        assertNull(repository.history.value.single().thumbnail)
    }
}
