package com.luckyagent.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.luckyagent.android.data.api.SkillSummary
import com.luckyagent.android.ui.AppUiState
import com.luckyagent.android.ui.AppViewModel
import com.luckyagent.android.ui.components.CloverCard
import com.luckyagent.android.ui.components.EmptyState
import com.luckyagent.android.ui.components.ErrorLine
import com.luckyagent.android.ui.components.MetaChip
import com.luckyagent.android.ui.components.ScreenHeader
import com.luckyagent.android.ui.theme.CloverAccent
import com.luckyagent.android.ui.theme.CloverBg
import com.luckyagent.android.ui.theme.CloverError
import com.luckyagent.android.ui.theme.CloverSurface2
import com.luckyagent.android.ui.theme.CloverText2
import com.luckyagent.android.ui.theme.CloverText3

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SkillsScreen(state: AppUiState, vm: AppViewModel) {
    val filtered = remember(state.skills, state.skillsQuery) {
        val q = state.skillsQuery.trim().lowercase()
        if (q.isEmpty()) state.skills
        else state.skills.filter { skill ->
            listOfNotNull(
                skill.name,
                skill.description,
                skill.summary,
                skill.state,
                skill.author,
            ).any { it.lowercase().contains(q) } ||
                skill.aliases.any { it.lowercase().contains(q) } ||
                skill.tools.any { it.name.lowercase().contains(q) || (it.fullName?.lowercase()?.contains(q) == true) }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(CloverBg),
    ) {
        ScreenHeader(
            eyebrow = "Capabilities",
            title = "Skills",
            subtitle = state.skillsDir?.let { "dir · $it" } ?: "Loaded skill packs from the host runtime",
            actions = {
                IconButton(onClick = vm::refreshSkills) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                }
            },
        )

        if (state.skillsLoading) {
            Text("Loading skills…", color = CloverText2, modifier = Modifier.padding(horizontal = 16.dp))
        }
        state.skillsError?.let { ErrorLine(it) }

        Text(
            "Install / enable / rollback stay on desktop GUI. Phone view is structured read-only inventory.",
            color = CloverText3,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        BasicTextField(
            value = state.skillsQuery,
            onValueChange = vm::updateSkillsQuery,
            singleLine = true,
            cursorBrush = SolidColor(CloverAccent),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CloverSurface2)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            decorationBox = { inner ->
                if (state.skillsQuery.isEmpty()) {
                    Text("Filter skills / tools", color = CloverText3)
                }
                inner()
            },
        )

        when {
            !state.skillsLoading && state.skills.isEmpty() && state.skillsError == null -> {
                EmptyState(
                    title = "No skills loaded",
                    body = "The runtime returned an empty skills list.",
                    modifier = Modifier.padding(16.dp),
                )
            }
            !state.skillsLoading && filtered.isEmpty() -> {
                EmptyState(
                    title = "No matches",
                    body = "No skills match “${state.skillsQuery}”.",
                    modifier = Modifier.padding(16.dp),
                )
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 420.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(filtered, key = { it.name }) { skill ->
                        SkillCard(skill)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkillCard(skill: SkillSummary) {
    CloverCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                skill.name.ifBlank { "(unnamed)" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            skill.state?.takeIf { it.isNotBlank() }?.let { MetaChip(it) }
        }
        val blurb = skill.summary?.takeIf { it.isNotBlank() } ?: skill.description
        if (!blurb.isNullOrBlank()) {
            Text(blurb, color = CloverText2, style = MaterialTheme.typography.bodyMedium)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            skill.version?.takeIf { it.isNotBlank() }?.let { MetaChip("v$it") }
            skill.author?.takeIf { it.isNotBlank() }?.let { MetaChip(it) }
            val toolCount = skill.toolCount ?: skill.tools.size
            MetaChip("$toolCount tools")
            if (skill.managed == true) MetaChip("managed")
            if (skill.available == false) MetaChip("unavailable")
            skill.aliases.take(4).forEach { MetaChip(it) }
        }
        if (skill.tools.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Tools", style = MaterialTheme.typography.labelSmall, color = CloverText3)
                skill.tools.take(8).forEach { tool ->
                    Text(
                        buildString {
                            append(tool.fullName ?: tool.name)
                            if (tool.enabled == false) append(" · off")
                            if (tool.registered == false) append(" · missing")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (skill.tools.size > 8) {
                    Text("+${skill.tools.size - 8} more", color = CloverText3, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (skill.unhealthyTools.isNotEmpty()) {
            Text(
                "Unhealthy: ${skill.unhealthyTools.joinToString()}",
                color = CloverError,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (!skill.error.isNullOrBlank()) {
            Text(skill.error, color = CloverError, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
