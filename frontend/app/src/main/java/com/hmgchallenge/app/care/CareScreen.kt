package com.hmgchallenge.app.care

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.hmgchallenge.app.R
import com.hmgchallenge.app.driving.LocalUxRestrictions
import com.hmgchallenge.app.driving.UxRestrictionsState
import com.hmgchallenge.app.ui.theme.PleosTheme
import com.hmgchallenge.shared.care.BabyProfile
import com.hmgchallenge.shared.care.CareEvent
import com.hmgchallenge.shared.care.CareEventType
import com.hmgchallenge.shared.care.CareJournal
import com.hmgchallenge.shared.care.CareNotice
import com.hmgchallenge.shared.care.CareProblem
import com.hmgchallenge.shared.care.CareRecord
import com.hmgchallenge.shared.care.CareState
import com.hmgchallenge.shared.care.CareStoreStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val HOME = "home"
private const val PROFILE = "profile"
private const val HISTORY = "history"
private const val EDIT = "edit"
private const val CONNECTIONS = "connections"
private const val FIVE_MINUTES = 300_000L
private val AMOUNTS = listOf(null, 60, 90, 120, 150, 180, 210, 240)

/** Width, rather than device orientation, controls the layout of resizable Pleos windows.
 * Profile/history/detail routes are removed immediately when restrictions change. MainViewModel also guards writes.
 */
