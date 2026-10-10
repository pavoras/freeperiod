package org.freeperiod.app.ui.settings

import org.freeperiod.app.R
import org.freeperiod.app.data.ItemSetCategory

/** Pain diary categories; keys are stored in CustomCategory.itemSet and never change. Icons are FpIcons keys. */
private val painDiary = listOf(
    Triple("pain_diary:where", R.string.set_where to "person", listOf(
        R.string.set_lower_belly to "cramps", R.string.set_back to "backache", R.string.set_pelvis to "circle",
        R.string.set_legs to "footprints", R.string.set_head to "headache", R.string.set_breasts to "breast")),
    Triple("pain_diary:during", R.string.set_during to "pulse", listOf(
        R.string.set_sex to "sex", R.string.set_bowel to "digestion", R.string.set_peeing to "drop")),
    Triple("pain_diary:daily", R.string.set_daily to "home", listOf(
        R.string.set_missed to "briefcase", R.string.set_cancelled to "people", R.string.set_rested to "bed")),
)

/** Names come from the current language once; afterwards they are the user's own and can be renamed. */
internal fun painDiaryCategories(text: (Int) -> String): List<ItemSetCategory> = painDiary.map { (key, category, items) ->
    ItemSetCategory(key, text(category.first), category.second, items.map { (label, icon) -> text(label) to icon })
}
