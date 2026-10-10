package org.freeperiod.engine

import org.junit.Assert.*
import org.junit.Test

class EntryAppearanceTest {
    @Test fun defaultsOverridesAndOwnRowsResolveInOnePlace() {
        val overrides = listOf(UiOverride("item:mood:GOOD", true, 3, "Content", "leaf"))
        assertEquals(EntryAppearance("Content", "leaf"), resolveEntryAppearance("item:mood:GOOD", "Good", "good", overrides))
        assertEquals(EntryAppearance("Gut", "good"), resolveEntryAppearance("item:mood:GOOD", "Gut", "good", emptyList()))
        val tag = Tag(3, "Calm", iconKey = "sun")
        assertEquals(EntryAppearance("Calm", "sun"), resolveEntryAppearance("tag:3", "Tag", "tag", overrides, tag))
    }

    @Test fun resetRestoresTranslatedDefaultAndLeavesVisibilityAndOrder() {
        val override = UiOverride("category:mood", true, 7, "Feelings", "leaf")
        val reset = override.copy(label = null, iconKey = null)
        assertTrue(reset.hidden)
        assertEquals(7, reset.sortOrder)
        assertEquals(EntryAppearance("Stimmung", "mood"), resolveEntryAppearance(reset.key, "Stimmung", "mood", listOf(reset)))
    }
}
