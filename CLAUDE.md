# CLAUDE.md

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.

---

## Project: navisplit (this repo)

The four principles above are the global Karpathy guidelines (mirror of `~/Claude/CLAUDE.md`).
Below is the project-specific layer — facts verified on the target device.

Freeform multi-window launcher for a headless Android auto-box: tap a scenario tile → two
apps open side by side in freeform ("split").

- **Build:** `bash build.sh` → `navisplit.apk` at repo root. Toolchain (JDK 17 + Android SDK) is
  git-ignored and fetched by `fetch-toolchain.sh` on first build.
- **`targetSdkVersion=25` is load-bearing** — it's how the app reaches the hidden
  `setLaunchWindowingMode` API for freeform. Do NOT raise it to 33; that breaks tiling.
- **Re-tiling an ALREADY-RUNNING app needs shell/root.** A normal app can apply freeform bounds
  only to apps that aren't already running; for an existing task `setLaunchBounds` is ignored
  (verified: a second launch and `FLAG_ACTIVITY_CLEAR_TASK` don't help either). The working
  recipe is two steps, in order: `am start --windowingMode 5 -n <component>` (flip the task to
  freeform) then `am task resize <taskId> L T R B` (resize is a no-op unless the task is already
  freeform). `Launcher.java` runs this via `su` when available, else falls back to the
  `ActivityOptions` path (fresh-launch only).
- **Device (the box):** Qualcomm `trinket` (SM6125), Android 13 / API 33 (release string "14" is
  fake), 1920×716 landscape, A/B (active slot `_b`, patch `boot_b`), bootloader unlocked (orange),
  no root yet.
- **adb:** over Wi-Fi on port 5555; the box IP drifts (host stays `.163`, subnet changes) — Mac and
  box must share one hotspot. On "device offline"/"connection refused" after a good ping, retry or
  `adb kill-server && adb start-server`.
