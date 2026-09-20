# Before the repository URL goes into the form

Written 20 September 2026 (Nila). The prototype URL is the repository. When this was written
the repository existed only on one laptop; at 19:41 it was created as
`https://github.com/vedant7007/in2fit`, PRIVATE, `master` pushed after the authorship audit
below. This is what must be true before a judge opens that link, which is to say before it is
made public. Each item was CHECKED against the repository as it stood at `1e5c42f`,
not just listed; the state found is beside it. Items marked **[Vedant]** are his decisions.

## 1. Nothing secret, nothing personal beyond what Vedant chooses to publish

| check | how it was checked | state at `1e5c42f` |
| --- | --- | --- |
| No keys, tokens, credentials in tracked files | `git ls-files` for `local.properties`, keystores, `.env`, `secret`, `token`, `credential`, `.pem`; `git grep` for the shapes of Hugging Face, GitHub, AWS and OpenAI tokens and `Bearer` headers | **clean**; `local.properties` is gitignored and absent from history |
| No absolute paths from anyone's machine | `git grep` for `Users\vedan`, `AppData`, `C:\CODING`, container paths | **five hits, all the username `vedan` inside the SDK path** `C:\Users\vedan\AppData\Local\Android\Sdk`: `HANDOVER.md` §2 and §5, `docs/decisions/0003` (twice), `docs/review-packets/phase-1b.md`, and hard-coded in `tools/hardware-probe.ps1:24`. The documents describe the SDK choice and could say `%LOCALAPPDATA%\Android\Sdk` instead; the script should read `$env:LOCALAPPDATA` as `build-llama-android.ps1` does (Rao's file, one line). **[Vedant]**: these reveal only his Windows username; edit or accept. |
| No private network details | `git grep` for IP addresses | **one**: the phone's LAN address `192.168.29.235:5555` (adb over Wi-Fi) in `COORDINATION.md`, twice. Harmless in itself; a home-network detail all the same. **[Vedant]**: redact or accept. |
| No personal data beyond the author's | `git grep` for email addresses | Vedant's own address as commit author and in `HANDOVER.md` (his choice); a dataset contact `smtiitm@gmail.com` quoted from a public model card; espeak-ng maintainers' addresses inside upstream GPL data files, which travel with that data everywhere it is redistributed. Nothing else. |
| Nobody else's name without consent | read | Team-mates named in the spec and records (Abhinav, listed roles) are Vedant's to confirm as publishable. **[Vedant]** |
| The device | read | `realme RMX3780` and its measurements throughout: a device model, not a person. Fine. |

## 2. No large binaries, in the working tree or in history

Checked with `git rev-list --objects --all` and `cat-file --batch-check` over every object ever
committed. **Clean.** The largest blob in all of history is 0.5 MB (`espeak-ng-data/phondata`);
the food database at 0.3 MB has four versions; the screenshots are 0.2 MB each. No `.gguf`,
`.onnx`, `.so`, `.aar`, `.apk`, `.tar`, `.zip` object exists at any point in history; the only
`.jar` is `gradle-wrapper.jar`, which belongs there. `.git` is 17 MB. The `.gitignore` keeps
`data-sources/`, `logs/`, `app/libs/` and `jniLibs/` out, and the fetch script with checksums
(`tools/fetch-models.ps1`) is how a clone gets them back.

## 3. A LICENSE, chosen deliberately, consistent with 0005

**Done.** `LICENSE` is the canonical Apache-2.0 text (11,358 bytes, sha256 `cfc7749b…`), ruled by
Vedant on 20 September, reasoning in `0005`. It is consistent with every shipped dependency
including the GPL one (Apache-2.0 is GPL-3-compatible in the direction that matters, and the
combined APK is conveyed under GPL-3 terms with a source pointer). `0005` records the duties and
that they attach on distributing the APK, not the source. The one thing still owed under it
if Piper ships: the pointer to the exact espeak-ng source sherpa-onnx built (parked until the
TTS probe reports).

## 4. The README as the landing page

**Done** (`README.md`, 20 September 17:43): what it is, where to read and in what order, how to
build on a fresh clone, the rules the code keeps. It says the working name was Katori and where
that name survives, so the file tree does not surprise a reader.

## 5. The repository name, and IN2FIT everywhere a reader can see

| where | state |
| --- | --- |
| Repository name | **Settled: `in2fit`**, created 20 Sep 19:41 as `https://github.com/vedant7007/in2fit`, PRIVATE, `master` pushed at `83e7375`. The working directory is still `IQOOOOO` locally, which nobody but the team sees. |
| README title, STATUS title, the form, the deck | IN2FIT |
| `HANDOVER.md` title | "Katori — handover report", with a dated addendum at the top saying the product is IN2FIT. Left as history. |
| Package `io.github.vedant7007.katori`, class names (`KatoriDatabase`, `KatoriApp`), `katori-food.db`, `katori_llama.cpp`, `Theme.Katori` | **stay**, by ruling (`0015`): the JNI symbol names encode the package. A reader of the file tree sees them; the README explains in one line. Not reader-visible on the phone: the app is labelled IN2FIT and the launcher icon is the app's own. |

