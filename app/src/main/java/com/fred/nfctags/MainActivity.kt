package com.fred.nfctags

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class WriteAction(val label: String) {
    class Text(val text: String) : WriteAction("texto")
    class Uri(val uri: String) : WriteAction("URL")
    class Raw(val bytes: ByteArray, val desc: String) : WriteAction("cópia de \"$desc\"")
}

class MainActivity : ComponentActivity() {

    private lateinit var store: TagStore
    private var nfcAdapter: NfcAdapter? = null

    private val scanned = mutableStateOf<ScannedTag?>(null)
    private val saved: SnapshotStateList<SavedTag> = mutableStateListOf()
    private val status = mutableStateOf("Aproxime uma tag NFC do aparelho.")
    private val pending = mutableStateOf<WriteAction?>(null)
    private val nfcSupported = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = TagStore(this)
        saved.addAll(store.load())
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        nfcSupported.value = nfcAdapter != null

        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                AppScreen(
                    scanned = scanned.value,
                    saved = saved,
                    status = status.value,
                    pending = pending.value,
                    nfcSupported = nfcSupported.value,
                    onSaveScanned = { name -> saveScanned(name) },
                    onDelete = { id ->
                        store.delete(id)
                        saved.removeAll { it.id == id }
                    },
                    onArmWriteText = { text ->
                        pending.value = WriteAction.Text(text)
                        status.value = "Pronto para gravar texto. Aproxime a tag de destino."
                    },
                    onArmWriteUri = { uri ->
                        pending.value = WriteAction.Uri(uri)
                        status.value = "Pronto para gravar URL. Aproxime a tag de destino."
                    },
                    onArmCopy = { tag ->
                        val bytes = TagStore.base64ToNdefBytes(tag.ndefBase64)
                        if (bytes == null) {
                            status.value = "Essa tag salva não tem dados NDEF para copiar."
                        } else {
                            pending.value = WriteAction.Raw(bytes, tag.name)
                            status.value = "Pronto para copiar \"${tag.name}\". Aproxime a tag de destino."
                        }
                    },
                    onCancelPending = {
                        pending.value = null
                        status.value = "Operação cancelada."
                    }
                )
            }
        }

        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        enableForegroundDispatch()
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun enableForegroundDispatch() {
        val adapter = nfcAdapter ?: return
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_MUTABLE else 0
        val pi = PendingIntent.getActivity(this, 0, intent, flags)
        runCatching { adapter.enableForegroundDispatch(this, pi, null, null) }
    }

    private fun getTag(intent: Intent): Tag? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        else
            @Suppress("DEPRECATION") intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action ?: return
        if (action != NfcAdapter.ACTION_TAG_DISCOVERED &&
            action != NfcAdapter.ACTION_TECH_DISCOVERED &&
            action != NfcAdapter.ACTION_NDEF_DISCOVERED
        ) return

        val tag = getTag(intent) ?: return
        val armed = pending.value
        if (armed != null) {
            performWrite(tag, armed)
            pending.value = null
        } else {
            scanned.value = NfcUtils.read(tag)
            status.value = "Tag lida. Veja os dados abaixo."
        }
    }

    private fun performWrite(tag: Tag, action: WriteAction) {
        val result = when (action) {
            is WriteAction.Text -> NfcUtils.writeText(tag, action.text)
            is WriteAction.Uri -> NfcUtils.writeUri(tag, action.uri)
            is WriteAction.Raw -> NfcUtils.writeRaw(tag, action.bytes)
        }
        status.value = when (result) {
            is NfcUtils.WriteResult.Success -> "✓ Gravação concluída (${action.label})."
            is NfcUtils.WriteResult.Error -> "✗ Erro: ${result.message}"
        }
    }

    private fun saveScanned(name: String) {
        val s = scanned.value ?: return
        val tag = SavedTag(
            id = (s.uidHex + "-" + System.currentTimeMillis()),
            name = name.ifBlank { "Tag ${s.uidHex}" },
            uidHex = s.uidHex,
            techList = s.techList,
            ndefText = s.ndefText,
            ndefBase64 = TagStore.ndefBytesToBase64(s.ndefBytes),
            savedAt = System.currentTimeMillis()
        )
        store.add(tag)
        saved.add(0, tag)
        status.value = "Tag \"${tag.name}\" cadastrada."
    }
}

/* ----------------------------- UI ----------------------------- */

