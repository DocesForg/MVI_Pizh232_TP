package com.example.quickpayment

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.quickpayment.data.UpcomingPayment
import com.example.quickpayment.ui.MainViewModel
import com.example.quickpayment.ui.theme.QuickPaymentTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QuickPaymentTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNav(
                        viewModel = viewModel,
                        openPaymentDirectly = intent?.getStringExtra("open_screen") == "payment",
                        intentTitle = intent?.getStringExtra("title").orEmpty(),
                        intentAccount = intent?.getStringExtra("account").orEmpty(),
                        intentAmount = intent?.getStringExtra("amount").orEmpty()
                    )
                }
            }
        }
    }
}

@Composable
private fun AppNav(
    viewModel: MainViewModel,
    openPaymentDirectly: Boolean,
    intentTitle: String,
    intentAccount: String,
    intentAmount: String
) {
    val navController = rememberNavController()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = if (openPaymentDirectly) "payment" else "home"
    ) {
        composable("home") {
            when {
                state.loading -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) { CircularProgressIndicator() }

                else -> PaymentsScreen(
                    payments = state.payments,
                    onPay = { payment ->
                        viewModel.restoreToWidget(payment.id)
                        viewModel.savePrefill(payment)
                        navController.navigate("payment")
                    },
                    onAddPayment = { title, account, amount, dueDate ->
                        viewModel.addCustomPayment(title, account, amount, dueDate)
                    },
                    onDeletePayment = { paymentId ->
                        viewModel.deletePayment(paymentId)
                    }
                )
            }
        }
        composable("payment") {
            val fromStorage = viewModel.readPrefill()
            PaymentFormScreen(
                startTitle = intentTitle.ifBlank { fromStorage.first },
                startAccount = intentAccount.ifBlank { fromStorage.second },
                startAmount = intentAmount.ifBlank { fromStorage.third }
            )
        }
        composable(
            route = "payment?title={title}&account={account}&amount={amount}",
            arguments = listOf(
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("account") { type = NavType.StringType; defaultValue = "" },
                navArgument("amount") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStack ->
            val passedTitle = backStack.arguments?.getString("title").orEmpty()
            val passedAccount = backStack.arguments?.getString("account").orEmpty()
            val passedAmount = backStack.arguments?.getString("amount").orEmpty()
            val fromStorage = viewModel.readPrefill()
            PaymentFormScreen(
                startTitle = passedTitle.ifBlank { fromStorage.first },
                startAccount = passedAccount.ifBlank { fromStorage.second },
                startAmount = passedAmount.ifBlank { fromStorage.third }
            )
        }
    }
}

@Composable
private fun PaymentsScreen(
    payments: List<UpcomingPayment>,
    onPay: (UpcomingPayment) -> Unit,
    onAddPayment: (String, String, String, String) -> Unit,
    onDeletePayment: (String) -> Unit
) {
    var newTitle by remember { mutableStateOf("") }
    var newAccount by remember { mutableStateOf("") }
    var newAmount by remember { mutableStateOf("") }
    var newDueDate by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp)
            ) {
                Text("Quick Payment", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    "Предстоящие платежи",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Добавить свой платеж", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = newTitle, onValueChange = { newTitle = it }, label = { Text("Название") })
                    OutlinedTextField(
                        value = newAccount,
                        onValueChange = { newAccount = sanitizeDigitsInput(it) },
                        label = { Text("Номер/счет") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = newAmount,
                        onValueChange = { newAmount = sanitizeAmountInput(it) },
                        label = { Text("Сумма") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    val context = LocalContext.current
                    Button(onClick = {
                        val dialog = DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                newDueDate = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                            },
                            2026,
                            0,
                            1
                        )
                        dialog.show()
                    }) {
                        Text(if (newDueDate.isBlank()) "Выбрать дату" else "Дата: $newDueDate")
                    }
                    Button(
                        onClick = {
                            onAddPayment(newTitle, newAccount, newAmount, newDueDate)
                            newTitle = ""
                            newAccount = ""
                            newAmount = ""
                            newDueDate = ""
                        },
                        enabled = newTitle.isNotBlank() && newAccount.isNotBlank() && newAmount.isNotBlank() && newDueDate.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Добавить") }
                }
            }
        }

        items(payments) { payment ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(payment.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Счет: ${payment.account}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Сумма: ${payment.amount} ₽", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    Text("Оплатить до: ${payment.dueDate}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onPay(payment) }, modifier = Modifier.padding(top = 8.dp)) { Text("Оплатить") }
                        Button(onClick = { onDeletePayment(payment.id) }, modifier = Modifier.padding(top = 8.dp)) { Text("Удалить") }
                    }
                }
            }
        }
    }
}

private fun sanitizeDigitsInput(input: String): String = input.filter { it.isDigit() }

private fun sanitizeAmountInput(input: String): String {
    val filtered = input.filter { it.isDigit() || it == ',' }
    val firstComma = filtered.indexOf(',')
    if (firstComma == -1) return filtered

    val integerPart = filtered.substring(0, firstComma).filter { it.isDigit() }
    val decimalPart = filtered.substring(firstComma + 1).filter { it.isDigit() }.take(2)
    return if (decimalPart.isEmpty()) "$integerPart," else "$integerPart,$decimalPart"
}

@Composable
private fun PaymentFormScreen(startTitle: String, startAccount: String, startAmount: String) {
    var title by remember { mutableStateOf(startTitle) }
    var account by remember { mutableStateOf(startAccount) }
    var amount by remember { mutableStateOf(startAmount) }
    var status by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Форма быстрого платежа", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Получатель") })
        OutlinedTextField(
            value = account,
            onValueChange = { account = sanitizeDigitsInput(it) },
            label = { Text("Номер/счет") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = sanitizeAmountInput(it) },
            label = { Text("Сумма") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { status = "Платёж ${title.ifBlank { "без названия" }} на $amount ₽ отправлен (демо)." },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Оплатить")
            }
        }
        if (status.isNotBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Text(status, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }
}
