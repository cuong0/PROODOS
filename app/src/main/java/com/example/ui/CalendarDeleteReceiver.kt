package com.example.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.content.ContentUris

class CalendarDeleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra("EVENT_ID", -1L)
        if (eventId != -1L) {
            try {
                val cr = context.contentResolver
                val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
                cr.delete(uri, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