private enum class Screen(val title: String) {
    READ("Ler"), LIBRARY("Salvos"), WRITE("Gravar")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScreen(
    scanned: ScannedTag?,
    saved: List<SavedTag>,
    status: String,
    pending: WriteAction?,
    nfcSupported: Boolean,
    onSaveScanned: (String) -> Unit,
    onDelete: (String) -> Unit,
    onArmWriteText: (String) -> Unit,
    onArmWriteUri: (String) -> Unit,
    onArmCopy: (SavedTag) -> Unit,
    onCancelPending: () -> Unit
) {
    var screen by remember { mutableStateOf(Screen.READ) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("NFC Tags") }) },
        bottomBar = {
            NavigationBar {
                Screen.values().forEach { s ->
                    NavigationBarItem(
                        selected = screen == s,
                        onClick = { screen = s },
                        icon = {
                            Icon(
                                when (s) {
                                    Screen.READ -> Icons.Filled.Nfc
                                    Screen.LIBRARY -> Icons.Filled.List
                                    Screen.WRITE -> Icons.Filled.Edit
                                },
                                contentDescription = s.title
                            )
                        },
                        label = { Text(s.title) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (!nfcSupported) {
                WarningCard("Este aparelho não tem NFC ou está desligado. Ative o NFC nas configurações do sistema.")
                Spacer(Modifier.height(12.dp))
            }

            StatusBar(status, pending, onCancelPending)
            Spacer(Modifier.height(16.dp))

            when (screen) {
                Screen.READ -> ReadScreen(scanned, onSaveScanned)
                Screen.LIBRARY -> LibraryScreen(saved, onDelete, onArmCopy)
                Screen.WRITE -> WriteScreen(onArmWriteText, onArmWriteUri)
            }
        }
    }
}

@Composable
private fun StatusBar(status: String, pending: WriteAction?, onCancel: () -> Unit) {
    Card {
        Column(Modifier.padding(14.dp)) {
            Text(status, style = MaterialTheme.typography.bodyMedium)
            if (pending != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Aguardando tag… (${pending.label})",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onCancel) { Text("Cancelar") }
                }
            }
        }
    }
}

@Composable
private fun WarningCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Text(text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReadScreen(scanned: ScannedTag?, onSave: (String) -> Unit) {
    Text("Ler tag", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))
    if (scanned == null) {
        Text("Nenhuma tag lida ainda. Aproxime uma tag NFC para ver o UID, o tipo e o conteúdo.")
        return
    }
    var name by remember(scanned) { mutableStateOf("") }

    Card {
        Column(Modifier.padding(14.dp)) {
            Field("UID", scanned.uidHex, mono = true)
            Field("Tecnologias", scanned.techList)
            Field("Regravável", if (scanned.writable) "Sim" else "Não")
            if (scanned.maxWritableSize > 0) Field("Capacidade", "${scanned.maxWritableSize} bytes")
            Spacer(Modifier.height(8.dp))
            Text("Conteúdo NDEF", style = MaterialTheme.typography.labelLarge)
            Text(scanned.ndefText, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    }
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Nome para cadastrar") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    Button(onClick = { onSave(name) }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Save, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Cadastrar esta tag")
    }
}

@Composable
private fun LibraryScreen(
    saved: List<SavedTag>,
    onDelete: (String) -> Unit,
    onCopy: (SavedTag) -> Unit
) {
    Text("Tags cadastradas", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))
    if (saved.isEmpty()) {
        Text("Você ainda não cadastrou nenhuma tag. Leia uma tag na aba \"Ler\" e toque em cadastrar.")
        return
    }
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    saved.forEach { tag ->
        Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text(tag.name, style = MaterialTheme.typography.titleMedium)
                Text(tag.uidHex, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                Text(
                    tag.ndefText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text("Salva em ${fmt.format(Date(tag.savedAt))}", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(8.dp))
                Row {
                    val hasData = tag.ndefBase64.isNotEmpty()
                    Button(onClick = { onCopy(tag) }, enabled = hasData) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Copiar p/ tag")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { onDelete(tag.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Excluir")
                    }
                }
            }
        }
    }
}

@Composable
private fun WriteScreen(onText: (String) -> Unit, onUri: (String) -> Unit) {
    Text("Gravar em tag", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))
    Text("Escreva um texto ou uma URL e aproxime uma tag regravável para gravar.")
    Spacer(Modifier.height(16.dp))

    var text by remember { mutableStateOf("") }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text("Texto") },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = { onText(text) },
        enabled = text.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) { Text("Gravar texto") }

    Spacer(Modifier.height(24.dp))

    var uri by remember { mutableStateOf("https://") }
    OutlinedTextField(
        value = uri,
        onValueChange = { uri = it },
        label = { Text("URL") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = { onUri(uri) },
        enabled = uri.length > 8,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Gravar URL") }
}

@Composable
private fun Field(label: String, value: String, mono: Boolean = false) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("$label: ", style = MaterialTheme.typography.labelLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
        )
    }
}
