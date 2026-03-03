package com.example.quickpayment.data

data class UpcomingPayment(
    val id: String,
    val title: String,
    val account: String,
    val amount: Double,
    val dueDate: String
)
