# Notification Fixes - Draft GitHub Issues (LOCAL ONLY)

- **Date:** 2026-07-17
- **Status:** Drafts for review. **Nothing here has been created on GitHub.** Copy/paste per issue once approved.
- **Source:** `reports/notification-root-cause.md` (causes C1-C14, plan F0.1-F0.11) and `reports/notification-issues-triage.md`.
- All file:line references verified against `main` @ `f5d36f7c53` (23.0). Code snippets under "Current code" are verbatim; snippets under "Proposed" are sketches, not final patches.
- Per repo policy (AGENTS.md + TDD): every fix lands test-first; each draft has a Test plan.

## Index

| Draft | Title | Fixes cause | Closes/helps | Effort |
|-------|-------|-------------|--------------|--------|
| I-01 | fix(notification): post new-mail notifications synchronously from sync path | C8 | #10890 | S |
| I-02 | fix(account-settings): reschedule mail sync when poll frequency changes in new settings UI | C3 | #9898-class | S |
| I-03 | fix(push): scope push auto-disable to the failing folder and inform the user | C7 | #10208, #5594-class | M |
| I-04 | fix(notification): always dismiss the tapped notification, even when internal state is stale | C10 | #11065, part of #10936/#9317 | M |
| I-05 | fix(notification): detect and surface missing POST_NOTIFICATIONS at post time and on enable paths | C9 | #7554, #8712 | M |
| I-06 | feat(push): proper exact-alarm permission request flow (enable, import, banner) | C4 | #8549-class, #8434 | M |
| I-07 | fix(core): restore delivery after reboot and app update for all configurations | C2/C6 | #5593, #5808 | S |
| I-08 | feat(sync): catch-up sync when the app comes to the foreground and an account is overdue | C2 | #7839 UX | S |
| I-09 | fix(notification): restore quick-delete setting UI and honor configured actions on summary/Wear | C11 | #11130, #11076 | M |
| I-10 | chore(sync): unify the disabled-poll-interval guards | C3 | hygiene | XS |
| I-11 | fix(imap): parser fails on FETCH response with keyword containing `[` | C13 | #7589 | S |
| E-01 | epic: delivery health state and status surface | R3/C13 | #8830, #8831, #9246 | L |
| E-02 | epic: polling resilience under Doze/App Standby/OEM restrictions | C1 | #7839 family | L |
| E-03 | epic: push connection resilience (liveness, backoff, FGS-denied retry) | C5/C6 | #9520, #8751 | L |
| E-04 | epic: honest notification state machine (reconcile with NotificationManager) | C10 | #10936 family | L |
| N-01 | feat(notification): add MessageReference to MailNotification model and action creators | new-path gap | #11159 chain | M |
| N-02 | fix(notification): persist NotificationRegistry and make id allocation collision-safe | new-path gap | #11259 | M |
| N-03 | fix(notification): permission + failure handling in AndroidSystemNotificationNotifier | C9 twin | #11259 | S |
| N-04 | feat(notification): group/summary/appearance support in the new system notifier | new-path gap | #11159, #11205 | M |
| N-05 | fix(notification): NotificationSenderCompat must not fire-and-forget | C8 twin | #10890 lesson | S |
| N-06 | feat(notification): honor configured actions, quiet time, and appearance in the new path | C11 twin | #11076 | M |
| N-07 | chore(notification): implement isAppInBackground TODO and define flag rollout plan | C14 | #11259, #9391 | S |

---

## I-01 fix(notification): post new-mail notifications synchronously from sync path

**Labels:** `type: bug`, `type: regression` | **Priority:** P0 | **Related:** #10890, #11056, #11059

