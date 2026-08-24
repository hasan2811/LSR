package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme

/**
 * Standard Action Screen layout adhering to strict spatial UX, 8dp grid,
 * thumb-zone one-handed navigation, and WCAG accessibility standards.
 *
 * Layout Structure:
 * 1. Top Section (Read-only / Context): Status bar padding, navigational back affordance,
 *    and clear semantic Title + Subtitle hierarchy.
 * 2. Middle Section (Scrollable Thumb Zone): LazyColumn with strict 16dp outer margins,
 *    Arrangement.spacedBy(16.dp), 24dp/32dp section gaps, and 16dp inner card padding.
 * 3. Bottom Sticky Action Bar: Fixed elevated container with minimum 48dp touch targets,
 *    safe window insets handling for gesture navigation bars, and thumb-friendly reachability.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandardActionScreen(
    onNavigateBack: () -> Unit = {},
    onPrimaryAction: () -> Unit = {},
    onSecondaryAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Local state variables for interactive elements inside cards
    var selectedPriority by rememberSaveable { mutableStateOf("High") }
    var notificationsEnabled by rememberSaveable { mutableStateOf(true) }
    var notesText by rememberSaveable { mutableStateOf("") }
    var isVerified by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            // =========================================================================
            // 2. THUMB ZONE: STICKY BOTTOM ACTION BAR (Strict 48dp+ interactive heights)
            // =========================================================================
            StickyBottomActionBar(
                primaryButtonText = "Submit Inspection",
                onPrimaryClick = onPrimaryAction,
                secondaryButtonText = "Save Draft",
                onSecondaryClick = onSecondaryAction
            )
        }
    ) { innerPadding ->
        // =========================================================================
        // SCROLLABLE CONTENT (16dp outer padding, strict 8dp multiples, content grouping)
        // =========================================================================
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("standard_action_screen_scroll"),
            contentPadding = PaddingValues(
                start = 16.dp, // Strict 16dp outer margin
                end = 16.dp,
                top = 16.dp,
                bottom = 24.dp // 24dp breathing room before sticky action bar
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp) // Consistent 16dp inter-card spacing
        ) {
            // --- SECTION 1: HEADER & HEADLINE (Read-only, placed at top) ---
            item(key = "header_section") {
                ScreenHeader(
                    title = "Site Safety Audit",
                    subtitle = "Verify control barriers and environmental readiness before work authorization.",
                    onBackClick = onNavigateBack
                )
            }

            // --- SECTION DIVIDER / BREATHING GAP (24dp section separation) ---
            item(key = "section_gap_1") {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // --- SECTION 2: STATUS OVERVIEW CARD (Grouped Surface with 16dp inner padding) ---
            item(key = "status_card") {
                StatusOverviewCard(
                    statusLabel = "Pending Review",
                    idLabel = "AUDIT-2026-9042",
                    isVerified = isVerified,
                    onToggleVerification = { isVerified = !isVerified }
                )
            }

            // --- SECTION 3: PRIORITY SELECTOR CARD (Finger-first minimum 48dp touch targets) ---
            item(key = "priority_card") {
                PrioritySelectionCard(
                    selectedPriority = selectedPriority,
                    onPrioritySelected = { selectedPriority = it }
                )
            }

            // --- SECTION 4: FORM INPUT CARD (Accessible Text Field + Helper text) ---
            item(key = "notes_input_card") {
                InputDetailsCard(
                    notes = notesText,
                    onNotesChange = { notesText = it }
                )
            }

            // --- SECTION 5: CONFIGURATION TOGGLE CARD (Merged semantics for screen readers) ---
            item(key = "preferences_card") {
                ConfigurationCard(
                    notificationsEnabled = notificationsEnabled,
                    onNotificationsChanged = { notificationsEnabled = it }
                )
            }

            // --- EXTRA SPACER TO PREVENT BOTTOM OVERFLOW ---
            item(key = "bottom_breathing_spacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// =========================================================================
// SUB-COMPONENTS: CLEAN ARCHITECTURE & ACCESSIBILITY-COMPLIANT PIECES
// =========================================================================

/**
 * Top Area: Contextual Headline and Navigation.
 * Clear typography hierarchy using MaterialTheme.typography scales.
 */
@Composable
private fun ScreenHeader(
    title: String,
    subtitle: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Back Button with minimum 48x48dp interactive touch target
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .testTag("screen_header_back_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Navigate back to dashboard",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // Semantic Heading for Screen Readers
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() }
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )
    }
}

/**
 * Status Overview Card: Surface with 16dp inner padding, clear border, and tokenized colors.
 */
