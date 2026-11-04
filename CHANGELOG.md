# Changelog

## Unreleased

- `::find <item>` chat command: who in the group holds a matching item (last
  bank, inventory, equipment, rune pouch, quiver, seed vault, group storage)
  and who last took or deposited it in group storage. Shown only in your own
  chatbox, at most five lines. `::`, not `!`, so the query is never sent to
  public chat. New setting *::find chat command*, on by default.
- "Who has it?" in the panel also lists the newest three storage log visits
  that moved a matching item.
- The storage log already survived a restart (it is in
  `.runelite/build-guard/group-cache.json`, capped at 200 visits); now covered
  by a round-trip test.
