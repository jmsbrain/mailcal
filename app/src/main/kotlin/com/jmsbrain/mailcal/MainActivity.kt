package com.jmsbrain.mailcal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import java.time.LocalDate
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(applicationContext)
        Feeds.init(applicationContext)
        setContent { App() }
    }

    override fun onStart() {
        super.onStart()
        // Refresh on open, at most every few minutes.
        val s = Repo.state.value
        val stale = (s.updatedAt ?: 0) < System.currentTimeMillis() - 5 * 60_000
        if (s.account != null && !s.syncing && stale) lifecycleScope.launch { Repo.sync() }
        else Repo.rebuild()  // The day may have changed.
        Feeds.rebuild()
        Repo.refreshFeedsIfStale()
    }
}

/** Empty marker so LightOS lists this app as a tool. */
class LightMarkerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) = Unit
}

sealed interface Screen {
    data class Event(val item: Item) : Screen
    /** [item] null makes a new event on [day]; [one] edits a single repeat. */
    data class Edit(val item: Item?, val one: Boolean = false, val day: LocalDate? = null) : Screen
    data object Calendars : Screen
    data object Feeds : Screen
    data class FeedEdit(val id: String) : Screen
}

@Composable
fun App() {
    val s by Repo.state.collectAsState()
    val feeds by Feeds.state.collectAsState()
    var source by remember { mutableStateOf(Repo.source()) }
    val stack = remember { mutableStateListOf<Screen>() }
    var month by remember { mutableStateOf(Repo.monthView()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }

    Box(
        Modifier
            .fillMaxSize()
            .background(LightStyle.background)
            .systemBarsPadding()
            .imePadding(),
    ) {
        if (s.account == null) {
            stack.clear()
            LoginScreen()
            return@Box
        }
        BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }
        fun back() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }

        when (val top = stack.lastOrNull()) {
            null -> AgendaScreen(
                s = s,
                onOpen = { stack += Screen.Event(it) },
                onNew = { stack += Screen.Edit(null, day = it) },
                onCalendars = { stack += Screen.Calendars },
                feeds = feeds,
                source = source,
                onSource = { source = it; Repo.setSource(it) },
                onFeeds = { stack += Screen.Feeds },
                month = month,
                onMonthChange = { month = it; Repo.setMonthView(it) },
                selected = selected,
                onSelect = { selected = it },
            )
            is Screen.Event -> EventScreen(
                item = top.item,
                showCalendar = if (top.item.cal.url.startsWith(Feeds.FEED_PREFIX)) true else s.calendars.size > 1,
                onBack = ::back,
                onEdit = { one -> stack += Screen.Edit(top.item, one) },
                onDeleted = { stack.clear() },
            )
            is Screen.Edit -> EditScreen(
                s = s,
                item = top.item,
                one = top.one,
                day = top.day,
                onCancel = ::back,
                onSaved = { stack.clear() },
            )
            Screen.Calendars -> CalendarsScreen(s, onBack = ::back)
            Screen.Feeds -> FeedsScreen(feeds, onBack = ::back, onEdit = { stack += Screen.FeedEdit(it) })
            is Screen.FeedEdit -> FeedEditScreen(top.id, feeds, onBack = ::back)
        }
    }
}
