# Notification Issues - Triage Report

- **Date:** 2026-07-17
- **Repository:** `thunderbird/thunderbird-android`
- **Method:** Read-only `gh` search across open issues (title terms: notification, notify, push, "new mail", sync,
  background, battery, doze, idle, poll, polling, fetch; body phrases: "no notification(s)", "notifications stopped")
  plus closed issues with `notification` in the title updated since 2025-01-01. 123 unique open issues matched; 63
  recently-updated closed issues were reviewed for context. Bodies and comment threads of the ~100 relevant issues were
  read.
- **No mutations were performed on GitHub.**

---

## 1. Executive summary

- Notification complaints are the project's **top user-facing problem**. The team's own investigation issue (#10963)
  states notification issues have been **up to 40% of all user complaints**; epic #11057 calls notifications and sync "
  our top complaints from users".
- The team already has an internal **"Notification Revamp / Notification Improvements" project** in flight: #11057 (
  refactor epic), #11056 (high-priority bug fixing), #11059 (SPIKE on delivery/delay), #10990 (technical document),
  #11250 (settings RFC), #11259 (NotificationSender architecture audit), #11159/#11160/#11205/#11206/#11207 (InboxStyle
  refactor chain), #11083 (global folder sync/push SPIKE). New triage work should attach to these umbrellas instead of
  duplicating them.
- The open bugs fall into **8 clusters**. The two P0 clusters ("mail is fetched late or never" and "mail is fetched but
  no notification is posted") are what users mean by "notifications not arriving". Everything else (actions, settings,
  content) is secondary but drags ratings.
