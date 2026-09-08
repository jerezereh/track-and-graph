package com.samco.trackandgraph.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.RemoteViews
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.samco.trackandgraph.R
import com.samco.trackandgraph.data.database.dto.GraphStatType
import com.samco.trackandgraph.data.interactor.DataInteractor
import com.samco.trackandgraph.graphstatview.factories.LineGraphDataFactory
import com.samco.trackandgraph.graphstatview.factories.viewdto.IGraphStatViewData
import com.samco.trackandgraph.graphstatview.ui.renderLineGraphWidgetBitmap
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

@HiltWorker
class GraphWidgetWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val dataInteractor: DataInteractor,
    private val lineGraphDataFactory: LineGraphDataFactory,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val manager = AppWidgetManager.getInstance(context)
        for (id in GraphWidgetProvider.ids(context)) {
            val views = RemoteViews(context.packageName, R.layout.graph_widget)
            configureAction(context, views, id)
            try {
                val finished = withTimeoutOrNull(20_000) {
                    populate(context, manager, views, id)
                    true
                }
                if (finished != true) showMessage(views, R.string.graph_widget_error)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to render graph widget %s", id)
                showMessage(views, R.string.graph_widget_error)
            }
            // A widget may have been removed or reconfigured while its graph was loading.
            if (id in GraphWidgetProvider.ids(context)) manager.updateAppWidget(id, views)
        }
        return Result.success()
    }

    private fun configureAction(context: Context, views: RemoteViews, id: Int) {
        val configure = Intent(context, GraphWidgetConfigureActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        views.setOnClickPendingIntent(R.id.graph_widget_settings, PendingIntent.getActivity(
            context, id, configure, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ))
    }

    private suspend fun populate(
        context: Context, manager: AppWidgetManager, views: RemoteViews, id: Int
    ) {
        val prefs = GraphWidgetProvider.preferences(context)
        val graphId = prefs.getLong(GraphWidgetProvider.graphKey(id), -1)
        val featureId = prefs.getLong(GraphWidgetProvider.featureKey(id), -1)
        val graph = dataInteractor.tryGetGraphStatById(graphId)
        val tracker = dataInteractor.tryGetTrackerByFeatureId(featureId)
        if (graph == null || tracker == null) {
            showMessage(views, R.string.graph_widget_missing)
            return
        }
        views.setTextViewText(R.id.graph_widget_title, graph.name)
        views.setContentDescription(R.id.graph_widget_image, graph.name)
        val label = context.getString(R.string.graph_widget_add_to, tracker.name)
        views.setContentDescription(R.id.graph_widget_add, label)
        val addIntent = TrackWidgetInputDataPointActivity.createInputIntent(context, featureId)
        val addAction = PendingIntent.getActivity(context, id, addIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.graph_widget_image, addAction)
        views.setOnClickPendingIntent(R.id.graph_widget_add, addAction)
        views.setViewVisibility(R.id.graph_widget_add, View.VISIBLE)
        if (graph.type != GraphStatType.LINE_GRAPH) {
            showMessage(views, R.string.graph_widget_line_graphs_only)
            return
        }
        val data = lineGraphDataFactory.createViewData(graph) {}
        if (data.state != IGraphStatViewData.State.READY) {
            showMessage(views, R.string.graph_widget_error)
            return
        }
        if (!data.hasPlottableData) {
            showMessage(views, R.string.graph_stat_view_not_enough_data_graph)
            return
        }
        val options = manager.getAppWidgetOptions(id)
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val widthKey = if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
            else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
        val heightKey = if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
            else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
        val widthDp = options.getInt(widthKey, 280).coerceIn(120, 800)
        val (width, height) = graphWidgetBitmapSize(
            widthDp, options.getInt(heightKey, 180),
            context.resources.displayMetrics.density,
        )
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val renderContext = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply {
                densityDpi = (160f * width / widthDp).toInt().coerceAtLeast(1)
            }
        )
        val bitmap = withContext(Dispatchers.Main) {
            renderLineGraphWidgetBitmap(
                ContextThemeWrapper(renderContext, R.style.AppTheme), data, width, height,
                if (night) Color.WHITE else Color.BLACK,
                context.getColor(R.color.graph_widget_background),
            )
        }
        views.setImageViewBitmap(R.id.graph_widget_image, bitmap)
        views.setViewVisibility(R.id.graph_widget_image, View.VISIBLE)
        views.setViewVisibility(R.id.graph_widget_message, View.GONE)
    }

    private fun showMessage(views: RemoteViews, message: Int) {
        views.setViewVisibility(R.id.graph_widget_image, View.GONE)
        views.setViewVisibility(R.id.graph_widget_message, View.VISIBLE)
        views.setTextViewText(R.id.graph_widget_message, applicationContext.getString(message))
    }
}