@Composable
private fun StatusOverviewCard(
    statusLabel: String,
    idLabel: String,
    isVerified: Boolean,
    onToggleVerification: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp), // Strict 16dp inner breathing room
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "REFERENCE NUMBER",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = idLabel,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Interactive Verification Row with merged A11y semantics & 48dp minimum height
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        role = Role.Checkbox,
                        onClick = onToggleVerification
                    )
                    .background(
                        if (isVerified) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (isVerified) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
                    contentDescription = null, // decorative within merged row
                    tint = if (isVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )

                Text(
                    text = if (isVerified) "Prerequisites marked as verified" else "Tap to mark prerequisites as verified",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Priority Selection Card: Demonstrates 48dp x 48dp clickable touch targets
 * with minimum 8dp-12dp spacing between adjacent interactive options.
 */
@Composable
private fun PrioritySelectionCard(
    selectedPriority: String,
    onPrioritySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val priorities = listOf("Low", "Medium", "High", "Critical")

    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp), // Strict 16dp inner padding
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Severity / Priority Level",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Grid of options: Each item has min 48dp height and 8dp spacing between items
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp) // 8dp gap between buttons
            ) {
                priorities.forEach { priority ->
                    val isSelected = selectedPriority == priority

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp) // Minimum 48dp touch target
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(
                                selected = isSelected,
                                onClick = { onPrioritySelected(priority) },
                                role = Role.RadioButton
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$priority Priority",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )

                            RadioButton(
                                selected = isSelected,
                                onClick = null, // handled by parent Surface selectable modifier
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Form Input Card: OutlinedTextField with explicit labels, helper hints, and proper padding.
 */
@Composable
private fun InputDetailsCard(
    notes: String,
    onNotesChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Field Notes & Observations",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = notes,
                onValueChange = onNotesChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 112.dp)
                    .testTag("field_notes_text_field"),
                placeholder = {
                    Text(
                        text = "Enter any specific hazards or corrective actions observed...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                supportingText = {
                    Text(
                        text = "Max 500 characters. Visible to all safety inspectors.",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }
    }
}

/**
 * Configuration Card: Uses Compose semantics to merge the toggle label and switch
 * so screen readers announce it cleanly as a single unit.
 */
@Composable
private fun ConfigurationCard(
    notificationsEnabled: Boolean,
    onNotificationsChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        // Toggleable Row with merged semantics and minimum 48dp touch target
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clip(RoundedCornerShape(16.dp))
                .toggleable(
                    value = notificationsEnabled,
                    onValueChange = onNotificationsChanged,
                    role = Role.Switch
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = null, // decorative within toggleable row
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Real-time Escalation Alerts",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Notify supervisor if critical findings are logged",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = notificationsEnabled,
                onCheckedChange = null, // Handled by parent toggleable modifier
                modifier = Modifier.clearAndSetSemantics { }
            )
        }
    }
}

/**
 * Sticky Bottom Action Bar:
 * Pinned at the very bottom (Thumb Zone), handles navigation window insets,
 * and maintains strict 48dp+ tap targets with proper button hierarchy.
 */
@Composable
fun StickyBottomActionBar(
    primaryButtonText: String,
    onPrimaryClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryButtonText: String? = null,
    onSecondaryClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
        tonalElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars) // Respect system gesture bar
                .padding(horizontal = 16.dp, vertical = 12.dp), // 16dp outer margin, 12dp vertical padding
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp), // 12dp spacing between adjacent actions
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Secondary Action (if provided)
                if (secondaryButtonText != null && onSecondaryClick != null) {
                    FilledTonalButton(
                        onClick = onSecondaryClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp) // Exceeds 48dp minimum for fat-finger safety
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("sticky_secondary_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(
                            text = secondaryButtonText,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Primary Action: High-contrast, easily reachable within thumb zone
                Button(
                    onClick = onPrimaryClick,
                    modifier = Modifier
                        .weight(if (secondaryButtonText != null) 1.5f else 1f)
                        .height(52.dp) // Exceeds 48dp minimum
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("sticky_primary_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = primaryButtonText,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null, // decorative companion
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// PREVIEW
// =========================================================================
@Preview(name = "Light Mode", showBackground = true)
@Composable
private fun StandardActionScreenLightPreview() {
    MyApplicationTheme(darkTheme = false) {
        StandardActionScreen()
    }
}

@Preview(name = "Dark Mode", showBackground = true)
@Composable
private fun StandardActionScreenDarkPreview() {
    MyApplicationTheme(darkTheme = true) {
        StandardActionScreen()
    }
}
