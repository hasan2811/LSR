package com.example

import com.example.data.ActivityFilterCriteria
import com.example.data.AppLanguage
import com.example.data.AppStrings
import com.example.data.AppThemeMode
import com.example.data.DashboardPerspective
import com.example.data.DateRangeFilter
import com.example.data.FindingItem
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
            "theme_dark"
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
}
