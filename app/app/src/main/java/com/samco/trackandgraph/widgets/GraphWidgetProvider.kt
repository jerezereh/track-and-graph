package com.samco.trackandgraph.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.edit
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/** Separate provider so existing quick-entry widgets keep their configuration and identity. */
class GraphWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        requestUpdate(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, id: Int, options: Bundle
    ) {
        requestUpdate(context)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        preferences(context).edit {
            ids.forEach { id ->
                remove(graphKey(id))
                remove(featureKey(id))
            }
        }
    }

    override fun onRestored(context: Context, oldIds: IntArray, newIds: IntArray) {
        val prefs = preferences(context)
        val configurations = oldIds.map { id ->
            prefs.getLong(graphKey(id), -1) to prefs.getLong(featureKey(id), -1)
        }
        // Read all old values before editing: Android can reuse IDs during restore.
        prefs.edit {
            oldIds.forEach { remove(graphKey(it)); remove(featureKey(it)) }
            newIds.zip(configurations).forEach { (id, config) ->
                putLong(graphKey(id), config.first)
                putLong(featureKey(id), config.second)
            }
        }
        requestUpdate(context)
    }

    companion object {
        fun preferences(context: Context) =
            context.getSharedPreferences("graph_widgets", Context.MODE_PRIVATE)

        fun graphKey(id: Int) = "graph_$id"
        fun featureKey(id: Int) = "feature_$id"

        fun ids(context: Context): IntArray = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, GraphWidgetProvider::class.java))

        fun requestUpdate(context: Context) {
            if (ids(context).isEmpty()) return
            // Survives the quick-entry Activity closing and serializes snapshot generation.
            WorkManager.getInstance(context).enqueueUniqueWork(
                "graph-widget-refresh",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<GraphWidgetWorker>().build(),
            )
        }
    }
}