- A large fraction of "not arriving" reports trace to three recurring mechanisms visible in the reports themselves:
    1. **Exact-alarm permission (`SCHEDULE_EXACT_ALARM`) not granted or never requested**, which silently breaks Push
       IDLE refresh (evidence in #8549, #8434, #8849, #10098).
    2. **OEM/OS background restrictions** (Samsung "Optimized" battery mode, vivo, Xiaomi, swipe-away kills) suspending
       WorkManager sync for hours (evidence: log analysis in #9526 shows an 8-hour gap where no `MailSyncWorker` ran;
       #10955; #9829; #8751).
    3. **Push (IMAP IDLE) connection lifecycle bugs**: dead sockets after network switch are not detected (#9520), the
       service does not resume correctly after process kill (#8751) or app update (#5593), and the foreground service
       can crash on start on some Android 16 builds (#9728).
- There is also a **true notification-pipeline regression** independent of sync: #10890 (since TB ~17.0, mail arrives
  and the badge updates but no system notification is posted). This is the most actionable P0 bug because sync is proven
  working in the report.
- Maintainer comment in #10662 confirms the team's assessment: the problem is "related to the foundational core of K9
  and how it was created to handle folder syncing and push notifications... It'll take us some time to rebuild that."

---

## 2. Cluster overview and priorities

| # | Cluster                                                | Priority  | Open issues                                                                                                                                                  | Anchor issue |
|---|--------------------------------------------------------|-----------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------|
| A | Mail not fetched (poll scheduling unreliable)          | **P0**    | #7839, #9898, #8849, #10212, #9829, #9526, #9326, #11211                                                                                                     | #7839        |
| B | Push (IMAP IDLE) unreliable                            | **P0**    | #9520, #8751, #5593, #9216, #9728, #10208, #8434, #9239                                                                                                      | #9520        |
| C | Mail fetched but no notification posted                | **P0**    | #10890, #10098, #10662-class, (#11017 closed/worksforme)                                                                                                     | #10890       |
| D | Notification actions broken / notification not cleared | **P1**    | #10936, #10713, #11065, #10089, #11076, #11130                                                                                                               | #10936       |
| E | Silent failure: sync errors invisible to user          | **P1**    | #9424, #4227, #5315, #7589, (#8986 closed) + epic #8831 (#9246-#9249)                                                                                        | #9424        |
| F | Settings / onboarding traps that disable delivery      | **P1**    | #7554, #8712, #10079, #11083, #8213, (#8462, #8501, #8549, #8726 closed)                                                                                     | #7554        |
| G | Wrong/ghost/duplicate notifications, content bugs      | **P2**    | #7768, #10722, #7200, #9639, #6813, #9154, #8534, (#10983, #10256, #7244, #9645 closed)                                                                      | #7768        |
| H | Enhancements changing the delivery model               | **P2/P3** | #5165 (UnifiedPush), #826 (IMAP NOTIFY), #5513 (<15 min poll), #8830 (push state UI), #6819, #5808, #7257, #6091, #3908, #3463, #1376, #1144, #3267-adjacent | #5165        |
| - | Team meta/refactor tracking (do not duplicate)         | n/a       | #11057, #11056, #11059, #10990, #11250, #11259, #11159, #11160, #11205, #11206, #11207, #11083, #9417, #9418, #9419                                          | #11057       |

Priority rationale: A/B/C are the literal "notifications not arriving" complaint and by the team's own numbers drive ~
40% of complaints and the Play Store rating decline (#11056). D is highly visible daily friction. E converts transient
failures into permanent silent ones (users only discover missing mail days later). F causes "worked on K-9, broke on
Thunderbird" cohorts. G is polish. H is the strategic fix path.

---

## 3. Cluster A - Mail not fetched: poll scheduling unreliable (P0)

The app polls with a WorkManager periodic worker (min 15 min). Reports consistently show the worker not firing while the
device is idle, then firing on unlock/app-open.

| Issue  | Title (abridged)                                    | Age / activity                                          | Evidence highlights                                                                                                                                                                          |
|--------|-----------------------------------------------------|---------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| #7839  | Folder polling is not reliable any more             | 2024-05, 19 comments, still repro on TB 16.1/Android 16 | Screen off => no polling; polls happen on wake/unlock/app open. Started around 6.802. Battery unrestricted.                                                                                  |
| #9898  | Emails not fetched with set delay (2 h interval)    | 2025-10, 4 comments                                     | 2 h interval never fires; manual refresh works. "Started after the latest update."                                                                                                           |
| #8849  | Does not fetch on 24 h poll interval                | 2025-02, 17 comments, `status: answered`                | Never polls at long intervals. Commenters point at `SCHEDULE_EXACT_ALARM` and at notification toggle defaults.                                                                               |
| #10212 | Poll on mobile data not as scheduled                | 2025-12                                                 | Polls fine on Wi-Fi, erratic on mobile data (network-constraint / doze interaction).                                                                                                         |
| #9829  | No background sync after swiping app away           | 2025-09                                                 | AOSP 15; process kill stops all background fetching.                                                                                                                                         |
| #9526  | Samsung A16/OneUI 8: notifications erratic          | 2025-07, 5 comments                                     | **Log shows zero WorkManager activity 21:13 -> 05:18** while other apps notified. Reporter isolates Samsung battery "Optimized" mode; "Unrestricted" fixes it. Exact duplicate #9527 closed. |
| #9326  | Notifications not showing on Android 15 (S24 Ultra) | 2025-06, 5 comments                                     | 15-min fetch, battery unrestricted, still nothing for 5 h.                                                                                                                                   |
| #11211 | Battery Usage                                       | 2026-06                                                 | Flip side of the same subsystem (kept for correlation: fixes for A/B must not regress battery).                                                                                              |

Closed context: #10955 (vivo, polls "very very late", closed unconfirmed), #11017 (closed `qa: worksforme`; QA could not
reproduce on stock devices, which is consistent with the OEM-restriction theory).

**Correlation:** #7839 ~ #9898 ~ #8849 ~ #10212 ~ #9326 are very likely the same root behavior (periodic work deferred
by Doze/App Standby/OEM killers, no exact-alarm fallback, no catch-up run on wake). #9526/#9527/#10955 are the
OEM-aggressive variant. #9829 is expected Android behavior after force-stop but shows the app never recovers until
manually opened.

---

## 4. Cluster B - Push (IMAP IDLE) unreliable (P0)

| Issue  | Title (abridged)                                  | Age / activity                           | Evidence highlights                                                                                                                                                                                                          |
|--------|---------------------------------------------------|------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| #9520  | Push receiver stops after Wi-Fi<->cellular switch | 2025-07, logs attached                   | IDLE socket dies on network change and is never detected; recovery only via airplane-mode toggle or connection refresh. Reproduced on 3 devices/Android 12-16.                                                               |
| #8751  | Push does not restart after process kill          | 2025-01, 20 comments                     | FGS restarts (bell icon returns) but IDLE connections are not re-established; tapping the FGS notification revives it. Maintainer (cketti) confirms connections die on kill and must be re-established.                      |
| #5593  | No mail after app update until app opened         | 2021-08, 9 comments                      | Push-only accounts: Play Store update kills process; nothing restarts push until manual open (no `MY_PACKAGE_REPLACED` recovery path).                                                                                       |
| #9216  | Push delivery broken in 11.0b2                    | 2025-05                                  | Beta regression report; per reporter works in prod build. Overlaps delay cluster.                                                                                                                                            |
| #9728  | Android 16 crash enabling push (FGS specialUse)   | 2025-09                                  | `SecurityException: Starting FGS with type specialUse ... requires FOREGROUND_SERVICE_SPECIAL_USE` on crDroid/A16, app 12.1. App unusable until data cleared.                                                                |
| #10208 | Push option disables itself                       | 2025-12, 3 comments                      | Folder push toggle reverts on reopen; no notifications. Team member reproduced on long-installed beta but not fresh install. Related historical behavior: #5594 (auto-disable when server lacks IDLE, closed).               |
| #8434  | Delay in receiving notifications                  | 2024-10, 27 comments, `status: answered` | Long thread. Key exchange: kewisch states the **alarms permission "is a necessity to make notifications work reliably"**; several users report the persistent "waiting" notification missing until alarm permission granted. |
| #9239  | New mail notifications not timely                 | 2025-05, 17 comments, `status: answered` | Even with push enabled, up to 15 min delay for some; workaround for some users: set "Refresh IDLE connection" from 24 min to 2 min. Cross-linked by users to #9216/#8434.                                                    |

Closed context: #8549 (push dead after importing K-9 settings on A15; root cause visible in thread: **the exact-alarm
permission prompt never appears on the import path**; toggling push off/on or reinstalling until the prompt shows fixes
it), #8810 (A9, closed), #5594 (auto-disable without informing user; superseded by #8830 epic).

**Correlation:** #9520 ~ #8751 ~ #5593 share one theme: IDLE connection lifecycle is not resilient (no dead-socket
detection, no restart-on-kill/update). #8434 ~ #9239 ~ #9216 form the "push exists but behaves like 15-min poll" delay
theme, most plausibly the IDLE refresh alarm falling back to inexact scheduling when `SCHEDULE_EXACT_ALARM` is not
granted (direct evidence in #8549/#8434 threads). #9728 and #10208 are setup-path breakages that turn push off entirely.

---

## 5. Cluster C - Mail fetched, notification not posted (P0)

This cluster is distinct from A/B: sync works, the message row appears, but no system notification fires. These are true
notification-pipeline bugs.

| Issue                       | Title (abridged)                                     | Evidence highlights                                                                                                                                                                                                                                                |
|-----------------------------|------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| #10890                      | Notifications for new emails not showing up any more | Poll-based account. Mail arrives, **unified inbox widget badge updates**, no notification. Reporter (cremor, also author of #7839) explicitly separates it from fetch issues; regression window "first version I saw it with was Thunderbird 17.0".                |
| #10098                      | New mail notifications not working                   | Push configured, persistent notification says "Waiting for new emails", mail visible in app, no notifications. Thread surfaces quiet-time bug #9629 (13.0: quiet time active even when displayed disabled) and Samsung memory "excluded apps" workaround.          |
| #10662 (closed)             | New mail notifications not working 2                 | New install: folders that have never been loaded locally (empty Junk with "load up to 100 more") produce **no notification until the user manually loads the folder once**. Maintainer (Alecaddd) confirms related to #10098 and to the K-9 folder-sync/push core. |
| #11017 (closed, worksforme) | No notification despite proper sync/permissions      | Same complaint; QA could not reproduce on their devices.                                                                                                                                                                                                           |

**Correlation:** #10890 is probably a distinct regression (17.0 window) and deserves its own bisect.
#10098/#10662/#11017 mix causes: quiet-time state bug (#9629), never-synced-folder gating, and OEM restrictions.
Recommend splitting #10098 into confirmed sub-causes after code investigation rather than closing as duplicate.

---

## 6. Cluster D - Notification actions broken / notifications not cleared (P1)

| Issue  | Title (abridged)                                          | Evidence highlights                                                                                                                                                                                           |
|--------|-----------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| #10936 | Notifications remain in shade after deleting              | Play-review sourced; delete/read from notification works but the notification immediately reappears; suspected state desync between local action and next sync pass. Team links it to #10713. Versions 16-18. |
| #10713 | Actions stop working when Message List Widget added (A16) | Reproducible toggle: add widget => notification "Mark as Read"/"Delete" do nothing; remove widget => works. `qa: worksforme`.                                                                                 |
| #11065 | Interacting with notification does not always clear it    | If the action is a no-op (message already read), the notification never clears.                                                                                                                               |
| #10089 | Delete from notification does not always delete           | Intermittent; `status: answered`.                                                                                                                                                                             |
| #11076 | "Notification actions" configuration not applied          | Config UI (19.0, A16 Samsung) has no effect; always shows default read/answer/favorite set. Settings write path vs notification builder read path mismatch suspected.                                         |
| #11130 | Cannot enable delete button (K-9 19.2)                    | `notificationQuickDelete=NEVER` trap: the preference UI to change it does not exist in K-9 build, only the Thunderbird code path; user must hand-edit prefs.                                                  |

Closed context: #9317 (delete works but notification stays, A16; closed answered), #10494 (16.0 regression:
delete-confirmation checkbox state reset on upgrade; closed fixed), #5691 (2021 same symptom as #9317), #10983 (
dismissing summary re-posts individual notifications; closed), #3530 (configurable actions design; closed).

