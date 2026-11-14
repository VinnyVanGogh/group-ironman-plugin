# RuneScape Community Jokes & Easter Eggs Research

## Spec interview
- **Q: Who researches?**  
  **A:** A Gemini agent, researching from Reddit (`r/2007scape`, `r/ironscape`), the OSRS Wiki, and community lore (Board 2026-11-11).
- **Q: How should it be organised?**  
  **A:** Categories matched to the plugin's exact `GroupEvent.Type` triggers, auditing every line already on `main`, applying the Board's 2026-11-11 review direction, and ending with a single master implementation table (`trigger | final line(s) | source | keep/new/replace`).

---

## Scope, Constraints & Codebase Invariants

This document is the verified specification for the **GIM Utilities** optional Discord Easter eggs (`EasterEggs.java` and `EasterEggLines.java`), feeding the implementation task *"GIM Utilities: RuneScape meme Easter eggs (level 73 B0aty and a curated set)"*.

### Hard Guardrails (`EasterEggsTest.java` & Hub Rules)
1. **Strict Length Limit (`<= 80` UTF-16 chars):** `EasterEggsTest.everyLineIsShortAndKind()` enforces `line.length() <= 80` on every string returned by `EasterEggLines.all()`. Every final and candidate line in this document is `<= 65` UTF-16 code units.
2. **Kindness & Content Safety:** Zero self-harm wording (`"suicide"`, `"kill yourself"`, `"kys"`, `"self-harm"`, `"self harm"`, `"die irl"`), sexual innuendo, slurs, PvP toxicity aimed at teammates, real-world trading (RWT), Harry Potter (`Gringotts`) crossover jokes, or `"ironmom/irondad"` phrasing.
3. **Streamer References:** Only widely known, affectionate community memes are allowed (`73` in deadpan style without explaining B0aty, and `Zezima` on maxing). Flagged explicitly per entry.
4. **Existing Test Invariants to Preserve:**
   - `EasterEggLines.LEGEND.length >= 4` and `EasterEggLines.FREELOADER.length >= 4` (`legendAndFreeloaderRotateStablyWithAFixedSeed()`).
   - At least **2 lines** in `EasterEggLines.all()` must contain supplementary-plane Unicode emoji (`Character::isSupplementaryCodePoint`, encoded as Java surrogate escapes so builds are encoding-independent), checked by `anEmojiLineSurvivesTheEmbedAndPlainPayloads()`. We preserve `\uD83E\uDEF6` (`🫶`) in `LEGEND` and `\uD83D\uDC40` (`👀`) in `FREELOADER`.
   - `EasterEggLines.ITEMS.size() >= 10` (`everyLineIsShortAndKind()`).
5. **Citation & Game-Fact Verification:** Every URL cited in this document was fetched and verified against live `oldschool.runescape.wiki` pages or real `r/2007scape` / `r/ironscape` threads. Every item ID was verified against the OSRS Wiki item infobox / Exchange module.

---

## Audit of Existing Lines on `main` (`EasterEggLines.java` & `EasterEggs.java`)

PR #33 landed an initial `EasterEggLines.java` and `EasterEggs.java` on `main`. Every constant currently on `main` is audited below per the Board's 2026-11-11 review.

### 1. Event & Threshold Arrays (`EasterEggLines.java`)