@Composable
fun CareScreen(
    state: CareState,
    onRecord: (CareEventType) -> Unit,
    onUndo: () -> Unit,
    onReload: () -> Unit,
    onSaveProfile: (Int, Int?) -> Unit,
    onEditEvent: (String, Long, Int?) -> Unit,
    embeddedInShell: Boolean = false,
    onSubRouteChange: ((Boolean, (() -> Unit)?) -> Unit)? = null,
    connections: @Composable () -> Unit,
) {
    val dimens = PleosTheme.dimens
    val type = PleosTheme.typography
    val ux = LocalUxRestrictions.current
    val restricted = ux.requiresDistractionOptimization || ux.noSetup
    var route by rememberSaveable { mutableStateOf(HOME) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var awaitingRevision by rememberSaveable { mutableStateOf<Long?>(null) }
    val goHome = { route = HOME; editingId = null; awaitingRevision = null }
    val goBack = {
        route = if (route == EDIT) HISTORY else HOME
        editingId = null
        awaitingRevision = null
    }
    val visibleRoute = if (restricted) HOME else route

    LaunchedEffect(restricted) { if (restricted) goHome() }
    LaunchedEffect(visibleRoute) {
        onSubRouteChange?.invoke(visibleRoute != HOME, if (visibleRoute != HOME) goBack else null)
    }
    DisposableEffect(Unit) {
        onDispose { onSubRouteChange?.invoke(false, null) }
    }
    LaunchedEffect(state.revision, state.notice, state.problem) {
        val waiting = awaitingRevision
        if (waiting != null && state.revision > waiting &&
            (state.notice == CareNotice.PROFILE_SAVED || state.notice == CareNotice.EDITED)
        ) goHome()
        else if (state.problem != null) awaitingRevision = null
    }
    BackHandler(enabled = visibleRoute != HOME) { goBack() }

    if (visibleRoute == CONNECTIONS) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(if (embeddedInShell) dimens.contentInset else dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(if (embeddedInShell) dimens.itemSpacing else dimens.sectionSpacing),
        ) {
            if (!embeddedInShell) CareButton(stringResource(R.string.care_back), goBack, primary = false)
            Box(Modifier.weight(1f)) { connections() }
        }
        return
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Observed Pleos AVD window: 616x324dp in landscape. Keep two 260dp control columns at 600dp+.
            val wide = maxWidth >= dimens.breakpointCareWide
            val scrollState = key(visibleRoute) { rememberScrollState() }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(if (embeddedInShell) dimens.contentInset else dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing),
            ) {
                val homeRecord = state.record.takeIf { visibleRoute == HOME }
                if (homeRecord != null) {
                    CareHomeHeader(homeRecord, state, onUndo)
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(dimens.sectionSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!embeddedInShell && visibleRoute != HOME) {
                            CareButton(stringResource(R.string.care_back), goBack, Modifier.width(dimens.headerBackWidth), primary = false)
                        }
                        CareText(
                            stringResource(when (visibleRoute) {
                                PROFILE -> R.string.care_profile_title
                                HISTORY -> R.string.care_history_title
                                EDIT -> R.string.care_edit_title
                                else -> R.string.care_title
                            }),
                            Modifier.weight(1f),
                            type.titleSmall,
                        )
                    }
                }
                if (visibleRoute != HOME || state.record == null) CareMessage(state, onReload)
                when (visibleRoute) {
                    PROFILE -> ProfileForm(state.record?.profile, state.canWrite) { month, amount ->
                        awaitingRevision = state.revision
                        onSaveProfile(month, amount)
                    }
                    HISTORY -> state.record?.let { record ->
                        CareHistory(record, state.canWrite) { editingId = it; route = EDIT }
                    }
                    EDIT -> {
                        val event = state.record?.events?.firstOrNull { it.id == editingId }
                        if (event == null) CareText(stringResource(R.string.care_record_missing))
                        else EventForm(event, state.canWrite) { at, amount ->
                            awaitingRevision = state.revision
                            onEditEvent(event.id, at, amount)
                        }
                    }
                    else -> {
                        val record = state.record
                        if (record == null && state.status == CareStoreStatus.READY) {
                            Column(verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)) {
                                CareText(stringResource(if (restricted) R.string.care_park_to_setup else R.string.care_first_detail))
                                CareButton(stringResource(R.string.care_setup), { route = PROFILE }, Modifier.fillMaxWidth(), enabled = state.canWrite && !restricted, tag = "care_setup")
                            }
                        } else if (record != null) {
                            if (wide) {
                                Row(horizontalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)) {
                                    QuickRecords(record, state.canWrite, onRecord, Modifier.weight(1f))
                                    LatestRecord(record, Modifier.weight(1f))
                                }
                            } else {
                                QuickRecords(record, state.canWrite, onRecord)
                                LatestRecord(record)
                            }
                            if (state.problem != null || state.status != CareStoreStatus.READY) CareMessage(state, onReload)
                            if (!restricted) {
                                Row(horizontalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)) {
                                    CareButton(stringResource(R.string.care_history_open), { route = HISTORY }, Modifier.weight(1f), primary = false, tag = "care_history")
                                    CareButton(stringResource(R.string.care_profile_title), { route = PROFILE }, Modifier.weight(1f), enabled = state.canWrite, primary = false, tag = "care_profile")
                                }
                            }
                        }
                        if (!restricted) {
                            CareButton(stringResource(R.string.care_connections), { route = CONNECTIONS }, Modifier.fillMaxWidth(), primary = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CareHomeHeader(record: CareRecord, state: CareState, onUndo: () -> Unit) {
    val ux = LocalUxRestrictions.current
    val drivingLabel = stringResource(when {
        ux.source != UxRestrictionsState.Source.CAR_SERVICE -> R.string.care_drive_short_unknown
        ux.requiresDistractionOptimization -> R.string.care_drive_short_moving
        else -> R.string.care_drive_short_parked
    })
    val status = when {
        state.isSaving -> R.string.care_saving
        state.status == CareStoreStatus.LOADING -> R.string.care_loading_short
        state.problem != null -> R.string.care_check_short
        else -> when (state.notice) {
            CareNotice.PROFILE_SAVED, CareNotice.RECORDED, CareNotice.EDITED -> R.string.care_saved_short
            CareNotice.UNDONE -> R.string.care_undone_short
            null -> if (CareJournal.isSleeping(record)) R.string.care_sleeping else R.string.care_ready_short
        }
    }
    val dimens = PleosTheme.dimens
    Row(
        Modifier.fillMaxWidth().heightIn(min = dimens.buttonHeight),
        horizontalArrangement = Arrangement.spacedBy(dimens.sectionSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            CareText(stringResource(R.string.care_home_heading, record.profile.monthAge, drivingLabel), maxLines = 1)
            CareText(stringResource(status), maxLines = 1)
        }
        CareButton(
            stringResource(R.string.care_undo_short), onUndo, Modifier.width(dimens.compactActionWidth),
            enabled = state.canWrite && record.events.isNotEmpty(), primary = false,
            tag = "care_undo", description = stringResource(R.string.care_undo),
        )
    }
}

@Composable
private fun CareMessage(state: CareState, onReload: () -> Unit) {
    val problem = state.problem
    val notice = state.notice
    val message = when {
        state.isSaving -> stringResource(R.string.care_saving)
        state.status == CareStoreStatus.LOADING -> stringResource(R.string.care_loading)
        problem != null -> stringResource(problemText(problem))
        notice != null -> stringResource(when (notice) {
            CareNotice.PROFILE_SAVED -> R.string.care_profile_saved
            CareNotice.RECORDED -> R.string.care_record_saved
            CareNotice.UNDONE -> R.string.care_record_undone
            CareNotice.EDITED -> R.string.care_record_edited
        })
        else -> null
    }
    if (message != null) CareText(message)
    if (state.status == CareStoreStatus.NEEDS_RELOAD) {
        CareButton(stringResource(R.string.care_reload), onReload, Modifier.fillMaxWidth(), tag = "care_reload")
    }
}

@Composable
private fun QuickRecords(record: CareRecord, enabled: Boolean, onRecord: (CareEventType) -> Unit, modifier: Modifier = Modifier) {
    val dimens = PleosTheme.dimens
    val sleeping = CareJournal.isSleeping(record)
    val amount = record.profile.preferences.preferredFeedAmountMl
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Two 72dp rows fit the 324dp-high Pleos window with 24dp edge/target spacing.
        val columns = if (maxWidth >= dimens.breakpointQuickRecordTwoCol) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)) {
            CareEventType.entries.chunked(columns).forEach { types ->
                Row(horizontalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)) {
                    types.forEach { type ->
                        val label = if (type == CareEventType.FEED && amount != null) stringResource(R.string.care_feed_button, amount) else eventName(type)
                        val allowed = enabled && when (type) {
                            CareEventType.SLEEP_START -> !sleeping
                            CareEventType.SLEEP_END -> sleeping
                            else -> true
                        }
                        CareButton(label, { onRecord(type) }, Modifier.weight(1f), enabled = allowed, compact = true, tag = "care_record_" + type.name)
                    }
                }
            }
        }
    }
}

