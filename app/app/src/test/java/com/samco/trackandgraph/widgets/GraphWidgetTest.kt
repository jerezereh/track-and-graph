package com.samco.trackandgraph.widgets

import com.samco.trackandgraph.data.interactor.DataUpdateType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphWidgetTest {
    @Test fun bitmapFitsIpcBudgetEvenForOversizedLauncherDimensions() {
        for (width in listOf(Int.MIN_VALUE, 0, 180, 280, 800, Int.MAX_VALUE)) {
            for (height in listOf(0, 120, 180, 600, Int.MAX_VALUE)) {
                for (density in listOf(Float.NaN, -1f, 1f, 3f, Float.POSITIVE_INFINITY)) {
                    val (w, h) = graphWidgetBitmapSize(width, height, density)
                    assertTrue(w > 0 && h > 0)
                    assertTrue(w.toLong() * h <= 160_000)
                }
            }
        }
    }

    @Test fun editsImportsAndIndirectFunctionChangesInvalidateGraphs() {
        val changes = listOf(
            DataUpdateType.DataPoint(42), DataUpdateType.Unknown,
            DataUpdateType.TrackerDeleted(1, 42), DataUpdateType.TrackerUpdated(1, 42),
            DataUpdateType.GraphOrStatUpdated(1), DataUpdateType.GraphOrStatDeleted,
            DataUpdateType.FunctionUpdated(99), DataUpdateType.FunctionDeleted(99),
        )
        changes.forEach { assertTrue(it.affectsGraphWidgets()) }
        assertFalse(DataUpdateType.Reminder(1).affectsGraphWidgets())
        assertFalse(DataUpdateType.GlobalNote.affectsGraphWidgets())
        assertFalse(DataUpdateType.DisplayIndex(1).affectsGraphWidgets())
    }
}
