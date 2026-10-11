package org.freeperiod.app.support

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A one-time product to support FreePeriod; [price] comes formatted from the store. */
data class SupportProduct(val id: String, val price: String)

/** Settings show the support page only when there is something to buy or the user already supports. */
data class SupportState(val products: List<SupportProduct> = emptyList(), val supported: Boolean = false, val pending: Boolean = false) {
    val shown get() = products.isNotEmpty() || supported || pending
}

/** The optional supporter purchase. It unlocks nothing: Play builds sell it, F-Droid builds have none. */
interface Supporter {
    val state: StateFlow<SupportState>
    /** Licence line for the store library in About, null when the build has none. */
    val libraryNotice: Int?
    /** Loads products and earlier purchases (also from other phones); safe to call often. */
    fun refresh()
    fun buy(activity: Activity, productId: String)
}

object NoSupporter : Supporter {
    override val state: StateFlow<SupportState> = MutableStateFlow(SupportState()).asStateFlow()
    override val libraryNotice: Int? = null
    override fun refresh() {}
    override fun buy(activity: Activity, productId: String) {}
}

/** Product ids as created in Play Console (one-time; the app consumes them so they can be bought again), in the order shown: 4.99 € and 9.99 €. */
val supportProductIds = listOf("support_small", "support_large")

/** Products in [supportProductIds] order; ids the store doesn't know are left out. */
fun supportState(prices: Map<String, String>, supported: Boolean, pending: Boolean) =
    SupportState(supportProductIds.mapNotNull { id -> prices[id]?.let { SupportProduct(id, it) } }, supported, pending && !supported)
