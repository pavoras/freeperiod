package org.freeperiod.app.ui.today

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/**
 * Stacks [top], [flex] and [bottom] so they fill exactly one screen: [flex] gets the height that is
 * left. Only when that is less than [minFlex] (large fonts, small screens) [flex] keeps [minFlex]
 * and the whole column scrolls, so nothing is squeezed away.
 */
@Composable
internal fun OneScreenColumn(minFlex: Dp, top: @Composable () -> Unit, flex: @Composable () -> Unit,
    bottom: @Composable () -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val viewport = constraints.maxHeight
        Layout(listOf(top, flex, bottom), Modifier.verticalScroll(rememberScrollState())) { (tops, flexes, bottoms), constraints ->
            val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
            val above = tops.map { it.measure(loose) }
            val below = bottoms.map { it.measure(loose) }
            val height = maxOf(minFlex.roundToPx(), viewport - above.sumOf { it.height } - below.sumOf { it.height })
            val middle = flexes.map { it.measure(constraints.copy(minHeight = height, maxHeight = height)) }
            val all = above + middle + below
            layout(constraints.maxWidth, all.sumOf { it.height }) {
                var y = 0
                all.forEach { it.place(0, y); y += it.height }
            }
        }
    }
}
