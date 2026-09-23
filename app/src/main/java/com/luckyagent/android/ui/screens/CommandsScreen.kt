package com.luckyagent.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.luckyagent.android.data.api.RuntimeCommand
import com.luckyagent.android.ui.AppUiState
import com.luckyagent.android.ui.AppViewModel
import com.luckyagent.android.ui.components.CloverCard
import com.luckyagent.android.ui.components.EmptyState
import com.luckyagent.android.ui.components.ErrorLine
import com.luckyagent.android.ui.components.MarkdownText
import com.luckyagent.android.ui.components.MetaChip
import com.luckyagent.android.ui.components.ScreenHeader
import com.luckyagent.android.ui.theme.CloverBg
import com.luckyagent.android.ui.theme.CloverError
import com.luckyagent.android.ui.theme.CloverText2
import com.luckyagent.android.ui.theme.CloverText3
import com.luckyagent.android.ui.theme.CloverWarning

@Composable
fun CommandsScreen(state: AppUiState, vm: AppViewModel) {
    var query by remember { mutableStateOf("") }
    var selectedCommand by remember { mutableStateOf<RuntimeCommand?>(null) }
    var args by remember(selectedCommand?.name) { mutableStateOf("") }
    val filtered = remember(state.commands, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) state.commands
        else state.commands.filter { command ->
            listOf(command.name, command.usage, command.description, command.group)
                .any { it.lowercase().contains(q) }
        }
    }

    Column(
        Modifier.fillMaxSize().background(CloverBg),
    ) {
        ScreenHeader(
            eyebrow = "Runtime",
            title = "Commands",
            subtitle = if (state.commands.isEmpty()) {
                "Run commands exposed by the connected LuckyAgent server"
            } else {
                "${state.commands.size} commands from the connected runtime"
            },
            actions = {
                IconButton(onClick = vm::refreshCommands) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh commands")
                }
            },
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            label = { Text("Search commands") },
            placeholder = { Text("Name, usage, or description") },
        )

        if (state.commandsLoading) {
            Text("Loading command catalog…", color = CloverText2, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        }
        state.commandsError?.let { ErrorLine(it) }

        state.commandExecution?.let { result ->
            CloverCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("/${result.command}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    MetaChip(if (result.ok) "completed" else "error")
                }
                MarkdownText(
                    markdown = result.output.ifBlank { "No output returned." },
                    color = if (result.ok) MaterialTheme.colorScheme.onSurface else CloverError,
                )
            }
        }

        when {
            !state.commandsLoading && state.commands.isEmpty() && state.commandsError == null -> {
                EmptyState(
                    title = "No commands available",
                    body = "The connected server did not return a command catalog. Check the server version and API endpoint.",
                    modifier = Modifier.padding(16.dp),
                )
            }
            !state.commandsLoading && state.commands.isEmpty() && state.commandsError != null -> {
                EmptyState(
                    title = "Could not load commands",
                    body = "Check the connection and retry using the refresh button above.",
                    modifier = Modifier.padding(16.dp),
                )
            }
            !state.commandsLoading && filtered.isEmpty() -> {
                EmptyState(
                    title = "No matching commands",
                    body = "Try a shorter search term.",
                    modifier = Modifier.padding(16.dp),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    filtered.groupBy { it.group }.forEach { (group, commands) ->
                        item(key = "group-$group") {
                            Text(
                                group.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelLarge,
                                color = CloverText3,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 2.dp, top = 8.dp),
                            )
                        }
                        items(commands, key = { it.name }) { command ->
                            CommandRow(command = command, onClick = { selectedCommand = command })
                        }
                    }
                }
            }
        }
    }

    selectedCommand?.let { command ->
        val changesState = command.name in setOf("remember", "remember_long", "memdecay", "promote", "rename") ||
            (command.name == "model" && args.isNotBlank())
        AlertDialog(
            onDismissRequest = { selectedCommand = null },
            title = { Text("/${command.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(command.description, color = CloverText2)
                    Text(
                        "Usage: ${command.usage}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                    OutlinedTextField(
                        value = args,
                        onValueChange = { args = it },
                        label = { Text("Arguments") },
                        placeholder = { Text(command.usage.substringAfter(command.name).trim().ifBlank { "No arguments" }) },
                        enabled = !state.commandExecuting,
                        singleLine = command.name !in setOf("remember", "remember_long", "recall"),
                        maxLines = if (command.name in setOf("remember", "remember_long", "recall")) 4 else 1,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (changesState) {
                        Text(
                            "This command can change runtime or saved data. Review the arguments before running it.",
                            color = CloverWarning,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    state.commandsError?.let { Text(it, color = CloverError, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !state.commandExecuting,
                    onClick = {
                        vm.runCommand(command, args)
                        selectedCommand = null
                    },
                ) { Text(if (state.commandExecuting) "Running…" else "Run command") }
            },
            dismissButton = {
                TextButton(onClick = { selectedCommand = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun CommandRow(command: RuntimeCommand, onClick: () -> Unit) {
    CloverCard(
        modifier = Modifier.clickable(onClick = onClick),
    ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "/${command.name}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            MetaChip(command.group)
        }
        Text(command.description, style = MaterialTheme.typography.bodyMedium, color = CloverText2)
        Text(
            command.usage,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = CloverText3,
        )
    }
}