## 6. Public or private, and how a judge is given access

**[Vedant]**. State on 20 Sep 19:45: **private**, by ruling, until 7a is answered; it goes
public before submission and not before. Recommendation for that moment: **public**. The reasons: judges are not known in advance, and a
private repository on GitHub needs each reader added as a collaborator by name, which nobody
will be doing during judging; the GPL duties in `0005` are about the APK and are satisfied
more simply when the source is public; the form's answer says "a reader will find", which is a
promise the link has to keep for anyone who follows it. If private is chosen, the form answer
must carry the access mechanism (an invitation to a named judging account, arranged with the
organisers before submission), and "a reader will find" becomes "on request".

## 7. Two things that are not housekeeping, surfaced for Vedant, not solved

### 7a. Six voices in the records, three people on the deck

`COORDINATION.md` is written as a log in six named voices (Nila, Rao, Priya, Jacob, Meera, Arjun;
182 dated entries). Sixteen decision records carry those names ("added by Meera", "Rao's
probe"), and ten source files mention them in comments. The deck's team page says three people.
A judge who opens the repository sees six names and a three-person team, and has to be able to
tell at a glance what the names are: **session labels for AI assistants used as tooling by the
person who authored every commit, not team members.** Those records are the strongest evidence
of rigour the project has; nobody wants to lose them; and nothing in the repository currently
says what they are.

Two facts bear on the choice:

- The standing rule says no AI name or attribution anywhere in the repository. Two committed
  files already contain one: `docs/spec.md` §17.3 ("Working with Claude Code and Cowork", the
  original plan to have assistant sessions drive the build, committed by Vedant in `df82e35`),
  and `HANDOVER.md` §6 rule 7, which quotes the rejected trailer with a model name in it. So the
  repository as it stands both discloses the tooling in one place and names it in two.
- Nothing in the repository claims the six names are people, and nothing may be written that
  does.

The options:

1. **Keep every record as it is, and add one paragraph, once, where a reader lands** (the
   README's "Where to look" table, beside `COORDINATION.md`), saying in plain words that the
   names in the log and the records are labels for assistant sessions used as tooling, that
   every commit is authored by the one person who directed them, and that the records are kept
   because they are the evidence for every number. This needs Vedant to lift the standing rule
   for that one paragraph, and to reconcile it with `spec.md` §17.3, which already says the same
   thing at length. **Recommended.** It is true, it is cheap, it makes the strongest evidence
   readable instead of suspicious, and a judge who works it out unaided later will assume the
   worst.
2. **Keep every record as it is and say nothing.** Consistent with the rule as written;
   inconsistent with `spec.md` §17.3, which is already in the repository; and it leaves a reader
   to guess what six first-person voices and a three-person deck mean.
3. **Publish without the working log**: move `COORDINATION.md` (and the session-voiced lines in
   the sixteen records and ten source files) out of the published repository into a private
   mirror, and publish the code, tests, decision records in an impersonal voice, and the demo
   files. Loses the evidence that the audit and the rules work, which is what the standout text
   on the form is about.
4. Rewrite the names out of the history. Not proposed: it is a rewrite of the evidence, and the
   names are in 182 log entries, sixteen records and ten source files.

Whatever is chosen, the README's one line about Katori is the model: a reader told once, plainly,
does not have to guess.

### 7b. The standing rule and the two files that already break it

`docs/spec.md` §17.3 and `HANDOVER.md` §6 rule 7 (lines 405–410) name an AI tool. **[Vedant]**:
either the rule applies to commits, PRs and attribution from here on and these two historical
passages stand, or they are edited before publishing. Not edited here; both are records of
what was planned and what was decided, and this checklist's rule is that a correction is
appended, never overwritten.

## The order to do it in

1. Decide 7a and 7b (they decide what the README says).
2. Edit or accept the five `vedan` paths and the LAN address (item 1); Rao's one-line script fix.
3. ~~Create the repository under the settled name~~ Done, private, `origin` set in the shared
   git config so every worktree sees it. Pushing is a separate step from landing:
   `tools\land.ps1` fast-forwards `master` locally and nothing pushes it; `git push origin
   master` from any worktree does, and it should be run after the commit that is meant to be
   read. Making it public is `gh repo edit vedant7007/in2fit --visibility public`, after 7a.
4. Open the URL in a private browser window and read what a judge reads: the README first, then
   `docs/demo/`, then `docs/decisions/`.
5. Paste the URL into the form, and re-read `docs/submission/form-answers.md`'s "prototype URL"
   text against what is actually there.
