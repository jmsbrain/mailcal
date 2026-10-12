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
    month: Boolean,
    onMonthChange: (Boolean) -> Unit,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
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
            left = {
                Row {
                    TextButton("TODAY") {
                        if (month) onSelect(today) else scope.launch { listState.scrollToItem(todayIndex) }
                    }
                    HGap(24f)
                    TextButton(if (month) "LIST" else "MONTH") { onMonthChange(!month) }
                }
            },
            right = {
                Row {
                    if (s.calendars.any { !it.info.readOnly }) {
                        TextButton("NEW") { onNew(if (month) selected else null) }
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

        if (month) {
            MonthBody(s, selected, onSelect, onOpen, showCalendar, zone)
            return@Column
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
fun EventRow(item: Item, showCalendar: Boolean, zone: ZoneId, onClick: () -> Unit) {
    val o = item.occ
    val past = o.end.toInstant() < Instant.now()
    val color = if (past) LightStyle.secondary else LightStyle.content
    Row(Modifier.fillMaxWidth().flatClickable(onClick).padding(vertical = designDp(7f))) {
        Column(Modifier.width(designDp(112f)).padding(top = designDp(3f))) {
            val (start, end) = timeLines(o, zone)
            BasicText(start, style = paragraphStyle().copy(color = LightStyle.secondary))
            if (end != null) BasicText(end, style = paragraphStyle().copy(color = LightStyle.secondary), maxLines = 2)
        }
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

/** Start and end for the time column, e.g. "9:00a" / "9:30a", or "all day" / "to Fri". */
private fun timeLines(o: Occurrence, zone: ZoneId): Pair<String, String?> {
    val s = o.start.withZoneSameInstant(zone)
    val e = o.end.withZoneSameInstant(zone)
    val days = java.time.temporal.ChronoUnit.DAYS.between(s.toLocalDate(), e.toLocalDate())
    fun day(d: LocalDate) = if (days < 7) d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.US)
        else d.format(java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.US))
    if (o.allDay) {
        val last = e.toLocalDate().minusDays(1)
        return "all day" to if (last > s.toLocalDate()) "to " + day(last) else null
    }
    val start = clock(s.toLocalTime())
    return when {
        e <= s -> start to null
        days == 0L -> start to clock(e.toLocalTime())
        else -> start to day(e.toLocalDate()) + " " + clock(e.toLocalTime())
    }
}

private fun updatedText(at: Long, zone: ZoneId): String {
    val t = Instant.ofEpochMilli(at).atZone(zone)
    val time = clock(t.toLocalTime())
    return if (t.toLocalDate() == LocalDate.now(zone)) time else t.toLocalDate().toString() + " " + time
}