@Composable
private fun LatestRecord(record: CareRecord, modifier: Modifier = Modifier) {
    val type = PleosTheme.typography
    CareCard(stringResource(R.string.care_latest_title), modifier, compact = true) {
        val event = record.events.firstOrNull()
        if (event == null) {
            CareText(stringResource(R.string.care_empty_records))
        } else {
            CareText(eventSummary(event), style = type.titleSmall)
            CareText(eventTime(event.occurredAtEpochMillis))
        }
    }
}

@Composable
private fun ProfileForm(profile: BabyProfile?, enabled: Boolean, onSave: (Int, Int?) -> Unit) {
    val dimens = PleosTheme.dimens
    val type = PleosTheme.typography
    var month by rememberSaveable(profile?.id) { mutableIntStateOf(profile?.monthAge ?: 0) }
    var amount by rememberSaveable(profile?.id) { mutableStateOf(profile?.preferences?.preferredFeedAmountMl) }
    CareCard(stringResource(R.string.care_month_title)) {
        CareText(stringResource(R.string.care_month_age, month), style = type.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)) {
            CareButton(stringResource(R.string.care_month_less), { month-- }, Modifier.weight(1f), enabled = enabled && month > 0, primary = false)
            CareButton(stringResource(R.string.care_month_more), { month++ }, Modifier.weight(1f), enabled = enabled && month < 24, primary = false)
        }
    }
    CareCard(stringResource(R.string.care_default_amount)) {
        CareText(stringResource(R.string.care_default_amount_detail))
        AmountChoices(amount, enabled) { amount = it }
    }
    CareButton(stringResource(R.string.care_save_profile), { onSave(month, amount) }, Modifier.fillMaxWidth(), enabled = enabled, tag = "care_profile_save")
}

