# Live Demo Script — Java Upgrade Agent Suite (Java 8 → Java 17)

**Story arc:** "Here's a suite of 6 cooperating Copilot Chat agents → watch them
audit a Java 8 project, propose fixes, get human approval, apply them, and
validate the result — safely, with a rollback net."

Format: **SAY** = what to speak. **DO** = what to click/type. Keep SAY lines short —
you're narrating live output, not reading slides.

> Note: you're handling the Java 8 downgrade / pom.xml prep yourself before this
> script picks up — this script starts once the project is sitting on Java 8 and
> you're ready to open Copilot Chat.

---

## 0. Before the room fills up (prep, not part of the show)

- [ ] Open `c:\JAVA_AGENT` in VS Code.
- [ ] Open the GitHub Copilot Chat panel (`Ctrl+Alt+I`) and confirm the 6 agents
      appear in the `@` picker (`java-upgrade-orchestrator`, `-audit`, `-fixer`,
      `-validator`, `-rollback`, `-metrics`).
- [ ] Confirm no stale run state at the project root (these should **not** exist —
      if they do, delete or rename them so the audit runs fresh):
      `.java-upgrade-audit-cache.md`, `.java-upgrade-required-changes.md`,
      `.java-upgrade-fixer-checkpoint.md`, `.orchestrator-state-*.json`.
- [ ] Note: `.java-upgrade-artifacts/` already contains sample output from a prior
      run. **Do not delete it** — it's your fallback if live Copilot Chat hiccups
      (see §5).
- [ ] Know the fixer password: **`tcs_agent`** (type it only when the chat prompts
      for it interactively — never paste it ahead of time).

---

## 1. Act 1 — Meet the agents themselves (2–3 min)

**DO:** Expand `.github/agents/` in the VS Code explorer. Open
`java-upgrade-audit.agent.md` and scroll to the top frontmatter block.

**SAY:**
> "Each of these six agents is just a markdown file with a YAML header. That
> header is the entire contract Copilot Chat uses to know the agent exists, what
> it's for, and what it's allowed to touch."

**DO:** Point at each frontmatter field as you name it:
- `name:` — the identifier, matches the filename, what you type after `@`
- `description:` — shown in the `@` picker dropdown so anyone in the team can
  discover what an agent does without opening the file
- `argumentHint:` — the placeholder text shown in the chat input box, telling you
  exactly what arguments to pass
- `tools:` — what capabilities this agent is granted (e.g. `codebase` read access)

**DO:** Open `java-upgrade-orchestrator.agent.md` and point at its extra
frontmatter field:
- `agents:` — the list of sub-agents *this one* is allowed to call
  (audit, fixer, validator, rollback, metrics) — nothing outside that list

**SAY:**
> "So the orchestrator you're about to see isn't one giant script — it's a
> coordinator that's only permitted to call five specific named agents, in a
> specific order. That's enforced by this file, not by convention."

*(Optional, if time: quickly flash `java-upgrade-fixer.agent.md`'s
`argumentHint` to show it explicitly expects `mode (dry-run|apply)` and an
`apply_guard` — foreshadows the approval gates coming up in Act 3.)*

---

## 2. Act 2 — Audit agent (read-only scan) (2–3 min)

**DO:** In Copilot Chat:
```
@java-upgrade-audit . java8 java17
```

**SAY while it runs:**
> "This agent never modifies a file — it's pure static analysis. It runs 10
> checks gated to the exact version boundaries between 8 and 17, so it doesn't
> waste time on checks that only matter for, say, Java 21."

**DO:** Let the findings render — don't narrate individual files, just the shape
of the output: severity levels, `file:line` locations, a summary count.

**SAY:**
> "Everything here is read-only — nothing has changed on disk yet."

*(Optional aside if asked about speed):*
```
@java-upgrade-audit . java8 java17 --estimate-only
```
> "For a huge codebase, this gives a fast critical/medium count for sprint
> planning before committing to a full scan."

---

## 3. Act 3 — The full orchestrated upgrade (the main event, ~8 min)

**SAY:**
> "Now let's run the real pipeline — pre-flight checks, audit, human review,
> fixer, validator — all coordinated by one orchestrator agent."

**DO:**
```
@java-upgrade-orchestrator . java8 java17
```

**Phase 0 — Pre-flight checks.** Narrate this explicitly, it's easy to blink and
miss:
**SAY:**
> "Before it asks me anything, it runs sanity checks: it verifies its own agent
> files are on compatible versions, confirms this is actually a Maven project by
> checking for `pom.xml` — and would bail out with a clear message if this were
> a Gradle project instead — then it checks the installed Java version on this
> machine against the target version and warns if the target JDK isn't
> available. Only after all of that passes does it say 'pre-flight checks
> passed' and move on."

