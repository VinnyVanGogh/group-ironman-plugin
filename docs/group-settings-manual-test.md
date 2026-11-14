# Group settings: two-account manual test

What only a person can check: two real clients, one Group Ironman group.
Allow about 25 minutes. Nothing here automates game input.

## Setup

- Two accounts in the **same** Group Ironman group. Below, **A** is the one
  that will lead and **B** the other.
- Two development clients, each with its own `.runelite`, so their configs
  stay apart:

  ```
  ./gradlew run                                   # client A
  ./gradlew run -PaltHome=$HOME/gimu-alt-home     # client B
  ```

  Log in with the Jagex-account steps in the RuneLite wiki
  (<https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts>).
- In both: GIM Utilities on, *Sync with my group* on, *Share group events*
  on. In A only: *Post to Discord* on with a test webhook.

## 1. Leader from the key

1. On A, with no key yet, press **Create group key**, then **Copy group
   key**. Paste it on B with **Paste group key**. Both press **Join** if asked.
2. Open the **Settings** tab on both.
   - A: *Leader: A*, *You are the leader*, a **Forget leader** button and an
     *Editors* list with B.
   - B: *Leader: A*, *You are a member: view only*. No *Claim leadership*
     button.

*If your group already has a key from before this update:* the tab shows
**Claim leadership** on both. Press it on A only; within a few seconds B
shows *Leader: A* and the button disappears.

## 2. A non-editor cannot change anything

1. On B, nothing in *Your group's settings* can be ticked or typed into, and
   there is no **Save to group** button. (Until A has saved once, the section
   is not shown on B at all.)

## 3. First save seeds from the leader

1. On A, in RuneLite's own config for GIM Utilities, set *Level ups from
   level* to **60** and tick *Discord: which events* → *Level up* and
   *Overlays: which events* → *Level up*.
2. In A's Settings tab the editor shows *Nothing saved yet: these are your
   own settings*, with 60 under *Level ups from level*. Press **Save to
   group**: *Saved and sent to your group.*
3. On B within a few seconds: *Version 1, saved by A*, *Recent changes: A set
   N settings*, and the read-only values match A's (60, Level up ticked).

## 4. A change reaches the other client

1. On A, set **Post every N levels** to **5**, press **Save to group**.
2. On B: *Version 2*, and *A changed Post every N levels from 1 to 5* at the
   top of *Recent changes*.
3. In RuneLite's config on B, *Level ups from level* still shows B's own old
   value: the group value is used, the member's own is kept for if the group
   ever has none.

## 5. Editors

1. On A, *Editors* → **Make editor** next to B.
2. On B: *You are an editor*; the settings become editable with **Save to
   group**. Change *Kill count milestone every* and save.
3. On A: the new value, *saved by B*, and B's line in *Recent changes*.
4. On A, **Remove editor** next to B. On B: back to *view only*. On A,
   *Recent changes* shows *A re-signed the settings B saved*.

## 6. Offline pickup

1. Log B out (or close client B).
2. On A, change any setting and save (version goes up by one).
3. Log B back in and join. Within a few seconds B shows the new version.

## 7. Opt-outs are personal

1. On B, *My events* → open *Per event kind*, untick **Post** for *Level up*.
2. On A, open *Who posts what*: the *Level up* row shows *no* under B, *yes*
   under A. The cells cannot be clicked.
3. On B, tick **Don't post my events**. On A the whole B column becomes *no*.
4. Untick it again on B.

## 8. Level ups every N, Discord and overlay

Needs a skill near a multiple of 5 above 60 on A (any skill A can train
quickly). With version 2's settings (from 60, every 5):

1. Gain a level to a non-multiple (e.g. 61 to 62): no banner on A or B, no
   Discord post from A.
2. Gain a level to a multiple of 5 (e.g. 64 to 65): a banner on both clients
   and one Discord post from A.
3. (If anyone reaches 99 during testing: it posts whatever N is.)

## 9. Webhook stays put

1. On A, check the Discord channel received only A's own posts.
2. On B, RuneLite's config still shows B's own (empty) webhook fields: A's
   URL never appeared there.

## 10. Restart keeps everything

1. Close and reopen client B. Settings tab: same leader, same version, same
   role, without anyone resending.

Report anything that differs, with the step number.