**Correlation:** #10936 ~ #9317 ~ #5691 (delete leaves notification) and #11065 (no-op leaves notification) look like
one dismissal-logic defect family. #10713 is its own A16+widget interaction. #11076 ~ #11130 are settings-plumbing bugs,
likely introduced with the new settings UI.

---

## 7. Cluster E - Silent sync failure: errors invisible (P1)

Users lose mail delivery and are never told. This multiplies every other cluster because failures look like "no new
mail".

| Issue          | Title (abridged)                                | Evidence highlights                                                                                                                                              |
|----------------|-------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| #9424          | Gmail stops syncing, fails silently             | Works after re-add, then silently dies again. Log shows sync completing "0 of 1" without surfaced error. Comment thread suspects OAuth/F-Droid signing variants. |
| #4227          | K-9 silently stopped IMAP sync                  | `NumberFormatException` aborts folder sync forever, only visible as tiny grey status line.                                                                       |
| #7589          | Sync fails on IMAP keyword containing `[`       | Parser bug with ready-made failing test in the issue body; folder never syncs again => never notifies.                                                           |
| #5315          | Mail stuck in Outbox without notification       | SMTP auth failure notifies nothing while IMAP side does.                                                                                                         |
| #8986 (closed) | Missing notification when IMAP connection fails | Fixed/closed 2025-04.                                                                                                                                            |

