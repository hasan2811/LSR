package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ActivityFilterCriteria
import com.example.data.AppLanguage
import com.example.data.AppStrings
import com.example.data.DateRangeFilter
import com.example.ui.theme.DarkNavySurface
import com.example.ui.theme.SafetyBlue
import com.example.ui.theme.SafetyTeal

@Composable
fun SleekSearchFilterBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    activeFilterCount: Int,
    onOpenFilterSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                if (query.isNotBlank()) SafetyBlue.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            ),
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = if (query.isNotBlank()) SafetyBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp)
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(SafetyBlue),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .clickable { onQueryChange("") }
                    )
                }
            }
        }

        // High-Contrast Obsidian/Navy Filter Settings Button
        BadgedBox(
            badge = {
                if (activeFilterCount > 0) {
                    Badge(containerColor = SafetyTeal, contentColor = Color.White) {
                        Text(
                            text = activeFilterCount.toString(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        ) {
            Surface(
                onClick = onOpenFilterSheet,
                shape = RoundedCornerShape(16.dp),
                color = if (activeFilterCount > 0) DarkNavySurface else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (activeFilterCount > 0) DarkNavySurface else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                ),
                shadowElevation = 2.dp,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = AppStrings.get("filter_sheet_title", AppLanguage.ID),
                        tint = if (activeFilterCount > 0) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveFilterSummaryStrip(
    language: AppLanguage,
    criteria: ActivityFilterCriteria,
    onUpdate: (ActivityFilterCriteria) -> Unit,
    onResetAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (criteria.activeCount == 0) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (criteria.dateRange != DateRangeFilter.ALL) {
            val dateLabel = when (criteria.dateRange) {
                DateRangeFilter.TODAY -> AppStrings.get("filter_date_today", language)
                DateRangeFilter.LAST_7_DAYS -> AppStrings.get("filter_date_7d", language)
                DateRangeFilter.LAST_30_DAYS -> AppStrings.get("filter_date_30d", language)
                else -> ""
            }
            ActivePill(label = "📅 $dateLabel") {
                onUpdate(criteria.copy(dateRange = DateRangeFilter.ALL))
            }
        }
        if (criteria.userName.isNotBlank()) {
            ActivePill(label = "👤 ${criteria.userName}") {
                onUpdate(criteria.copy(userName = ""))
            }
        }
        if (criteria.lsrType.isNotBlank()) {
            ActivePill(label = "🛡️ ${criteria.lsrType}") {
                onUpdate(criteria.copy(lsrType = ""))
            }
        }
        if (criteria.company.isNotBlank()) {
            ActivePill(label = "🏢 ${criteria.company}") {
                onUpdate(criteria.copy(company = ""))
            }
        }
        if (criteria.location.isNotBlank()) {
            ActivePill(label = "📍 ${criteria.location}") {
                onUpdate(criteria.copy(location = ""))
            }
        }
        TextButton(
            onClick = onResetAll,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = AppStrings.get("filter_reset_btn", language),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = SafetyBlue
            )
        }
    }
}

@Composable
private fun ActivePill(label: String, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = DarkNavySurface,
        modifier = Modifier.clickable { onRemove() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ActivityFilterBottomSheet(
    language: AppLanguage,
    currentCriteria: ActivityFilterCriteria,
    availableUsers: List<String>,
    availableLsrTypes: List<String>,
    availableCompanies: List<String>,
    availableLocations: List<String>,
    onApply: (ActivityFilterCriteria) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(currentCriteria) { mutableStateOf(currentCriteria) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DarkNavySurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = AppStrings.get("filter_sheet_title", language),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = AppStrings.get("filter_sheet_subtitle", language),
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = SafetyTeal.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "${draft.activeCount}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = SafetyTeal,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Date Range
                FilterSectionBlock(title = AppStrings.get("filter_date_label", language), icon = Icons.Default.DateRange) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val dateOptions = listOf(
                            DateRangeFilter.ALL to AppStrings.get("filter_date_all", language),
                            DateRangeFilter.TODAY to AppStrings.get("filter_date_today", language),
                            DateRangeFilter.LAST_7_DAYS to AppStrings.get("filter_date_7d", language),
                            DateRangeFilter.LAST_30_DAYS to AppStrings.get("filter_date_30d", language)
                        )
                        dateOptions.forEach { (mode, label) ->
                            SelectableDarkPill(
                                label = label,
                                selected = draft.dateRange == mode,
                                onClick = { draft = draft.copy(dateRange = mode) }
                            )
                        }
                    }
                }

                // 2. User / Inspector / PIC
                if (availableUsers.isNotEmpty()) {
                    FilterSectionBlock(title = AppStrings.get("filter_user_label", language), icon = Icons.Default.Person) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SelectableDarkPill(
                                label = AppStrings.get("filter_all", language),
                                selected = draft.userName.isBlank(),
                                onClick = { draft = draft.copy(userName = "") }
                            )
                            availableUsers.forEach { u ->
                                SelectableDarkPill(
                                    label = u,
                                    selected = draft.userName.equals(u, ignoreCase = true),
                                    onClick = {
                                        draft = draft.copy(userName = if (draft.userName.equals(u, ignoreCase = true)) "" else u)
                                    }
                                )
                            }
                        }
                    }
                }

                // 3. LSR Rule Type
                if (availableLsrTypes.isNotEmpty()) {
                    FilterSectionBlock(title = AppStrings.get("filter_lsr_label", language), icon = Icons.Default.Security) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SelectableDarkPill(
                                label = AppStrings.get("filter_all", language),
                                selected = draft.lsrType.isBlank(),
                                onClick = { draft = draft.copy(lsrType = "") }
                            )
                            availableLsrTypes.forEach { lsr ->
                                SelectableDarkPill(
                                    label = lsr,
                                    selected = draft.lsrType.equals(lsr, ignoreCase = true),
                                    onClick = {
                                        draft = draft.copy(lsrType = if (draft.lsrType.equals(lsr, ignoreCase = true)) "" else lsr)
                                    }
                                )
                            }
                        }
                    }
                }

                // 4. Company
                if (availableCompanies.isNotEmpty()) {
                    FilterSectionBlock(title = AppStrings.get("filter_company_label", language), icon = Icons.Default.Business) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SelectableDarkPill(
                                label = AppStrings.get("filter_all", language),
                                selected = draft.company.isBlank(),
                                onClick = { draft = draft.copy(company = "") }
                            )
                            availableCompanies.forEach { comp ->
                                SelectableDarkPill(
                                    label = comp,
                                    selected = draft.company.equals(comp, ignoreCase = true),
                                    onClick = {
                                        draft = draft.copy(company = if (draft.company.equals(comp, ignoreCase = true)) "" else comp)
                                    }
                                )
                            }
                        }
                    }
                }

                // 5. Location
                if (availableLocations.isNotEmpty()) {
                    FilterSectionBlock(title = AppStrings.get("filter_location_label", language), icon = Icons.Default.LocationOn) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SelectableDarkPill(
                                label = AppStrings.get("filter_all", language),
                                selected = draft.location.isBlank(),
                                onClick = { draft = draft.copy(location = "") }
                            )
                            availableLocations.forEach { loc ->
                                SelectableDarkPill(
                                    label = loc,
                                    selected = draft.location.equals(loc, ignoreCase = true),
                                    onClick = {
                                        draft = draft.copy(location = if (draft.location.equals(loc, ignoreCase = true)) "" else loc)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { draft = ActivityFilterCriteria() },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = AppStrings.get("filter_reset_btn", language),
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(
                    onClick = {
                        onApply(draft)
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1.4f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkNavySurface)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = AppStrings.get("filter_apply_btn", language),
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterSectionBlock(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = SafetyBlue, modifier = Modifier.size(16.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        content()
    }
}

@Composable
private fun SelectableDarkPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) DarkNavySurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            1.dp,
            if (selected) DarkNavySurface else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}
