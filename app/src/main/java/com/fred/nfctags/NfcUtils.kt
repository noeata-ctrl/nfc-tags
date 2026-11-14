package com.fred.nfctags

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import java.nio.charset.Charset

/** A tag just read from the reader, before the user decides to save it. */
data class ScannedTag(
    val uidHex: String,
    val techList: String,
    val ndefText: String,
    val ndefBytes: ByteArray?,
    val maxWritableSize: Int,
    val writable: Boolean
)

object NfcUtils {

    fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString(":") { String.format("%02X", it) }

    /** Read UID, tech list and NDEF content (if present) from a tag. */
    fun read(tag: Tag): ScannedTag {
        val uid = bytesToHex(tag.id)
        val techs = tag.techList.joinToString(", ") { it.substringAfterLast('.') }

        var ndefText = "Sem dados NDEF nesta tag."
        var ndefBytes: ByteArray? = null
        var maxSize = 0
        var writable = false

        val ndef = Ndef.get(tag)
        if (ndef != null) {
            try {
                ndef.connect()
                maxSize = ndef.maxSize
                writable = ndef.isWritable
                val msg = ndef.cachedNdefMessage ?: ndef.ndefMessage
                if (msg != null) {
                    ndefBytes = msg.toByteArray()
                    ndefText = describe(msg)
                }
            } catch (e: Exception) {
                ndefText = "Não foi possível ler NDEF: ${e.message}"
            } finally {
                runCatching { ndef.close() }
            }
        } else if (NdefFormatable.get(tag) != null) {
            writable = true
            ndefText = "Tag vazia, pronta para ser formatada e gravada."
        }

        return ScannedTag(uid, techs, ndefText, ndefBytes, maxSize, writable)
    }

    /** Human-readable description of an NDEF message's records. */
    fun describe(msg: NdefMessage): String {
        val sb = StringBuilder()
        msg.records.forEachIndexed { i, r ->
            sb.append("Registro ${i + 1}: ")
            sb.append(describeRecord(r))
            sb.append("\n")
        }
        return sb.toString().trim()
    }

    private fun describeRecord(r: NdefRecord): String {
        return when {
            r.tnf == NdefRecord.TNF_WELL_KNOWN &&
                r.type.contentEquals(NdefRecord.RTD_TEXT) -> "Texto → " + decodeText(r)
            r.tnf == NdefRecord.TNF_WELL_KNOWN &&
                r.type.contentEquals(NdefRecord.RTD_URI) -> "URL → " + decodeUri(r)
            r.tnf == NdefRecord.TNF_MIME_MEDIA ->
                "MIME (${String(r.type, Charsets.US_ASCII)}) → " +
                    String(r.payload, Charsets.UTF_8)
            else -> "Bytes (${r.payload.size} bytes)"
        }
    }

    private fun decodeText(r: NdefRecord): String {
        return try {
            val payload = r.payload
            val isUtf16 = (payload[0].toInt() and 0x80) != 0
            val langLen = payload[0].toInt() and 0x3F
            val charset: Charset = if (isUtf16) Charsets.UTF_16 else Charsets.UTF_8
            String(payload, 1 + langLen, payload.size - 1 - langLen, charset)
        } catch (e: Exception) {
            "(texto ilegível)"
        }
    }

    private fun decodeUri(r: NdefRecord): String {
        return try {
            r.toUri()?.toString() ?: "(uri ilegível)"
        } catch (e: Exception) {
            "(uri ilegível)"
        }
    }

    sealed class WriteResult {
        object Success : WriteResult()
        data class Error(val message: String) : WriteResult()
    }

    /** Write a plain-text NDEF record to a writable tag. */
    fun writeText(tag: Tag, text: String): WriteResult {
        val record = NdefRecord.createTextRecord("pt", text)
        return writeMessage(tag, NdefMessage(arrayOf(record)))
    }

    /** Write a URI NDEF record to a writable tag. */
    fun writeUri(tag: Tag, uri: String): WriteResult {
        val record = NdefRecord.createUri(uri)
        return writeMessage(tag, NdefMessage(arrayOf(record)))
    }

    /** Write raw NDEF bytes (used to "copy" a saved tag onto a new one). */
    fun writeRaw(tag: Tag, bytes: ByteArray): WriteResult {
        return try {
            val msg = NdefMessage(bytes)
            writeMessage(tag, msg)
        } catch (e: Exception) {
            WriteResult.Error("Dados NDEF inválidos: ${e.message}")
        }
    }

    private fun writeMessage(tag: Tag, message: NdefMessage): WriteResult {
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            return try {
                ndef.connect()
                if (!ndef.isWritable) return WriteResult.Error("Esta tag é somente leitura.")
                if (ndef.maxSize < message.toByteArray().size) {
                    return WriteResult.Error("A tag não tem espaço suficiente.")
                }
                ndef.writeNdefMessage(message)
                WriteResult.Success
            } catch (e: Exception) {
                WriteResult.Error(e.message ?: "Falha ao gravar.")
            } finally {
                runCatching { ndef.close() }
            }
        }
        val formatable = NdefFormatable.get(tag)
        if (formatable != null) {
            return try {
                formatable.connect()
                formatable.format(message)
                WriteResult.Success
            } catch (e: Exception) {
                WriteResult.Error(e.message ?: "Falha ao formatar/gravar.")
            } finally {
                runCatching { formatable.close() }
            }
        }
        return WriteResult.Error("Esta tag não suporta NDEF.")
    }
}