Continue narrating each phase as it streams — **do not skip ahead**, the tool
enforces these gates in order:

| Phase | What happens on screen | What to say |
|---|---|---|
| 1 | Detects Java 8 from `pom.xml`, confirms target 17 | "It read the current version itself — I didn't have to tell it twice." |
| 2 | Runs the audit, writes `.java-upgrade-required-changes.md` | "This file is the audit trail — a human-readable change list, generated *before* any fix is applied." |
| 2.5 | Shows numbered proposed fixes (`[compiler-01]`, `[dep-01]`, `[api-01]`...) | "This is the approval gate. Nothing is applied until I say so." |

**DO:** Respond to the fix-list prompt with:
```
ALL
```
*(or, to show the exclusion feature: name one fix-id to exclude and explain "I
can carve out anything I want to handle by hand.")*

**DO:** When prompted, type the literal word:
```
PROCEED
```

**SAY:**
> "That's confirmation of intent. Next it generates a one-time numeric token —
> this stops any automated or accidental run from silently applying changes."

**DO:** Type back the 6-digit token exactly as shown.

**SAY:**
> "And now the fixer itself asks for a password — typed here in chat, never
> from an environment variable or config file."

**DO:** When prompted for the authorization password, type:
```
tcs_agent
```

**SAY while fixer applies changes:**
> "Every single fix is written to a checkpoint file as it happens — so if this
> got interrupted right now, nothing would be lost or re-applied twice."

**DO:** Let Phase 4 (validator) run.

**SAY:**
> "Six checks, not just 'does it compile' — it diffs the changes for leftover
> old patterns, checks charset-unsafe calls, and re-runs a security pass on the
> post-change dependencies. In this environment it validates with `javac`/`java`
> directly rather than invoking `mvn`, since Maven execution is disabled here —
> that's a deliberate demo-mode safety setting, not a limitation of the agent."

**DO:** Show the final `PASS` / `WARN` result and the
`.java-upgrade-fixer-checkpoint.md` ledger of applied fixes.

---

## 4. Act 4 — Prove the safety net (2–3 min, optional but strong closer)

**SAY:**
> "Two features exist purely so nobody is afraid to run this on a real
> codebase: rollback and metrics."

**DO (dry run only — do not actually roll back your finished demo unless you
want to re-run the whole thing)**:
```
@java-upgrade-rollback . .java-upgrade-fixer-checkpoint.md --dry-run
```
**SAY:**
> "This previews exactly what would be reverted, in reverse order, with zero
> files touched. Only the real run — password-gated again — actually reverts."

**DO:**
```
@java-upgrade-metrics .
```
**SAY:**
> "And this is the observability layer — success rates per agent, common
> failure categories, trend lines across every run of this pipeline, not just
> today's."

---

## 5. Fallback plan (only if Copilot Chat misbehaves live)

The `.java-upgrade-artifacts/` folder already contains output from a prior
clean run:
- `java-upgrade-audit-cache.md` — pre-baked audit findings
- `java-upgrade-required-changes.md` — the human-readable change report
- `java-upgrade-proposed-fix-plan.md` — the dry-run fix list
- `metrics_summary.csv` — sample metrics output

If a live agent call stalls, open the corresponding file from this folder and
say "here's the output from a clean run this morning" — keep narrating from the
tables above without missing a beat.

---

## 6. Closing talking points (1 min)

**SAY, pick 2–3:**
> - "Nothing applies without two independent human confirmations — `PROCEED`
>   plus a one-time token — and then a password, entered live in chat."
> - "Every fix is checkpointed, so partial runs resume instead of restarting,
>   and every applied fix can be rolled back individually."
> - "The audit agent alone is useful standalone — teams can run it just for
>   sprint estimation before committing to any fix."
> - "This isn't Java-8-to-17 specific — it's a boundary system covering 8, 11,
>   17, 21, 25, 26, so the same six agents cover any upgrade path we hit next."

---

## Cheat sheet (copy-paste order for the live run)

```text
@java-upgrade-audit . java8 java17
@java-upgrade-orchestrator . java8 java17
  → ALL                     (approve all proposed fixes)
  → PROCEED                 (confirm intent)
  → <6-digit token>          (echo the token shown in chat)
  → tcs_agent                (fixer authorization password)
@java-upgrade-rollback . .java-upgrade-fixer-checkpoint.md --dry-run
@java-upgrade-metrics .
```
