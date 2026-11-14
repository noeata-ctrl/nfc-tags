package com.fred.nfctags

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * A tag the user has saved/registered in the app.
 *
 * ndefBase64 holds the raw bytes of the NDEF message (if any) so the tag's
 * content can later be written ("copied") onto another writable tag.
 */
data class SavedTag(
    val id: String,
    val name: String,
    val uidHex: String,
    val techList: String,
    val ndefText: String,
    val ndefBase64: String,
    val savedAt: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("uidHex", uidHex)
        put("techList", techList)
        put("ndefText", ndefText)
        put("ndefBase64", ndefBase64)
        put("savedAt", savedAt)
    }

    companion object {
        fun fromJson(o: JSONObject) = SavedTag(
            id = o.optString("id"),
            name = o.optString("name"),
            uidHex = o.optString("uidHex"),
            techList = o.optString("techList"),
            ndefText = o.optString("ndefText"),
            ndefBase64 = o.optString("ndefBase64"),
            savedAt = o.optLong("savedAt")
        )
    }
}

/** Simple JSON-in-SharedPreferences store. No database dependency needed. */
class TagStore(context: Context) {
    private val prefs = context.getSharedPreferences("nfc_tags", Context.MODE_PRIVATE)

    fun load(): List<SavedTag> {
        val raw = prefs.getString("tags", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { SavedTag.fromJson(arr.getJSONObject(it)) }
            .sortedByDescending { it.savedAt }
    }

    private fun persist(list: List<SavedTag>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("tags", arr.toString()).apply()
    }

    fun add(tag: SavedTag) {
        val list = load().toMutableList()
        list.add(0, tag)
        persist(list)
    }

    fun delete(id: String) {
        persist(load().filterNot { it.id == id })
    }

    companion object {
        fun ndefBytesToBase64(bytes: ByteArray?): String =
            if (bytes == null) "" else Base64.encodeToString(bytes, Base64.NO_WRAP)

        fun base64ToNdefBytes(s: String): ByteArray? =
            if (s.isEmpty()) null else Base64.decode(s, Base64.NO_WRAP)
    }
}
