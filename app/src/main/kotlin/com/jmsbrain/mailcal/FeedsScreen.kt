package com.jmsbrain.mailcal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch

/** The subscribed .ics feeds: add by link, tap one to change it. */
@Composable
fun FeedsScreen(feeds: FeedsState, onBack: () -> Unit, onEdit: (String) -> Unit) {
    var link by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current

    fun add(text: String) {
        if (text.isBlank()) { message = "Type or paste a calendar link first."; return }
        busy = true
        message = null
        scope.launch {
            val before = Feeds.state.value.feeds.map { it.id }.toSet()
            val n = Feeds.addLinks(text)
            busy = false
            message = when (n) {
                0 -> "No new calendar link found. It may already be added."
                else -> Feeds.state.value.feeds.filter { it.id !in before }.firstNotNullOfOrNull { it.error }
                    ?: if (n == 1) "Added." else "Added $n feeds."
            }
            if (n > 0) link = ""
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(left = { TextButton("BACK", enabled = !busy, onClick = onBack) })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = designDp(24f)),
        ) {
            BasicText("Feeds", style = headingStyle())
            Gap(8f)
            Note("Subscribed calendars (.ics or webcal links), read-only. Google share links work if the calendar is public.")
            Gap(8f)
            Field("ADD A LINK", link, { link = it; message = null }, "https://…",
                keyboardType = KeyboardType.Uri, capitalization = KeyboardCapitalization.None)
            Row {
                if (busy) Note("Adding…") else {
                    TextButton("ADD") { add(link) }
                    HGap(32f)
                    TextButton("PASTE LINK") { add(clipboard.getText()?.text.orEmpty()) }
                }
            }
            message?.let {
                Gap(6f)
                BasicText(it, style = paragraphStyle())
            }
            Gap(20f)
            feeds.feeds.forEach { f ->
                Row(
                    Modifier.fillMaxWidth().flatClickable { onEdit(f.id) }.padding(vertical = designDp(10f)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        BasicText(f.name.ifBlank { Feeds.hostOf(Feeds.httpUrl(f.url)) },
                            style = copyStyle().copy(color = if (f.hidden) LightStyle.secondary else LightStyle.content), maxLines = 2)
                        BasicText(
                            when {
                                f.error != null -> "Couldn't update"
                                f.updatedAt != null -> "Updated " + ago(f.updatedAt)
                                else -> "Not downloaded yet"
                            },
                            style = labelStyle(),
                        )
                    }
                    if (f.hidden) BasicText("HIDDEN", style = buttonStyle().copy(color = LightStyle.secondary))
                }
            }
            Gap(40f)
        }
    }
}

private fun ago(at: Long): String {
    val t = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    val today = java.time.LocalDate.now()
    return if (t.toLocalDate() == today) clock(t.toLocalTime())
    else t.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.US))
}

/** One feed: name, link, shown or hidden, refresh, delete. */
@Composable
fun FeedEditScreen(id: String, feeds: FeedsState, onBack: () -> Unit) {
    val original = remember(id) { Feeds.feed(id) }
    if (original == null) {
        onBack()
        return
    }
    var name by remember { mutableStateOf(original.name) }
    var url by remember { mutableStateOf(original.url) }
    var hidden by remember { mutableStateOf(original.hidden) }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val live = feeds.feeds.firstOrNull { it.id == id } ?: original
    val scope = rememberCoroutineScope()

    fun save(then: () -> Unit) {
        if (url.isBlank()) return
        busy = true
        scope.launch {
            val current = Feeds.feed(id) ?: original
            val changedUrl = current.url != url.trim()
            Feeds.save(current.copy(name = name.trim().ifBlank { Feeds.hostOf(Feeds.httpUrl(url)) }, url = url.trim(), hidden = hidden))
            if (changedUrl) Feeds.refresh(id)
            busy = false
            then()
        }
    }

    BackHandler(enabled = !busy) { save(onBack) }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            left = { if (busy) Note("Saving…") else TextButton("BACK") { save(onBack) } },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = designDp(24f)),
        ) {
            Field("NAME", name, { name = it })
            Field("LINK", url, { url = it }, keyboardType = KeyboardType.Uri, capitalization = KeyboardCapitalization.None)
            ValueRow("ON THE AGENDA", if (hidden) "Hidden" else "Shown") { hidden = !hidden }
            Gap(8f)
            BasicText("STATUS", style = labelStyle())
            Gap(2f)
            BasicText(
                live.error ?: live.updatedAt?.let { "Updated " + ago(it) } ?: "Not downloaded yet",
                style = paragraphStyle(),
            )
            Gap(12f)
            if (!busy) TextButton("REFRESH NOW") {
                busy = true
                scope.launch {
                    val current = Feeds.feed(id) ?: original
                    Feeds.save(current.copy(name = name.trim().ifBlank { current.name }, url = url.trim(), hidden = hidden))
                    Feeds.refresh(id)
                    busy = false
                }
            }
            Gap(40f)
            if (confirmDelete) {
                BasicText("Remove this feed from Mailcal? The calendar itself isn't affected.", style = paragraphStyle())
                Row {
                    TextButton("REMOVE") {
                        busy = true
                        scope.launch { Feeds.delete(id); onBack() }
                    }
                    HGap(32f)
                    TextButton("KEEP") { confirmDelete = false }
                }
            } else if (!busy) TextButton("REMOVE FEED") { confirmDelete = true }
            Gap(40f)
        }
    }
}
