package com.example.quickpayment.data

import retrofit2.http.GET

interface PaymentsApi {
    @GET("payments/upcoming")
    suspend fun upcomingPayments(): List<UpcomingPayment>
}
