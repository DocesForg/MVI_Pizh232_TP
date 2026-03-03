package com.example.quickpayment.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.quickpayment.data.PaymentsRepository
import com.example.quickpayment.data.UpcomingPayment
import com.example.quickpayment.widget.QuickPaymentWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val loading: Boolean = true,
    val payments: List<UpcomingPayment> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PaymentsRepository(application)
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = UiState(loading = true)
            val payments = repository.getUpcomingPayments()
            _uiState.value = UiState(loading = false, payments = payments)
            QuickPaymentWidgetProvider.forceUpdate(getApplication())
        }
    }

    fun addCustomPayment(title: String, account: String, amount: String, dueDate: String) {
        val payment = repository.addCustomPayment(title, account, amount, dueDate)
        _uiState.value = _uiState.value.copy(payments = listOf(payment) + _uiState.value.payments)
        QuickPaymentWidgetProvider.forceUpdate(getApplication())
    }

    fun deletePayment(paymentId: String) {
        repository.deletePayment(paymentId)
        _uiState.value = _uiState.value.copy(payments = _uiState.value.payments.filterNot { it.id == paymentId })
        QuickPaymentWidgetProvider.forceUpdate(getApplication())
    }

    fun hideFromWidget(paymentId: String) {
        repository.hideFromWidget(paymentId)
        QuickPaymentWidgetProvider.forceUpdate(getApplication())
    }

    fun restoreToWidget(paymentId: String) {
        repository.unhideFromWidget(paymentId)
        QuickPaymentWidgetProvider.forceUpdate(getApplication())
    }

    fun isHiddenInWidget(paymentId: String): Boolean = repository.isHiddenInWidget(paymentId)

    fun savePrefill(payment: UpcomingPayment) = repository.savePrefill(payment)
    fun readPrefill() = repository.readPrefill()
}
