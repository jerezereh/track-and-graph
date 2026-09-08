package com.samco.trackandgraph.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.samco.trackandgraph.R
import com.samco.trackandgraph.data.database.dto.GraphStatType
import com.samco.trackandgraph.data.interactor.DataInteractor
import com.samco.trackandgraph.selectitemdialog.SelectItemDialogContent
import com.samco.trackandgraph.selectitemdialog.SelectItemDialogViewModelImpl
import com.samco.trackandgraph.selectitemdialog.SelectableItemType
import com.samco.trackandgraph.ui.theming.TnGComposeTheme
import com.samco.trackandgraph.ui.ui.CustomDialog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GraphWidgetConfigureActivity : AppCompatActivity() {
    @Inject lateinit var dataInteractor: DataInteractor
    private val picker by viewModels<SelectItemDialogViewModelImpl>()
    private var graphId by mutableStateOf<Long?>(null)
    private var busy by mutableStateOf(false)
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        graphId = savedInstanceState?.getLong("selectedGraph", -1)?.takeIf { it >= 0 }
        setContent {
            TnGComposeTheme {
                CustomDialog(onDismissRequest = { finish() }, scrollContent = false) {
                    SelectItemDialogContent(
                        title = stringResource(if (graphId == null)
                            R.string.graph_widget_select_graph else R.string.graph_widget_select_tracker),
                        selectableTypes = setOf(if (graphId == null)
                            SelectableItemType.GRAPH else SelectableItemType.TRACKER),
                        viewModel = picker,
                        selectionEnabled = !busy,
                        dismissAfterSelection = false,
                        onGraphSelected = ::selectGraph,
                        onFeatureSelected = ::save,
                        onDismissRequest = { finish() },
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("selectedGraph", graphId ?: -1)
        super.onSaveInstanceState(outState)
    }

    private fun selectGraph(id: Long) {
        if (busy) return
        busy = true
        lifecycleScope.launch {
            try {
                val graph = dataInteractor.tryGetGraphStatById(id)
                if (graph?.type != GraphStatType.LINE_GRAPH) {
                    Toast.makeText(this@GraphWidgetConfigureActivity,
                        R.string.graph_widget_line_graphs_only, Toast.LENGTH_LONG).show()
                } else {
                    picker.reset()
                    graphId = id
                }
            } finally {
                busy = false
            }
        }
    }

    private fun save(featureId: Long) {
        val selectedGraph = graphId ?: return
        if (busy) return
        // Selection IDs are stable database IDs, never list positions or display names.
        GraphWidgetProvider.preferences(this).edit(commit = true) {
            putLong(GraphWidgetProvider.graphKey(widgetId), selectedGraph)
            putLong(GraphWidgetProvider.featureKey(widgetId), featureId)
        }
        GraphWidgetProvider.requestUpdate(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}
