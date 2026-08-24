package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class CustomChecklistStore(context: Context) {
    private val prefs = context.getSharedPreferences("lsr_custom_checklist_prefs", Context.MODE_PRIVATE)

    private val _currentChecklist = MutableStateFlow<List<LsrItem>>(loadChecklist())
    val currentChecklist: StateFlow<List<LsrItem>> = _currentChecklist.asStateFlow()

    private fun loadChecklist(): List<LsrItem> {
        val savedJson = prefs.getString("custom_items_json", null)
        if (!savedJson.isNullOrBlank()) {
            try {
                val jsonArray = JSONArray(savedJson)
                val list = parseJsonArray(jsonArray)
                if (list.isNotEmpty()) {
                    return list
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return LsrChecklist.items
    }

    private fun parseJsonArray(jsonArray: JSONArray): List<LsrItem> {
        val resultList = mutableListOf<LsrItem>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val id = obj.optString("id", "LSR-CUSTOM-${i + 1}").ifBlank { "LSR-${i + 1}" }
            val category = obj.optString("category", "General Safety").ifBlank { "General Safety" }
            val categoryEn = obj.optString("category_en", category)
            val itemText = obj.optString("item_text", "").ifBlank {
                obj.optString("question", obj.optString("text", "Checklist item $i"))
            }
            val itemTextEn = obj.optString("item_text_en", itemText)
            val standardRef = obj.optString("standard_ref", "Company HSE Standard")
            val isCritical = obj.optBoolean("is_critical", false)

            resultList.add(
                LsrItem(
                    id = id,
                    category = category,
                    category_en = categoryEn,
                    item_text = itemText,
                    item_text_en = itemTextEn,
                    standard_ref = standardRef,
                    is_critical = isCritical
                )
            )
        }
        return resultList
    }

    fun saveChecklist(items: List<LsrItem>): Boolean {
        if (items.isEmpty()) return false
        return try {
            val jsonArray = JSONArray()
            items.forEach { item ->
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("category", item.category)
                if (item.category_en.isNotBlank()) obj.put("category_en", item.category_en)
                obj.put("item_text", item.item_text)
                if (item.item_text_en.isNotBlank()) obj.put("item_text_en", item.item_text_en)
                obj.put("standard_ref", item.standard_ref)
                obj.put("is_critical", item.is_critical)
                jsonArray.put(obj)
            }
            prefs.edit().putString("custom_items_json", jsonArray.toString()).apply()
            _currentChecklist.value = items
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun resetToDefault(): List<LsrItem> {
        prefs.edit().remove("custom_items_json").apply()
        _currentChecklist.value = LsrChecklist.items
        return LsrChecklist.items
    }

    fun exportAsJson(): String {
        val current = _currentChecklist.value
        val jsonArray = JSONArray()
        current.forEach { item ->
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("category", item.category)
            if (item.category_en.isNotBlank()) obj.put("category_en", item.category_en)
            obj.put("item_text", item.item_text)
            if (item.item_text_en.isNotBlank()) obj.put("item_text_en", item.item_text_en)
            obj.put("standard_ref", item.standard_ref)
            obj.put("is_critical", item.is_critical)
            jsonArray.put(obj)
        }
        return jsonArray.toString(2)
    }

    fun importFromJson(jsonString: String): Result<List<LsrItem>> {
        return try {
            val trimmed = jsonString.trim()
            val jsonArray = JSONArray(trimmed)
            if (jsonArray.length() == 0) {
                return Result.failure(Exception("JSON array is empty"))
            }
            val resultList = parseJsonArray(jsonArray)

            if (resultList.isNotEmpty()) {
                saveChecklist(resultList)
                Result.success(resultList)
            } else {
                Result.failure(Exception("No valid items found in JSON"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getSampleTemplateJson(): String {
        return """
[
  {
    "id": "LSR-WAH-01",
    "category": "Bekerja di Ketinggian",
    "category_en": "Working at Height",
    "item_text": "Apakah pekerja di ketinggian (>1.8m) telah menggunakan Full Body Harness?",
    "item_text_en": "Are workers at height (>1.8m) using certified 100% tie-off Full Body Harnesses?",
    "standard_ref": "OSHA 1926.501, IOGP LSR",
    "is_critical": true
  },
  {
    "id": "LSR-CSE-01",
    "category": "Ruang Terbatas",
    "category_en": "Confined Space Entry",
    "item_text": "Apakah izin kerja ruang terbatas aktif dan uji gas telah diverifikasi?",
    "item_text_en": "Is the Confined Space permit active and gas testing validated by an AGT?",
    "standard_ref": "OSHA 1910.146",
    "is_critical": true
  },
  {
    "id": "LSR-LTO-01",
    "category": "Isolasi Energi",
    "category_en": "Energy Isolation (LOTO)",
    "item_text": "Apakah isolasi energi berbahaya (LOTO) telah dipasang dan diuji nol energi?",
    "item_text_en": "Has Lockout/Tagout (LOTO) been applied and zero energy state verified?",
    "standard_ref": "OSHA 1910.147",
    "is_critical": true
  },
  {
    "id": "LSR-ELEC-01",
    "category": "Keselamatan Listrik",
    "category_en": "Electrical Safety",
    "item_text": "Apakah panel distribusi tertutup rapat dan alat pelindung busur api (Arc Flash PPE) tersedia?",
    "item_text_en": "Are distribution panels closed and arc flash protective equipment ready?",
    "standard_ref": "NFPA 70E",
    "is_critical": false
  }
]
        """.trimIndent()
    }
}
