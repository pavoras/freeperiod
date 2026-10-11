package org.freeperiod.app.support

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.freeperiod.app.R
import org.freeperiod.app.data.SettingsStore

/**
 * Google Play's billing: the library talks to the Play Store app on the phone, which handles payment and network.
 * Nothing is unlocked, so a purchase is trusted as Play reports it (no server). Every purchase is consumed so
 * people can support again; the thank-you is remembered in the settings instead.
 */
class PlaySupporter(context: Context, private val settings: SettingsStore, private val scope: CoroutineScope) :
    Supporter, PurchasesUpdatedListener {
    private val store = MutableStateFlow(SupportState())
    override val state: StateFlow<SupportState> = combine(store, settings.settings) { s, device ->
        supportState(s.products.associate { it.id to it.price }, s.supported || device.supported, s.pending, s.celebration)
    }.stateIn(scope, SharingStarted.Eagerly, SupportState())
    override val libraryNotice: Int = R.string.about_billing_library
    private val client = BillingClient.newBuilder(context).setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()
    @Volatile private var details: Map<String, ProductDetails> = emptyMap()
    @Volatile private var connecting = false
    /** Set when Play reports a fresh purchase; the next load celebrates it. */
    @Volatile private var celebrate = false

    override fun refresh() {
        if (client.connectionState == BillingClient.ConnectionState.CONNECTED) load()
        else if (!connecting) {
            connecting = true
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    connecting = false
                    if (result.responseCode == BillingResponseCode.OK) load()
                }
                override fun onBillingServiceDisconnected() { connecting = false }
            })
        }
    }

    private fun load() {
        val products = supportProductIds.map {
            QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(ProductType.INAPP).build()
        }
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(products).build()) { result, found ->
            if (result.responseCode == BillingResponseCode.OK) details = found.productDetailsList.associateBy { it.productId }
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()) { owned, purchases ->
                if (owned.responseCode == BillingResponseCode.OK) show(purchases)
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        // A new purchase only lists itself; reload so earlier ones count too.
        if (result.responseCode != BillingResponseCode.OK) return
        if (purchases.orEmpty().any { p -> p.purchaseState == Purchase.PurchaseState.PURCHASED && p.products.any { it in supportProductIds } }) {
            celebrate = true
        }
        load()
    }

    private fun show(purchases: List<Purchase>) {
        val ours = purchases.filter { purchase -> purchase.products.any { it in supportProductIds } }
        val bought = ours.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        // Remember first, then consume (which also acknowledges); a consumed product can be bought again.
        if (bought.isNotEmpty()) scope.launch {
            settings.update { it.copy(supported = true) }
            bought.forEach { purchase ->
                client.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) { _, _ -> }
            }
        }
        val prices = details.mapNotNull { (id, product) -> product.oneTimePurchaseOfferDetails?.formattedPrice?.let { id to it } }.toMap()
        val celebration = store.value.celebration + if (celebrate && bought.isNotEmpty()) 1 else 0
        if (bought.isNotEmpty()) celebrate = false
        store.value = supportState(prices, supported = bought.isNotEmpty(),
            pending = ours.any { it.purchaseState == Purchase.PurchaseState.PENDING }, celebration = celebration)
    }

    override fun buy(activity: Activity, productId: String) {
        val product = details[productId] ?: return
        val offer = product.oneTimePurchaseOfferDetails?.offerToken ?: return
        val params = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).setOfferToken(offer).build()
        client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params)).build())
    }
}