Team response already planned: epic #8831 (in-app error notifications) with tasks #9246 (last-synced info), #9247 (OAuth
failure), #9248 (wrong credentials), #9249 (sync failure), plus #8830 (push state surface) and #5425 (no-network refresh
feedback). **Recommendation:** raise the priority of #9249/#8830; they are the mitigation for this entire cluster.

---

## 8. Cluster F - Settings/onboarding traps that disable delivery (P1)

| Issue  | Title (abridged)                                                    | Evidence highlights                                                                                                                      |
|--------|---------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| #7554  | Ask for notification permission when enabling account notifications | Only asked during onboarding; decline once => notifications silently dead forever. Has `priority: medium` + `good first issue`.          |
| #8712  | Notification permission not re-prompted after backup restore        | Restored installs never regain POST_NOTIFICATIONS.                                                                                       |
| #10079 | Sync all folders automatically                                      | 10 comments, active. Per-folder sync opt-in means new folders never sync => never notify. Users with 70-600 folders. Feeds SPIKE #11083. |
| #11083 | SPIKE: global folder sync/push                                      | Team acknowledgement of #10079.                                                                                                          |
| #8213  | Wizard should offer IMAP push                                       | Push discoverability; most users never find it.                                                                                          |

Closed context that defined this cluster: #8462 (8.0 removed push/poll folder classes; per-folder toggles painful,
notify-on-everything default), #8501 (desktop import leaves notifications off), #8549 (import path skips alarm
permission => push dead), #8726 (notifications default-on decision), #10494 (setting reset on upgrade).