### Problem
Since 17.0 (commit `1c037e5fab`, PR #9652) the actual `notify()` call for single-message notifications runs in a fire-and-forget coroutine on the Main dispatcher. The sync path returns immediately after updating the DB/store. On poll accounts the WorkManager job then completes and the process becomes cacheable/freezable, so the queued post can run late or never. Symptom: mail is fetched, badge updates, no notification (poll accounts only; push keeps the process alive via the FGS).

### Current code
`legacy/core/src/main/java/com/fsck/k9/notification/SingleMessageNotificationCreator.kt:24-30,64`
```kotlin
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

fun createSingleNotification(
    baseNotificationData: BaseNotificationData,
    singleNotificationData: SingleNotificationData,
    isGroupSummary: Boolean = false,
) = scope.launch {
    // ... builder chain with `suspend` setAvatar(...) at line 41/67 ...
    notificationHelper.notify(account, notificationId, notification)
}
```
Caller does not await: `MessagingController.java:2786-2791` runs `notificationController.addNewMailNotification(...)` synchronously on the sync thread and returns.

### Proposed fix (preferred)
Move the only suspending part (avatar load) into the data-creation phase, which already runs on the background sync thread, and make the creator synchronous again:
```kotlin
// SingleMessageNotificationDataCreator: resolve the avatar up front
internal data class SingleNotificationData(
    // existing fields ...
    val avatarIcon: IconCompat?,
)

// SingleMessageNotificationCreator: no scope, no launch
fun createSingleNotification(
    baseNotificationData: BaseNotificationData,
    singleNotificationData: SingleNotificationData,
    isGroupSummary: Boolean = false,
) {
    val notification = notificationHelper
        .createNotificationBuilder(account = baseNotificationData.account, channelType = ChannelType.MESSAGES)
        // ...
        .setAvatar(avatarIcon = singleNotificationData.avatarIcon)
        // ...
        .build()
    notificationHelper.notify(
        account = baseNotificationData.account,
        notificationId = singleNotificationData.notificationId,
        notification = notification,
    )
}
```
Fallback (if avatar loading must stay where it is): return the `Job` and join at the controller boundary before `processNewMailNotificationData` returns, with a bounded timeout, so `addNewMailNotification` has happens-before on `notify()`.

### Acceptance criteria
- `NotificationController.addNewMailNotification` does not return until `NotificationManagerCompat.notify` has been invoked (or a failure was recorded).
- No `CoroutineScope` field remains in `SingleMessageNotificationCreator`.
- Behavior identical for push and poll accounts.

### Test plan (failing first)
1. Unit test: fake `NotificationHelper` records `notify` calls; assert `createSingleNotification` has called it by the time the function returns (fails today because the launch has not run).
2. Repro before/after on device: poll account, screen off, send mail, `adb shell dumpsys notification --noredact | grep -A2 net.thunderbird` after the sync worker logs completion.

---

## I-02 fix(account-settings): reschedule mail sync when poll frequency changes in new settings UI

**Labels:** `type: bug`, `type: regression` | **Priority:** P0 | **Related:** #9898, #11056

### Problem
The new Compose "Fetching mail" settings update the account but never reschedule the WorkManager periodic job. The old interval keeps running until the next process cold start. The legacy preference screen explicitly rescheduled (`AccountSettingsDataStore.kt:155-158` -> `reschedulePoll()`); the new path lost that obligation. `Preferences.saveAccount` listeners are only `PushController` and `NotificationChannelManager`, so nothing else compensates.

### Current code
`feature/account/settings/impl/src/main/kotlin/net/thunderbird/feature/account/settings/impl/domain/usecase/UpdateFetchingMailSettings.kt:43-45,86`
```kotlin
is Command.UpdateFolderPollFrequency -> {
    account.copy(automaticCheckIntervalMinutes = command.value)
}
// ...
repository.update(updatedAccount)
```
Legacy path that does it right: `legacy/ui/legacy/src/main/java/com/fsck/k9/ui/settings/account/AccountSettingsDataStore.kt:155-158,226-228`.

### Proposed fix
`feature:*` must not depend on `legacy:*`, so introduce a small API and bind it in `app-common`:
```kotlin
// core (api module), e.g. net.thunderbird.core.sync
interface MailSyncScheduler {
    fun schedulePeriodicSync(accountUuid: String)
}

// app-common Koin binding
single<MailSyncScheduler> {
    object : MailSyncScheduler {
        override fun schedulePeriodicSync(accountUuid: String) {
            val account = get<Preferences>().getAccount(accountUuid) ?: return
            get<K9JobManager>().scheduleMailSync(account)
        }
    }
}

// UpdateFetchingMailSettings
is Command.UpdateFolderPollFrequency -> {
    account.copy(automaticCheckIntervalMinutes = command.value)
        .also { rescheduleAfterUpdate = true }
}
// after repository.update(updatedAccount):
if (rescheduleAfterUpdate) mailSyncScheduler.schedulePeriodicSync(accountId.asRaw())
```
Audit alongside: every write the legacy `AccountSettingsDataStore` used to follow with `reschedulePoll()`/`restartPushers()` must have an equivalent in the new UI (at minimum poll frequency; `idleRefreshMinutes` already propagates through the push config flow).

### Acceptance criteria
Changing poll frequency in the new UI re-enqueues `MailSync:<uuid>` with the new period without app restart (`adb shell dumpsys jobscheduler | grep -A4 MailSync`).

### Test plan (failing first)
Unit test on the use case with a fake `MailSyncScheduler`: `UpdateFolderPollFrequency` invokes `schedulePeriodicSync(accountUuid)` exactly once; other commands do not.

---

## I-03 fix(push): scope push auto-disable to the failing folder and inform the user

**Labels:** `type: bug` | **Priority:** P0 | **Related:** #10208, #5594, #8830, #11056

### Problem
When the IDLE loop reports NOT_SUPPORTED, the app disables `push_enabled` for **every folder of the account** (SQL update with null WHERE), silently (verbose log only). NOT_SUPPORTED is also returned for a transient condition: the IDLE command completing without a continuation response, which flaky servers/proxies can produce. The UI keeps offering push (`ImapBackend.isPushCapable` is hard-coded `true`, `ImapBackend.kt:50`), so users re-enable and the toggle "turns itself off" again.

### Current code
`legacy/storage/src/main/java/com/fsck/k9/storage/messages/UpdateFolderOperations.kt:85-93`
```kotlin
fun setPushDisabled() {
    lockableDatabase.execute(false) { db ->
        val contentValues = ContentValues().apply {
            put("push_enabled", false)
        }

        db.update("folders", contentValues, null, null)
    }
}
```
Transient trigger: `mail/protocols/imap/src/main/java/com/fsck/k9/mail/store/imap/RealImapFolderIdler.kt:97-101`
```kotlin
val response = connection.readResponse()
if (response.tag == tag) {
    Log.w("%s.idle(): IDLE command completed without a continuation request response", logTag)
    return IdleResult.NOT_SUPPORTED
}
```
Callback chain: `ImapFolderPusher.kt:82-85` -> `AccountBackendPusherCallback.kt:29-32` -> `DefaultFolderRepository.setPushDisabled(accountId)`.

### Proposed fix
1. Carry the folder through the callback and scope the SQL:
```kotlin
// UpdateFolderOperations
fun setPushDisabled(folderServerId: String) {
    lockableDatabase.execute(false) { db ->
        val contentValues = ContentValues().apply { put("push_enabled", false) }
        db.update("folders", contentValues, "server_id = ?", arrayOf(folderServerId))
    }
}
```
2. Distinguish "capability absent" (`!connection.isIdleCapable`, definitive) from "no continuation" (transient): retry the latter N times with backoff in `ImapBackendPusher` before treating it as NOT_SUPPORTED.
3. Surface it: when push is auto-disabled, post an error notification / in-app notification ("Push disabled for folder X: server does not support IDLE") instead of `Log.v`. Hook into the #8831 in-app error system when available.

### Acceptance criteria
- Disabling affects only the reporting folder.
- A single spurious no-continuation response does not disable anything.
- The user sees why push turned off.

### Test plan (failing first)
1. `UpdateFolderOperationsTest`: seed two folders with `push_enabled=1`, call `setPushDisabled("folderA")`, assert folderB still enabled (fails today).
2. Pusher test: fake idler returning one no-continuation NOT_SUPPORTED then SYNC; assert push stays enabled.

---

## I-04 fix(notification): always dismiss the tapped notification, even when internal state is stale

**Labels:** `type: bug` | **Priority:** P1 | **Related:** #11065, #10936, #9317, #10089

### Problem
Dismissal is driven purely by the in-memory `NotificationDataStore`. If the tapped message is no longer in the store (already read/removed by a prior sync, process restart, race), `removeNotifications` returns `null` and the `NotificationManager.cancel` call is skipped, leaving a zombie notification that the user cannot clear by acting on it.

### Current code
`legacy/core/src/main/java/com/fsck/k9/notification/NotificationDataStore.kt:127-132,197-199`
```kotlin
): RemoveNotificationsResult? {
    var notificationData = getNotificationData(account)
    if (notificationData.isEmpty()) return null

    val removeMessageReferences = selector.invoke(notificationData.messageReferences)
    if (removeMessageReferences.isEmpty()) return null
    // ...
    return if (operations.isEmpty()) {
        null
    } else { /* RemoveNotificationsResult(...) */ }
}
```
`legacy/core/src/main/java/com/fsck/k9/notification/NotificationActionService.kt:42-65` runs the messaging op, then `cancelNotifications(intent, account)`, which funnels into the same store path.

### Proposed fix
Give the action a direct handle on the system notification and use it as a fallback:
```kotlin
// K9NotificationActionCreator: add the id to every action intent
intent.putExtra(EXTRA_NOTIFICATION_ID, notificationId)

// NotificationActionService.handleCommand, after the store-driven cancel:
val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
val handledByStore = /* propagate a Boolean result from cancelNotification... */
if (!handledByStore && notificationId != -1) {
    NotificationManagerCompat.from(this).cancel(notificationId)
}
```
Propagating the Boolean: `NewMailNotificationController.removeNewMailNotifications` currently swallows the `null`; return `false` in that case up through `MessagingController.cancelNotificationForMessage`. The fallback must run only on store miss so it cannot race the promotion logic (which reuses the freed id for a re-posted notification).

### Acceptance criteria
Acting on any visible new-mail notification removes it from the shade, regardless of internal store state.

### Test plan (failing first)
1. Store test: `removeNotifications` on an unknown ref reports a "not found" outcome distinct from success (new API shape).
2. Service test (Robolectric): intent with `EXTRA_NOTIFICATION_ID` and a store that misses; assert `NotificationManager.cancel(id)` was called (fails today).

---

## I-05 fix(notification): detect and surface missing POST_NOTIFICATIONS at post time and on enable paths

**Labels:** `type: bug` | **Priority:** P1 | **Related:** #7554 (priority: medium, good first issue), #8712, #11056

### Problem
No posting path checks `areNotificationsEnabled()`. With the runtime permission denied (never granted, revoked, or wiped by backup-restore), `notify()` is a silent no-op while all internal bookkeeping (store rows, `wasNotified`) advances as if delivered. The permission is requested once, in onboarding only (`feature/onboarding/permissions/.../PermissionsScreen.kt:61`).

### Current code
`legacy/core/src/main/java/com/fsck/k9/notification/NotificationHelper.kt:46-64`
```kotlin
fun notify(account: LegacyAccountDto, notificationId: Int, notification: Notification) {
    try {
        notificationManager.notify(notificationId, notification)
    } catch (e: SecurityException) {
        // When importing settings from another device, we could end up with a NotificationChannel that references
        // a non-existing notification sound. ...
    }
}
```
(The `SecurityException` branch handles broken sound URIs; the permission case never throws, it just drops.)

### Proposed fix
```kotlin
fun notify(account: LegacyAccountDto, notificationId: Int, notification: Notification) {
    if (!notificationManager.areNotificationsEnabled()) {
        logger.warn(TAG) { "POST_NOTIFICATIONS not granted; dropping notification $notificationId" }
        deliveryHealthTracker.onNotificationBlocked(account.uuid)
        return
    }
    // existing try/catch ...
}
```
Plus the UX half (this is the actual #7554 ask):
- Re-request the permission when the user enables notifications for an account/folder (account settings, manage-folders notify toggle) via `rememberLauncherForActivityResult(RequestPermission())` on the Compose side or an ActivityResultLauncher on the legacy fragment.
- After settings import/restore, include notification permission in a "delivery readiness" check (pairs with I-06).
- When blocked repeatedly, show an in-app banner pointing to the app notification settings.

### Acceptance criteria
Denied permission is visible: logged, counted, and the user is prompted at the moment they opt in to notifications, not just at onboarding.

### Test plan (failing first)
Unit test with a fake `NotificationManagerCompat` reporting disabled: `notify` records a blocked event and does not advance state silently (fails today: nothing observable happens).

---

## I-06 feat(push): proper exact-alarm permission request flow (enable, import, banner)

**Labels:** `type: enhancement`, `type: platform` | **Priority:** P0 | **Related:** #8549, #8434, #9239, #11250

### Problem
Push cannot run without `SCHEDULE_EXACT_ALARM` (the IDLE refresh is exact-alarm-only, and `PushController` suspends push entirely when the permission is missing). Android 14+ denies the permission by default for new installs targeting API 33+, and sets it to denied after backup-restore. The app's only request path is tapping the persistent push notification, which is `setSilent(true)` + `PRIORITY_MIN` on an `IMPORTANCE_LOW` channel, and invisible if POST_NOTIFICATIONS is denied. The settings-import path (`SettingsImporter` -> `AccountSettingsWriter.write` -> `Core.setServicesEnabled`, `AccountSettingsWriter.kt:109`) schedules poll only and never triggers any push/permission setup. This is the #8549 "imported from K-9, push dead, never asked" trap.

### Current code
`legacy/core/src/main/java/com/fsck/k9/controller/push/PushController.kt:166,173-176,231-234`
```kotlin
val alarmPermissionMissing = !alarmPermissionManager.canScheduleExactAlarms()
// ...
val shouldDisablePushAccounts = backgroundSyncDisabledViaSystem ||
    backgroundSyncDisabledInApp ||
    networkNotAvailable ||
    alarmPermissionMissing
// ...
alarmPermissionMissing -> {
    setPushNotificationState(ALARM_PERMISSION_MISSING)
    startServices()
}
```
`legacy/core/src/main/java/com/fsck/k9/notification/PushNotificationManager.kt:63-97` (the silent notification + `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` intent).

### Proposed fix
Foreground request at every push opt-in point:
```kotlin
// Shared helper (legacy/ui or core/android/permissions)
fun Context.launchExactAlarmSettingsIfNeeded(alarmPermissionManager: AlarmPermissionManager): Boolean {
    if (alarmPermissionManager.canScheduleExactAlarms()) return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:$packageName")
            },
        )
    }
    return true
}
```
Wire it into:
1. `FolderSettingsDataStore.putBoolean("folder_settings_push", true)` path (show explanation dialog first, then launch).
2. End of settings import when any imported folder has `pushEnabled=true` (`SettingsImporter`/post-import activation) - a "delivery readiness" step: notifications permission (I-05) + exact alarms + battery-exemption hint.
3. A visible in-app banner (not only the silent FGS notification) whenever `ALARM_PERMISSION_MISSING` is the active push state.
Existing grant-listener already handles resume: `AlarmPermissionManagerApi31` -> `onAlarmPermissionGranted` -> `updatePushers()` (`PushController.kt:123-125`).

### Acceptance criteria
- Enabling push without the permission immediately shows an explanation + system grant screen.
- Importing settings with push folders lands the user in the same flow.
- Push state "suspended: needs Alarms and reminders permission" is visible inside the app.

### Test plan (failing first)
UI test / Robolectric: toggling folder push with `canScheduleExactAlarms() == false` fires the settings intent (fails today: nothing happens except the silent notification relabel).

---

## I-07 fix(core): restore delivery after reboot and app update for all configurations

**Labels:** `type: bug` | **Priority:** P1 | **Related:** #5593, #5808, #8751

### Problem
The only boot/update receiver is gated on push having been active: `BootCompleteReceiver` is `android:enabled="false"` in the manifest and only enabled from `PushController.startServices()` (disabled again in `stopServices()`). It also only calls `pushController.init()` - it never reschedules poll workers. Poll-only users get nothing after an app update; push-only users lose delivery after updates whenever services were stopped at that moment (#5593).

### Current code
`legacy/core/src/main/java/com/fsck/k9/controller/push/BootCompleteReceiver.kt:14-22`
```kotlin
class BootCompleteReceiver : BroadcastReceiver(), KoinComponent {
    private val pushController: PushController by inject()

    override fun onReceive(context: Context, intent: Intent?) {
        Log.v("BootCompleteReceiver.onReceive() - %s", intent?.action)

        pushController.init()
    }
}
```
Manifest: `legacy/common/src/main/AndroidManifest.xml:214-224` (`android:enabled="false"`, filters `BOOT_COMPLETED` + `MY_PACKAGE_REPLACED`).

### Proposed fix
```xml
<receiver
    android:name="com.fsck.k9.controller.push.BootCompleteReceiver"
    android:enabled="true"
    android:exported="false">
    ...
</receiver>
```
```kotlin
override fun onReceive(context: Context, intent: Intent?) {
    Log.v("BootCompleteReceiver.onReceive() - %s", intent?.action)

    jobManager.scheduleAllMailJobs()
    pushController.init()
}
```
The receiver is cheap (both broadcasts are rare); `scheduleAllMailJobs` recomputes initial delays from `lastSyncTime`, so an overdue account syncs promptly after boot/update. Remove the enable/disable dance from `BootCompleteManager` or keep it as a no-op shim for one release.

### Acceptance criteria
After `adb shell am broadcast -a android.intent.action.MY_PACKAGE_REPLACED` (or a real update), push reconnects and an overdue poll account syncs without opening the app.

### Test plan (failing first)
Robolectric receiver test: `onReceive` invokes both `scheduleAllMailJobs` and `pushController.init` (fails today for the former). Manual: update flow on device with a push-only account.

---

## I-08 feat(sync): catch-up sync when the app comes to the foreground and an account is overdue

**Labels:** `type: enhancement` | **Priority:** P1 | **Related:** #7839, #7257, #11059

### Problem
The `lastSyncTime`-based catch-up only runs when scheduling runs, which is cold process start or a settings write. If Doze/OEM restrictions starved the periodic worker overnight, opening the app shows stale mail until the user pulls to refresh (`BaseApplication.kt:77` registers only a logging lifecycle observer).

### Current code
`legacy/core/src/main/java/com/fsck/k9/job/MailSyncWorkerManager.kt:87-97`
```kotlin
private fun calculateInitialDelay(lastSyncTime: Long, syncIntervalMinutes: Long): Long {
    @OptIn(ExperimentalTime::class)
    val now = clock.now().toEpochMilliseconds()
    val nextSyncTime = lastSyncTime + (syncIntervalMinutes * 60L * 1000L)

    return if (lastSyncTime > now || nextSyncTime <= now) {
        0L
    } else {
        nextSyncTime - now
    }
}
```

### Proposed fix
```kotlin
// Registered in BaseApplication.onCreate alongside the existing observer
class OverdueSyncLifecycleObserver(
    private val preferences: Preferences,
    private val jobManager: K9JobManager,
    private val clock: Clock,
) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        val now = clock.now().toEpochMilliseconds()
        val overdueAccounts = preferences.getAccounts().filter { account ->
            val intervalMinutes = account.automaticCheckIntervalMinutes
            intervalMinutes > 0 &&
                account.lastSyncTime + intervalMinutes * 60_000L < now
        }
        if (overdueAccounts.isNotEmpty()) {
            jobManager.scheduleImmediateCatchUpSync(overdueAccounts)
        }
    }
}
```
`scheduleImmediateCatchUpSync` enqueues a `OneTimeWorkRequest` (expedited, `OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST`) per overdue account, with a unique name so repeated foregrounds coalesce. Notifications from this run follow normal strategy rules (it is a periodic-style sync, not a manual one, so notifying is correct).

### Acceptance criteria
Foregrounding the app after a starved night triggers a sync within seconds, without touching pull-to-refresh, and does not double-run when the periodic worker is already executing.

### Test plan (failing first)
Unit test on the observer with fake clock/accounts: overdue account -> catch-up scheduled; fresh account -> nothing.

---

## I-09 fix(notification): restore quick-delete setting UI and honor configured actions on summary/Wear

**Labels:** `type: bug` | **Priority:** P1 | **Related:** #11130, #11076, #10494

### Problem
Two settings-plumbing gaps:
1. `notificationQuickDeleteBehaviour` gates the Delete action (`SingleMessageNotificationDataCreator.kt:122-124`) but **no settings screen exposes it anymore** (`res/xml/general_settings.xml` has no quick-delete preference; only quiet time :460-484, lock-screen :491-496, actions screen :500-502). Users whose storage carries the legacy `NEVER` default (pre-v67, `GeneralSettingsDescriptions.java:287-289`) can never get the Delete button back without editing prefs by hand (#11130).
2. The configured actions order/cutoff (feature `d708b7c91b`, 19.0) is honored only by single-message notifications. Summary (2+ messages) hard-codes MarkAsRead + optional Delete, and Wear actions are hard-coded, so most users "see no effect" (#11076).

### Current code
`legacy/core/src/main/java/com/fsck/k9/notification/SummaryNotificationDataCreator.kt:49-57` (hard-coded summary actions) and `SingleMessageNotificationDataCreator.kt:71-88` (hard-coded Wear list). Storage side is healthy: keys `messageActionsOrder`/`messageActionsCutoff` in `NotificationSettingKey.kt:11-12`, consumed at `SingleMessageNotificationDataCreator.kt:54-69`.

### Proposed fix
1. Re-add the preference (or fold it into the notification-actions screen) and/or run a one-time storage migration mapping stored `NEVER` -> `ALWAYS` **only when the value predates the UI removal** (do not clobber deliberate choices; #10494 was exactly a reset-on-upgrade complaint, so prefer restoring the UI over migrating data).
2. Apply the configured order to the summary where actions are group-applicable:
```kotlin
private fun createSummaryNotificationActions(): List<SummaryNotificationAction> {
    val order = parseActionsOrder(notificationPreference.messageActionsOrder)
    return order.mapNotNull { action ->
        when (action) {
            NotificationAction.MarkAsRead -> SummaryNotificationAction.MarkAsRead
            NotificationAction.Delete -> SummaryNotificationAction.Delete.takeIf { isDeleteActionAvailable() }
            else -> null // reply/star/archive/spam do not apply to a group
        }
    }.ifEmpty { listOf(SummaryNotificationAction.MarkAsRead) }
}
```
3. In the actions settings screen, show which configured actions are currently unavailable and why (no archive folder, delete disabled, group context), so the config UI matches the shade reality.

### Acceptance criteria
- A device with stored `notificationQuickDelete=NEVER` can enable the Delete button from settings in both flavors.
- With 2+ new messages, the summary reflects the configured group-applicable actions.

### Test plan (failing first)
1. `SummaryNotificationDataCreatorTest`: configured order `[delete, mark_as_read]` yields summary actions in that order (fails today: fixed list).
2. Settings screen test: quick-delete control exists and round-trips the value.

---

## I-10 chore(sync): unify the disabled-poll-interval guards

**Labels:** `type: maintenance` | **Priority:** P2

### Problem
Scheduler and worker disagree on what "disabled" means. A stored interval of `0` is scheduled (WorkManager clamps the period to 15 min) but every run no-ops - scheduled wakeups doing nothing.

### Current code
`MailSyncWorkerManager.kt:79-85`
```kotlin
private fun getSyncIntervalIfEnabled(account: LegacyAccountDto): Long? {
    val intervalMinutes = account.automaticCheckIntervalMinutes
    if (intervalMinutes <= LegacyAccountDto.INTERVAL_MINUTES_NEVER) {
        return null
    }

    return intervalMinutes.toLong()
}
```
`MailSyncWorker.kt:69-70`
```kotlin
private val LegacyAccountDto.isPeriodicMailSyncDisabled
    get() = automaticCheckIntervalMinutes <= 0
```

### Proposed fix
```kotlin
// LegacyAccountDto
val isPeriodicMailSyncEnabled: Boolean
    get() = automaticCheckIntervalMinutes > 0
```
Use it in both places; delete the local variants.

### Test plan (failing first)
Unit test: `automaticCheckIntervalMinutes = 0` -> `getSyncIntervalIfEnabled` returns null (fails today).

---

## I-11 fix(imap): parser fails on FETCH response with keyword containing `[`

**Labels:** `type: bug` | **Priority:** P1 | **Related:** #7589 (contains a ready-made failing test)

### Problem
An IMAP keyword containing `[` aborts folder sync permanently and silently (Cluster E in the triage report). The reporter supplied the failing parser test verbatim in #7589; it belongs in `ImapResponseParserTest` as the TDD starting point:
```kotlin
@Test
fun `FETCH response with keyword containing opening square bracket`() {
    val parser = createParserWithResponses(
        """* 1 FETCH (UID 23 FLAGS (\Seen [keyword))""",
    )

    val response = parser.readResponse()

    assertThat(response).hasSize(3)
    // ... full assertions in issue #7589 ...
    assertThatAllInputWasConsumed()
}
```

### Proposed fix
In the parser's atom/flag reading path, treat `[` as part of an atom when inside a parenthesized flag list (RFC 3501 `flag-keyword` is an atom; `[` only opens a response-code at specific positions). Scope the change to flag-list parsing to avoid disturbing `BODY[...]` handling.

### Acceptance criteria
The folder containing such a keyword syncs; the new test plus the existing parser suite pass.

---

## E-01 epic: delivery health state and status surface

**Labels:** `type: enhancement`, `tb-team` | **Priority:** P1 (do before E-02/E-03) | **Related:** #8830, #8831, #9246, #11059

Every failure in this report is invisible today (catch-and-log-only around FGS starts, `notify()`, push auto-disable, worker interrupts). Record per account: last scheduled sync, last successful sync, last push connect/disconnect + reason, last notification post/drop + reason. Sketch:
```kotlin
interface DeliveryHealthTracker {
    fun onSyncScheduled(accountUuid: String, intervalMinutes: Long)
    fun onSyncCompleted(accountUuid: String, success: Boolean, error: DeliveryError? = null)
    fun onPushStateChanged(accountUuid: String, state: PushState, error: DeliveryError? = null)
    fun onNotificationPosted(accountUuid: String, notificationId: Int)
    fun onNotificationBlocked(accountUuid: String, reason: BlockReason)
}
```
Backed by a small table or DataStore; consumed by (a) a "Delivery status" screen per account, (b) the in-app error notification system (#8831), (c) the "last synced" info notification (#9246), (d) exported debug logs. Acceptance: for any "no notifications" report, the status screen answers which link broke.

## E-02 epic: polling resilience under Doze/App Standby/OEM restrictions

**Labels:** `type: enhancement`, `type: platform` | **Priority:** P1 | **Related:** #7839, #9526, #9326, #10212, #7314

Current worker has no defenses (constraints only `CONNECTED` + storage, `MailSyncWorkerManager.kt:43-46`; no wake lock on the periodic path, `MessagingController.java:2334,2373-2382`). Work items:
1. Detect hostile conditions and tell the user:
```kotlin
val powerManager = context.getSystemService<PowerManager>()
val ignoresOptimizations = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
val bucket = context.getSystemService<UsageStatsManager>()?.appStandbyBucket
// bucket >= STANDBY_BUCKET_RARE or !ignoresOptimizations -> delivery-at-risk banner + OEM-specific guidance
```
2. Exact-alarm-assisted polling (opt-in, permission already in the manifest): alarm fires -> enqueue expedited one-time sync; keeps WorkManager as executor, alarm as trigger.
3. Switch `ExistingPeriodicWorkPolicy.REPLACE` to `UPDATE` where supported so cold starts stop resetting the period (`MailSyncWorkerManager.kt:69`).
4. Wake-lock or `setForeground`/expedited coverage for the sync run so it cannot be frozen mid-flight; bound `performPeriodicMailSync`'s `latch.await()` (`MessagingController.java:2349`) with a timeout that records a health event.

## E-03 epic: push connection resilience

**Labels:** `type: enhancement` | **Priority:** P1 | **Related:** #9520, #8751, #9216, #11211

Work items:
1. Liveness: on refresh-alarm fire after suspicious silence, bound the DONE round-trip and force reconnect on timeout (today a dead socket survives until `idleRefreshMinutes + 2 min`, `RealImapFolderIdler.kt:187-189`); evaluate TCP keepalive on the IDLE socket.
2. Network-change hardening: current recovery is only `registerDefaultNetworkCallback` (`ConnectivityManagerApi24.kt:41`); add capability-change handling and debounce reconnect storms (`ImapBackendPusher.reconnect` closes all connections per event, `ImapBackendPusher.kt:153-171`).
3. FGS-denied retry: `PushService.maybeStartForeground` swallows `ForegroundServiceStartNotAllowedException` (`PushService.kt:50-63`); instead schedule a retry (exact alarm or expedited work) and record health state.
4. Backoff with jitter in `ImapBackendPusher.onPushError` (flat 5/60 min today, `ImapBackendPusher.kt:190-226`).

## E-04 epic: honest notification state machine

**Labels:** `type: enhancement`, `tb-team` | **Priority:** P2 (feeds #11057 refactor) | **Related:** #10936, #10983, #11259

Replace in-memory-first state with DB-as-truth plus reconciliation against what is actually in the shade:
```kotlin
fun reconcile(account: LegacyAccountDto) {
    val shownIds = notificationManager.activeNotifications
        .filter { it.groupKey.contains(account.uuid) }
        .map { it.id }
        .toSet()
    val storedIds = notificationStore.activeNotificationIds(account.uuid)
    (shownIds - storedIds).forEach { notificationManager.cancel(it) }      // zombies
    (storedIds - shownIds).forEach { repostSilently(account, it) }         // lost posts
}
```
Run on process start and after mutations; make promotion (`NotificationDataStore.kt:164-183`) update-in-place instead of cancel+re-post where possible; align child vs summary delete-intent semantics (`NotificationActionIntents.kt:43-56`). This epic should land inside the NotificationSender migration (#11159-#11207) rather than on the legacy store.

---

# Assessment: can `:feature:notification` carry these fixes?

Requested follow-up: whether the new System Notifications architecture (`feature/notification/api` + `impl`) helps fix the issues above, and what it needs first. Verdict: **the architecture is the right vehicle, but today it cannot host new-mail notifications.** It structurally fixes the C8 class of bug (posting is a `suspend` call with a typed outcome instead of fire-and-forget), and its command outcomes are the natural hook for the delivery-health work (E-01). But the mail model is missing the message identity, the registry is process-local, the notifier re-creates two legacy bugs (no permission check, no group support), and the Java compat wrapper reintroduces the exact #10890 pattern. Recommendation: fix the legacy P0s now (I-01 to I-11); land the N-drafts below inside the existing #11159-#11207 InboxStyle chain; only then widen `use_notification_sender_for_system_notifications` beyond TB debug.

## What the new path already gets right

- **Synchronous, awaitable posting.** `NotificationNotifier.show` is `suspend` and `AndroidSystemNotificationNotifier.show` registers + posts inside the call (`impl/.../receiver/AndroidSystemNotificationNotifier.kt:31-38`); `DefaultNotificationSender.send` emits per-command outcomes in a cold flow, so a collecting caller has happens-before on the post. This is the structural fix for C8, if call sites collect properly (see N-05).
- **Typed failure outcomes.** `DisplaySystemNotificationCommand.execute` returns `Outcome.failure(UnsupportedCommand/CommandExecutionFailed)` instead of silently dropping - exactly the shape E-01 (delivery health) needs to record blocked/dropped posts.
- **The mail model exists.** `MailNotification.NewMailSingleMail` / `NewMailSummaryMail` / `Fetching` / `Sending` / `SendFailed` are already modeled (`api/.../content/MailNotification.kt`), with channels, lockscreen `publicVersion` mapping (`asLockscreenNotification`), severity, and a style DSL (`BigTextStyle`, `InboxStyle`) that the notifier renders (`AndroidSystemNotificationNotifier.kt:setNotificationStyle`).
- **KMP + fakes + real test coverage** (`impl/src/commonTest/**`), which the legacy pipeline never had.

## Gaps (each becomes an N-draft below)

| Gap | Evidence | Consequence |
|-----|----------|-------------|
| No message identity in the model | `NewMailSingleMail` fields: `accountUuid, accountName, summary, sender, subject, preview, group` - no `MessageReference`, no folder | Reply/Delete/MarkAsRead actions cannot target a message; sync cannot clear a notification when the mail is read elsewhere; registry dedup by data-class equality collides for identical-looking mails |
| Process-local registry, colliding ids | `DefaultNotificationRegistry`: in-memory maps + `rawId = AtomicInt(0)` incremented per process | After process restart the registry is empty (cannot dismiss what the old process posted) and ids restart at 1 - which is `PUSH_NOTIFICATION_ID = 1` in the legacy scheme (`NotificationIds.kt:6`), so the first new-path notification would overwrite the push FGS notification while both pipelines coexist |
| No permission/failure handling in the notifier | `AndroidSystemNotificationNotifier.show` calls `notificationManager.notify(...)` with no `areNotificationsEnabled()` check and no `SecurityException` handling | C9 carried over into the new world: denied POST_NOTIFICATIONS = silent no-op reported as `Success` |
| No group/summary/appearance rendering | `toAndroidNotification()` never calls `setGroup`/`setGroupSummary`/`setGroupAlertBehavior`; no `setColor`, no avatar/`setLargeIcon`, no silent/alert-once handling; `NewMailSummaryMail` does not populate its `InboxStyle` | Grouped new-mail UX (the whole point of #11159/#11205) cannot render; every message alerts independently |
| Compat wrapper is fire-and-forget | `NotificationSenderCompat.send` = `.launchIn(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))` (`api/.../sender/compat/NotificationSenderCompat.kt`) | The exact #10890 mechanism, rebuilt: a Java call site (e.g. `MessagingController`) posting through compat loses the happens-before guarantee the suspend API provides |
| Settings not consulted | No consumption of `messageActionsOrder`/`messageActionsCutoff`, quiet time, lock-screen visibility mode, or pre-O sound/vibrate/light anywhere in `impl` | C11 would need fixing twice; migrating without this regresses user configuration |
| Foreground detection is a stub | `DisplaySystemNotificationCommand.isAppInBackground = { /* TODO(#9391) */ false }` | The "suppress system notification while app is visible" rule for hybrid notifications cannot work yet |
| Channel ownership split | New path only references `channel.id` strings (`NotificationChannel.Messages(accountUuid, suffix)`); channel creation/recreation stays in legacy `NotificationChannelManager` | Id formats must stay in lockstep across modules; a drift silently posts to a dead channel |

## N-01 feat(notification): add MessageReference to MailNotification model and action creators

**Labels:** `type: enhancement`, `tb-team` | **Blocks:** any new-mail migration | **Related:** #11159, #9419

`NewMailSingleMail` must carry the message identity so actions and dismissal can work:
```kotlin
data class NewMailSingleMail(
    override val accountUuid: String,
    val messageReference: NotificationMessageReference, // new: KMP-safe value type wrapping account/folder/uid
    val accountName: String,
    // existing fields ...
)
```
`DefaultSystemNotificationActionCreator` then builds the same service intents the legacy `K9NotificationActionCreator` builds (unique `Intent.data` URI per message, immutable PendingIntents), and the sync path can dismiss by reference. Registry dedup becomes identity-based instead of content-based. Test-first: action creator test asserting the PendingIntent carries the reference; model test that two different messages with identical sender/subject do not dedup.

## N-02 fix(notification): persist NotificationRegistry and make id allocation collision-safe

**Labels:** `type: bug`, `tb-team` | **Related:** #11259, E-04

Current registry (`DefaultNotificationRegistry`): in-memory `MutableStateFlow` maps + `AtomicInt` starting at 0. Required:
1. **Id space:** while both pipelines coexist, allocate from a range that cannot hit legacy `NotificationIds` (push FGS id 1, per-account message id blocks). Simplest: offset base (e.g. `1_000_000 +`) or reuse the legacy per-account id math behind the registry.
2. **Persistence:** back the registry with the existing `notifications` table (`K9NotificationStore`) or a successor so a restarted process can enumerate, dismiss, and reconcile.
3. **Reconciliation hook (E-04):** on init, diff registry against `NotificationManagerCompat.getActiveNotifications()` - cancel zombies, re-adopt survivors.
Test-first: registry test that `register` after simulated restart does not hand out an id currently visible in the (fake) NotificationManager.

## N-03 fix(notification): permission + failure handling in AndroidSystemNotificationNotifier

**Labels:** `type: bug`, `tb-team` | **Related:** I-05 (legacy twin)

```kotlin
override suspend fun show(notification: SystemNotification): NotificationId {
    if (!notificationManager.areNotificationsEnabled()) {
        throw NotificationBlockedException(reason = BlockReason.PermissionDenied)
        // or: return a sealed ShowResult so DisplaySystemNotificationCommand maps it to Outcome.failure
    }
    // existing register + notify, with SecurityException mapped to a typed failure (broken sound URI case)
}
```
The command must map these to `Outcome.failure(...)` so `Success` is only emitted when the notification is actually eligible to display, and E-01 can count drops. Test-first: fake manager reporting disabled -> command outcome is failure, not success (fails today).

## N-04 feat(notification): group/summary/appearance support in the new system notifier

**Labels:** `type: enhancement`, `tb-team` | **Related:** #11159, #11205, #11160

`toAndroidNotification()` additions, driven by the model's existing `NotificationGroup`:
```kotlin
group?.let { group ->
    setGroup(group.key.value)
    setGroupSummary(this@toAndroidNotification is MailNotification.NewMailSummaryMail)
    setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
}
setOnlyAlertOnce(!shouldAlert) // batch semantics: first message alerts, rest are silent
setColor(accountColor)
avatarIcon?.let(::setLargeIcon)
```
Plus: populate `systemNotificationStyle { inbox { ... } }` in `NewMailSummaryMail.invoke` (it is `Undefined` today), and carry account color/avatar in the model. This IS the #11160/#11205 feature-flagged InboxStyle work; land it there. Test-first: notifier test asserting group key + summary flag on the built `Notification`.

## N-05 fix(notification): NotificationSenderCompat must not fire-and-forget

**Labels:** `type: bug`, `tb-team` | **Related:** I-01, #10890 (the lesson)

Current compat wrapper reintroduces the #10890 mechanism for Java call sites:
```kotlin
fun send(notification: Notification, onResultListener: OnResultListener) {
    notificationSender.send(notification)
        .onEach { outcome -> onResultListener.onResult(outcome) }
        .launchIn(scope) // scope = SupervisorJob() + Dispatchers.Main.immediate
}
```
Replace with a blocking variant for background call sites (the sync thread is not the main thread, so blocking is legal there) or an explicit completion callback the caller must wait on:
```kotlin
@WorkerThread
fun sendBlocking(notification: Notification): List<NotificationCommandOutcome<Notification>> =
    runBlocking { notificationSender.send(notification).toList() }
```
Guardrail: forbid `launchIn`-style dispatch for `SystemNotification` sends via lint or API shape. Test-first: compat test asserting the notifier's `show` completed before `sendBlocking` returns.

## N-06 feat(notification): honor configured actions, quiet time, and appearance in the new path

**Labels:** `type: enhancement`, `tb-team` | **Related:** I-09, #11076, #11250

- Build `MailNotification` action sets from `NotificationPreferenceManager` (`messageActionsOrder`, `messageActionsCutoff`, `isSummaryDeleteActionEnabled`) instead of the hard-coded `setOf(Reply, MarkAsRead, Delete, Archive, MarkAsSpam)` - single AND summary, so #11076 is fixed once, in one place.
- Route quiet-time and appearance decisions (silent, lock-screen visibility mode, pre-O sound/vibrate/light) through a single policy component consulted by the notifier, replacing the two duplicated `isQuietTime` extensions in the legacy path (`K9NotificationStrategy.kt:87-100`, `NotificationHelper.kt:156-170`).
- Decide channel ownership: either move `NotificationChannelManager` behind `:feature:notification:api` or freeze the id contract (`messages_channel_{uuid}{suffix}`) with a shared constant + test.

## N-07 chore(notification): implement isAppInBackground TODO and define flag rollout plan

**Labels:** `type: task`, `tb-team` | **Related:** #9391, #11259, #11160

- Implement `isAppInBackground` via `ProcessLifecycleOwner` (the stub returns `false` today, so hybrid notifications always behave as "app visible" except for Fatal/Critical severities).
- Publish the migration ladder the flags follow (per #11259's ask): N-01..N-06 land -> `use_notification_sender_for_system_notifications` on in **daily** -> beta with the InboxStyle flag chain (#11160/#11205) -> release -> delete legacy creators (#11206). Keep K-9 and TB flag matrices in lockstep so debug testing exercises what users run (C14).

---

*Local drafts only. No issues were created, commented, or modified on GitHub.*
