package com.example.quickpayment.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.quickpayment.MainActivity
import com.example.quickpayment.R
import com.example.quickpayment.data.PaymentsRepository
import com.example.quickpayment.data.UpcomingPayment

class QuickPaymentWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_HIDE_FROM_WIDGET) {
            val paymentId = intent.getStringExtra(EXTRA_PAYMENT_ID).orEmpty()
            if (paymentId.isNotBlank()) {
                PaymentsRepository(context).hideFromWidget(paymentId)
                forceUpdate(context)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(appWidgetId, buildViews(context))
        }
    }

    companion object {
        private const val ACTION_HIDE_FROM_WIDGET = "com.example.quickpayment.widget.HIDE_FROM_WIDGET"
        private const val EXTRA_PAYMENT_ID = "extra_payment_id"

        fun forceUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, QuickPaymentWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { id ->
                manager.updateAppWidget(id, buildViews(context))
            }
        }

        private fun buildViews(context: Context): RemoteViews {
            val payments = PaymentsRepository(context).loadWidgetPayments(3)
            val views = RemoteViews(context.packageName, R.layout.widget_quick_payment)

            val rows = listOf(
                Triple(R.id.row1, R.id.widgetPayment1, R.id.widgetDelete1),
                Triple(R.id.row2, R.id.widgetPayment2, R.id.widgetDelete2),
                Triple(R.id.row3, R.id.widgetPayment3, R.id.widgetDelete3)
            )

            if (payments.isEmpty()) {
                rows.forEach { (rowId, _, _) -> views.setViewVisibility(rowId, View.GONE) }
                views.setViewVisibility(R.id.widgetEmpty, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widgetEmpty, View.GONE)
                rows.forEachIndexed { index, (rowId, textId, deleteId) ->
                    if (index < payments.size) {
                        val payment = payments[index]
                        views.setViewVisibility(rowId, View.VISIBLE)
                        views.setTextViewText(textId, "${payment.title} • ${payment.amount} ₽ (${payment.dueDate})")
                        views.setOnClickPendingIntent(textId, openPaymentIntent(context, payment, index))
                        views.setOnClickPendingIntent(deleteId, hideFromWidgetIntent(context, payment.id, index))
                    } else {
                        views.setViewVisibility(rowId, View.GONE)
                    }
                }
            }

            return views
        }

        private fun openPaymentIntent(context: Context, payment: UpcomingPayment, requestCode: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("open_screen", "payment")
                putExtra("title", payment.title)
                putExtra("account", payment.account)
                putExtra("amount", payment.amount.toString())
            }
            return PendingIntent.getActivity(
                context,
                2000 + requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun hideFromWidgetIntent(context: Context, paymentId: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, QuickPaymentWidgetProvider::class.java).apply {
                action = ACTION_HIDE_FROM_WIDGET
                putExtra(EXTRA_PAYMENT_ID, paymentId)
            }
            return PendingIntent.getBroadcast(
                context,
                3000 + requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
