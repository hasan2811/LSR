package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.*
import com.example.ui.theme.*
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindingDetailScreen(
    language: AppLanguage,
    finding: FindingItem,
    teamRoster: List<TeamRosterMember>,
    actionLogsFlow: Flow<List<ActionHistoryLog>>,
    onBack: () -> Unit,
    onAssignPic: (picName: String, picEmail: String, picRole: String) -> Unit,
    onSubmitAction: (correctiveAction: String, evidenceNotes: String) -> Unit,
    onVerifyAndClose: (rating: Int, comment: String) -> Unit,
    onReopen: (reason: String) -> Unit,
    onAttachEvidencePhoto: (Uri) -> Unit = {},
    onSendMessage: (String) -> Unit = {},
    onSendReminder: () -> Unit = {},
    onDeleteFinding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val actionLogs by actionLogsFlow.collectAsState(initial = emptyList())

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onAttachEvidencePhoto(uri)
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            onAttachEvidencePhoto(uri)
        }
    }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    val formattedDate = remember(finding.timestamp) { dateFormat.format(Date(finding.timestamp)) }

    // Dialog state for Reassigning PIC
    var showAssignPicDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Form states
    var correctiveActionInput by remember(finding.correctiveAction) { mutableStateOf(finding.correctiveAction) }
    var evidenceNotesInput by remember(finding.actionEvidenceNotes) { mutableStateOf(finding.actionEvidenceNotes) }

    // Verification states
    var selectedSatisfactionRating by remember(finding.satisfactionRating) {
        mutableIntStateOf(if (finding.satisfactionRating > 0) finding.satisfactionRating else 5)
    }
    var auditorCommentInput by remember(finding.inspectorComment) { mutableStateOf(finding.inspectorComment) }
    var reopenReasonInput by remember(finding.reopenReason) { mutableStateOf(finding.reopenReason) }
    var isRejectMode by remember { mutableStateOf(false) }

    val statusNormalized = finding.status.uppercase()
    val statusColor = when (statusNormalized) {
        "CLOSED" -> SafetyTeal
        "ACTION_SUBMITTED", "IN_REVIEW", "IN_PROGRESS" -> AmberGold
        "REOPENED" -> CriticalOrange
        else -> DangerRed
    }

    val statusLabel = when (statusNormalized) {
        "CLOSED" -> AppStrings.get("status_closed", language)
        "ACTION_SUBMITTED", "IN_REVIEW", "IN_PROGRESS" -> AppStrings.get("status_in_review", language)
        "REOPENED" -> AppStrings.get("status_reopened", language)
        else -> AppStrings.get("status_open", language)
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("finding_detail_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = "Finding #${finding.ruleId}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = finding.area,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Share finding details
                        IconButton(
                            onClick = {
                                val sb = StringBuilder()
                                sb.append("🚨 *SAFETY FINDING ACTION REPORT*\n")
                                sb.append("ID Aturan: ${finding.ruleId} | Kategori: ${finding.category}\n")
                                sb.append("Status: $statusLabel\n")
                                sb.append("Lokasi: ${finding.area}\n")
                                sb.append("Inspektur: ${finding.inspectorName}\n")
                                sb.append("PIC: ${finding.assignedPicName} (${finding.assignedPicRole})\n\n")
                                sb.append("Ketidakpatuhan:\n\"${finding.itemText}\"\n")
                                if (finding.notes.isNotBlank()) sb.append("Catatan: ${finding.notes}\n\n")
                                if (finding.correctiveAction.isNotBlank()) {
                                    sb.append("Tindakan Koreksi PIC:\n${finding.correctiveAction}\n")
                                    if (finding.actionEvidenceNotes.isNotBlank()) {
                                        sb.append("Bukti: ${finding.actionEvidenceNotes}\n")
                                    }
                                }
                                if (finding.satisfactionRating > 0) {
                                    sb.append("Rating Kepuasan Auditor: ${finding.satisfactionRating}/5 ⭐\n")
                                    if (finding.inspectorComment.isNotBlank()) {
                                        sb.append("Ulasan Auditor: ${finding.inspectorComment}\n")
                                    }
                                }
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, sb.toString())
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Bagikan Rincian Temuan"))
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.primary)
                        }

                        // Delete option
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = DangerRed)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Status Hero Banner
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.12f)),
                border = BorderStroke(1.5.dp, statusColor.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = statusColor,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = when (statusNormalized) {
                                        "CLOSED" -> Icons.Default.CheckCircle
                                        "ACTION_SUBMITTED", "IN_REVIEW", "IN_PROGRESS" -> Icons.Default.PendingActions
                                        "REOPENED" -> Icons.Default.Replay
                                        else -> Icons.Default.WarningAmber
                                    },
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = statusLabel,
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                color = statusColor
                            )
                            Text(
                                text = "Dicatat pada $formattedDate",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!finding.status.equals("CLOSED", ignoreCase = true)) {
                                val slaLabel = if (finding.isOverdue()) {
                                    "⚠️ ${AppStrings.get("sla_overdue", language)}"
                                } else {
                                    "⏱️ ${AppStrings.get("sla_remaining", language)}: ${finding.slaRemainingHours().coerceAtLeast(0L)}h"
                                }
                                Text(
                                    text = slaLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (finding.isOverdue()) DangerRed else AmberGold
                                )
                            }
                        }
                    }

                    if (finding.satisfactionRating > 0) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AmberGold.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, AmberGold)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = AmberGold, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "${finding.satisfactionRating}/5",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    color = AmberGold
                                )
                            }
                        }
                    }
                }
            }

            // 1. Non-Compliance Overview Card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = finding.ruleId,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        if (finding.isCritical) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = CriticalOrange.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, CriticalOrange.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = CriticalOrange, modifier = Modifier.size(13.dp))
                                    Text("CRITICAL LSR", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = CriticalOrange)
                                }
                            }
                        }
                    }

                    Text(
                        text = finding.category,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = finding.itemText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp
                    )

                    if (finding.notes.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Catatan Inspektur Lapangan:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = finding.notes,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Auditor: ${finding.inspectorName.ifBlank { "HSE Inspector" }}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Standar: ${finding.standardRef}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 2. Assigned PIC Card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.PersonOutline, contentDescription = null, tint = SafetyBlue, modifier = Modifier.size(18.dp))
                            Text(
                                text = AppStrings.get("pic_assignment_title", language),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        TextButton(
                            onClick = { showAssignPicDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ganti PIC", fontSize = 12.sp)
                        }
                    }

                    val picAssigned = finding.assignedPicName.isNotBlank() && finding.assignedPicName != "Unassigned / HSE Field Team"
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (picAssigned) SafetyBlue.copy(alpha = 0.08f) else DangerRed.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, if (picAssigned) SafetyBlue.copy(alpha = 0.3f) else DangerRed.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (picAssigned) SafetyBlue else DangerRed,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (picAssigned) finding.assignedPicName.take(2).uppercase() else "??",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (picAssigned) finding.assignedPicName else "Belum Ditugaskan PIC Khusus",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (picAssigned) finding.assignedPicRole else "Klik 'Ganti PIC' untuk memilih penanggung jawab",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (picAssigned && finding.assignedPicEmail.isNotBlank()) {
                                    Text(
                                        text = finding.assignedPicEmail,
                                        fontSize = 11.sp,
                                        color = SafetyBlue
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Corrective Action Form (PIC Submission Area)
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.BuildCircle, contentDescription = null, tint = AmberGold, modifier = Modifier.size(20.dp))
                        Text(
                            text = AppStrings.get("action_submission_title", language),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = AppStrings.get("action_submission_desc", language),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = correctiveActionInput,
                        onValueChange = { correctiveActionInput = it },
                        label = { Text(AppStrings.get("corrective_action_label", language)) },
                        placeholder = { Text(AppStrings.get("corrective_action_placeholder", language)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_corrective_action"),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 3,
                        maxLines = 6
                    )

                    OutlinedTextField(
                        value = evidenceNotesInput,
                        onValueChange = { evidenceNotesInput = it },
                        label = { Text(AppStrings.get("action_evidence_label", language)) },
                        placeholder = { Text(AppStrings.get("action_evidence_placeholder", language)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_evidence_notes"),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 2,
                        maxLines = 4
                    )

                    // Compressed Field Evidence Photo Attachment
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = SafetyBlue, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = AppStrings.get("photo_evidence_title", language),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (finding.evidencePhotoUrl.isNotBlank() || finding.evidencePhotoUri.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (finding.evidencePhotoUrl.isNotBlank()) SafetyTeal.copy(alpha = 0.15f) else AmberGold.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = if (finding.evidencePhotoUrl.isNotBlank()) "Cloud Storage" else "Lokal (Antre)",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (finding.evidencePhotoUrl.isNotBlank()) SafetyTeal else AmberGold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            val photoSource = finding.evidencePhotoUrl.ifBlank { finding.evidencePhotoUri }
                            if (photoSource.isNotBlank()) {
                                val modelData: Any = if (photoSource.startsWith("http")) photoSource else File(photoSource)
                                AsyncImage(
                                    model = modelData,
                                    contentDescription = "Evidence Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        galleryLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(AppStrings.get("photo_pick_gallery", language), fontSize = 11.sp, maxLines = 1)
                                }
                                FilledTonalButton(
                                    onClick = {
                                        val dir = File(context.cacheDir, "camera_temp").apply { mkdirs() }
                                        val tmp = File(dir, "capture_${finding.id}_${System.currentTimeMillis()}.jpg")
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tmp)
                                        pendingCameraUri = uri
                                        cameraLauncher.launch(uri)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(AppStrings.get("photo_take_camera", language), fontSize = 11.sp, maxLines = 1)
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            onSubmitAction(correctiveActionInput, evidenceNotesInput)
                        },
                        enabled = correctiveActionInput.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_submit_pic_action"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberGold)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = AppStrings.get("submit_action_btn", language),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            color = Color.Black
                        )
                    }
                }
            }

            // 4. Auditor Verification & Satisfaction Rating Section
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = SafetyTeal, modifier = Modifier.size(20.dp))
                        Text(
                            text = AppStrings.get("auditor_verification_title", language),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = AppStrings.get("auditor_verification_desc", language),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Toggle: Satisfied (Close & Rate) vs Rejected (Reopen)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !isRejectMode,
                            onClick = { isRejectMode = false },
                            label = { Text(AppStrings.get("verification_satisfied", language), fontWeight = FontWeight.Bold) },
                            leadingIcon = {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SafetyTeal)
                            },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SafetyTeal.copy(alpha = 0.15f)),
                            modifier = Modifier.weight(1f)
                        )

                        FilterChip(
                            selected = isRejectMode,
                            onClick = { isRejectMode = true },
                            label = { Text(AppStrings.get("verification_reopen", language), fontWeight = FontWeight.Bold) },
                            leadingIcon = {
                                Icon(Icons.Default.Cancel, contentDescription = null, tint = CriticalOrange)
                            },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CriticalOrange.copy(alpha = 0.15f)),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (!isRejectMode) {
                        // 1 to 5 Star Rating
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = AppStrings.get("satisfaction_rating_label", language),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                (1..5).forEach { starIndex ->
                                    IconButton(
                                        onClick = { selectedSatisfactionRating = starIndex },
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (starIndex <= selectedSatisfactionRating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                            contentDescription = "Star $starIndex",
                                            tint = if (starIndex <= selectedSatisfactionRating) AmberGold else MaterialTheme.colorScheme.outlineVariant,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }

                            // Star label feedback
                            val ratingLabel = when (selectedSatisfactionRating) {
                                1 -> AppStrings.get("satisfaction_1", language)
                                2 -> AppStrings.get("satisfaction_2", language)
                                3 -> AppStrings.get("satisfaction_3", language)
                                4 -> AppStrings.get("satisfaction_4", language)
                                else -> AppStrings.get("satisfaction_5", language)
                            }
                            Text(
                                text = "Rating: $selectedSatisfactionRating ⭐ ($ratingLabel)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmberGold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = auditorCommentInput,
                                onValueChange = { auditorCommentInput = it },
                                label = { Text(AppStrings.get("auditor_comment_label", language)) },
                                placeholder = { Text(AppStrings.get("auditor_comment_placeholder", language)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_auditor_comment"),
                                shape = RoundedCornerShape(12.dp),
                                minLines = 2,
                                maxLines = 4
                            )

                            Button(
                                onClick = {
                                    onVerifyAndClose(selectedSatisfactionRating, auditorCommentInput)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("btn_verify_and_close"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SafetyTeal)
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = AppStrings.get("btn_verify_close", language),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        // Reopen Form
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = reopenReasonInput,
                                onValueChange = { reopenReasonInput = it },
                                label = { Text(AppStrings.get("reopen_reason_label", language)) },
                                placeholder = { Text(AppStrings.get("reopen_reason_placeholder", language)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_reopen_reason"),
                                shape = RoundedCornerShape(12.dp),
                                minLines = 3,
                                maxLines = 5
                            )

                            Button(
                                onClick = {
                                    onReopen(reopenReasonInput)
                                },
                                enabled = reopenReasonInput.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("btn_reopen_finding"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CriticalOrange)
                            ) {
                                Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = AppStrings.get("btn_reopen_finding", language),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            // 4.5 Direct Inter-User Communication & Reminder Ping
            var directMessageInput by remember { mutableStateOf("") }
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = DarkNavySurface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = AppStrings.get("direct_comm_title", language),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = AppStrings.get("direct_comm_desc", language),
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            if (!finding.status.equals("CLOSED", ignoreCase = true)) {
                                FilledTonalButton(
                                    onClick = onSendReminder,
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = AmberGold,
                                        contentColor = Color.Black
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = AppStrings.get("send_reminder_btn", language),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = directMessageInput,
                            onValueChange = { directMessageInput = it },
                            placeholder = {
                                Text(
                                    text = AppStrings.get("chat_placeholder", language),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            maxLines = 3
                        )
                        FilledIconButton(
                            onClick = {
                                if (directMessageInput.isNotBlank()) {
                                    onSendMessage(directMessageInput.trim())
                                    directMessageInput = ""
                                }
                            },
                            enabled = directMessageInput.isNotBlank(),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = SafetyBlue,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // 5. Audit Trail & Action History Logs (Chronological Timeline)
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Column {
                            Text(
                                text = AppStrings.get("audit_trail_title", language),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = AppStrings.get("audit_trail_subtitle", language),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (actionLogs.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Belum ada log aktivitas tambahan tercatat.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            actionLogs.forEachIndexed { index, log ->
                                ActionLogTimelineItem(
                                    log = log,
                                    dateFormat = dateFormat,
                                    isLast = index == actionLogs.lastIndex
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal: Assign PIC Dialog
    if (showAssignPicDialog) {
        AssignPicModalDialog(
            language = language,
            teamRoster = teamRoster,
            currentPicName = finding.assignedPicName,
            onDismiss = { showAssignPicDialog = false },
            onConfirm = { name, email, role ->
                onAssignPic(name, email, role)
                showAssignPicDialog = false
            }
        )
    }

    // Modal: Delete Confirm Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Hapus Temuan Ini?") },
            text = { Text("Temuan #${finding.ruleId} di area ${finding.area} akan dihapus secara permanen dari perangkat lokal.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteFinding()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Hapus Permanen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
fun ActionLogTimelineItem(
    log: ActionHistoryLog,
    dateFormat: SimpleDateFormat,
    isLast: Boolean
) {
    val dateStr = remember(log.timestamp) { dateFormat.format(Date(log.timestamp)) }
    val (badgeColor, iconVector, typeLabel) = when (log.actionType) {
        "CREATED_NON_COMPLIANT" -> Triple(DangerRed, Icons.Default.AddAlert, "Temuan Dibuat")
        "ASSIGNED_TO_PIC" -> Triple(SafetyBlue, Icons.Default.PersonAdd, "Penugasan PIC")
        "ACTION_SUBMITTED" -> Triple(AmberGold, Icons.Default.Build, "Tindakan Diajukan")
        "VERIFIED_AND_CLOSED" -> Triple(SafetyTeal, Icons.Default.CheckCircle, "Diverifikasi & Ditutup")
        "REOPENED_BY_INSPECTOR", "REOPENED" -> Triple(CriticalOrange, Icons.Default.Replay, "Dibuka Kembali")
        "REMINDER_PING" -> Triple(AmberGold, Icons.Default.NotificationsActive, "Pengingat Push")
        "COMMENT" -> Triple(SafetyBlue, Icons.Default.ChatBubbleOutline, "Pesan Langsung")
        else -> Triple(MaterialTheme.colorScheme.primary, Icons.Default.Info, log.actionType)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = badgeColor.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, badgeColor),
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(iconVector, contentDescription = null, tint = badgeColor, modifier = Modifier.size(14.dp))
                }
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .height(36.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = typeLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = badgeColor
                )
                Text(
                    text = dateStr,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "${log.actorName} (${log.actorRole.ifBlank { "User" }})",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (log.notes.isNotBlank()) {
                Text(
                    text = log.notes,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            if (log.rating > 0) {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    (1..log.rating).forEach {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = AmberGold, modifier = Modifier.size(13.dp))
                    }
                    Text(
                        text = " ${log.rating}/5 ⭐",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberGold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignPicModalDialog(
    language: AppLanguage,
    teamRoster: List<TeamRosterMember>,
    currentPicName: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, email: String, role: String) -> Unit
) {
    var selectedMember by remember { mutableStateOf(teamRoster.find { it.name == currentPicName }) }
    var customName by remember { mutableStateOf("") }
    var customRole by remember { mutableStateOf("") }
    var customEmail by remember { mutableStateOf("") }
    var isCustomMode by remember { mutableStateOf(false) }
    var searchPicQuery by remember { mutableStateOf("") }

    val filteredRoster = remember(teamRoster, searchPicQuery) {
        val clean = searchPicQuery.trim().lowercase()
        if (clean.isBlank()) teamRoster
        else teamRoster.filter {
            it.name.lowercase().contains(clean) ||
            it.role.lowercase().contains(clean) ||
            it.email.lowercase().contains(clean) ||
            it.department.lowercase().contains(clean)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = AppStrings.get("pic_assignment_title", language),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Text(
                    text = AppStrings.get("pic_assignment_desc", language),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isCustomMode,
                        onClick = { isCustomMode = false },
                        label = { Text("Roster Circle (${filteredRoster.size})") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = isCustomMode,
                        onClick = { isCustomMode = true },
                        label = { Text("Input Manual") },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (!isCustomMode) {
                    OutlinedTextField(
                        value = searchPicQuery,
                        onValueChange = { searchPicQuery = it },
                        placeholder = { Text("Cari nama, role, email PIC...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchPicQuery.isNotEmpty()) {
                                IconButton(onClick = { searchPicQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredRoster, key = { it.email.ifBlank { it.name } }) { member ->
                            val isSelected = selectedMember?.name == member.name || selectedMember?.email == member.email
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) SafetyBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = if (isSelected) BorderStroke(1.5.dp, SafetyBlue) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedMember = member }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSelected) SafetyBlue else MaterialTheme.colorScheme.outlineVariant,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = member.name.take(2).uppercase(),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = member.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${member.role} • ${member.department}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (member.email.isNotBlank()) {
                                            Text(
                                                text = member.email,
                                                fontSize = 10.sp,
                                                color = SafetyBlue
                                            )
                                        }
                                    }

                                    if (isSelected) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SafetyBlue, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = customName,
                            onValueChange = { customName = it },
                            label = { Text(AppStrings.get("pic_name_label", language)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = customRole,
                            onValueChange = { customRole = it },
                            label = { Text(AppStrings.get("pic_role_label", language)) },
                            placeholder = { Text("contoh: Supervisor Mekanikal") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = customEmail,
                            onValueChange = { customEmail = it },
                            label = { Text(AppStrings.get("pic_email_label", language)) },
                            placeholder = { Text("contoh: pic.field@perusahaan.com") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                    }
                }

                Button(
                    onClick = {
                        if (!isCustomMode && selectedMember != null) {
                            val m = selectedMember!!
                            onConfirm(m.name, m.email, m.role)
                        } else if (isCustomMode && customName.isNotBlank()) {
                            onConfirm(customName, customEmail, customRole.ifBlank { "PIC" })
                        }
                    },
                    enabled = (!isCustomMode && selectedMember != null) || (isCustomMode && customName.isNotBlank()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SafetyBlue)
                ) {
                    Text("Tetapkan PIC", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
