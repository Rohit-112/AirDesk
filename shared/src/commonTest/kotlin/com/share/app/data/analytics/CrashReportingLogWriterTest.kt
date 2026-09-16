package com.share.app.data.analytics

import co.touchlab.kermit.Severity
import com.share.app.domain.analytics.CrashReporter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrashReportingLogWriterTest {
    private class Recorder : CrashReporter {
        val breadcrumbs = mutableListOf<String>()
        val exceptions = mutableListOf<Throwable>()
        override fun log(message: String) {
            breadcrumbs += message
        }

        override fun recordException(throwable: Throwable) {
            exceptions += throwable
        }
    }

    @Test
    fun dropsChattyLinesSoTheyCannotEvictTheUsefulOnes() {
        val writer = CrashReportingLogWriter(Recorder())
        assertFalse(writer.isLoggable("Knotic", Severity.Debug))
        assertFalse(writer.isLoggable("Knotic", Severity.Verbose))
        assertTrue(writer.isLoggable("Knotic", Severity.Info))
    }

    @Test
    fun filesANonFatalOnlyForAnErrorWithAThrowable() {
        val recorder = Recorder()
        val writer = CrashReportingLogWriter(recorder)
        val failure = IllegalStateException("boom")

        writer.log(Severity.Warn, "Sending a file failed", "Knotic", failure)
        writer.log(Severity.Error, "No throwable here", "Knotic", null)
        assertTrue(recorder.exceptions.isEmpty())

        writer.log(Severity.Error, "createSession failed", "Knotic", failure)
        assertEquals(listOf<Throwable>(failure), recorder.exceptions)
        assertEquals("E/Knotic: createSession failed", recorder.breadcrumbs.last())
        assertEquals(3, recorder.breadcrumbs.size)
    }
}
