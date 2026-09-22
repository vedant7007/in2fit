# 0040. A day's target is computed from the profile, carries its basis, and is null before it is a guess

Date: 22 September 2026. Status: accepted, ruled by Vedant ("computed deterministically from
the profile, supplied to the UI as a figure, never produced by the model; every target carries
its basis, so that 'where does 2,100 come from' has an answer from inside the app").

## The rule (`TargetRules`, `domain/Targets.kt`)

Every figure stands on a row in `assets/knowledge/facts.csv`, cited there:

- **Energy**: ICMR-NIN 2020 Tables 1a and 1b (`energy.india_adults`): 2110 / 2710 / 3470 kcal a
  day for the 65 kg reference man doing sedentary / moderate / heavy work, 1660 / 2130 / 2720
  for the 55 kg reference woman, scaled by the person's weight against the reference weight,
  as the tables are built (the requirement is per kilogram). To the nearest 10 kcal.
- **Protein**: ICMR-NIN 2020 RDA, 0.83 g per kg (`protein.rda_india`).
- **Fat**: a ceiling, WHO at most 30% of energy (`fat.who_limits`) at 9 kcal/g
  (`fat.energy_per_gram`).
- **Carbohydrate**: the remainder of the energy target after the protein RDA at 4 kcal/g and the
  fat ceiling, at 4 kcal/g of carbohydrate (the Atwater general factor). Arithmetic on sourced
  figures, named as such in the basis.
- **Water**: no sourced figure in the file, so no target (`waterMl = 0`, and the water card
  treats 0 as none). The day a sourced row lands, the target does.

The basis is a sentence on the target: "Energy: ICMR-NIN 2020 Tables 1a/1b, 2110 kcal for a 65 kg
man doing sedentary work, scaled to 62 kg (32.5 kcal per kg). Protein: ICMR-NIN 2020 RDA, 0.83 g
per kg. Fat: a ceiling, WHO 30% of energy at 9 kcal per gram. Carbohydrate: the remainder at
4 kcal per gram. Water: no sourced target."

**The demo person** (19, 62 kg, male, hostel student, sedentary): 2010 kcal, 51 g protein, 67 g
fat as a ceiling, 301 g carbohydrate.

## What it refuses to guess

No weight, no sex stated as male or female (the tables have no third column and the app does
not average them), no activity level, an age under 18 (no adolescent table shipped): null, and
the ring stays empty rather than showing a plausible number. A goal of losing or gaining weight
gets the maintenance requirement with the basis saying so: the file carries no sourced deficit or
surplus, and a number without one is a guess.

## Progress

Per nutrient with a target, against today's COMPLETE totals only (a partial total is a floor and
is never compared): consumed, remaining (floored at zero), fraction, and a status with the rule's
own bands, not a source's: WITHIN from 90% to 110%, UNDER below, OVER above; the fat ceiling is
WITHIN at or below it and OVER above. A nutrient with nothing logged is UNDER at zero.

`TargetRulesTest`, seven tests, on the JVM. The activity levels are `ActivityLevel` (0040's
companion, landed with the चटनी alias): SEDENTARY, MODERATE, HEAVY, ICMR-NIN 2020's three, the
hostel student who walks to class being SEDENTARY.
