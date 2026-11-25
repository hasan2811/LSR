package com.example

import com.example.data.ActivityFilterCriteria
import com.example.data.AppLanguage
import com.example.data.AppStrings
import com.example.data.AppThemeMode
import com.example.data.CacheEvictionWorker
import com.example.data.DashboardPerspective
import com.example.data.DateRangeFilter
import com.example.data.FindingItem
import com.example.data.GoogleAppsScriptSnippet
import com.example.data.LsrRepository
import com.example.data.ReportExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScaleFeaturesUnitTest {

    @Test
    fun findingSla_criticalRuleUses24Hours() {
        val createdAt = 1_700_000_000_000L
        val finding = FindingItem(
            id = 1,
            ruleId = "WAH-01",
            category = "Working at Height",
            itemText = "Harness not anchored",
            standardRef = "IOGP 459",
            area = "Platform 04",
            inspectorName = "Rully",
            isCritical = true,
            status = "OPEN",
            timestamp = createdAt
        )

        val twentyHoursLater = createdAt + 20L * 3_600_000L
        assertFalse(finding.isOverdue(twentyHoursLater))
        assertEquals(4L, finding.slaRemainingHours(twentyHoursLater))

        val twentyFiveHoursLater = createdAt + 25L * 3_600_000L
        assertTrue(finding.isOverdue(twentyFiveHoursLater))
        assertEquals(-1L, finding.slaRemainingHours(twentyFiveHoursLater))
    }

    @Test
    fun findingSla_highRiskUses72HoursAndClosedNeverOverdue() {
        val createdAt = 1_700_000_000_000L
        val openHigh = FindingItem(
            id = 2,
            ruleId = "LOTO-02",
            category = "Energy Isolation",
            itemText = "Tag missing",
            standardRef = "OSHA 1910.147",
            area = "Workshop B",
            inspectorName = "Rully",
            isCritical = false,
            status = "OPEN",
            timestamp = createdAt
        )

        val fortyEightHoursLater = createdAt + 48L * 3_600_000L
        assertFalse(openHigh.isOverdue(fortyEightHoursLater))
        assertEquals(24L, openHigh.slaRemainingHours(fortyEightHoursLater))

        val eightyHoursLater = createdAt + 80L * 3_600_000L
        assertTrue(openHigh.isOverdue(eightyHoursLater))

        val closedFinding = openHigh.copy(status = "CLOSED")
        assertFalse(closedFinding.isOverdue(eightyHoursLater))
    }

    @Test
    fun reportExporter_csvEscapeQuotesAndFlattensNewlines() {
        val escaped = ReportExporter.csvEscape("Line 1\n\"Quoted\" Value, Area A")
        assertEquals("\"Line 1 \"\"Quoted\"\" Value, Area A\"", escaped)
    }

    @Test
    fun reportExporter_csvEscapeNeutralizesFormulaInjection() {
        assertEquals("\"'=cmd|'/C calc'!A0\"", ReportExporter.csvEscape("=cmd|'/C calc'!A0"))
        assertEquals("\"'+SUM(A1:A2)\"", ReportExporter.csvEscape("+SUM(A1:A2)"))
        assertEquals("\"'-1+1\"", ReportExporter.csvEscape("-1+1"))
        assertEquals("\"'@SUM(1+1)\"", ReportExporter.csvEscape("@SUM(1+1)"))
        assertEquals("\"'\t=1\"", ReportExporter.csvEscape("\t=1"))
    }

    @Test
    fun complianceScore_excludesNaAndMatchesSurveyViewFormula() {
        assertEquals(80, LsrRepository.calculateComplianceScore(yesCount = 8, noCount = 2))
        assertEquals(100, LsrRepository.calculateComplianceScore(yesCount = 0, noCount = 0))
        assertEquals(0, LsrRepository.calculateComplianceScore(yesCount = 0, noCount = 5))
    }

    @Test
    fun rbac_segregationOfDutiesPreventsSelfPicAndContractorApproval() {
        val finding = FindingItem(
            id = 10,
            ruleId = "WAH-01",
            category = "Working at Height",
            itemText = "Harness missing",
            standardRef = "IOGP 459",
            area = "WHP-01",
            inspectorName = "Budi",
            assignedPicName = "Ahmad",
            assignedPicEmail = "ahmad@contractor.co.id",
            isCritical = true,
            status = "ACTION_SUBMITTED",
            timestamp = 1_700_000_000_000L
        )

        assertTrue(
            com.example.ui.LsrViewModel.canManageOrVerifyFinding(
                userEmail = "budi@nusantara-epc.co.id",
                userRole = "Lead HSE Inspector",
                finding = finding
            )
        )
        assertFalse(
            com.example.ui.LsrViewModel.canManageOrVerifyFinding(
                userEmail = "AHMAD@contractor.co.id",
                userRole = "Lead HSE Inspector",
                finding = finding
            )
        )
        assertFalse(
            com.example.ui.LsrViewModel.canManageOrVerifyFinding(
                userEmail = "other@contractor.co.id",
                userRole = "Contractor Field PIC",
                finding = finding
            )
        )
    }

    @Test
    fun localization_scaleKeysResolveInBothLanguages() {
        val keys = listOf(
            "feed_search_placeholder",
            "feed_load_more",
            "feed_pending_sync",
            "feed_sync_now",
            "feed_clear_read",
            "feed_today",
            "feed_this_week",
            "feed_older",
            "export_pdf_btn",
            "export_csv_btn",
            "sla_overdue",
            "sla_remaining",
            "photo_evidence_title",
            "photo_pick_gallery",
            "photo_take_camera",
            "perspective_label",
            "no_records_title",
            "no_records_desc",
            "filter_sheet_title",
            "filter_sheet_subtitle",
            "filter_date_label",
            "filter_date_all",
            "filter_date_today",
            "filter_date_7d",
            "filter_date_30d",
            "filter_user_label",
            "filter_lsr_label",
            "filter_company_label",
            "filter_location_label",
            "filter_apply_btn",
            "filter_reset_btn",
            "direct_comm_title",
            "direct_comm_desc",
            "send_reminder_btn",
            "chat_placeholder",
            "theme_mode_title",
            "theme_system",
            "theme_light",
            "theme_dark",
            "sync_state_syncing",
            "sync_state_instant",
            "sync_pull_gas_btn",
            "sync_last_at",
            "paging_loading_more",
            "paging_retry_btn",
            "paging_end_findings",
            "paging_end_feed",
            "paging_end_notifications",
            "paging_error_append"
        )
        keys.forEach { key ->
            val idVal = AppStrings.get(key, AppLanguage.ID)
            val enVal = AppStrings.get(key, AppLanguage.EN)
            assertNotEquals("Missing ID string for $key", key, idVal)
            assertNotEquals("Missing EN string for $key", key, enVal)
            assertTrue("Empty ID string for $key", idVal.isNotBlank())
            assertTrue("Empty EN string for $key", enVal.isNotBlank())
        }
    }

    @Test
    fun dashboardPerspective_enumContainsAllFourPerspectives() {
        assertEquals(4, DashboardPerspective.entries.size)
    }

    @Test
    fun appThemeMode_containsSystemLightDark() {
        assertEquals(
            listOf(AppThemeMode.SYSTEM, AppThemeMode.LIGHT, AppThemeMode.DARK),
            AppThemeMode.entries
        )
    }

    @Test
    fun googleAppsScriptSnippet_containsBiDirectionalDoGetAndDoPost() {
        assertTrue(GoogleAppsScriptSnippet.DEFAULT_WEBHOOK_URL.endsWith("/exec"))
        assertTrue(GoogleAppsScriptSnippet.CODE_GS.contains("function doGet(e)"))
        assertTrue(GoogleAppsScriptSnippet.CODE_GS.contains("function doPost(e)"))
        assertTrue(GoogleAppsScriptSnippet.CODE_GS.contains("supportsPull: true"))
        assertTrue(GoogleAppsScriptSnippet.CODE_GS.contains("update_finding"))
        assertTrue(GoogleAppsScriptSnippet.CODE_GS.contains("LockService.getScriptLock()"))
        assertTrue(GoogleAppsScriptSnippet.CODE_GS.contains("SpreadsheetApp.flush()"))
        assertTrue(GoogleAppsScriptSnippet.CODE_GS.contains("sanitizeSheetText"))
        assertTrue(GoogleAppsScriptSnippet.CODE_GS.contains("getHeaderMapFromRow"))
        assertTrue(GoogleAppsScriptSnippet.CODE_GS.contains("isSamePersonServer"))
        assertTrue(GoogleAppsScriptSnippet.CODE_GS.contains("var SHEET_USERS = \"user\""))
        assertTrue(GoogleAppsScriptSnippet.CODE_GS.contains("function setupSheets()"))
        assertTrue(GoogleAppsScriptSnippet.CODE_GS.contains("register_user"))
        assertTrue(GoogleAppsScriptSnippet.CODE_GS.contains("login_user"))
        assertTrue(GoogleAppsScriptSnippet.CODE_GS.contains("hashPasswordServer"))
        assertTrue(GoogleAppsScriptSnippet.CODE_GS.contains("validateRequestToken"))
        assertTrue(GoogleAppsScriptSnippet.CODE_GS.contains("APP_API_TOKEN"))
        assertFalse(GoogleAppsScriptSnippet.CODE_GS.contains("setCellByHeader(sheet, headerMap, r, \"password\""))
        assertTrue(GoogleAppsScriptSnippet.CODE_GS.contains("version: \"3.2\""))
    }

    @Test
    fun mergeDirectoryUsers_prioritizesGasSheetFieldsAndKeepsFirestoreProjects() {
        val fsUser = com.example.data.FirestoreUser(
            uid = "fb_123",
            displayName = "Budi Lama",
            email = "budi@zakum.com",
            company = "Unassigned",
            position = "Not Set",
            projectIds = listOf("proj-a")
        )
        val gasUser = com.example.data.FirestoreUser(
            uid = "gas_999",
            displayName = "Budi Santoso",
            email = "BUDI@zakum.com",
            company = "PT Zakum",
            position = "HSE Supervisor"
        )
        val extraGas = com.example.data.FirestoreUser(
            uid = "gas_111",
            displayName = "Siti",
            email = "siti@zakum.com",
            company = "PT Zakum",
            position = "Field PIC"
        )

        val merged = com.example.ui.LsrViewModel.mergeDirectoryUsers(listOf(fsUser), listOf(gasUser, extraGas))
        assertEquals(2, merged.size)
        val budi = merged.first { it.email.equals("budi@zakum.com", ignoreCase = true) }
        assertEquals("fb_123", budi.uid)
        assertEquals("Budi Santoso", budi.displayName)
        assertEquals("PT Zakum", budi.company)
        assertEquals("HSE Supervisor", budi.position)
        assertEquals(listOf("proj-a"), budi.projectIds)
    }

    @Test
    fun adminDatabaseLock_verifiesHashedPasswordAndRejectsWrongInputs() {
        assertTrue(com.example.ui.LsrViewModel.verifyAdminDatabasePassword("hasan2811"))
        assertTrue(com.example.ui.LsrViewModel.verifyAdminDatabasePassword("  hasan2811  "))
        assertFalse(com.example.ui.LsrViewModel.verifyAdminDatabasePassword("wrongpass"))
        assertFalse(com.example.ui.LsrViewModel.verifyAdminDatabasePassword(""))
    }

    @Test
    fun mutationLock_protectsOptimisticEditsFor25Seconds() {
        val t0 = 1_700_000_000_000L
        assertTrue(LsrRepository.isTimestampWithinMutationLock(t0, t0))
        assertTrue(LsrRepository.isTimestampWithinMutationLock(t0, t0 + 24_999L))
        assertFalse(LsrRepository.isTimestampWithinMutationLock(t0, t0 + 25_000L))
        assertFalse(LsrRepository.isTimestampWithinMutationLock(0L, t0))

        assertEquals(
            "1700000000000_wah-01_platform 04",
            LsrRepository.buildFindingKey(t0, " WAH-01 ", " Platform 04 ")
        )
    }

    @Test
    fun appStrings_formatRelativeSyncHandlesReadyAndRecent() {
        assertEquals("Siap Sinkron", AppStrings.formatRelativeSync(0L, AppLanguage.ID))
        assertEquals("Ready to Sync", AppStrings.formatRelativeSync(0L, AppLanguage.EN))
        assertEquals("Baru saja", AppStrings.formatRelativeSync(System.currentTimeMillis(), AppLanguage.ID))
    }

    @Test
    fun activityFilterCriteria_matchesAllDimensionsAndCountsActive() {
        val now = 1_700_000_000_000L
        val threeDaysAgo = now - 3L * 86_400_000L
        val tenDaysAgo = now - 10L * 86_400_000L

        val criteria = ActivityFilterCriteria(
            dateRange = DateRangeFilter.LAST_7_DAYS,
            userName = "Rully",
            lsrType = "Working at Height",
            company = "PT Zakum Offshore",
            location = "Platform 04"
        )
        assertEquals(5, criteria.activeCount)

        assertTrue(
            criteria.matches(
                timestamp = threeDaysAgo,
                users = listOf("Rully Beriandi", "Ahmad PIC"),
                lsrTypes = "Working at Height, Lifting Operations",
                itemCompany = "PT Zakum Offshore",
                itemLocation = "Platform 04",
                now = now
            )
        )

        assertFalse(
            criteria.matches(
                timestamp = tenDaysAgo,
                users = listOf("Rully Beriandi"),
                lsrTypes = "Working at Height",
                itemCompany = "PT Zakum Offshore",
                itemLocation = "Platform 04",
                now = now
            )
        )

        assertFalse(
            criteria.matches(
                timestamp = threeDaysAgo,
                users = listOf("Budi"),
                lsrTypes = "Working at Height",
                itemCompany = "PT Zakum Offshore",
                itemLocation = "Platform 04",
                now = now
            )
        )

        assertFalse(
            criteria.matches(
                timestamp = threeDaysAgo,
                users = listOf("Rully Beriandi"),
                lsrTypes = "Confined Space",
                itemCompany = "PT Zakum Offshore",
                itemLocation = "Platform 04",
                now = now
            )
        )

        assertFalse(
            criteria.matches(
                timestamp = threeDaysAgo,
                users = listOf("Rully Beriandi"),
                lsrTypes = "Working at Height",
                itemCompany = "Other Co",
                itemLocation = "Platform 04",
                now = now
            )
        )

        assertFalse(
            criteria.matches(
                timestamp = threeDaysAgo,
                users = listOf("Rully Beriandi"),
                lsrTypes = "Working at Height",
                itemCompany = "PT Zakum Offshore",
                itemLocation = "Workshop A",
                now = now
            )
        )

        val todayOnly = ActivityFilterCriteria(dateRange = DateRangeFilter.TODAY)
        assertEquals(1, todayOnly.activeCount)
        assertTrue(
            todayOnly.matches(
                timestamp = now - 60_000L,
                users = emptyList(),
                lsrTypes = "",
                itemCompany = "",
                itemLocation = "",
                now = now
            )
        )
        assertFalse(
            todayOnly.matches(
                timestamp = threeDaysAgo,
                users = emptyList(),
                lsrTypes = "",
                itemCompany = "",
                itemLocation = "",
                now = now
            )
        )
    }

    @Test
    fun l1WindowCap_retainsBoundedItemsWithoutDuplicates() {
        val existing = (1..200).toList()
        val incoming = (190..280).toList()
        val capped = LsrRepository.capL1Window(
            existing = existing,
            incoming = incoming,
            keySelector = { it },
            maxItems = 250
        )
        assertEquals(250, capped.size)
        assertEquals(capped.distinct().size, capped.size)
        assertEquals(31, capped.first())
        assertEquals(280, capped.last())
    }

    @Test
    fun cacheEviction_neverEvictsUnsyncedOrOpenFindings() {
        val now = 1_700_000_000_000L
        val cutoff = now - 7L * 86_400_000L
        val oldTime = cutoff - 60_000L
        val freshTime = cutoff + 60_000L

        assertTrue(CacheEvictionWorker.isSafeToEvictFinding(isSynced = true, status = "CLOSED", cachedAt = oldTime, cutoffTimestamp = cutoff))
        assertFalse(CacheEvictionWorker.isSafeToEvictFinding(isSynced = false, status = "CLOSED", cachedAt = oldTime, cutoffTimestamp = cutoff))
        assertFalse(CacheEvictionWorker.isSafeToEvictFinding(isSynced = true, status = "OPEN", cachedAt = oldTime, cutoffTimestamp = cutoff))
        assertFalse(CacheEvictionWorker.isSafeToEvictFinding(isSynced = true, status = "ACTION_SUBMITTED", cachedAt = oldTime, cutoffTimestamp = cutoff))
        assertFalse(CacheEvictionWorker.isSafeToEvictFinding(isSynced = true, status = "REOPENED", cachedAt = oldTime, cutoffTimestamp = cutoff))
        assertFalse(CacheEvictionWorker.isSafeToEvictFinding(isSynced = true, status = "CLOSED", cachedAt = freshTime, cutoffTimestamp = cutoff))
    }
}
