# Licence texts filed verbatim

One file per third-party licence whose text this project is obliged to carry, or that a
reader should be able to check without leaving the repository. Every file here is the
upstream text byte-for-byte: `.gitattributes` keeps `docs/licences/**` out of line-ending
normalisation so a hash comparison still works on a Windows clone.

The reasoning that chose each dependency, and the licence of every model and library that is
NOT bundled as a file, is in [`docs/decisions/0005`](../decisions/0005-model-sourcing-and-licences.md).
The project's own licence is [`LICENSE`](../../LICENSE) at the root (Apache-2.0).

| file | covers | source, and when it was fetched | sha256 |
| --- | --- | --- | --- |
| `ibm-plex-ofl-1.1.txt` | `app/src/main/res/font/plex_regular.ttf`, `plex_medium.ttf`, `plex_semibold.ttf` (IBM Plex Sans Devanagari, the Devanagari and digit face) | `raw.githubusercontent.com/IBM/plex/master/LICENSE.txt`, 22 Sep 2026 | `7e6b2818edbd8f6a01ae80641cc8f16a51080d08fb4e532be3a0b6f74adb07da` |
| `instrument-sans-ofl-1.1.txt` | `app/src/main/res/font/instrument_sans.ttf` | `raw.githubusercontent.com/Instrument/instrument-sans/master/OFL.txt`, 22 Sep 2026 | `9e27a72ed30eb49a08678f6a5d6ed98ec7ba5368f541637ee0683ec9134ef966` |
| `instrument-serif-ofl-1.1.txt` | `app/src/main/res/font/instrument_serif.ttf`, `instrument_serif_italic.ttf` | `raw.githubusercontent.com/Instrument/instrument-serif/main/OFL.txt`, 22 Sep 2026 | `129ed7618959716959f2941fdd5b49e0ad6e6c1d78726761786a00253d865521` |
| `iitm-tts-eula.txt` | the IIT Madras Indic TTS database EULA, which governs three Piper voices this project considered and did not ship | transcribed from the PDF on 20 Sep 2026; **not yet diffed against the PDF**, see `0005` | `3dcfe36df6afa2e73849491333f25b0017682bd76cc7169652ea25fda6510f0c` |

**espeak-ng** ships as an asset tree (`app/src/main/assets/espeak-ng-data/`, 244 files) and is
**GPL-3.0-or-later**. Its text is not duplicated here because the duties it creates are not a
file to copy: they are recorded, with what must be done and when it attaches, under "COPYLEFT"
in `0005`. Read that section before any APK is handed to anyone.
