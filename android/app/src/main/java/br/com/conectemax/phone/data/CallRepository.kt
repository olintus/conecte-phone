package br.com.conectemax.phone.data

import android.content.Context
import br.com.conectemax.phone.model.CallDirection
import br.com.conectemax.phone.model.CallRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

class CallRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _recent = MutableStateFlow(load())
    val recent: StateFlow<List<CallRecord>> = _recent.asStateFlow()

    @Synchronized
    fun add(record: CallRecord) {
        val updated = (listOf(record) + _recent.value.filterNot { it.id == record.id })
            .sortedByDescending(CallRecord::occurredAt)
            .take(MAX_RECORDS)
        _recent.value = updated
        preferences.edit().putString(KEY_RECORDS, encode(updated)).apply()
    }

    @Synchronized
    fun clear() {
        _recent.value = emptyList()
        preferences.edit().remove(KEY_RECORDS).apply()
    }

    private fun load(): List<CallRecord> = runCatching {
        val array = JSONArray(preferences.getString(KEY_RECORDS, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    CallRecord(
                        id = item.getString("id"),
                        displayName = item.getString("displayName"),
                        handle = item.getString("handle"),
                        direction = CallDirection.valueOf(item.getString("direction")),
                        occurredAt = Instant.ofEpochMilli(item.getLong("occurredAt")),
                        durationSeconds = item.optLong("durationSeconds", 0L),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun encode(records: List<CallRecord>): String = JSONArray().apply {
        records.forEach { record ->
            put(
                JSONObject()
                    .put("id", record.id)
                    .put("displayName", record.displayName)
                    .put("handle", record.handle)
                    .put("direction", record.direction.name)
                    .put("occurredAt", record.occurredAt.toEpochMilli())
                    .put("durationSeconds", record.durationSeconds)
            )
        }
    }.toString()

    private companion object {
        const val PREFERENCES_NAME = "conecte_call_history"
        const val KEY_RECORDS = "records"
        const val MAX_RECORDS = 100
    }
}
