---
title: Graph home-screen widgets in the jerezereh fork
description: Separate installation, saved line graph rendering, tracker quick entry, refresh, and validation.
keywords: [widget, GraphWidgetProvider, GraphWidgetWorker, bitmap, fork, installation]
---

# Graph & Track widget

The fork uses application ID `com.jerezereh.trackandgraph`; its FOSS debug APK uses
`com.jerezereh.trackandgraph.debug` and launcher label **Graph & Track (Debug)**.
The upstream app can remain installed. Import an upstream backup into the fork;
the two installations have independent databases and do not synchronize.

## Use

1. Build `cd app && ./gradlew :app:assembleFossDebug`, or download the
   `graph-and-track-debug` artifact from the **Graph widget APK** GitHub Actions run.
2. Install the APK and import a backup through the app's backup/restore screen.
3. Long-press the Samsung home screen, select Widgets, then **Graph & Track (Debug)**
   and **Graph & Track**. Place the 4×2 widget.
4. Select a saved **line graph**, then select the tracker its add action should use.
5. Tap the graph or ＋ to use normal tracker entry, including configured default values.
   Use ⚙ to change the graph or entry tracker.

Only saved line graphs are supported in this first version. Graph calculations,
styles, durations, time windows, transformations and multiple series use the existing
`LineGraphDataFactory` and AndroidPlot renderer. Axes use three labels and the legend
is compressed to one row to fit the widget. Selection rejects other graph types.
The entry tracker is chosen separately; select a tracker used by the graph.

The graph is visible on the home screen even when the app's App Lock is enabled,
consistent with the existing quick-entry widget's access policy.

## Architecture

`GraphWidgetConfigureActivity` saves graph and feature IDs keyed by Android widget ID.
`GraphWidgetProvider` uses a separate provider identity from the old quick-entry widget,
handles resize, deletion and widget-ID remapping after restore, and queues a WorkManager
job. Android requests periodic refresh every 30 minutes (delivery may be delayed by
battery management). Startup data observers queue updates after edits, deletes,
imports, graph configuration changes and indirect function changes; updates are debounced.

`GraphWidgetWorker` calculates off the main thread, draws Views only on the main thread,
and sends one bounded bitmap through RemoteViews. Bitmaps are capped at 160,000 pixels.
Each graph calculation has a 20-second coroutine timeout. Missing/deleted inputs and
render errors show a message with a configuration action; the add action stays available
for valid trackers even when a graph has no points.

Rendering never writes to the database. Both chart and plus clicks reuse
`TrackWidgetInputDataPointActivity`; default-value saves are awaited before closing it.

## Validation

Run `cd app && ./gradlew :app:testFossDebugUnitTest :app:assembleFossDebug`.
The GitHub Actions workflow runs these and retains the APK and test report.
Host unit tests cover bitmap IPC bounds and dependency-change invalidation.

Device acceptance checks (require a phone or emulator):
- Import a backup and place two widgets with different graph/tracker pairs.
- Check line colors, time range, duration axes and multi-series legend against the app.
- Add data via each widget; edit/delete data in the app; confirm matching plots refresh.
- Verify default-value entry adds exactly one point and saves before closing.
- Resize, rotate, switch light/dark mode, reboot, and check eventual refresh.
- Test no-data and constant-zero graphs, deleted tracker/graph, and settings recovery.
- Confirm the original F-Droid installation and its data remain independent.

Debug builds are for testing. Keep a stable signing key for future installed updates;
CI debug keys are not a long-term release signing arrangement.
