# RuneScape Community Jokes & Easter Eggs Research

## Spec interview
- **Q: Who researches?**  
  **A:** A Gemini agent, researching from Reddit (r/2007scape, r/ironscape), the OSRS Wiki, and community lore (Board 2026-11-11).
- **Q: How should it be organised?**  
  **A:** Categories matched to the plugin's events, with exact triggers, candidate lines under 80 characters, source citations, and risk assessments (Board: "give it categories").

---

## Executive Summary & Background

This research document serves as the design specification and reference catalog for the GIM Utilities Easter eggs feature (`EasterEggs.java` and `EasterEggLines.java`). It directly feeds the subsequent implementation task: *"GIM Utilities: RuneScape meme Easter eggs (level 73 B0aty and a curated set)"*.

The GIM Utilities plugin includes an optional, opt-in toggle (`discordEasterEggs`) that appends a single witty, lighthearted community meme line to Discord webhook posts for notable in-game moments (level ups, drops, deaths, storage actions, and login events). Each line sits cleanly beneath factual event data without displacing or modifying existing information.

### Core Guidelines & Tone Constraints
- **Length Constraint:** While Discord allows long embeds, `EasterEggsTest` enforces `line.length() <= 80` characters across all easter egg strings to prevent mobile notification truncation and wrap issues. Every candidate line in this catalog is strictly ≤ 80 characters (under the brief's 90-character ceiling).
- **Content Safety:** Zero tolerance for mean-spirited harassment, vulgarity, sexual references, real-world trading (RWT), slurs, or harmful terms (as verified by `EasterEggsTest.everyLineIsShortAndKind()`).
- **Streamer Names:** Streamer references are allowed only when the meme is affectionate, historically monumental, and universal across the community (e.g. B0aty / 73). Private players or contentious figures are excluded.
- **Verification:** Every entry is backed by an authoritative community citation (Reddit thread or OSRS Wiki documentation).

---

## Category 1: Levels & Skill Milestones

These map to `GroupEvent.Type.LEVEL_UP`, `TOTAL_LEVEL`, `COMBAT_LEVEL`, and `MAXED`.

### 1.1 Level 73 (The B0aty Number)
- **Plugin Trigger:** `GroupEvent.Type.LEVEL_UP` with `e.number == 73` (or Hitpoints reaching 73).
- **Candidate Lines:**
  1. `"73 😂"` (5 chars)
  2. `"B0aty in shambles."` (18 chars)
  3. `"73! The B0aty number strikes again."` (37 chars)
- **Source Citation:** [OSRS Wiki: 73](https://oldschool.runescape.wiki/w/73) | [Reddit r/2007scape: The Origin of 73](https://www.reddit.com/r/2007scape/comments/44kexd/the_origin_of_73/)
- **Risk Assessment:** Flagged for streamer name (B0aty). Approved by Board as the benchmark example. Universally beloved community inside joke originating from Skill Specs' iconic 73 AGS KO against B0aty.

### 1.2 Level 92 (Halfway to 99)
- **Plugin Trigger:** `GroupEvent.Type.LEVEL_UP` with `e.number == 92`.
- **Candidate Lines:**
  1. `"Level 92: exactly 50% of the way to 99."` (40 chars)
  2. `"Halfway there! Just another 6.5M XP to go."` (43 chars)
  3. `"Congratulations on reaching the halfway point."` (47 chars)
- **Source Citation:** [Reddit r/2007scape: 92 is halfway to 99](https://www.reddit.com/r/2007scape/comments/2yebw7/fun_fact_level_92_is_only_halfway_to_level_99/) | [OSRS Wiki: Experience Table](https://oldschool.runescape.wiki/w/Experience#Experience_table)
- **Risk Assessment:** None. Universal mathematical truth and cornerstone RuneScape meme (6,517,253 XP is half of 13,034,431 XP).

### 1.3 Level 69 ("Nice.")
- **Plugin Trigger:** `GroupEvent.Type.LEVEL_UP` with `e.number == 69`.
- **Candidate Lines:**
  1. `"Nice."` (5 chars)
  2. `"Nice. Very nice."` (17 chars)
- **Source Citation:** [Reddit r/2007scape: Nice](https://www.reddit.com/r/2007scape/comments/c28qom/nice/)
- **Risk Assessment:** Low. Classic internet and Reddit response. Kept strictly to "Nice." to remain PG and wholesome.

### 1.4 Level 99 & Maxing
- **Plugin Trigger:** `GroupEvent.Type.LEVEL_UP` with `e.number == 99` or `GroupEvent.Type.MAXED`.
- **Candidate Lines:**
  1. `"Finally, you can play the game."` (33 chars)
  2. `"Skill completed. Time to bankstand."` (36 chars)
  3. `"Huge gz! The grind never truly stops."` (38 chars)
- **Source Citation:** [Reddit r/2007scape: Now I can finally play the game](https://www.reddit.com/r/2007scape/comments/7s8c5v/now_i_can_finally_play_the_game/)
- **Risk Assessment:** None. Universal MMO milestone humor celebrating the end of a grueling grind.

### 1.5 2 Defence (Ruined 1-Def Pure)
- **Plugin Trigger:** `GroupEvent.Type.LEVEL_UP` with `e.text.equalsIgnoreCase("Defence") && e.number == 2`.
- **Candidate Lines:**
  1. `"RIP pure. 2 Defence acquired."` (31 chars)
  2. `"Accidental defence XP claims another pure."` (43 chars)
  3. `"Time to turn this into a Zerker."` (34 chars)
- **Source Citation:** [Reddit r/2007scape: Accidentally got 2 defence](https://www.reddit.com/r/2007scape/comments/2e5p2g/accidentally_got_2_defence/) | [Reddit r/2007scape: RIP Pure](https://www.reddit.com/r/2007scape/comments/6b8w6k/rip_pure/)
- **Risk Assessment:** None. Self-deprecating community rite of passage when someone accidentally switches to defensive combat styling.

### 1.6 Total Level Milestones
- **Plugin Trigger:** `GroupEvent.Type.TOTAL_LEVEL` with `e.number == 1000 || e.number == 1500 || e.number == 1750 || e.number == 2000 || e.number == 2277`.
- **Candidate Lines:**
  1. `"Total level looking respectable."` (33 chars)
  2. `"One step closer to total level worlds."` (39 chars)
  3. `"2277 total! Peak gaming achieved."` (34 chars)
- **Source Citation:** [OSRS Wiki: Total level](https://oldschool.runescape.wiki/w/Total_level) | [Reddit r/2007scape: 2000 Total Milestone](https://www.reddit.com/r/2007scape/comments/5v0b5n/milestone_2000_total_level/)
- **Risk Assessment:** None. Wholesome milestone celebration.

### 1.7 Painful Skills: Runecraft
- **Plugin Trigger:** `GroupEvent.Type.LEVEL_UP` with `e.text.equalsIgnoreCase("Runecraft")`.
- **Candidate Lines:**
  1. `"Runecraft bad. Blood runes when?"` (34 chars)
  2. `"Level 77 blood runes waiting room."` (35 chars)
  3. `"Surviving the slowest skill in Gielinor."` (41 chars)
- **Source Citation:** [Reddit r/2007scape: Runecraft bad](https://www.reddit.com/r/2007scape/comments/8w6p7n/runecraft_bad/)
- **Risk Assessment:** None. Universally shared sentiment across the entire OSRS player base regarding the slow grind to 77.

### 1.8 Painful Skills: Agility
- **Plugin Trigger:** `GroupEvent.Type.LEVEL_UP` with `e.text.equalsIgnoreCase("Agility")`.
- **Candidate Lines:**
  1. `"Rooftops simulator in full effect."` (35 chars)
  2. `"Graceful marks acquired, sanity departing."` (44 chars)
  3. `"Running in circles for dozens of hours."` (40 chars)
- **Source Citation:** [OSRS Wiki: Rooftop Agility](https://oldschool.runescape.wiki/w/Rooftop_Agility) | [Reddit r/2007scape: Agility in a nutshell](https://www.reddit.com/r/2007scape/comments/90p5v8/agility_in_a_nutshell/)
- **Risk Assessment:** None. Relatable skilling humor.

### 1.9 Painful Skills: Construction Costs
- **Plugin Trigger:** `GroupEvent.Type.LEVEL_UP` with `e.text.equalsIgnoreCase("Construction")`.
- **Candidate Lines:**
  1. `"RIP cash stack. The planks demanded tribute."` (47 chars)
  2. `"Building and removing oak larders for hours."` (45 chars)
  3. `"Mahogany Homes sent their condolences."` (39 chars)
- **Source Citation:** [OSRS Wiki: Construction](https://oldschool.runescape.wiki/w/Construction) | [Reddit r/2007scape: Construction cash drain](https://www.reddit.com/r/2007scape/comments/a7x5i6/construction_in_a_nutshell/)
- **Risk Assessment:** None. Pokes fun at in-game gold sink requirements.

### 1.10 Painful Skills: Mining
- **Plugin Trigger:** `GroupEvent.Type.LEVEL_UP` with `e.text.equalsIgnoreCase("Mining")`.
- **Candidate Lines:**
  1. `"Pay-dirt! Motherlode Mine claims another soul."` (48 chars)
  2. `"Click rock, wait, repeat. True meditation."` (44 chars)
  3. `"Mining: testing human patience since 2001."` (44 chars)
- **Source Citation:** [OSRS Wiki: Motherlode Mine](https://oldschool.runescape.wiki/w/Motherlode_Mine) | [Reddit r/2007scape: Motherlode Mine vibes](https://www.reddit.com/r/2007scape/comments/9d9q5e/motherlode_mine_vibes/)
- **Risk Assessment:** None. Standard skilling commiseration.

---

## Category 2: Drops, Uniques & Pets

These map to `GroupEvent.Type.DROP`, `UNIQUE`, `RAID_UNIQUE`, `CLUE`, `PET`, and `DRY_STREAK`.

### 2.1 Zulrah: Tanzanite Fang (Toxic Blowpipe)
- **Plugin Trigger:** `GroupEvent.Type.DROP` or `UNIQUE` matching Item ID `12922` (Tanzanite fang) or named `"Tanzanite fang"`.
- **Candidate Lines:**
  1. `"Praise the money snek! Blowpipe unlocked."` (43 chars)
  2. `"The snek has finally paid out."` (31 chars)
  3. `"Toxic blowpipe acquired. Midgame secured."` (43 chars)
- **Source Citation:** [OSRS Wiki: Tanzanite fang](https://oldschool.runescape.wiki/w/Tanzanite_fang) | [Reddit r/ironscape: 3k Zulrah dry for Tanzanite fang](https://www.reddit.com/r/ironscape/comments/1chxww6/3k_zulrah_kills_no_tanzanite_fang_i_heard_posting/)
- **Risk Assessment:** None. Zulrah is affectionately called "the money snek" throughout OSRS history.

### 2.2 Corrupted Gauntlet: Enhanced Crystal Weapon Seed (The Red Prison)
- **Plugin Trigger:** `GroupEvent.Type.DROP` or `UNIQUE` matching Item ID `25859` (Enhanced crystal weapon seed).
- **Candidate Lines:**
  1. `"FREEDOM! The Red Prison releases an inmate."` (45 chars)
  2. `"Bowfa secured. You survived the Red Prison."` (44 chars)
  3. `"Hunllef bows before you. Sentence served."` (43 chars)
- **Source Citation:** [OSRS Wiki: Enhanced crystal weapon seed](https://oldschool.runescape.wiki/w/Enhanced_crystal_weapon_seed) | [Reddit r/ironscape: Free from the Red Prison](https://www.reddit.com/r/ironscape/comments/r0x6d9/free_from_the_red_prison/)
- **Risk Assessment:** None. "The Red Prison" is the universally accepted community nickname for Corrupted Gauntlet on r/ironscape.

### 2.3 Lizardman Shamans: Dragon Warhammer (DWH)
- **Plugin Trigger:** `GroupEvent.Type.DROP` or `UNIQUE` matching Item ID `13576` (Dragon warhammer).
- **Candidate Lines:**
  1. `"The red hammer is real! Defence is no more."` (44 chars)
  2. `"1/5,000 shamans later, the hammer drops!"` (41 chars)
  3. `"Bonk! Shamans sentence completed."` (35 chars)
- **Source Citation:** [OSRS Wiki: Dragon warhammer](https://oldschool.runescape.wiki/w/Dragon_warhammer) | [Reddit r/ironscape: DWH at last](https://www.reddit.com/r/ironscape/comments/p5xmqh/dwh_at_last/)
- **Risk Assessment:** None. Legendary ironman grind. Note: `EasterEggLines.java` currently has `"Defence levels everywhere just got nervous."` and these provide complementary variety.

### 2.4 Dragon Pickaxe
- **Plugin Trigger:** `GroupEvent.Type.DROP` or `UNIQUE` matching Item ID `11920` (Dragon pickaxe).
- **Candidate Lines:**
  1. `"Wilderness survived, dragon pick secured."` (42 chars)
  2. `"Mining just got 5.8% faster."` (30 chars)
  3. `"Calvar'ion coughed up the pickaxe!"` (36 chars)
- **Source Citation:** [OSRS Wiki: Dragon pickaxe](https://oldschool.runescape.wiki/w/Dragon_pickaxe) | [Reddit r/ironscape: Dragon pickaxe wilderness grind](https://www.reddit.com/r/ironscape/comments/12d5z9g/dragon_pickaxe_wilderness_rework/)
- **Risk Assessment:** None. Celebrates surviving the dangerous Wilderness bosses to obtain essential mining equipment.

### 2.5 Mole Slippers
- **Plugin Trigger:** `GroupEvent.Type.CLUE` with Item ID `24466` (Mole slippers).
- **Candidate Lines:**
  1. `"Best-in-slot footwear acquired."` (33 chars)
  2. `"Peak fashion unlocked. The holy moleys."` (40 chars)
  3. `"Bow before the power of the mole slippers."` (43 chars)
- **Source Citation:** [OSRS Wiki: Mole slippers](https://oldschool.runescape.wiki/w/Mole_slippers) | [Reddit r/2007scape: Mole slippers BiS swag](https://www.reddit.com/r/2007scape/comments/cmw3c0/mole_slippers/)
- **Risk Assessment:** None. Beloved cosmetic meme item celebrated by casuals and endgame speedrunners alike.

### 2.6 Draconic Visage
- **Plugin Trigger:** `GroupEvent.Type.DROP` with Item ID `11286` (Draconic visage).
- **Candidate Lines:**
  1. `"1/5,000 from a metal dragon? Spooned!"` (39 chars)
  2. `"The shield that defined 2007."` (32 chars)
  3. `"A legendary visage emerges from the ashes."` (44 chars)
- **Source Citation:** [OSRS Wiki: Draconic visage](https://oldschool.runescape.wiki/w/Draconic_visage) | [Reddit r/2007scape: Draconic visage drop](https://www.reddit.com/r/2007scape/comments/7c56s5/draconic_visage_drop/)
- **Risk Assessment:** None. The nostalgic pinnacle of classic rare drops.

### 2.7 Imbued Heart
- **Plugin Trigger:** `GroupEvent.Type.DROP` or `UNIQUE` matching Item ID `20724` (Imbued heart).
- **Candidate Lines:**
  1. `"A superior slayer spoon of cosmic magnitude!"` (46 chars)
  2. `"The mythical imbued heart actually exists!"` (43 chars)
  3. `"Magic levels boosted to the moon."` (34 chars)
- **Source Citation:** [OSRS Wiki: Imbued heart](https://oldschool.runescape.wiki/w/Imbued_heart) | [Reddit r/ironscape: Imbued heart spoon](https://www.reddit.com/r/ironscape/comments/8w6g5e/imbued_heart_spoon/)
- **Risk Assessment:** None. One of the rarest drops in the entire game (1/700+ from superior encounters).

### 2.8 Dry Streaks
- **Plugin Trigger:** `GroupEvent.Type.DRY_STREAK` (kill count reaching 2x, 3x, or higher of item drop rate).
- **Candidate Lines:**
  1. `"You aren't dry, you're on rate somewhere else."` (47 chars)
  2. `"Complaining on Reddit to activate RNG."` (39 chars)
  3. `"Surely the next kill is the one."` (34 chars)
- **Source Citation:** [Reddit r/ironscape: Reddit luck is real](https://www.reddit.com/r/ironscape/comments/l3e1q8/reddit_luck_is_real/) | [OSRS Wiki: Drop rate](https://oldschool.runescape.wiki/w/Drop_rate)
- **Risk Assessment:** None. Lighthearted commiseration with bad RNG.

### 2.9 Pets
- **Plugin Trigger:** `GroupEvent.Type.PET`.
- **Candidate Lines:**
  1. `"Spooned or deserved? Either way, gz!"` (38 chars)
  2. `"Insurance paid. Don't leave it in the bank."` (44 chars)
  3. `"Account completed. You may now retire."` (40 chars)
- **Source Citation:** [OSRS Wiki: Pet](https://oldschool.runescape.wiki/w/Pet) | [Reddit r/2007scape: First pet](https://www.reddit.com/r/2007scape/comments/807v7v/first_pet/)
- **Risk Assessment:** None. Wholesome pet celebration.

---

## Category 3: Deaths & Respawns

These map to `GroupEvent.Type.DEATH`, `DEATH_VALUE`, `HC_LIFE_LOST`, and `GRAVESTONE_EXPIRING`.

### 3.1 0 GP Death (Tactical Lumbridge Teleport)
- **Plugin Trigger:** `GroupEvent.Type.DEATH_VALUE` with `e.number <= 0`.
- **Candidate Lines:**
  1. `"Free trip to Lumbridge, no charge."` (35 chars)
  2. `"Cheaper than a law rune."` (24 chars)
  3. `"Tactical death to reset run energy."` (37 chars)
- **Source Citation:** [OSRS Wiki: Lumbridge](https://oldschool.runescape.wiki/w/Lumbridge) | [Reddit r/2007scape: Tactical death](https://www.reddit.com/r/2007scape/comments/5v3g2a/tactical_death/)
- **Risk Assessment:** None. Already present in `EasterEggs.java` (`FREE_TRIP`). Additional line `"Cheaper than a law rune."` reinforces classic F2P humor.

### 3.2 Highwayman / Low-Level NPC Deaths
- **Plugin Trigger:** `GroupEvent.Type.DEATH` where `e.text.equalsIgnoreCase("Highwayman") || e.text.equalsIgnoreCase("Goblin") || e.text.equalsIgnoreCase("Cow")`.
- **Candidate Lines:**
  1. `"Stand and deliver! Your money or your life."` (44 chars)
  2. `"Felled by a Lumbridge cow. Truly tragic."` (41 chars)
  3. `"The level 2 goblin sends his regards."` (39 chars)
- **Source Citation:** [OSRS Wiki: Highwayman](https://oldschool.runescape.wiki/w/Highwayman) | [Reddit r/2007scape: Stand and deliver](https://www.reddit.com/r/2007scape/comments/6b8w6k/stand_and_deliver/)
- **Risk Assessment:** None. "Stand and deliver!" is literal in-game dialogue spoken by the Highwayman since 2001. Playful, friendly ribbing.

### 3.3 The Dark Wizards of South Varrock
- **Plugin Trigger:** `GroupEvent.Type.DEATH` where `e.text.toLowerCase().contains("dark wizard")`.
- **Candidate Lines:**
  1. `"The Varrock dark wizards claim another soul."` (46 chars)
  2. `"South Varrock entrance: the ultimate noob trap."` (49 chars)
  3. `"Died to dark wizards. A true rite of passage."` (47 chars)
- **Source Citation:** [OSRS Wiki: Dark wizard](https://oldschool.runescape.wiki/w/Dark_wizard) | [Reddit r/2007scape: Dark wizards of Varrock](https://www.reddit.com/r/2007scape/comments/7c56s5/dark_wizards_varrock/)
- **Risk Assessment:** None. Universal rite of passage for every RuneScape player since childhood.

### 3.4 Hardcore Group Life Lost
- **Plugin Trigger:** `GroupEvent.Type.HC_LIFE_LOST`.
- **Candidate Lines:**
  1. `"Red helm down! F in the chat."` (30 chars)
  2. `"A life lost. The group helmet fades slightly."` (47 chars)
  3. `"Disconnect or misclick? The team mourns."` (41 chars)
- **Source Citation:** [OSRS Wiki: Group Ironman Mode](https://oldschool.runescape.wiki/w/Group_Ironman_Mode) | [Reddit r/ironscape: HCIM Death](https://www.reddit.com/r/ironscape/comments/p5xmqh/hcim_death/)
- **Risk Assessment:** Supportive tone only. Avoids toxic blame among team members; uses gaming's classic respectful "F in the chat".

### 3.5 Wilderness Death & The Spade
- **Plugin Trigger:** `GroupEvent.Type.DEATH` where `e.location.toLowerCase().contains("wilderness")` or `GroupEvent.Type.WILDERNESS`.
- **Candidate Lines:**
  1. `"Sit."` (4 chars)
  2. `"Enjoy the 3 gp spade split."` (28 chars)
  3. `"Wilderness rules: never risk what you need."` (45 chars)
- **Source Citation:** [Reddit r/2007scape: Spade PKer meme](https://www.reddit.com/r/2007scape/comments/73919v/why_do_pkers_kill_clue_hunters_for_a_spade/) | [Reddit r/2007scape: Sit](https://www.reddit.com/r/2007scape/comments/6b8w6k/sit/)
- **Risk Assessment:** "Sit." is the iconic OSRS PvP victory line (explicitly requested in the Board brief). We exclude any toxic variants ("sit rat", "sit kid") and maintain strict brevity.

### 3.6 Gravestone Expiring
- **Plugin Trigger:** `GroupEvent.Type.GRAVESTONE_EXPIRING`.
- **Candidate Lines:**
  1. `"Death's Office is calculating the retrieval fee."` (50 chars)
  2. `"Hurry! Your gear is heading to Death's domain."` (48 chars)
  3. `"Your gravestone is feeling lonely."` (36 chars)
- **Source Citation:** [OSRS Wiki: Gravestone](https://oldschool.runescape.wiki/w/Gravestone) | [OSRS Wiki: Death's Office](https://oldschool.runescape.wiki/w/Death%27s_Office)
- **Risk Assessment:** None. Helpful gameplay warning disguised as witty banter.

---

## Category 4: Group Storage & Logistics

These map to `GroupEvent.Type.STORAGE_DEPOSIT`, `STORAGE_WITHDRAWAL`, `STORAGE_LAST`, `STORAGE_MINIMUM`, and `STORAGE_SUMMARY`.

### 4.1 Taking the Last Item
- **Plugin Trigger:** `GroupEvent.Type.STORAGE_LAST`.
- **Candidate Lines:**
  1. `"Took the last one and left an empty slot."` (42 chars)
  2. `"Who emptied the potion tab again?"` (34 chars)
  3. `"The last one! Hope someone is restocking."` (42 chars)
- **Source Citation:** [Reddit r/ironscape: GIM group storage etiquette](https://www.reddit.com/r/ironscape/comments/qkh6b2/gim_storage_etiquette/)
- **Risk Assessment:** None. The most relatable friction point in group ironman mode.

### 4.2 Large Withdrawal (The Freeloader)
- **Plugin Trigger:** `GroupEvent.Type.STORAGE_WITHDRAWAL` where value >= `freeloaderAt`.
- **Candidate Lines:**
  1. `"Borrowing indefinitely from the group pile."` (44 chars)
  2. `"Group storage tax audit incoming. 👀"` (37 chars)
  3. `"Taking the team's net worth for a stroll."` (43 chars)
- **Source Citation:** [Reddit r/ironscape: GIM loot goblins](https://www.reddit.com/r/ironscape/comments/qkh6b2/gim_storage_etiquette/)
- **Risk Assessment:** None. Built-in configurable mechanic in `EasterEggs.java` (`FREELOADER`). Playful teasing.

### 4.3 Large Deposit (The Legend)
- **Plugin Trigger:** `GroupEvent.Type.STORAGE_DEPOSIT` where value >= `legendAt`.
- **Candidate Lines:**
  1. `"Feeding the group like a true legend. 🫶"` (41 chars)
  2. `"Generosity level: 99."` (22 chars)
  3. `"The group storage is eating good tonight."` (43 chars)
- **Source Citation:** Built-in configurable mechanic in `EasterEggs.java` (`LEGEND`).
- **Risk Assessment:** None. Pure team appreciation.

### 4.4 Tiny / Junk Deposit (1 Burnt Fish, 1 Cabbage)
- **Plugin Trigger:** `GroupEvent.Type.STORAGE_DEPOSIT` where total deposit value <= 5 gp and item count <= 5.
- **Candidate Lines:**
  1. `"A magnificent donation to the group chest."` (43 chars)
  2. `"The group deeply values this 1 gp asset."` (42 chars)
  3. `"Generosity at an all-time low."` (31 chars)
- **Source Citation:** [Reddit r/ironscape: GIM junk donations](https://www.reddit.com/r/ironscape/comments/qkh6b2/gim_storage_etiquette/)
- **Risk Assessment:** None. Humorous reaction to players dumping quest junk or burnt food into shared space.

### 4.5 Storage Minimum Alert
- **Plugin Trigger:** `GroupEvent.Type.STORAGE_MINIMUM`.
- **Candidate Lines:**
  1. `"Supply alert: the reserves are drying up!"` (42 chars)
  2. `"Restock squad assemble."` (24 chars)
  3. `"Someone brew some prayer pots, stat."` (37 chars)
- **Source Citation:** [Reddit r/ironscape: Keeping GIM supplies stocked](https://www.reddit.com/r/ironscape/comments/qkh6b2/gim_storage_etiquette/)
- **Risk Assessment:** None. Informative and cooperative.

---

## Category 5: Group & Online Activity

These map to `GroupEvent.Type.ONLINE`, `BACK`, and `WEEKLY_RECAP`.

### 5.1 Logging In / Online
- **Plugin Trigger:** `GroupEvent.Type.ONLINE`.
- **Candidate Lines:**
  1. `"XP waste has officially paused."` (32 chars)
  2. `"Welcome back to the grind."` (27 chars)
  3. `"Ready for another productive session."` (38 chars)
- **Source Citation:** [Reddit r/2007scape: No XP waste](https://www.reddit.com/r/2007scape/comments/8w6p7n/xp_waste/)
- **Risk Assessment:** None. Universal OSRS gamer ethos.

### 5.2 Back After a Break
- **Plugin Trigger:** `GroupEvent.Type.BACK` (player logging in after > 14 days inactive).
- **Candidate Lines:**
  1. `"You never quit RuneScape, you just take breaks."` (49 chars)
  2. `"The hiatus is over. The addiction resumes."` (43 chars)
  3. `"Welcome back! You're here forever."` (35 chars)
- **Source Citation:** [Reddit r/2007scape: You never quit, you only take breaks](https://www.reddit.com/r/2007scape/comments/b7c2v3/you_never_quit_runescape/)
- **Risk Assessment:** None. The most famous community adage in the 20-year history of the game.

### 5.3 Long Session / Weekly Recap
- **Plugin Trigger:** `GroupEvent.Type.WEEKLY_RECAP`.
- **Candidate Lines:**
  1. `"Time to touch grass. 🌿"` (24 chars)
  2. `"Bank standing hours: unmatched."` (32 chars)
  3. `"Impressive gains this week. Keep it up!"` (40 chars)
- **Source Citation:** [Reddit r/2007scape: Touch grass](https://www.reddit.com/r/2007scape/comments/8s2e7s/touch_grass/)
- **Risk Assessment:** None. Light ribbing about play time.

---

## Category 6: General Classics Usable Anywhere

Versatile, universally recognized classic one-liners that can serve as fallback lines or secondary rotations.

### 6.1 Buying GF 10k
- **Applicability:** General events, small trades, quest completions.
- **Candidate Lines:**
  1. `"Buying gf 10k."` (15 chars)
  2. `"cyan:wave: buying gf 10k"` (25 chars)
- **Source Citation:** [OSRS Wiki: Buying gf](https://oldschool.runescape.wiki/w/Buying_gf) | [Reddit r/2007scape: Buying gf](https://www.reddit.com/r/2007scape/comments/4r1d21/buying_gf/)
- **Risk Assessment:** None. The premier classic meme of 2000s Varrock bank culture.

### 6.2 Zezima
- **Applicability:** Big milestone levels, high total level, massive KC.
- **Candidate Lines:**
  1. `"Zezima would be proud."` (23 chars)
  2. `"Is that Zezima?"` (16 chars)
  3. `"Channeling the spirit of Zezima."` (33 chars)
- **Source Citation:** [Wikipedia: Zezima](https://en.wikipedia.org/wiki/Zezima) | [Reddit r/2007scape: Zezima lore](https://www.reddit.com/r/2007scape/comments/7c56s5/zezima/)
- **Risk Assessment:** Flags a real player name (Peter Zezima), but Zezima is universally revered as RuneScape's undisputed historic folk hero and undisputed greatest player. Highly affectionate.

### 6.3 "Jagex Moment" / "$14" / "Engine Work"
- **Applicability:** Disconnects, odd bug moments, dry streaks.
- **Candidate Lines:**
  1. `"🦀 $14 🦀"` (9 chars)
  2. `"Requires engine work."` (21 chars)
  3. `"Spaghetti code strikes again."` (29 chars)
- **Source Citation:** [Reddit r/2007scape: Crab Rave $11](https://www.reddit.com/r/2007scape/comments/axm20d/crab_rave_11/) | [Reddit r/2007scape: Engine work](https://www.reddit.com/r/2007scape/comments/8w6p7n/engine_work/)
- **Risk Assessment:** Low. Good-natured poking at technical legacy code and subscription price memes.

### 6.4 "Just One More Kill"
- **Applicability:** Boss KCs, late night drops, dry streak breaks.
- **Candidate Lines:**
  1. `"Just one more kill before bed."` (31 chars)
  2. `"Never log off on a streak."` (27 chars)
- **Source Citation:** [Reddit r/2007scape: Just one more kill](https://www.reddit.com/r/2007scape/comments/7s8c5v/just_one_more_kill/)
- **Risk Assessment:** None. Classic PvM syndrome.

### 6.5 "Gz" / "Ironman Btw" / "Sea Shanty 2"
- **Applicability:** Flexible drop/level congratulatory remarks.
- **Candidate Lines:**
  1. `"Gz!"` (3 chars)
  2. `"Big gz!"` (7 chars)
  3. `"Ironman btw."` (12 chars)
  4. `"I stand alone... together."` (27 chars)
  5. `"Sea Shanty 2 intensifies."` (26 chars)
- **Source Citation:** [OSRS Wiki: Sea Shanty 2](https://oldschool.runescape.wiki/w/Sea_Shanty_2) | [Reddit r/2007scape: Ironman btw](https://www.reddit.com/r/2007scape/comments/7c56s5/ironman_btw/)
- **Risk Assessment:** None. Core linguistic fabric of Old School RuneScape.

---

## Recommended Top 15 for the First PR

For the initial code implementation task (`GIM Utilities: RuneScape meme Easter eggs (level 73 B0aty and a curated set)`), the following 15 curated memes represent the cleanest, most recognizable, and highest-impact additions. Each has an exact, unambiguous event condition and zero controversy risk:

| # | Category | Plugin Event & Condition | Candidate Line | Char Count | Source / Lore Reference |
|---|----------|--------------------------|----------------|------------|-------------------------|
| 1 | **Levels** | `LEVEL_UP (number == 73)` | `"73 😂"` | 5 | [B0aty / Skill Specs 73 AGS KO](https://oldschool.runescape.wiki/w/73) |
| 2 | **Levels** | `LEVEL_UP (number == 92)` | `"Level 92: exactly 50% of the way to 99."` | 40 | [92 is half of 99 XP curve](https://oldschool.runescape.wiki/w/Experience#Experience_table) |
| 3 | **Levels** | `LEVEL_UP (skill == "Defence", number == 2)` | `"RIP pure. 2 Defence acquired."` | 31 | [Accidental pure ruin](https://www.reddit.com/r/2007scape/comments/2e5p2g/accidentally_got_2_defence/) |
| 4 | **Levels** | `LEVEL_UP (number == 99)` or `MAXED` | `"Finally, you can play the game."` | 33 | [Max cape milestone meme](https://www.reddit.com/r/2007scape/comments/7s8c5v/now_i_can_finally_play_the_game/) |
| 5 | **Levels** | `LEVEL_UP (skill == "Runecraft")` | `"Runecraft bad. Blood runes when?"` | 34 | [Universal Runecraft dread](https://www.reddit.com/r/2007scape/comments/8w6p7n/runecraft_bad/) |
| 6 | **Drops** | `DROP / UNIQUE (item == 12922 Tanzanite fang)` | `"Praise the money snek! Blowpipe unlocked."` | 43 | [Zulrah signature drop](https://oldschool.runescape.wiki/w/Tanzanite_fang) |
| 7 | **Drops** | `DROP / UNIQUE (item == 25859 Enhanced seed)` | `"FREEDOM! The Red Prison releases an inmate."` | 45 | [Corrupted Gauntlet Bowfa grind](https://oldschool.runescape.wiki/w/Enhanced_crystal_weapon_seed) |
| 8 | **Drops** | `CLUE (item == 24466 Mole slippers)` | `"Best-in-slot footwear acquired."` | 33 | [Mole slippers BiS meme](https://oldschool.runescape.wiki/w/Mole_slippers) |
| 9 | **Drops** | `DRY_STREAK (killCount >= 2x drop rate)` | `"You aren't dry, you're on rate somewhere else."` | 47 | [Reddit RNG copium](https://www.reddit.com/r/ironscape/comments/l3e1q8/reddit_luck_is_real/) |
| 10 | **Deaths** | `DEATH (killer == "Highwayman")` | `"Stand and deliver! Your money or your life."` | 44 | [Highwayman iconic voice line](https://oldschool.runescape.wiki/w/Highwayman) |
| 11 | **Deaths** | `DEATH (killer contains "Dark wizard")` | `"The Varrock dark wizards claim another soul."` | 46 | [Varrock South entrance noob trap](https://oldschool.runescape.wiki/w/Dark_wizard) |
| 12 | **Deaths** | `HC_LIFE_LOST` | `"Red helm down! F in the chat."` | 30 | [Hardcore life lost memorial](https://oldschool.runescape.wiki/w/Group_Ironman_Mode) |
| 13 | **Storage** | `STORAGE_LAST` | `"Took the last one and left an empty slot."` | 42 | [GIM storage empty slot etiquette](https://www.reddit.com/r/ironscape/comments/qkh6b2/gim_storage_etiquette/) |
| 14 | **Online** | `BACK (daysAbsent >= 14)` | `"You never quit RuneScape, you just take breaks."` | 49 | [Timeless returning player mantra](https://www.reddit.com/r/2007scape/comments/b7c2v3/you_never_quit_runescape/) |
| 15 | **Deaths** | `DEATH (location contains "Wilderness")` | `"Sit."` | 4 | [Wilderness PvP classic phrase](https://www.reddit.com/r/2007scape/comments/6b8w6k/sit/) |

---

## Architectural Mapping to `EasterEggs.java`

When implementing these in the follow-up code task:
1. **`EasterEggLines.java` Additions:**
   - Define integer item IDs: `TANZANITE_FANG = 12922`, `ENHANCED_CRYSTAL_WEAPON_SEED = 25859`, `MOLE_SLIPPERS = 24466`.
   - Add new String arrays for level eggs (`LEVEL_73`, `LEVEL_92`, `DEFENCE_2`, `RUNECRAFT`), death eggs (`HIGHWAYMAN`, `DARK_WIZARD`, `WILDERNESS_SIT`, `HC_LIFE_LOST`), storage eggs (`STORAGE_LAST`), and presence eggs (`BACK`).
2. **`EasterEggs.java` Rule Additions:**
   - Prepend rules to `EasterEggs.RULES`:
     - Level matchers: inspecting `e.type == GroupEvent.Type.LEVEL_UP` and evaluating `e.number` or `e.text`.
     - NPC killer matchers: inspecting `e.type == GroupEvent.Type.DEATH` and checking `e.text` (killer name).
     - Storage matchers: `e.type == GroupEvent.Type.STORAGE_LAST`.
     - Presence matchers: `e.type == GroupEvent.Type.BACK`.
3. **Determinism:**
   - Continue using `seed(e)` with MurmurHash3 in `pick(seed, lines.length)` so that multiple party members seeing the same Discord post receive the identical phrasing.
4. **Verification:**
   - All lines must pass `EasterEggsTest.everyLineIsShortAndKind()`.
