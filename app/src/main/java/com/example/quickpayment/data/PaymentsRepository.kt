package com.example.quickpayment.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.UUID

class PaymentsRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val api: PaymentsApi by lazy {
        val logger = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder().addInterceptor(logger).build()
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PaymentsApi::class.java)
    }

    suspend fun getUpcomingPayments(): List<UpcomingPayment> = withContext(Dispatchers.IO) {
        val loaded = runCatching { api.upcomingPayments() }
            .onSuccess { saveNetworkCache(it) }
            .getOrElse { loadNetworkCache().ifEmpty { demoFallback() } }

        val merged = filterDeleted(mergeWithCustom(loaded))
        saveNetworkCache(merged)
        merged
    }

    fun addCustomPayment(title: String, account: String, amount: String, dueDate: String): UpcomingPayment {
        val item = UpcomingPayment(
            id = UUID.randomUUID().toString(),
            title = title,
            account = account,
            amount = amount.replace(',', '.').toDoubleOrNull() ?: 0.0,
            dueDate = dueDate
        )
        val updatedCustom = listOf(item) + loadCustomPayments()
        saveCustomPayments(updatedCustom)

        val updatedDeleted = loadDeletedIds().toMutableSet().apply { remove(item.id) }
        saveDeletedIds(updatedDeleted)

        val merged = filterDeleted(mergeWithCustom(loadNetworkCache().ifEmpty { demoFallback() }))
        saveNetworkCache(merged)
        return item
    }

    fun deletePayment(paymentId: String) {
        val deleted = loadDeletedIds().toMutableSet().apply { add(paymentId) }
        saveDeletedIds(deleted)
        saveCustomPayments(loadCustomPayments().filterNot { it.id == paymentId })
        saveNetworkCache(loadNetworkCache().filterNot { it.id == paymentId })
        unhideFromWidget(paymentId)
    }

    fun hideFromWidget(paymentId: String) {
        val hidden = loadHiddenInWidgetIds().toMutableSet().apply { add(paymentId) }
        saveHiddenInWidgetIds(hidden)
    }

    fun unhideFromWidget(paymentId: String) {
        val hidden = loadHiddenInWidgetIds().toMutableSet().apply { remove(paymentId) }
        saveHiddenInWidgetIds(hidden)
    }

    fun savePrefill(payment: UpcomingPayment) {
        prefs.edit()
            .putString(PREFILL_TITLE, payment.title)
            .putString(PREFILL_ACCOUNT, payment.account)
            .putString(PREFILL_AMOUNT, payment.amount.toString())
            .apply()
    }

    fun readPrefill(): Triple<String, String, String> {
        return Triple(
            prefs.getString(PREFILL_TITLE, "") ?: "",
            prefs.getString(PREFILL_ACCOUNT, "") ?: "",
            prefs.getString(PREFILL_AMOUNT, "") ?: ""
        )
    }

    fun loadCache(): List<UpcomingPayment> = filterDeleted(mergeWithCustom(loadNetworkCache()))

    fun loadWidgetPayments(limit: Int = 3): List<UpcomingPayment> {
        val hidden = loadHiddenInWidgetIds()
        return loadCache().filterNot { it.id in hidden }.take(limit)
    }

    fun isHiddenInWidget(paymentId: String): Boolean = paymentId in loadHiddenInWidgetIds()

    private fun saveNetworkCache(items: List<UpcomingPayment>) {
        prefs.edit().putString(CACHE_KEY, gson.toJson(items)).apply()
    }

    private fun loadNetworkCache(): List<UpcomingPayment> {
        val raw = prefs.getString(CACHE_KEY, null) ?: return emptyList()
        val type = object : TypeToken<List<UpcomingPayment>>() {}.type
        return gson.fromJson(raw, type)
    }

    private fun loadCustomPayments(): List<UpcomingPayment> {
        val raw = prefs.getString(CUSTOM_CACHE_KEY, null) ?: return emptyList()
        val type = object : TypeToken<List<UpcomingPayment>>() {}.type
        return gson.fromJson(raw, type)
    }

    private fun saveCustomPayments(items: List<UpcomingPayment>) {
        prefs.edit().putString(CUSTOM_CACHE_KEY, gson.toJson(items)).apply()
    }

    private fun loadDeletedIds(): Set<String> {
        val raw = prefs.getStringSet(DELETED_IDS_KEY, emptySet()) ?: emptySet()
        return raw.toSet()
    }

    private fun loadHiddenInWidgetIds(): Set<String> {
        val raw = prefs.getStringSet(WIDGET_HIDDEN_IDS_KEY, emptySet()) ?: emptySet()
        return raw.toSet()
    }

    private fun saveHiddenInWidgetIds(ids: Set<String>) {
        prefs.edit().putStringSet(WIDGET_HIDDEN_IDS_KEY, ids).apply()
    }

    private fun saveDeletedIds(ids: Set<String>) {
        prefs.edit().putStringSet(DELETED_IDS_KEY, ids).apply()
    }

    private fun filterDeleted(items: List<UpcomingPayment>): List<UpcomingPayment> {
        val deleted = loadDeletedIds()
        return items.filterNot { it.id in deleted }
    }

    private fun mergeWithCustom(base: List<UpcomingPayment>): List<UpcomingPayment> {
        val custom = loadCustomPayments()
        return (custom + base).distinctBy { it.id }
    }

    private fun demoFallback() = listOf(
        UpcomingPayment("1", "МТС", "+79991234567", 550.0, "2026-03-10"),
        UpcomingPayment("2", "Интернет", "ЛС 4450012", 790.0, "2026-03-15")
    )

    companion object {
        private const val BASE_URL = "https://demo.payments.local/"
        private const val PREFS_NAME = "quick_payment_prefs"
        private const val CACHE_KEY = "cached_payments"
        private const val CUSTOM_CACHE_KEY = "custom_payments"
        private const val DELETED_IDS_KEY = "deleted_payment_ids"
        private const val WIDGET_HIDDEN_IDS_KEY = "widget_hidden_payment_ids"
        private const val PREFILL_TITLE = "prefill_title"
        private const val PREFILL_ACCOUNT = "prefill_account"
        private const val PREFILL_AMOUNT = "prefill_amount"
    }
}