@Composable
private fun AmountChoices(selected: Int?, enabled: Boolean, onSelect: (Int?) -> Unit) {
    val dimens = PleosTheme.dimens
    BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
        val columns = if (maxWidth >= dimens.breakpointThreeGrid) 3 else if (maxWidth >= dimens.breakpointTwoGrid) 2 else 1
        val values = if (selected != null && selected !in AMOUNTS) AMOUNTS + selected else AMOUNTS
        Column(verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)) {
            values.chunked(columns).forEach { options ->
                Row(horizontalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)) {
                    options.forEach { value ->
                        val label = if (value == null) stringResource(R.string.care_amount_none) else stringResource(R.string.care_amount_ml, value)
                        CareButton(label, { onSelect(value) }, Modifier.weight(1f), enabled = enabled, primary = false, selected = selected == value)
                    }
                    repeat(columns - options.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun CareHistory(record: CareRecord, enabled: Boolean, onEdit: (String) -> Unit) {
    // Keep the stored history intact. Only this parked detail view is limited to the most recent 20 rows.
    CareText(stringResource(R.string.care_history_detail, record.events.size))
    if (record.events.isEmpty()) CareText(stringResource(R.string.care_empty_records))
    record.events.take(20).forEach { event ->
        CareCard(eventSummary(event)) {
            CareText(eventTime(event.occurredAtEpochMillis))
            CareButton(stringResource(R.string.care_edit), { onEdit(event.id) }, Modifier.fillMaxWidth(), enabled = enabled, primary = false)
        }
    }
}

@Composable
private fun EventForm(event: CareEvent, enabled: Boolean, onSave: (Long, Int?) -> Unit) {
    val dimens = PleosTheme.dimens
    val type = PleosTheme.typography
    var at by rememberSaveable(event.id) { mutableStateOf(event.occurredAtEpochMillis) }
    var amount by rememberSaveable(event.id) { mutableStateOf(event.feedAmountMl) }
    CareCard(eventName(event.type)) {
        CareText(eventTime(at), style = type.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)) {
            CareButton(stringResource(R.string.care_time_earlier), { at -= FIVE_MINUTES }, Modifier.weight(1f), enabled = enabled && at >= FIVE_MINUTES, primary = false)
            CareButton(stringResource(R.string.care_time_later), { at += FIVE_MINUTES }, Modifier.weight(1f), enabled = enabled && at <= System.currentTimeMillis() - FIVE_MINUTES, primary = false)
        }
        CareButton(stringResource(R.string.care_time_now), { at = System.currentTimeMillis() }, Modifier.fillMaxWidth(), enabled = enabled, primary = false)
        CareText(stringResource(R.string.care_time_detail))
    }
    if (event.type == CareEventType.FEED) {
        CareCard(stringResource(R.string.care_event_amount)) { AmountChoices(amount, enabled) { amount = it } }
    }
    CareButton(stringResource(R.string.care_save_edit), { onSave(at, amount) }, Modifier.fillMaxWidth(), enabled = enabled, tag = "care_edit_save")
}

@Composable
private fun eventName(type: CareEventType): String = stringResource(when (type) {
    CareEventType.FEED -> R.string.care_feed
    CareEventType.DIAPER -> R.string.care_diaper
    CareEventType.SLEEP_START -> R.string.care_sleep_start
    CareEventType.SLEEP_END -> R.string.care_sleep_end
})

@Composable
private fun eventSummary(event: CareEvent): String {
    val amount = event.feedAmountMl
    return if (event.type == CareEventType.FEED && amount != null) {
        stringResource(R.string.care_feed_amount, amount)
    } else eventName(event.type)
}

@Composable
private fun eventTime(at: Long): String = DateTimeFormatter.ofPattern(stringResource(R.string.care_date_pattern), Locale.getDefault())
    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(at))

private fun problemText(problem: CareProblem): Int = when (problem) {
    CareProblem.READ_FAILED -> R.string.care_error_read
    CareProblem.INVALID_DATA -> R.string.care_error_data
    CareProblem.UNSUPPORTED_SCHEMA -> R.string.care_error_schema
    CareProblem.WRITE_FAILED -> R.string.care_error_write
    CareProblem.PROFILE_REQUIRED -> R.string.care_first_title
    CareProblem.INVALID_PROFILE -> R.string.care_error_profile
    CareProblem.DUPLICATE_RECORD -> R.string.care_error_duplicate
    CareProblem.INVALID_SLEEP_ORDER -> R.string.care_error_sleep
    CareProblem.CLOCK_BEHIND -> R.string.care_error_clock
    CareProblem.INVALID_TIME -> R.string.care_error_time
    CareProblem.RECORD_NOT_FOUND -> R.string.care_record_missing
    CareProblem.PARK_REQUIRED -> R.string.care_park_to_setup
}