**Correlation:** #7554 ~ #8712 (permission lifecycle), #10079 ~ #11083 ~ #8213 (delivery opt-in friction). These are
the "user thinks notifications are on but a hidden precondition is off" family; they directly generate Cluster A/B/C bug
reports that QA then cannot reproduce (`qa: worksforme` pattern on #11017, #10713, #9154).

---

## 9. Cluster G - Wrong/ghost/duplicate notifications and content (P2)

| Issue                                     | Title (abridged)                                         | Notes                                                                                                                                  |
|-------------------------------------------|----------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| #7768                                     | Duplicate notification when desktop filter moves mail    | Notify-on-move; same mechanism as #10722.                                                                                              |
| #10722                                    | Notifications after moving items to archive              | Moves treated as new mail.                                                                                                             |
| #7200                                     | Notification timestamp = emission time, not message time | Old mail moved/synced late looks "new now"; amplifies #7768/#10722 annoyance.                                                          |
| #9639                                     | Hebrew body text rendered incorrectly in notification    | RTL handling in preview extraction. Related closed: #10256 (HTML entities shown).                                                      |
| #6813                                     | WearOS vibrates despite phone settings                   | Channel/bridged-notification config. Related closed: #7271.                                                                            |
| #9154                                     | No sound (HONOR, `qa: worksforme`)                       | Likely channel/quiet-time/OEM; keep grouped with #10066 (closed FAQ workaround: toggling quiet time revives sound) and #9645 (closed). |
| #8534                                     | Notification LED settings inconsistent                   | Pre-O style setting vs channel reality.                                                                                                |
| #3949-adjacent / #8049 (closed, verified) | Outbox IllegalStateException notification spam           | Fixed; example of error-notification noise.                                                                                            |

**Correlation:** #7768 ~ #10722 ~ #7244(closed) ~ #10983(closed) with #7200 as amplifier: the "notify for messages that
are not actually new arrivals" family, rooted in how sync decides notification-worthiness.

---

## 10. Cluster H - Enhancements that change the delivery model (P2/P3)

Strategic items, several of which are the real fix for Clusters A/B:

- **#5165 Use UnifiedPush** (2021, active interest): server-independent push without persistent sockets on FCM-free
  devices.
- **#826 IMAP NOTIFY (RFC 5465)** (2015, `rls` label): multi-folder push over one connection; reduces connection count
  and battery.
- **#5513 Poll intervals < 15 min via foreground service**; #9239's Samsung-comparison complaint is exactly this gap.
- **#8830 Display push state to user** (team epic): mitigates B by making failures visible.
- **#5808 Enable BootCompleteReceiver when notifications enabled**: restores notification capability after reboot for
  poll-only users.
- **#7257 Sync on app start**, **#6091 notification on manual check**, **#6819 push/poll per connection type**, **#3908
  spam-folder notifications**, **#3463/#1376/#1144 notification filtering**, **#2314/#7089-class avatar options** (
  partially shipped, follow-up #10750 closed).

---

## 11. Duplicate / correlation matrix (candidates for merge or cross-linking)

| Keep (canonical) | Close or cross-link as duplicate/subset                     | Confidence | Basis                                                                                                          |
|------------------|-------------------------------------------------------------|------------|----------------------------------------------------------------------------------------------------------------|
| #9526            | #9527 (already closed as clone)                             | Confirmed  | Identical text, same reporter.                                                                                 |
| #7839            | #9898, #10212; large overlap with #8849, #9326              | High       | Same symptom (screen-off => no poll; wake => poll), different intervals/devices.                               |
| #9526            | #10955 (closed), OEM variants inside #7839/#9326 threads    | Medium     | Vendor battery management; #9526 has the isolating log evidence.                                               |
| #10098           | #11017 (closed worksforme), #10662 (closed), #8537 (closed) | Medium     | Maintainer linked #10662 to #10098; #11017 asked by team if same as #10098. Split by sub-cause before closing. |
| #8434            | #9239 delay reports; parts of #9216                         | Medium     | Same "push behaves like poll" delay; users cross-link them.                                                    |
| #9520            | Network-switch reports inside #8751, #9239 threads          | Medium     | Dead IDLE socket after connectivity change.                                                                    |
| #10936           | #9317 (closed), #5691 (closed), overlap with #11065         | High       | Delete/read action leaves or re-posts notification.                                                            |
| #11076           | #11130                                                      | Medium     | Both: notification-action settings not effective; different app flavors/paths.                                 |
| #9424            | #4227                                                       | Medium     | Silent permanent sync failure; different trigger, same failure mode (E).                                       |
| #7768            | #10722, #7244 (closed)                                      | High       | Notification on server-side move.                                                                              |
| #10079           | #11083 (team SPIKE supersedes)                              | Confirmed  | #11083 explicitly created from #10079.                                                                         |

Notes: none of these should be closed blindly; A/B/C symptoms are underdetermined without logs, which is why several
were closed `worksforme` before. Suggested process: convert the canonical issue of each family into a tracked bug with a
diagnosis checklist (permission state, OEM, push vs poll, quiet time), then close others against it.

---

## 12. Prioritized action list (triage output)

**P0 (drive Play rating & 40% of complaints; attach to #11056/#11059):**

1. #10890 - bisect 16.1 -> 17.0 notification pipeline regression (mail present, badge updates, no notification). Most
   isolated, most actionable.
2. #9520 + #8751 + #5593 - push connection lifecycle: detect dead sockets on connectivity change, re-establish after
   process death/app update.
3. #7839 family - poll scheduling under Doze/OEM restrictions: add catch-up sync + exact-alarm assisted scheduling +
   user-visible health state.
4. #8549-class / #8434 / #10098 - make `SCHEDULE_EXACT_ALARM` a hard, visible precondition for push (request on every
   enable path, warn when revoked).
5. #9728 - FGS `specialUse` start crash on Android 16 (verify manifest permission + fallback handling).
6. #10208 - push toggle self-disabling (settings write/read path).

**P1:**

7. #10936/#11065/#10713 - action handling + dismissal state machine; investigate A16 widget interaction.
8. #11076/#11130 - notification-actions settings plumbing.
9. #9249/#8830 (epic #8831) - surface sync/push errors; kills Cluster E and reduces false bug reports.
10. #7554/#8712 - notification permission re-request flows.
11. #10079/#11083 - global sync/push defaults (also fixes #10662-class "never-synced folder" gap).
12. #7589 - IMAP parser bug with ready failing test (cheap, permanent silent-failure fix).

**P2:** #7768/#10722/#7200 (notify-on-move + timestamps), #9639, #6813, #9154, #8534, #10256-class content fixes.

**P3 / strategic:** #5165 UnifiedPush, #826 IMAP NOTIFY, #5513 sub-15-min foreground polling, #6819, #5808, #7257,
#6091, notification filtering (#1144/#1376/#3463), #3908.

---

## 13. Issues matched by search but excluded from notification triage

- Thunderbird account-sync feature (different "sync"): #8033, #8036, #8037, #8038, #8039, #8040, #8041.
- Message-list/sync correctness without notification impact: #8578 (unified inbox syncs all folders), #5869, #5554,
  #5450, #5093 (NPE aborts folder sync; borderline Cluster E), #7114, #10972 (fetch blocked by offline account;
  borderline A), #2295, #2154, #3649, #3369, #2871, #2870, #1610, #4973, #4854, #3042, #4572, #4571, #5540, #6541,
  #6460 (push memory side-effect; keep an eye for battery narrative), #6367, #5062, #1379, #11114, #8990.
- DSN/MDN "delivery notification" (email feature, not device notifications): #5137, #3901, #1453.
- Misc: #5601 (mute thread), #2677, #2239, #1000, #1135, #764, #5425 (UX toast; grouped under E mitigations), #3267.

## 14. Recently closed issues that inform triage

- Fixed/verified: #10494 (settings reset regression), #8049 (outbox error spam), #10750 (avatar toggle), #11191 (actions
  screen scrolling), #8986 (connection-failure notification), #10066/#10007-class (quiet-time sound workaround
  documented).
- Closed as worksforme/unreproducible (pattern: OEM/permission dependent): #11017, #10955, #10662, #9154-adjacent
  reports, #10713 (`qa: worksforme` label but still open).
- Closed process/meta: #10963 (investigation doc done), #10991, #9527 (dup of #9526), #8549/#8462/#8501 (8.0 migration
  wave, addressed), #9317, #5691, #10983, #10256, #9645, #7244, #7271, #3530, #3082, #5594, #8537, #8810, #8726.

---

*Prepared read-only from GitHub data; no issues were commented on, edited, labeled, or closed. Titles abridged; em
dashes in original titles rendered as plain dashes.*
