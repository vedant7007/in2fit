# 0039. A schema version is never changed in place once it has landed

Date: 21 September 2026, midday. Status: accepted (Arjun, after Ira found the crash). Affects
`KatoriDatabase`, every migration after it, and every session that lands one.

## The defect

`da67d04` (08:01) landed Room version 3 with three profile columns. `d6efa54` (10:54) added
`meals.source` and `meal_items.display_name` to the SAME version 3 instead of a version 4,
because "nothing had shipped v3 yet". The emulator had opened the first build. Under the
second, Room read a version-3 file whose identity hash was the first shape's, found no
migration to run (3 = 3), and refused it: "Room cannot verify the data integrity". The app did
not open. That is the one failure worse than a slow answer (Vedant, 21 Sep), and it was one
`adb install` away from the realme.

## The ruling

1. Once a version has landed on master it is FROZEN, whoever opened it. A schema change after
   that is the next version with its own migration, even if the change is "just a column" and
   even if you believe no device opened the last build. You cannot know what opened it.
2. A migration is written so that EVERY shape a device could hold arrives at the exported
   schema: `migrate3To4` adds the two columns when `PRAGMA table_info` says they are missing,
   then creates the v4 tables. `ADD COLUMN` is not idempotent; check first.
3. Every migration is tested on a POPULATED database on the JVM (`ProfileMigrationTest`: the
   same SQL over sqlite-jdbc, rows before and after, columns and indices compared with the
   exported schema string for string) AND on the actual device with the actual rows (queue
   item 36's first line), because Android has been known to disagree with the JVM.
4. The device's database is copied before any check opens it (`tools/arjun-checks.ps1`), so a
   refused migration is reproducible from the real rows and nothing the person logged is lost.
5. No `fallbackToDestructiveMigration`, still, anywhere.

## Measured

`ProfileMigrationTest`: v2 → v3 → v4 on populated rows; the FIRST v3 shape (profile columns
only, with a meal and an item) → v4 through the same code; the migration run a second time
neither fails nor duplicates a column; the 3 → 4 statements equal `4.json`'s. On the device:
queue item 36 (`a_item10and24`).
