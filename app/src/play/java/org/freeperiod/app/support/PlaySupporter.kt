package org.freeperiod.app.support

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.freeperiod.app.R

/**
 * Google Play's billing: the library talks to the Play Store app on the phone, which handles payment and network.
 * Nothing is unlocked, so a purchase is trusted as Play reports it (no server). Purchases are acknowledged, or
 * Play refunds them after three days.
 */
class PlaySupporter(context: Context) : Supporter, PurchasesUpdatedListener {
    private val mutable = MutableStateFlow(SupportState())
    override val state: StateFlow<SupportState> = mutable.asStateFlow()
    override val libraryNotice: Int = R.string.about_billing_library
    private val client = BillingClient.newBuilder(context).setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()
    @Volatile private var details: Map<String, ProductDetails> = emptyMap()
    @Volatile private var connecting = false

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
        if (result.responseCode == BillingResponseCode.OK) load()
    }

    private fun show(purchases: List<Purchase>) {
        val ours = purchases.filter { purchase -> purchase.products.any { it in supportProductIds } }
        ours.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }.forEach { purchase ->
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) {}
        }
        val prices = details.mapNotNull { (id, product) -> product.oneTimePurchaseOfferDetails?.formattedPrice?.let { id to it } }.toMap()
        mutable.value = supportState(prices, supported = ours.any { it.purchaseState == Purchase.PurchaseState.PURCHASED },
            pending = ours.any { it.purchaseState == Purchase.PurchaseState.PENDING })
    }

    override fun buy(activity: Activity, productId: String) {
        val product = details[productId] ?: return
        val offer = product.oneTimePurchaseOfferDetails?.offerToken ?: return
        val params = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).setOfferToken(offer).build()
        client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params)).build())
    }
}
