package com.samco.trackandgraph.widgets

import android.content.Context
import com.samco.trackandgraph.data.interactor.DataInteractor
import com.samco.trackandgraph.data.interactor.DataUpdateType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@Singleton
class GraphWidgetUpdates @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataInteractor: DataInteractor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            dataInteractor.getDataUpdateEvents()
                .filter { it.affectsGraphWidgets() }
                .debounce(300)
                .collect { GraphWidgetProvider.requestUpdate(context) }
        }
    }
}

// Refresh all configured graphs: a function can depend indirectly on any changed tracker.
internal fun DataUpdateType.affectsGraphWidgets(): Boolean = when (this) {
    DataUpdateType.Unknown,
    is DataUpdateType.DataPoint,
    is DataUpdateType.TrackerUpdated,
    is DataUpdateType.TrackerDeleted,
    is DataUpdateType.GraphOrStatUpdated,
    DataUpdateType.GraphOrStatDeleted,
    DataUpdateType.GroupDeleted,
    is DataUpdateType.FeatureUpdate -> true
    else -> false
}
