package org.freeperiod.app.ui.settings

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedbackTest {
    @Test fun technicalInfoHasNoHealthData() {
        assertEquals("FreePeriod. 1.0.0 · Android API 35 · Samsung SM-S911B · de-DE",
            feedbackInfo("1.0.0", 35, "Samsung SM-S911B", Locale.GERMANY))
    }
}
