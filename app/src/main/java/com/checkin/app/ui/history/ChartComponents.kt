package com.checkin.app.ui.history

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.entry.entryOf
import com.patrykandpatrick.vico.core.entry.entryModelOf

@Composable
fun SessionChart(aggregatedData: Map<String, Float>) {
    if (aggregatedData.isEmpty()) return

    val dates = remember(aggregatedData) { aggregatedData.keys.toList() }

    val chartEntryModel = remember(aggregatedData) {
        val entries = aggregatedData.values.mapIndexed { index, value ->
            entryOf(index, value)
        }
        entryModelOf(entries)
    }

    val bottomAxisValueFormatter = remember(dates) {
        AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
            val index = value.toInt()
            if (index >= 0 && index < dates.size) dates[index] else ""
        }
    }

    Chart(
        chart = columnChart(),
        model = chartEntryModel,
        startAxis = rememberStartAxis(),
        bottomAxis = rememberBottomAxis(
            valueFormatter = bottomAxisValueFormatter
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    )
}
