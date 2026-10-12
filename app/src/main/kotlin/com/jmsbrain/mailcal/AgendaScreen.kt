package com.jmsbrain.mailcal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

private sealed interface Row {
    val key: String
    data class Day(val day: LocalDate, val empty: Boolean) : Row { override val key = "d$day" }
    data class Event(val item: Item) : Row { override val key = "e${item.href}|${item.occ.start.toInstant()}|${item.occ.uid}" }
}

@Composable
fun AgendaScreen(
    s: UiState,
    onOpen: (Item) -> Unit,
    onNew: (LocalDate?) -> Unit,
    onCalendars: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val showCalendar = s.calendars.count { !it.hidden } > 1
    var confirmSignOut by remember { mutableStateOf(false) }

    val rows = remember(s.items, today) {
        val out = mutableListOf<Row>()
        val byDay = s.items.groupBy { it.occ.start.withZoneSameInstant(zone).toLocalDate() }
        val days = (byDay.keys + today).toSortedSet()
        for (d in days) {
            val list = byDay[d].orEmpty()
            out += Row.Day(d, list.isEmpty())
            list.forEach { out += Row.Event(it) }
        }
        out.distinctBy { it.key }
    }
    val todayIndex = rows.indexOfFirst { it is Row.Day && it.day == today }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = todayIndex)
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        TopBar(
            left = { TextButton("TODAY") { scope.launch { listState.scrollToItem(todayIndex) } } },
            right = {
                Row {
                    if (s.calendars.any { !it.info.readOnly }) {
                        TextButton("NEW") { onNew(null) }
                        HGap(24f)
                    }
                    MenuButton(
                        "MENU",
                        listOf(
                            "REFRESH" to { Repo.refresh() },
                            "CALENDARS" to onCalendars,
                            "SIGN OUT" to { confirmSignOut = true },
                        ),
                    )
                }
            },
        )
        Column(Modifier.padding(horizontal = designDp(24f))) {
            when {
                confirmSignOut -> {
                    BasicText("Sign out? This only removes the copy on this phone; your calendar stays on mailbox.org.", style = paragraphStyle())
                    Row {
                        TextButton("SIGN OUT") { Repo.signOut() }
                        HGap(32f)
                        TextButton("CANCEL") { confirmSignOut = false }
                    }
                    Gap(8f)
                }
                s.syncing -> Status("Updating…")
                s.error != null -> BasicText(s.error, style = paragraphStyle())
                s.updatedAt != null -> Status("Updated " + updatedText(s.updatedAt, zone))
            }
            Gap(6f)
            Box(Modifier.fillMaxWidth().height(1.dp).background(LightStyle.secondary))
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = designDp(24f))) {
            items(rows, key = { it.key }) { row ->
                when (row) {
                    is Row.Day -> Column(Modifier.fillMaxWidth().flatClickable { onNew(row.day) }.padding(top = designDp(18f), bottom = designDp(4f))) {
                        BasicText(dayLabel(row.day), style = labelStyle().copy(color = if (row.day == today) LightStyle.content else LightStyle.secondary))
                        if (row.empty) {
                            Gap(6f)
                            Note(if (s.syncing && s.items.isEmpty()) "Loading…" else "Nothing scheduled.")
                        }
                    }
                    is Row.Event -> EventRow(row.item, showCalendar, zone) { onOpen(row.item) }
                }
            }
            item { Gap(60f) }
        }
    }
}

@Composable
private fun Status(text: String) {
    BasicText(text, style = labelStyle())
}

@Composable
private fun EventRow(item: Item, showCalendar: Boolean, zone: ZoneId, onClick: () -> Unit) {
    val o = item.occ
    val past = o.end.toInstant() < Instant.now()
    val color = if (past) LightStyle.secondary else LightStyle.content
    Row(Modifier.fillMaxWidth().flatClickable(onClick).padding(vertical = designDp(7f))) {
        BasicText(
            if (o.allDay) "all day" else clock(o.start.withZoneSameInstant(zone).toLocalTime()),
            style = paragraphStyle().copy(color = LightStyle.secondary),
            modifier = Modifier.width(designDp(112f)).padding(top = designDp(3f)),
        )
        Column(Modifier.weight(1f)) {
            BasicText(o.title, style = copyStyle().copy(color = color), maxLines = 2)
            val sub = listOfNotNull(
                o.location.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() },
                item.cal.name.takeIf { showCalendar },
            ).joinToString(" · ")
            if (sub.isNotEmpty()) BasicText(sub, style = paragraphStyle().copy(color = LightStyle.secondary), maxLines = 1)
        }
    }
}

private fun updatedText(at: Long, zone: ZoneId): String {
    val t = Instant.ofEpochMilli(at).atZone(zone)
    val time = clock(t.toLocalTime())
    return if (t.toLocalDate() == LocalDate.now(zone)) time else t.toLocalDate().toString() + " " + time
}