| Constant on `main` | Current Trigger (`EasterEggs.java`) | Current Line(s) on `main` | Verdict | Reason & Action |
| :--- | :--- | :--- | :--- | :--- |
| `FREE_TRIP` | `DEATH_VALUE` (`e.number <= 0`) | `"Free trip to Lumbridge, no charge."`<br>`"Nothing to lose, nothing lost. Suspiciously intentional."`<br>`"Ironman death insurance: own nothing."` | **keep** | Clean, classic Lumbridge home-teleport / 0-gp death humour. Note: place `HC_LIFE_LOST` ahead of `nothingLost` so a 0-gp Hardcore death never reads `"no charge"`. |
| `CLUE_HUNTERS` | `DEATH_VALUE` (lost `Spade` `952` + optional clue items only) | `"Those clue hunters again."` | **keep** | Accurate Wilderness spade-PK meme; item ID `952` verified. |
| `PET` | `PET` | `"Congrats, you can stop now."`<br>`"Look who followed you home."`<br>`"Pet! Now pretend you were going for it."` | **keep** | Fits boss, skilling, and duplicate pet drops cleanly. |
| `LEVEL_73` | `LEVEL_UP` (`e.number == 73`) | `"73. B0aty would approve."`<br>`"Seventy-three. A B0aty classic."` | **replace** | Board direction: deadpan `"73."` only — do not explain the B0aty joke. |
| `LEVEL_92` | `LEVEL_UP` (`e.number == 92`) | `"92: halfway to 99."`<br>`"Halfway to 99. Only the other half to go."` | **keep** | Exact XP math meme (6,517,253 / 13,034,431 XP). |
| `SKILL_99` | `MAXED` (`!"total".equalsIgnoreCase(e.text)`) | `"Cape on, emote out."`<br>`"99! Time to stand in a bank and emote."` | **keep** | Classic skillcape emote celebration. |
| `MAXED_TOTAL` | `MAXED` (`"total".equalsIgnoreCase(e.text)`) | `"Move over, Zezima."`<br>`"Maxed. Zezima would be impressed."` | **keep** | Affectionate nod to Zezima, the first player to reach max total. |
| `COMBAT_126` | `COMBAT_LEVEL` (`e.number == 126`) | `"126. Max combat, max swagger."` | **keep** | Accurate OSRS max combat level (126). |
| `TOTAL_2000` | `TOTAL_LEVEL` (`e.number == 2000`) | `"Welcome to the 2K total club."` | **keep** | Unlocks 2,000 total worlds; fires when `ProgressTracker` crosses 2,000. |
| `XP_200M` | `XP_MILESTONE` (`e.number == 200_000_000L`) | `"200M. The XP counter has nowhere left to go."` | **keep** | Permanent 200M skill XP cap. |
| `COOKS_ASSISTANT` | `QUEST` (`"Cook's Assistant"`) | `"The Duke gets his birthday cake after all."` | **keep** | Accurate quest lore (Duke Horacio's birthday cake). |
| `DRAGON_SLAYER` | `QUEST` (`"Dragon Slayer I"`) | `"Rune platebody unlocked. Elvarg had it coming."` | **keep** | Matches OSRS quest name `"Dragon Slayer I"` and reward. |
| `RECIPE_FOR_DISASTER` | `QUEST` (`"Recipe for Disaster"`) | `"Barrows gloves! An ironman rite of passage."` | **keep line, fix trigger** | **Bug on `main`:** Finale quest scroll reads `"Congratulations! You have defeated the Culinaromancer!"` (see RuneLite `ScreenshotPluginTest`). `EventText.questName()` never outputs `"Recipe for Disaster"`. Match `e.text` containing `"culinaromancer"` (or `"Recipe for Disaster"`). |
| `JAD` | `DEATH` (`"TzTok-Jad"`) | `"Jad claims another fire cape hopeful."`<br>`"Pray mage, pray range, try again."` | **keep** | Iconic Fight Cave prayer-flick death. Does not violate combat helper rules (post-death Discord text only). |
| `SIT` | `DRY_STREAK` | `"Sit. (Affectionately.)"`<br>`"The RNG says sit. Keep going."` | **drop / replace** | Board direction: `"Sit"` is a PvP taunt, wrong tone/context for PvM dry streaks. Replace `DRY_STREAK` with the 5-rung escalating ladder below. |
| `BUYING_GF` | `STORAGE_DEPOSIT` / `STORAGE_SUMMARY` containing `GOLD_RING` (`1635`) | `"Buying gf, paying in gold rings."` | **keep** | Clean nod to classic Varrock West `"buying gf"`; item ID `1635` verified on OSRS Wiki (previously marked unverified in PR #33). |
| `BONES_ONLY` | `DROP` (`items` is solely `BONES` `526`) | `"Bones. Truly valuable."`<br>`"Someone check the valuable drop setting."` | **keep** | Fires only when `dropMin` is low enough to let normal `Bones` (`526`) pass `EventRouter.threshold()`. |
| `LEGEND` | `STORAGE_DEPOSIT` / `STORAGE_SUMMARY` (`deposited >= legendAt`) | 1. `"Feeding the group like a true ironmom/irondad. 🫶"` (**drop**)<br>2. `"Gringotts thanks you for your service."` (**drop**)<br>3. `"Generosity level: 99."` (**keep**)<br>4. `"The group storage is eating good tonight."` (**keep**) | **reword (replace 2, keep 2)** | Board direction: remove `"ironmom/irondad"` and remove `"Gringotts"`. Replace slots 1–2 with Falador Party Room (`🫶` preserved) and Bank of Gielinor lines so `LEGEND.length == 4`. |
| `FREELOADER` | `STORAGE_WITHDRAWAL` / `STORAGE_SUMMARY` (`took >= freeloaderAt && took >= deposited`) | 1. `"Gringotts is watching you. 👀"` (**drop**)<br>2. `"Borrowing, not stealing. Right?"` (**replace**)<br>3. `"The group storage will remember this."` (**replace**)<br>4. `"Taking from the pile like a true group ironman."` (**replace**) | **replace** | Board direction: remove `"Gringotts"`, rewrite big storage withdrawals around **Wise Old Man** (Draynor bank robbery / blue partyhat) plus 2–3 RuneScape-lore heist/charm jokes (`👀` preserved, `length >= 4`). |

### 2. Item Drop Table (`EasterEggLines.ITEMS`) — OSRS Wiki ID Audit

All 14 item IDs currently defined in `EasterEggLines.java` were checked against live OSRS Wiki Exchange/infobox pages:

| Constant on `main` | ID | OSRS Wiki Verification | Current Line(s) on `main` | Verdict |
| :--- | :--- | :--- | :--- | :--- |
| `SPADE` | `952` | [Module:Exchange/Spade](https://oldschool.runescape.wiki/w/Module:Exchange/Spade) | *(used by `CLUE_HUNTERS`)* | **keep** |
| `BONES` | `526` | [Bones](https://oldschool.runescape.wiki/w/Bones) | *(used by `BONES_ONLY`)* | **keep** |
| `GOLD_RING` | `1635` | [Module:Exchange/Gold_ring](https://oldschool.runescape.wiki/w/Module:Exchange/Gold_ring) | *(used by `BUYING_GF`)* | **keep** |
| `TWISTED_BOW` | `20997` | [Twisted bow](https://oldschool.runescape.wiki/w/Twisted_bow) | `"Ok, you can log off now."`, `"Somebody frame this one."` | **keep** |
| `SCYTHE_OF_VITUR_UNCHARGED` | `22486` | [Scythe of vitur](https://oldschool.runescape.wiki/w/Scythe_of_vitur) | `"The Theatre says thanks for playing."` | **keep** |
| `TUMEKENS_SHADOW_UNCHARGED` | `27277` | [Tumeken's shadow](https://oldschool.runescape.wiki/w/Tumeken%27s_shadow) | `"The shadow has chosen you."` | **keep** |
| `DRAGON_WARHAMMER` | `13576` | [Module:Exchange/Dragon_warhammer](https://oldschool.runescape.wiki/w/Module:Exchange/Dragon_warhammer) | `"Defence levels everywhere just got nervous."` | **keep** |
| `ELDER_MAUL` | `21003` | [Module:Exchange/Elder_maul](https://oldschool.runescape.wiki/w/Module:Exchange/Elder_maul) | `"Bonk."` | **keep** |
| `DRAGON_PICKAXE` | `11920` | [Dragon pickaxe](https://oldschool.runescape.wiki/w/Dragon_pickaxe) | `"Rocks across Gielinor fear you now."` | **keep** |
| `DRACONIC_VISAGE` | `11286` | [Module:Exchange/Draconic_visage](https://oldschool.runescape.wiki/w/Module:Exchange/Draconic_visage) | `"A face only a dragon could love."` | **keep** |
| `ZENYTE_SHARD` | `19529` | [Module:Exchange/Zenyte_shard](https://oldschool.runescape.wiki/w/Module:Exchange/Zenyte_shard) | `"One shard closer to a very shiny necklace."` | **keep** |
| `DRAGON_CHAINBODY` | `3140` | [Dragon chainbody](https://oldschool.runescape.wiki/w/Dragon_chainbody) | `"Chainbody? In this economy?"` | **keep** |
| `ABYSSAL_WHIP` | `4151` | [RuneScape:Lookup (4151)](https://oldschool.runescape.wiki/w/RuneScape:Lookup) | `"Whip it. Whip it good."` | **keep** |
| `DRAGON_MED_HELM` | `1149` | [Module:Exchange/Dragon_med_helm](https://oldschool.runescape.wiki/w/Module:Exchange/Dragon_med_helm) | `"The legendary med helm."`, `"Every adventurer's first big drop."` | **keep** |

### 3. Pass-1 Fact Errors Corrected
- **Mole slippers & Wyson the gardener:** Pass 1 invented item ID `24466` and called Mole slippers `"The holy moleys"`. In reality, `Mole slippers` are item ID `23285` ([Module:Exchange/Mole_slippers](https://oldschool.runescape.wiki/w/Module:Exchange/Mole_slippers)), **Wyson the gardener** in Falador Park trades Giant Mole parts (`Mole claw` / `Mole skin`) for bird nests ([Exchanging mole parts](https://oldschool.runescape.wiki/w/Exchanging_mole_parts)), and `Holy moleys` ([Holy moleys](https://oldschool.runescape.wiki/w/Holy_moleys)) are a separate item combining Holy sandals with Mole slippers. Dropped from the recommended `CLUE` triggers per Board review.
- **Imbued heart (`20724`) drop rate:** Pass 1 claimed `"1/700+"`. Per [Superior slayer monster](https://oldschool.runescape.wiki/w/Superior_slayer_monster) and [Imbued heart](https://oldschool.runescape.wiki/w/Imbued_heart), superiors spawn at `1/200` on task and roll a monster-specific rate: ~`1/142` from a Colossal Hydra (`1/17.8 × 1/8`), ~`1/200` from a Nuclear smoke devil, ~`1/797` from an Insatiable Bloodveld, down to ~`1/1,376` from a Crushing hand. No rate number is stated in the final line.
- **Dragon pickaxe (`11920`) `"5.8% faster"`:** Dropped per Board review (`main` already has `"Rocks across Gielinor fear you now."`).
- **`"cyan:wave: buying gf 10k"`:** Garbled chat-effect syntax dropped (`main` already has `"Buying gf, paying in gold rings."` on `GOLD_RING = 1635`).

---

## Board-Directed Special Mechanics

### 1. Escalating Dry Streak Ladder (`GroupEvent.Type.DRY_STREAK`)

In `DryStreak.java`, tracked bosses (`"vorkath"` at `oneIn = 714` across Draconic visage, Skeletal visage, Dragonbone necklace; `"zulrah"` at `oneIn = 128` across Tanzanite fang, Magic fang, Serpentine visage, Uncut onyx) emit `DRY_STREAK` each time `kills % rate.oneIn == 0`, setting `e.text = boss` and `e.number = count` (`1 × oneIn`, `2 × oneIn`, `3 × oneIn`, ...).

Instead of random selection (`pick(seed)`) or `"Sit"`, `EasterEggs` computes the **dry multiple** `rung = count / rate.oneIn` (defaulting to `1` if `rate` is unknown, and clamping at the top rung `Math.min(rung, DRY_STREAK_LADDER.length)`):

| Rung | Condition (`count / rate.oneIn`) | Final Line (`<= 80` chars) | Length | Lore / Meme Source |
| :--- | :--- | :--- | :--- | :--- |
| **1** | `1×` drop rate (first dry post) | `"It's a 50/50: you either get it or you don't."` | 45 | [r/2007scape: "50/50 — You either get it or you don't!"](https://www.reddit.com/r/2007scape/comments/u2gm9t/5050/) |
| **2** | `2×` drop rate (second dry post) | `"Next time, for sure."` | 20 | Universal bossing "one more trip" copium |
| **3** | `3×` drop rate (third dry post) | `"Okay, THIS time for sure."` | 25 | Escalated dry-streak copium (Board direction) |
| **4** | `4×` drop rate (fourth dry post) | `"Did you remember to talk to Oziach first?"` | 41 | [OSRS Wiki: Oziach](https://oldschool.runescape.wiki/w/Oziach) (classic Draconic visage unlock myth) & [r/ironscape: "when you don't talk to Oziach"](https://www.reddit.com/r/ironscape/comments/qcv6ep/112_my_ass/) |
| **5+** | `>= 5×` drop rate (clamped ceiling) | `"Surely it's in the game files. Surely."` | 38 | Deep dry-streak sanity check |

- **What Resets the Escalation:** Receiving any tracked unique from that boss (or that specific item if `DryStreak.RATES` expands to per-item keys). `DryStreak.drop(boss, itemNames, now)` **already** clears `kills.remove(key)` and cancels any pending `held.remove(key)` the moment a listed unique drops, persisting the reset to RuneLite profile config key `dryStreaks`. The next dry streak on that boss starts back at **Rung 1** (`"It's a 50/50: you either get it or you don't."`).
- **Implementation Note:** Because `DryStreak.RATES` exposes `rate.oneIn` by `e.text.toLowerCase(Locale.ROOT)` (and `e.detail` also carries `"unique rate about 1/<oneIn>"`), `EasterEggs` can compute `int rung = Math.max(1, (int) (e.number / rate.oneIn))` purely without extra persistent state!

---

### 2. Big Storage Withdrawals & Deposits: Pure RuneScape Bank Lore (`FREELOADER` & `LEGEND`)

#### `FREELOADER` (Withdrawal `>= freeloaderAt` and `>= deposited`)
Replaces all Harry Potter (`Gringotts`) and generic lines with 4 RuneScape heist/persuasion classics, keeping `\uD83D\uDC40` (`👀`) for `EasterEggsTest`:
1. `"Pulling a Wise Old Man on the group storage. 👀"` (47 UTF-16 chars) — **Wise Old Man's Draynor Bank Robbery** ([OSRS Wiki: Draynor bank robbery](https://oldschool.runescape.wiki/w/Draynor_bank_robbery), [Wise Old Man](https://oldschool.runescape.wiki/w/Wise_Old_Man)).
2. `"Draynor bank called; check the security tape for a blue partyhat."` (65 chars) — References the security camera cutscene in Draynor Village Bank where Dionysius steals the blue partyhat ([Transcript: Draynor bank robbery](https://oldschool.runescape.wiki/w/Transcript:Draynor_bank_robbery)).
3. `"Telegrabbing the Varrock West vault again?"` (42 chars) — Every F2P player's dream of casting Telekinetic Grab on the gold bars and ruby ring behind the bars in [Varrock West Bank's basement](https://oldschool.runescape.wiki/w/Varrock_west_bank).
4. `"(Charm) The group doesn't need these anyway."` (44 chars) — The [Ring of Charos (a)](https://oldschool.runescape.wiki/w/Ring_of_charos) `(Charm)` dialogue prefix used to sweet-talk NPCs out of coins and items.

#### `LEGEND` (Deposit `>= legendAt`)
Keeps the 2 clean `main` lines and replaces `"ironmom/irondad"` + `"Gringotts"` with 2 RuneScape-lore lines, keeping `\uD83E\uDEF6` (`🫶`) for `EasterEggsTest`:
1. `"Falador Party Room drop party in group storage! 🫶"` (50 UTF-16 chars) — [Falador Party Room](https://oldschool.runescape.wiki/w/Falador_Party_Room) chest & balloon drop parties.
2. `"The Bank of Gielinor salutes your deposit."` (42 chars) — Official [Bank of Gielinor](https://oldschool.runescape.wiki/w/List_of_banks) lore.
3. `"Generosity level: 99."` (21 chars — **kept** from `main`).
4. `"The group storage is eating good tonight."` (41 chars — **kept** from `main`).

---

### 3. Exactly 25 gp in Group Storage: Tutorial Island Once-Per-Member Egg

- **Game Fact:** Every fresh account leaving Tutorial Island starts with **25 coins** (`Coins`, item ID `995`, quantity `25`) pre-stocked inside their bank ([OSRS Wiki: New player guide](https://oldschool.runescape.wiki/w/New_player_guide), [Tutorial Island](https://oldschool.runescape.wiki/w/Tutorial_Island_%28quest%29)).
- **Exact Trigger Condition:** A storage visit where the moved stack is **solely `25 x Coins` (`ID 995, qty == 25`)** — either:
  - `STORAGE_DEPOSIT` or `STORAGE_WITHDRAWAL` or `STORAGE_LAST` with `e.items.size() == 1 && e.items.getOrDefault(995, 0) == 25`, or
  - `STORAGE_SUMMARY` where `(e.items.equals(Map.of(995, 25)) && e.taken.isEmpty()) || (e.taken.equals(Map.of(995, 25)) && e.items.isEmpty())`.
- **At-Most-Once-Per-Member-Ever Gate:** Track normalized member names (`GroupMembers.key(e.member)`) in RuneLite config group `"gimutilities"` (e.g. semicolon-delimited config key `"tutorialGpSeen"`). Before firing, check `!seen.contains(memberKey)`; when posting to Discord, record `memberKey` so that member can never trigger it a second time.
- **Routing Nuance (`EventRouter.threshold`):** By default, `EventRouter.threshold()` filters `STORAGE_DEPOSIT` and `STORAGE_WITHDRAWAL` by `settings.storageMin`. If `storageMin > 25` and `discordStorageSummary` is off, `EventRouter.threshold` should either let `{995: 25}` through when `discordEasterEggs` is enabled and `!tutorialGpSeen.contains(member)` or evaluate it on `STORAGE_SUMMARY` when enabled.
- **Final Line:** `"Fresh off Tutorial Island with all 25 gp."` (41 chars).

---

## Categorised Community Memes Mapped to Plugin Triggers

### Category 1: Levels, Quests & Progress (`LEVEL_UP`, `MAXED`, `COMBAT_LEVEL`, `TOTAL_LEVEL`, `XP_MILESTONE`, `QUEST`)

> **Rule Ordering Requirement in `EasterEggs.RULES`:** Number-specific level rules (`73`, `92`, `69`, `Defence 2`) and `MAXED` (`99` / `total`) **must** sit above skill-wide `LEVEL_UP` rules (`Runecraft`, `Construction`, `Agility`). Otherwise, hitting 73 or 92 Runecraft would match the generic Runecraft rule instead of `73` / `92`. Also note that a skill-wide `LEVEL_UP` rule fires on every posted level in that skill (`level >= settings.levelMin`); if skill-pain jokes feel too frequent on early levels, gating skill-name rules to `e.number >= 50` (or `e.number % 5 == 0`) keeps them special.

#### 1.1 Level 73 (Deadpan)
- **Trigger:** `GroupEvent.Type.LEVEL_UP` where `e.number == 73`.
- **Final Line:** `"73."` (3 chars) — **replace** `main`'s `"73. B0aty would approve."` / `"Seventy-three. A B0aty classic."`.
- **Sources:** [OSRS Wiki: Armadyl godsword (Trivia — searching "73" in settings)](https://oldschool.runescape.wiki/w/Armadyl_godsword), [r/2007scape: "Meaning of 73?"](https://www.reddit.com/r/2007scape/comments/617mj0/meaning_of_73/).
- **Risk Note:** **Streamer-origin meme (B0aty / Skill Specs)** — final text is just the number `"73."`, naming nobody. Zero risk.

#### 1.2 Level 92 ("Halfway to 99")
- **Trigger:** `GroupEvent.Type.LEVEL_UP` where `e.number == 92`.
- **Final Lines:** `"92: halfway to 99."` (18 chars), `"Halfway to 99. Only the other half to go."` (41 chars) — **keep** from `main`.
- **Sources:** [OSRS Wiki: Experience](https://oldschool.runescape.wiki/w/Experience), [r/2007scape: "Why do we say 92 is halfway to 99?"](https://www.reddit.com/r/2007scape/comments/181o661/why_do_we_say_92_is_halfway_to_99/).
- **Risk Note:** None.

#### 1.3 Level 69 ("Nice.")
- **Trigger:** `GroupEvent.Type.LEVEL_UP` where `e.number == 69`.
- **Candidate Line:** `"Nice."` (5 chars) — **new** (optional; include only if desired alongside 73/92).
- **Sources:** [r/2007scape: "69 slayer with 69420 xp remaining. Nice."](https://www.reddit.com/r/2007scape/comments/186ba3l/69_slayer_with_69420_xp_remaining_nice/), [r/2007scape: "goal of getting level 69 in everything"](https://www.reddit.com/r/2007scape/comments/1lvthq8/my_friends_say_my_goal_of_getting_level_69_in/).
- **Risk Note:** Low. The word `"Nice."` is completely clean and ubiquitous in OSRS level-up chat, though `69` originates as internet slang.

#### 1.4 Level 2 Defence ("Ruined Pure")
- **Trigger:** `GroupEvent.Type.LEVEL_UP` where `"Defence".equalsIgnoreCase(e.text) && e.number == 2`.
- **Final Line:** `"RIP the 1 Defence pure."` (23 chars) — **new**.
- **Sources:** [r/2007scape: "Accidentally got 2 def on my pure from dragon battleaxe spec"](https://www.reddit.com/r/2007scape/comments/afu6dg/accidentally_got_2_def_on_my_pure_from_dragon/), [r/2007scape: "I just gained 2 defence levels on my 1 defence pure"](https://www.reddit.com/r/2007scape/comments/14oz74u/i_just_gained_2_defence_levels_on_my_1_defence/).
- **Risk Note:** None. Fires at most once per account.

#### 1.5 Runecraft Level-Ups ("Runecraft Slow / Tears of Guthix")
- **Trigger:** `GroupEvent.Type.LEVEL_UP` where `"Runecraft".equalsIgnoreCase(e.text)` (placed after `73`/`92`/`69`; optional `e.number >= 50` or milestone gate so early quest XP jumps do not spam).
- **Final Lines:**
  - `"Tears of Guthix sends its regards."` (34 chars)
  - `"Blink twice if ZMI is holding you hostage."` (42 chars)
- **Sources:** [r/2007scape: "I really hate Runecraft, to be honest."](https://www.reddit.com/r/2007scape/comments/lesua0/i_really_hate_runecraft_to_be_honest/), [OSRS Wiki: Quest experience rewards (recommended slow skills)](https://oldschool.runescape.wiki/w/Quest_experience_rewards).
- **Risk Note:** None. Uses zero self-harm phrasing (`EasterEggsTest` safe).

#### 1.6 Construction Level-Ups ("Oak Larders & Demon Butler")
- **Trigger:** `GroupEvent.Type.LEVEL_UP` where `"Construction".equalsIgnoreCase(e.text)` (placed after `73`/`92`/`69`).
- **Final Lines:**
  - `"Your demon butler's kids are going to college."` (46 chars)
  - `"Another oak larder built, another oak larder smashed."` (53 chars)
- **Sources:** [OSRS Wiki: Construction](https://oldschool.runescape.wiki/w/Construction), [r/2007scape: "Construction is just too expensive"](https://www.reddit.com/r/2007scape/comments/gue7vl/construction_is_just_too_expensive/).
- **Risk Note:** None.

#### 1.7 Agility Level-Ups ("Rooftop Laps")
- **Trigger:** `GroupEvent.Type.LEVEL_UP` where `"Agility".equalsIgnoreCase(e.text)` (placed after `73`/`92`/`69`).
- **Final Line:** `"Only 4,000 more rooftop laps until this feels fun."` (50 chars) — **new**.
- **Sources:** [OSRS Wiki: Agility (Rooftop Agility Courses)](https://oldschool.runescape.wiki/w/Agility), [r/2007scape: "Rooftops suck and Graceful is overrated"](https://www.reddit.com/r/2007scape/comments/10dnrqs/rooftops_suck_and_graceful_is_overrated/).
- **Risk Note:** None.

#### 1.8 Skill 99, Max Total, 126 Combat, 2000 Total, 200M XP & Quests (`main`)
- **`MAXED` (`"total"`):** `"Move over, Zezima."`, `"Maxed. Zezima would be impressed."` (optional 3rd candidate: `"Maxed! Now you can finally play the game."` — 41 chars). Sources: [r/2007scape: "Zezima is my main"](https://www.reddit.com/r/2007scape/comments/u2zcvg/just_wait_til_i_tell_her_zezima_is_my_main_boys/), [r/ironscape: Rank 3 duo GIM group storage](https://old.reddit.com/r/ironscape/comments/1hnsn8h/rank_3_duo_gim_group_storage/). **Streamer/Player Flag:** Names Zezima affectionately (**keep**).
- **`MAXED` (single skill 99):** `"Cape on, emote out."`, `"99! Time to stand in a bank and emote."` ([OSRS Wiki: Cape of Accomplishment](https://oldschool.runescape.wiki/w/Cape_of_Accomplishment), **keep**).
- **`COMBAT_LEVEL == 126`:** `"126. Max combat, max swagger."` ([OSRS Wiki: Combat level](https://oldschool.runescape.wiki/w/Combat_level), **keep**).
- **`TOTAL_LEVEL == 2000`:** `"Welcome to the 2K total club."` ([OSRS Wiki: Quests/Requirements](https://oldschool.runescape.wiki/w/Quests/Requirements_by_skill), **keep**).
- **`XP_MILESTONE == 200_000_000L`:** `"200M. The XP counter has nowhere left to go."` ([OSRS Wiki: Experience](https://oldschool.runescape.wiki/w/Experience), **keep**).
- **`QUEST` (`"Cook's Assistant"`, `"Dragon Slayer I"`, `"Culinaromancer"` / `"Recipe for Disaster"`):** **keep** lines, fix RFD matcher to detect `"Culinaromancer"` ([OSRS Wiki: Recipe for Disaster](https://oldschool.runescape.wiki/w/Recipe_for_Disaster), [Dragon Slayer I](https://oldschool.runescape.wiki/w/Dragon_Slayer_I), [Duke Horacio](https://oldschool.runescape.wiki/w/Duke_Horacio)).

---

### Category 2: Drops, Collection Log, Uniques & Pets (`DROP`, `UNIQUE`, `CLUE`, `BARROWS`, `RAID_UNIQUE`, `CLOG`, `PET`, `DRY_STREAK`)

> **How `EasterEggs.itemRow` Works:** `EasterEggLines.ITEMS` matches **both** `DROPS` (`DROP`, `UNIQUE`, `CLUE`, `BARROWS` via `e.items.containsKey(id)`) **and** `NAMED` (`RAID_UNIQUE`, `CLOG` via `lookup.name(id).equalsIgnoreCase(e.text)`). That means chest-looted items that do not fire `NpcLootReceived` (like The Gauntlet's Reward Chest) still match `ITEMS` automatically whenever they unlock a `CLOG` slot or appear as a `RAID_UNIQUE`!

#### 2.1 Enhanced Crystal Weapon Seed — "The Red Prison" (`ID 25859`)
- **Trigger:** `EasterEggLines.ITEMS` row `25859` (`Enhanced crystal weapon seed`). Fires via `GroupEvent.Type.CLOG` (`EasterEggs.NAMED`) when looted from The Gauntlet's Reward Chest (since chest rewards do not emit `NpcLootReceived` `DROP`).
- **Final Lines:**
  - `"Parole granted from the Red Prison."` (35 chars)
  - `"Free from Corrupted Gauntlet at last."` (37 chars)
- **Sources:** [OSRS Wiki: Module:Exchange/Enhanced_crystal_weapon_seed (`id = 25859`)](https://oldschool.runescape.wiki/w/Module:Exchange/Enhanced_crystal_weapon_seed), [r/ironscape: "For those who are in red prison"](https://www.reddit.com/r/ironscape/comments/10anvef/for_those_who_are_in_red_prison/), [r/ironscape: "How did you guys survive the red prison?"](https://www.reddit.com/r/ironscape/comments/18s2uuj/how_did_you_guys_survive_the_red_prison/).
- **Risk Note:** None. Defining ironman progression milestone.

#### 2.2 Tanzanite Fang — Zulrah "Money Snek" (`ID 12922`)
- **Trigger:** `EasterEggLines.ITEMS` row `12922` (`Tanzanite fang`), firing on `DROP`, `UNIQUE`, or `CLOG`.
- **Final Line:** `"Money snek finally coughed up the blowpipe."` (43 chars).
- **Sources:** [OSRS Wiki: Tanzanite fang (`id = 12922`)](https://oldschool.runescape.wiki/w/Tanzanite_fang), [r/ironscape: "3k Zulrah kills no tanzanite fang, I heard posting helps"](https://www.reddit.com/r/ironscape/comments/1chttns/3k_zulrah_kills_no_tanzanite_fang_i_heard_posting/).
- **Risk Note:** None.

#### 2.3 Imbued Heart — Superior Slayer Jackpot (`ID 20724`)
- **Trigger:** `EasterEggLines.ITEMS` row `20724` (`Imbued heart`), firing on `DROP` or `CLOG`.
- **Final Line:** `"The superior monster actually paid out."` (39 chars).
- **Sources:** [OSRS Wiki: Module:Exchange/Imbued_heart (`id = 20724`)](https://oldschool.runescape.wiki/w/Module:Exchange/Imbued_heart), [OSRS Wiki: Superior slayer monster](https://oldschool.runescape.wiki/w/Superior_slayer_monster).
- **Risk Note:** None. Avoids quoting monster-dependent drop rates.

#### 2.4 Existing 11 `ITEMS` Rows, `PET` & `BONES_ONLY` (`main`)
- All 11 existing `ITEMS` entries (`20997`, `22486`, `27277`, `13576`, `21003`, `11920`, `11286`, `19529`, `3140`, `4151`, `1149`), `PET` ([OSRS Wiki: Pet](https://oldschool.runescape.wiki/w/Pet)), and `BONES_ONLY` (`526`, [OSRS Wiki: Bones](https://oldschool.runescape.wiki/w/Bones)) are verified and marked **keep**.
- **`DRY_STREAK`:** Replaced by the 5-rung escalating ladder specified above.

---

### Category 3: Deaths, Hardcore Lives & Wilderness (`DEATH`, `DEATH_VALUE`, `HC_LIFE_LOST`, `GRAVESTONE_EXPIRING`, `WILDERNESS`)

#### 3.1 Highwayman Death ("Stand and deliver!")
- **Trigger:** `GroupEvent.Type.DEATH` where `"Highwayman".equalsIgnoreCase(e.text)`.
- **Final Line:** `"Stand and deliver!"` (18 chars) — **new**.
- **Sources:** [OSRS Wiki: Highwayman](https://oldschool.runescape.wiki/w/Highwayman), [OSRS Wiki: Transcript:Highwayman](https://oldschool.runescape.wiki/w/Transcript:Highwayman), [r/2007scape: Draynor Highwayman post](https://www.reddit.com/r/2007scape/comments/1nogldm/if_you_stand_in_a_specific_part_of_the_draynor/).
- **Risk Note:** None. Verbatim NPC overhead chat from 2001 onward.

#### 3.2 Dark Wizard Death (South Varrock Stone Circle)
- **Trigger:** `GroupEvent.Type.DEATH` where `"Dark wizard".equalsIgnoreCase(e.text)`.
- **Final Line:** `"South Varrock claims another victim."` (36 chars) — **new**.
- **Sources:** [OSRS Wiki: Dark wizard](https://oldschool.runescape.wiki/w/Dark_wizard), [r/2007scape: "Dark mages strike again"](https://www.reddit.com/r/2007scape/comments/1loin5v/dark_mages_strike_again/).
- **Risk Note:** None.

#### 3.3 Hardcore Group Life Lost (`HC_LIFE_LOST`)
- **Trigger:** `GroupEvent.Type.HC_LIFE_LOST` (or `DEATH` with `e.number == GroupEvent.LIFE_LOST` when merged in `DiscordMerge.Family.DEATH`; place before `nothingLost` so a 0-gp Hardcore death never gets `FREE_TRIP`).
- **Final Line:** `"A group life vanishes into Lumbridge. F."` (40 chars) — **new**.
- **Sources:** [OSRS Wiki: Group Ironman Mode](https://oldschool.runescape.wiki/w/Group_Ironman_Mode), [r/2007scape: "Group Ironman Blog" (HCGIM shared lives)](https://www.reddit.com/r/2007scape/comments/pfr3no/group_ironman_blog/).
- **Risk Note:** Low. Gentle group memorial rather than blaming the member who died.

#### 3.4 Gravestone Expiring (`GRAVESTONE_EXPIRING`)
- **Trigger:** `GroupEvent.Type.GRAVESTONE_EXPIRING` (fires when an uncollected gravestone reaches `warnMinutes` remaining in `EventWatchers.graveTick`).
- **Candidate Line:** `"Death's Office is eyeing your gear. Run!"` (40 chars) — **new** (optional; post is an urgent alert, so keeping it brisk helps).
- **Sources:** [OSRS Wiki: New player guide (Gravestone mechanics)](https://oldschool.runescape.wiki/w/New_player_guide).
- **Risk Note:** Low; does not obscure the `Where` / `World` fields in the Discord embed.

#### 3.5 Wilderness Skull / Entry (`WILDERNESS`) & Spade Deaths (`CLUE_HUNTERS`)
- **Plugin Trigger Reality Check:**
  - `GroupEvent.Type.WILDERNESS` exists in `GroupEvent.java` (`EventWatchers.java:296-303`) and emits **only** `e.text = "entered the Wilderness"` or `e.text = "got skulled"` — it is a presence/status alert, not a death event.
  - Generic Wilderness PvP deaths cannot reliably check `e.location.contains("Wilderness")` because `regions.txt` maps Wilderness areas to specific names (`"Lava Maze"`, `"Mage Arena"`, `"Rogues' Castle"`, `"Demonic Ruins"`).
  - Wilderness clue-hunter PK deaths are **already** caught accurately by `CLUE_HUNTERS` (`DEATH_VALUE` losing `Spade` `952`, [r/2007scape: "My spade is lucrative loot for PKers"](https://www.reddit.com/r/2007scape/comments/1go6wnz/my_spade_is_lucrative_loot_for_pkers/)).
  - Lumbridge respawn has no separate trigger (`DEATH` records where you died, not where you respawned); `FREE_TRIP` (`"Free trip to Lumbridge, no charge."`, [OSRS Wiki: Lumbridge](https://oldschool.runescape.wiki/w/Lumbridge)) already covers it.
- **Optional `WILDERNESS` ("got skulled") Candidate:** `"Hide your spades, someone just skulled up."` (42 chars) on `GroupEvent.Type.WILDERNESS` where `"got skulled".equalsIgnoreCase(e.text)`.

---

### Category 4: Group Storage (`STORAGE_DEPOSIT`, `STORAGE_WITHDRAWAL`, `STORAGE_SUMMARY`, `STORAGE_LAST`, `STORAGE_MINIMUM`)

1. **`FREELOADER` (Big Withdrawal `>= freeloaderAt`):** Replaced with the 4 **Wise Old Man / Varrock West Vault / Ring of Charos** lines above.
2. **`LEGEND` (Big Deposit `>= legendAt`):** Updated with **Falador Party Room (`🫶`) + Bank of Gielinor** replacing `"ironmom/irondad"` and `"Gringotts"`.
3. **25 gp Tutorial Island (`Coins` `995`, `qty == 25`, once per member ever):** `"Fresh off Tutorial Island with all 25 gp."` (41 chars).
4. **`BUYING_GF` (`Gold ring` `1635` deposited):** `"Buying gf, paying in gold rings."` (32 chars — **keep** from `main`; [r/2007scape: "Buying gf" (8,729 votes)](https://www.reddit.com/r/2007scape/comments/kwqp9a/buying_gf/)).
5. **Took the Last One (`STORAGE_LAST`):**
   - **Trigger:** `GroupEvent.Type.STORAGE_LAST` when neither `FREELOADER` nor the `25 gp` Tutorial Island rule applies (`settings.freeloaderAt <= 0 || EventText.value(e.items, lookup) < settings.freeloaderAt`, because `STORAGE_LAST` precedes `STORAGE_WITHDRAWAL` in `DiscordMerge.Family.STORAGE`).
   - **Final Line:** `"Took the last one from the group shelf."` (39 chars) — **new**.
   - **Sources:** [OSRS Wiki: Group Ironman Mode (Group storage)](https://oldschool.runescape.wiki/w/Group_Ironman_Mode), [r/ironscape: "GIMs, how does your team handle splitting up drops"](https://www.reddit.com/r/ironscape/comments/yjbnsj/gims_how_does_your_team_handle_splitting_up_drops/).

---

### Category 5: Group & Online (`BACK`, `ONLINE`, `WEEKLY_RECAP`)

#### 5.1 Returning After a Break (`BACK`)
- **Trigger:** `GroupEvent.Type.BACK` (emitted by `GroupEventEngine` when a member logs in after `>= config.backAfterDays()` offline, default 7 days).
- **Final Line:** `"You never quit RuneScape, you just take breaks."` (47 chars) — **new**.
- **Sources:** [r/2007scape: "You never quit, you just take breaks" (3,205 votes)](https://www.reddit.com/r/2007scape/comments/9ccr6m/you_never_quit_you_just_take_breaks/), [r/2007scape: "'Runescapers don't quit, they take breaks' isn't a meme"](https://www.reddit.com/r/2007scape/comments/15zcjch/runescapers_dont_quit_they_take_breaks_isnt_a_meme/).
- **Risk Note:** None. Possibly the single most universal motto in RuneScape history.

#### 5.2 Login & Weekly Recap (`ONLINE`, `WEEKLY_RECAP`) — Optional Candidates
- **`ONLINE` (`"online".equalsIgnoreCase(e.text)`):** Not recommended for every login because `ONLINE` fires daily and an egg on every session login gets repetitive fast. If ever gated, `"XP waste officially paused."` (27 chars; [r/2007scape: "Big Guide to No XP Waste!"](https://www.reddit.com/r/2007scape/comments/259qrr/big_guide_to_no_xp_waste/)).
- **`WEEKLY_RECAP` (`e.number >= 5_000_000L` weekly XP):** `"Numbers went up. Good week."` (27 chars; optional, low priority).

---

### Category 6: General Classics & How They Map

| Classic Meme | Disposition & Trigger Mapping | Verified Source |
| :--- | :--- | :--- |
| **`"buying gf"`** | Mapped to `GOLD_RING` (`1635`) storage deposit (`BUYING_GF`, **keep**). Garbled `"cyan:wave: buying gf 10k"` **dropped**. | [r/2007scape: "Buying gf"](https://www.reddit.com/r/2007scape/comments/kwqp9a/buying_gf/) |
| **`"Sit."`** | **Dropped** from `DRY_STREAK` (PvP taunt). Not added to PvE deaths; PK spade deaths already use `CLUE_HUNTERS`. | [r/2007scape: "What is this 'Sit.' culture..."](https://www.reddit.com/r/2007scape/comments/kml19h/what_is_this_sit_culture_thats_going_on_in_osrs/) |
| **`Zezima`** | Mapped to `MAXED` (`"total"`) in `MAXED_TOTAL` (**keep**). | [r/2007scape: "Zezima is my main"](https://www.reddit.com/r/2007scape/comments/u2zcvg/just_wait_til_i_tell_her_zezima_is_my_main_boys/) |
| **`"One more kill"` / `"50/50"` / `Oziach`** | Built directly into the 5-rung **`DRY_STREAK` escalating ladder** (**replace**). | [r/2007scape: "50/50"](https://www.reddit.com/r/2007scape/comments/u2gm9t/5050/), [OSRS Wiki: Oziach](https://oldschool.runescape.wiki/w/Oziach) |
| **`Wise Old Man` bank heist** | Built directly into **`FREELOADER`** big storage withdrawals (**replace**). | [OSRS Wiki: Draynor bank robbery](https://oldschool.runescape.wiki/w/Draynor_bank_robbery) |
| **`"Gz"` / Skillcape emote** | Built into `SKILL_99` (`MAXED` single skill, **keep**). | [OSRS Wiki: Slang dictionary](https://oldschool.runescape.wiki/w/Slang_dictionary) |
| **`"Engine work"`** | General subreddit dev-blog meme ([r/2007scape](https://www.reddit.com/r/2007scape/comments/1kmu8kg/if_jagex_made_an_agility_course_that_was_just_1/)); no clean gameplay event trigger — omitted rather than forced. | [OSRS Wiki: Slang dictionary](https://oldschool.runescape.wiki/w/Slang_dictionary) |

---

## Final Master Implementation Table

This table is the complete, authoritative specification for `EasterEggs.java` and `EasterEggLines.java` in the follow-up code PR. Every string is `<= 65` UTF-16 characters (`EasterEggsTest` limit: `80`), every item ID is verified on the OSRS Wiki, and rule precedence notes are included inline.

| Trigger (Exact `GroupEvent.Type` + Condition) | Final Line(s) | Source | Action (`keep` / `new` / `replace`) |
| :--- | :--- | :--- | :--- |
| `DEATH_VALUE` where `clueDeath(e, lookup)` (`Spade` `952` + optional clue items only) | `"Those clue hunters again."` | [OSRS Wiki: Spade (`952`)](https://oldschool.runescape.wiki/w/Module:Exchange/Spade), [r/2007scape](https://www.reddit.com/r/2007scape/comments/1go6wnz/my_spade_is_lucrative_loot_for_pkers/) | **keep** |
| `HC_LIFE_LOST` *(or `DEATH` with `e.number == LIFE_LOST`; place before `nothingLost`)* | `"A group life vanishes into Lumbridge. F."` | [OSRS Wiki: Group Ironman Mode](https://oldschool.runescape.wiki/w/Group_Ironman_Mode), [r/2007scape](https://www.reddit.com/r/2007scape/comments/pfr3no/group_ironman_blog/) | **new** |
| `DEATH` where `"TzTok-Jad".equalsIgnoreCase(e.text)` | `"Jad claims another fire cape hopeful."`<br>`"Pray mage, pray range, try again."` | [OSRS Wiki: TzTok-Jad](https://oldschool.runescape.wiki/w/TzTok-Jad) | **keep** |
| `DEATH` where `"Highwayman".equalsIgnoreCase(e.text)` | `"Stand and deliver!"` | [OSRS Wiki: Highwayman](https://oldschool.runescape.wiki/w/Highwayman) | **new** |
| `DEATH` where `"Dark wizard".equalsIgnoreCase(e.text)` | `"South Varrock claims another victim."` | [OSRS Wiki: Dark wizard](https://oldschool.runescape.wiki/w/Dark_wizard), [r/2007scape](https://www.reddit.com/r/2007scape/comments/1loin5v/dark_mages_strike_again/) | **new** |
| `DEATH_VALUE` where `e.number <= 0` (`nothingLost`) | `"Free trip to Lumbridge, no charge."`<br>`"Nothing to lose, nothing lost. Suspiciously intentional."`<br>`"Ironman death insurance: own nothing."` | [OSRS Wiki: Lumbridge](https://oldschool.runescape.wiki/w/Lumbridge) | **keep** |
| `PET` | `"Congrats, you can stop now."`<br>`"Look who followed you home."`<br>`"Pet! Now pretend you were going for it."` | [OSRS Wiki: Pet](https://oldschool.runescape.wiki/w/Pet) | **keep** |
| `MAXED` where `"total".equalsIgnoreCase(e.text)` | `"Move over, Zezima."`<br>`"Maxed. Zezima would be impressed."` | [r/2007scape](https://www.reddit.com/r/2007scape/comments/u2zcvg/just_wait_til_i_tell_her_zezima_is_my_main_boys/) | **keep** |
| `MAXED` *(single skill 99)* | `"Cape on, emote out."`<br>`"99! Time to stand in a bank and emote."` | [OSRS Wiki: Cape of Accomplishment](https://oldschool.runescape.wiki/w/Cape_of_Accomplishment) | **keep** |
| `LEVEL_UP` where `e.number == 73` | `"73."` | [OSRS Wiki: Armadyl godsword](https://oldschool.runescape.wiki/w/Armadyl_godsword), [r/2007scape](https://www.reddit.com/r/2007scape/comments/617mj0/meaning_of_73/) | **replace** |
| `LEVEL_UP` where `e.number == 92` | `"92: halfway to 99."`<br>`"Halfway to 99. Only the other half to go."` | [OSRS Wiki: Experience](https://oldschool.runescape.wiki/w/Experience), [r/2007scape](https://www.reddit.com/r/2007scape/comments/181o661/why_do_we_say_92_is_halfway_to_99/) | **keep** |
| `LEVEL_UP` where `"Defence".equalsIgnoreCase(e.text) && e.number == 2` | `"RIP the 1 Defence pure."` | [r/2007scape](https://www.reddit.com/r/2007scape/comments/afu6dg/accidentally_got_2_def_on_my_pure_from_dragon/) | **new** |
| `LEVEL_UP` where `"Runecraft".equalsIgnoreCase(e.text)` *(after 73/92)* | `"Tears of Guthix sends its regards."`<br>`"Blink twice if ZMI is holding you hostage."` | [r/2007scape](https://www.reddit.com/r/2007scape/comments/lesua0/i_really_hate_runecraft_to_be_honest/) | **new** |
| `LEVEL_UP` where `"Construction".equalsIgnoreCase(e.text)` *(after 73/92)* | `"Your demon butler's kids are going to college."`<br>`"Another oak larder built, another oak larder smashed."` | [OSRS Wiki: Construction](https://oldschool.runescape.wiki/w/Construction), [r/2007scape](https://www.reddit.com/r/2007scape/comments/gue7vl/construction_is_just_too_expensive/) | **new** |
| `LEVEL_UP` where `"Agility".equalsIgnoreCase(e.text)` *(after 73/92)* | `"Only 4,000 more rooftop laps until this feels fun."` | [OSRS Wiki: Agility](https://oldschool.runescape.wiki/w/Agility), [r/2007scape](https://www.reddit.com/r/2007scape/comments/10dnrqs/rooftops_suck_and_graceful_is_overrated/) | **new** |
| `COMBAT_LEVEL` where `e.number == 126` | `"126. Max combat, max swagger."` | [OSRS Wiki: Combat level](https://oldschool.runescape.wiki/w/Combat_level) | **keep** |
| `TOTAL_LEVEL` where `e.number == 2000` | `"Welcome to the 2K total club."` | [OSRS Wiki: Quests/Requirements](https://oldschool.runescape.wiki/w/Quests/Requirements_by_skill) | **keep** |
| `XP_MILESTONE` where `e.number == 200_000_000L` | `"200M. The XP counter has nowhere left to go."` | [OSRS Wiki: Experience](https://oldschool.runescape.wiki/w/Experience) | **keep** |
| `QUEST` where `"Cook's Assistant".equalsIgnoreCase(e.text)` | `"The Duke gets his birthday cake after all."` | [OSRS Wiki: Duke Horacio](https://oldschool.runescape.wiki/w/Duke_Horacio) | **keep** |
| `QUEST` where `"Dragon Slayer I".equalsIgnoreCase(e.text)` | `"Rune platebody unlocked. Elvarg had it coming."` | [OSRS Wiki: Dragon Slayer I](https://oldschool.runescape.wiki/w/Dragon_Slayer_I) | **keep** |
| `QUEST` where `e.text` contains `"Culinaromancer"` *(or `"Recipe for Disaster"`)* | `"Barrows gloves! An ironman rite of passage."` | [OSRS Wiki: Recipe for Disaster](https://oldschool.runescape.wiki/w/Recipe_for_Disaster) | **replace** *(fix trigger)* |
| `DRY_STREAK` *(escalating by `rung = e.number / rate.oneIn`, resets to Rung 1 when boss/item unique drops in `DryStreak.drop`)* | 1: `"It's a 50/50: you either get it or you don't."`<br>2: `"Next time, for sure."`<br>3: `"Okay, THIS time for sure."`<br>4: `"Did you remember to talk to Oziach first?"`<br>5+: `"Surely it's in the game files. Surely."` | [r/2007scape: 50/50](https://www.reddit.com/r/2007scape/comments/u2gm9t/5050/), [OSRS Wiki: Oziach](https://oldschool.runescape.wiki/w/Oziach) | **replace** *(drops `SIT`)* |
| `DROP` where `items` is solely `Bones` (`526`) | `"Bones. Truly valuable."`<br>`"Someone check the valuable drop setting."` | [OSRS Wiki: Bones (`526`)](https://oldschool.runescape.wiki/w/Bones) | **keep** |
| `ITEMS` `20997` (`Twisted bow`: `DROP`/`UNIQUE`/`RAID_UNIQUE`/`CLOG`) | `"Ok, you can log off now."`<br>`"Somebody frame this one."` | [OSRS Wiki: Twisted bow (`20997`)](https://oldschool.runescape.wiki/w/Twisted_bow) | **keep** |
| `ITEMS` `22486` (`Scythe of vitur (uncharged)`: `RAID_UNIQUE`/`CLOG`) | `"The Theatre says thanks for playing."` | [OSRS Wiki: Scythe of vitur (`22486`)](https://oldschool.runescape.wiki/w/Scythe_of_vitur) | **keep** |
| `ITEMS` `27277` (`Tumeken's shadow (uncharged)`: `RAID_UNIQUE`/`CLOG`) | `"The shadow has chosen you."` | [OSRS Wiki: Tumeken's shadow (`27277`)](https://oldschool.runescape.wiki/w/Tumeken%27s_shadow) | **keep** |
| `ITEMS` `25859` (`Enhanced crystal weapon seed`: fires on `CLOG` via `NAMED`) | `"Parole granted from the Red Prison."`<br>`"Free from Corrupted Gauntlet at last."` | [OSRS Wiki: Enhanced crystal weapon seed (`25859`)](https://oldschool.runescape.wiki/w/Module:Exchange/Enhanced_crystal_weapon_seed), [r/ironscape](https://www.reddit.com/r/ironscape/comments/10anvef/for_those_who_are_in_red_prison/) | **new** |
| `ITEMS` `12922` (`Tanzanite fang`: `DROP`/`UNIQUE`/`CLOG`) | `"Money snek finally coughed up the blowpipe."` | [OSRS Wiki: Tanzanite fang (`12922`)](https://oldschool.runescape.wiki/w/Tanzanite_fang), [r/ironscape](https://www.reddit.com/r/ironscape/comments/1chttns/3k_zulrah_kills_no_tanzanite_fang_i_heard_posting/) | **new** |
| `ITEMS` `20724` (`Imbued heart`: `DROP`/`CLOG`) | `"The superior monster actually paid out."` | [OSRS Wiki: Imbued heart (`20724`)](https://oldschool.runescape.wiki/w/Module:Exchange/Imbued_heart) | **new** |
| `ITEMS` `13576` (`Dragon warhammer`: `DROP`/`CLOG`) | `"Defence levels everywhere just got nervous."` | [OSRS Wiki: Dragon warhammer (`13576`)](https://oldschool.runescape.wiki/w/Module:Exchange/Dragon_warhammer) | **keep** |
| `ITEMS` `21003` (`Elder maul`: `RAID_UNIQUE`/`CLOG`) | `"Bonk."` | [OSRS Wiki: Elder maul (`21003`)](https://oldschool.runescape.wiki/w/Module:Exchange/Elder_maul) | **keep** |
| `ITEMS` `11920` (`Dragon pickaxe`: `DROP`/`CLOG`) | `"Rocks across Gielinor fear you now."` | [OSRS Wiki: Dragon pickaxe (`11920`)](https://oldschool.runescape.wiki/w/Dragon_pickaxe) | **keep** |
| `ITEMS` `11286` (`Draconic visage`: `DROP`/`CLOG`) | `"A face only a dragon could love."` | [OSRS Wiki: Draconic visage (`11286`)](https://oldschool.runescape.wiki/w/Module:Exchange/Draconic_visage) | **keep** |
| `ITEMS` `19529` (`Zenyte shard`: `DROP`/`CLOG`) | `"One shard closer to a very shiny necklace."` | [OSRS Wiki: Zenyte shard (`19529`)](https://oldschool.runescape.wiki/w/Module:Exchange/Zenyte_shard) | **keep** |
| `ITEMS` `3140` (`Dragon chainbody`: `DROP`/`CLOG`) | `"Chainbody? In this economy?"` | [OSRS Wiki: Dragon chainbody (`3140`)](https://oldschool.runescape.wiki/w/Dragon_chainbody) | **keep** |
| `ITEMS` `4151` (`Abyssal whip`: `DROP`/`CLOG`) | `"Whip it. Whip it good."` | [OSRS Wiki: Lookup (`4151`)](https://oldschool.runescape.wiki/w/RuneScape:Lookup) | **keep** |
| `ITEMS` `1149` (`Dragon med helm`: `DROP`/`BARROWS`/`CLOG`) | `"The legendary med helm."`<br>`"Every adventurer's first big drop."` | [OSRS Wiki: Dragon med helm (`1149`)](https://oldschool.runescape.wiki/w/Module:Exchange/Dragon_med_helm) | **keep** |
| `STORAGE_DEPOSIT` / `STORAGE_WITHDRAWAL` / `STORAGE_SUMMARY` / `STORAGE_LAST` moving **exact** `{995: 25}` (`25 x Coins`), **at most once per member ever** (persisted per-member config set) | `"Fresh off Tutorial Island with all 25 gp."` | [OSRS Wiki: New player guide (25 gp in bank)](https://oldschool.runescape.wiki/w/New_player_guide) | **new** |
| `STORAGE_WITHDRAWAL` / `STORAGE_SUMMARY` where `freeloader(e, lookup, settings)` (`took >= freeloaderAt && took >= deposited`) | `"Pulling a Wise Old Man on the group storage. 👀"`<br>`"Draynor bank called; check the security tape for a blue partyhat."`<br>`"Telegrabbing the Varrock West vault again?"`<br>`"(Charm) The group doesn't need these anyway."` | [OSRS Wiki: Draynor bank robbery](https://oldschool.runescape.wiki/w/Draynor_bank_robbery), [Varrock West Bank](https://oldschool.runescape.wiki/w/Varrock_west_bank), [Ring of Charos](https://oldschool.runescape.wiki/w/Ring_of_charos) | **replace** *(drops Gringotts)* |
| `STORAGE_DEPOSIT` / `STORAGE_SUMMARY` where `legend(e, lookup, settings)` (`deposited >= legendAt`) | `"Falador Party Room drop party in group storage! 🫶"`<br>`"The Bank of Gielinor salutes your deposit."`<br>`"Generosity level: 99."`<br>`"The group storage is eating good tonight."` | [OSRS Wiki: Falador Party Room](https://oldschool.runescape.wiki/w/Falador_Party_Room), [List of banks](https://oldschool.runescape.wiki/w/List_of_banks) | **replace** *(drops Gringotts & ironmom/irondad; keeps 2)* |
| `STORAGE_DEPOSIT` / `STORAGE_SUMMARY` where `deposited(e).containsKey(1635)` (`Gold ring`) | `"Buying gf, paying in gold rings."` | [OSRS Wiki: Gold ring (`1635`)](https://oldschool.runescape.wiki/w/Module:Exchange/Gold_ring), [r/2007scape](https://www.reddit.com/r/2007scape/comments/kwqp9a/buying_gf/) | **keep** |
| `STORAGE_LAST` *(when `< freeloaderAt` and not `{995: 25}`)* | `"Took the last one from the group shelf."` | [OSRS Wiki: Group Ironman Mode](https://oldschool.runescape.wiki/w/Group_Ironman_Mode), [r/ironscape](https://www.reddit.com/r/ironscape/comments/yjbnsj/gims_how_does_your_team_handle_splitting_up_drops/) | **new** |
| `BACK` *(member logs in after `>= backAfterDays` offline)* | `"You never quit RuneScape, you just take breaks."` | [r/2007scape](https://www.reddit.com/r/2007scape/comments/9ccr6m/you_never_quit_you_just_take_breaks/) | **new** |
