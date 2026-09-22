# Localisation

The interface is English. Every visible string is a key in `app/src/main/res/values/strings.xml`
(a test refuses a literal in a screen), which is what makes another language a review job
rather than a rebuild.

| file or folder | what it is |
| --- | --- |
| `string-conventions.md` | the rules for anyone adding a key: grep first, sentence shape, what may never be softened |
| `telugu-review-queue.md` | **the reviewer packet**, frozen 20 September at 22:00, 176 items. It ships once and is not regenerated while a reviewer holds it |
| `telugu-review-check.md` | what was imported back into the app, and what was superseded |
| `replies/` | the record of every machine-generated candidate line, kept unedited. Nobody on this team reads Telugu; these files are the provenance of every line in `values-te/` |
| `packet-extra/` | material that travels with the packet but is not a string |

**Every Telugu line in the app is machine-generated and unreviewed**, marked as such beside
the key, and it stays marked until a fluent speaker confirms it. The tools that build the
packet and import a reply are `tools/make_review_queue.py` and `tools/import_review_queue.py`.
