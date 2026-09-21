# 0038. A marker explanation says what the marker measures, and nothing else

Date: 21 September 2026, late morning. Status: accepted (Arjun, under 15.1, 15.2 and 0018;
Priya may strike or rewrite any row, Nila the words). Affects `knowledge/markers.csv`,
`MarkerExplanations`, the Reports and marker screens.

## What the design asked for

A line under each report row saying what the marker means. The design wrote it as a verdict
("Prediabetic", "Low"). The app's flag is "below / above / within the printed range" and no
other word (0018, Ira's amendment 6), so the line under it must not become the verdict by
another route.

## The ruling

1. SHIPPED, NOT GENERATED. One row per marker in `assets/knowledge/markers.csv`, read back
   verbatim; the model never writes one and never sees one.
2. WHAT A ROW MAY SAY: what the marker is and what the test measures, in the source's own
   words where possible. WHAT IT MAY NOT SAY: a number that could read as a threshold, a
   unit, a reference range, "normal", a condition, a diagnosis, "may indicate". The range is
   the report's own; a threshold in this file would let a card turn "below the printed range"
   into a diagnosis.
3. EVERY ROW CITES A SOURCE READ ON THE DATE IN `accessed`; the loader refuses a row without
   one, a duplicate alias, a wrong header, the way `KnowledgeFacts` does.
4. MATCHING BY PRINTED NAME. A report prints "Haemoglobin", "Hemoglobin", "Hb", "HbA1c",
   "Glycosylated Haemoglobin (HbA1c)". A row carries `|`-separated aliases matched as whole
   words in the printed name; the row with the MOST aliases hit wins (the HbA1c row beats the
   haemoglobin row on the long form); a tie shows nothing, because a wrong explanation is
   worse than none; a marker with no row shows nothing.

## Measured

`MarkerExplanationsTest`: every shipped row cited and dated; no digit outside the marker's own
name, no unit, no range word, no condition word, or the file is refused; the printed names
above resolve as ruled; "Platelet count" finds nothing. Seven rows shipped 21 Sep
(haemoglobin, HbA1c, ferritin, vitamin D, vitamin B12, TSH, glucose), each to a MedlinePlus
lab-test page opened that day. On the device: queue item 27 and 36 (`g_item27`).
