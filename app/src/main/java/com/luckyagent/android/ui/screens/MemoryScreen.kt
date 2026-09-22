package com.luckyagent.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luckyagent.android.ui.AppUiState
import com.luckyagent.android.ui.theme.CloverBg
import com.luckyagent.android.ui.theme.CloverLine
import com.luckyagent.android.ui.theme.CloverSurface
import com.luckyagent.android.ui.theme.CloverText
import com.luckyagent.android.ui.theme.CloverText2
import com.luckyagent.android.ui.theme.CloverText3

@Composable
fun MemoryScreen(state: AppUiState, onRefresh: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(CloverBg)
            .padding(16.dp),
    ) {
        Text("Memory graph", style = MaterialTheme.typography.headlineSmall, color = CloverText)
        Text(
            "对照 GUI Memory：先列表召回，图可视化下一迭代再补。",
            style = MaterialTheme.typography.bodyMedium,
            color = CloverText2,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Button(onClick = onRefresh) { Text("Refresh") }
        state.memoryError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        LazyColumn(
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.memoryEntries, key = { it.id ?: it.content.orEmpty() }) { entry ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(CloverSurface, RoundedCornerShape(12.dp))
                        .border(1.dp, CloverLine, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                ) {
                    Text(
                        listOfNotNull(entry.category, entry.tier, entry.importance?.let { "imp=$it" })
                            .joinToString(" · ")
                            .ifBlank { "memory" },
                        style = MaterialTheme.typography.labelSmall,
                        color = CloverText3,
                    )
                    Text(entry.content.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = CloverText)
                }
            }
        }
    }
}
