# Chess Tutor — Product and Engineering Improvement Roadmap

**Document status:** Proposed implementation plan  
**Prepared:** 22 November 2026  
**Target repository:** `Bennykent21/Chess-tutor-app`  
**Starting point:** v1.2.0 on `main`, plus the first Coaching Intelligence implementation on `phase-14-coaching-intelligence`

---

## 1. Executive direction

Chess Tutor should become a tutor that understands a player's recurring strengths and weaknesses, chooses an appropriate next learning activity, explains why it chose that activity, and checks later whether the player improved.

The next stage is not to accumulate more features. It is to make the features already present behave like parts of one teaching system.

The product should keep its existing identity:

- Offline-first and useful without an account or a server.
- Stockfish, Maia, lessons, puzzles, repertoire training, game review and endgame practice remain specialised systems.
- A central coaching layer coordinates those systems; it does not replace them.
- The existing five-tab information architecture is retained: Home, Learn, Puzzles, Train and Account. Do not add a sixth tab just to expose the coach.
- The approved design direction remains premium minimalism: one clear action at a time, few words, strong typography, four themes, light/dark support, original coach art and a consistent board.

**Product success is better chess, not more screens, more activity, more XP or more recommendations.** The strongest evidence of success is that a player makes fewer recurring mistakes and performs better on later, unfamiliar positions and in subsequent games.

## 2. Is the UI what shows the centralised system?

**Yes. The UI is the visible face of the centralised coaching system, but it is not the system itself.** The coaching intelligence lives in the domain layer. The UI presents its conclusions and gives the player a route into the right existing activity.

The separation should look like this:

```text
Existing sources of evidence
  Game reviews / saved games / opening repertoire / puzzles
  Lessons / endgame positions / spaced-review history
                       |
                       v
             Evidence adapters
     Convert source-specific results into
        comparable, traceable signals
                       |
                       v
          Central Coaching Intelligence
       Learner model + confidence + recency
       Prioritisation + training-plan policy
                       |
                       v
             Coaching recommendation
        What to practise + why + evidence
        Confidence + destination + outcome ID
                       |
                       v
                UI surfaces
       Home: the next useful action
       Train: today's coordinated practice
       Learn/Puzzles: contextual teaching
       Account: progress and evidence over time
                       |
                       v
              Outcome feedback
       Started / completed / helped / missed
       Later performance on related positions
                       +---------------------> Learner model
```

### Responsibilities by layer

| Layer | Responsibility | Must not do |
|---|---|---|
| Data | Read and persist games, reviews, puzzle attempts, lesson progress, repertoire reviews and position results | Decide what the player should practise next |
| Evidence adapters | Translate existing feature outputs into typed signals with source, timestamp, sample size and destination | Recalculate chess evaluations or invent evidence |
| Domain: learner model | Summarise what is known, how much evidence supports it and whether it is changing | Collapse every skill into one misleading number |
| Domain: coaching policy | Rank candidate actions, respect due reviews and choose the next useful activity | Contain Compose or navigation code |
| Feature systems | Continue to teach, analyse, drill, schedule and evaluate their own activities | Independently compete to own the global recommendation |
| UI / navigation | Show the selected plan, explain it and open the relevant activity | Reimplement prioritisation rules in ViewModels or Composables |

The UI should not become an AI chat window. Pip, Rookie and Sage can explain a lesson or present a recommendation, but the central system should initially be a deterministic, testable decision engine. The learner should see the result of good coaching, not have to converse with a chatbot to use it.

## 3. Current baseline and what is already in place

The supplied handoff, `NEXT.md`, design-language document and plan describe a substantial existing application. The current repository baseline is v1.2.0. The app already has game review, Stockfish, Maia, lessons, 13,000+ puzzles, opening repertoire data, spaced review, endgame/middlegame positions, a Train tab, games from the player's own games, and local/friend play features.

The first Coaching Intelligence slice has been pushed to `phase-14-coaching-intelligence`. It adds a rule-based selector that can prioritise scheduled mistakes, repeated reviewed-game mistakes, recurring opening slips, endgame conversion findings and weak puzzle themes, with the existing bot recommendation as fallback. It connects those decisions to Home and existing activity destinations, adds tests and documents the first policy.

That slice is a starting point, not the finished central system. It currently lacks a durable learner profile, a complete outcome feedback loop, calibrated confidence and recency, and a dedicated progress view. The opening and game-review thresholds are provisional and must be validated before they are treated as evidence of a player's true ability.

### Important current design and product constraints

- The latest handoff adds the Train tab as the fifth tab. The older Phase 7 design-language text says four tabs; that older navigation description is superseded by the later Train work.
- The approved Round 5 direction has an opening library with board tiles and readiness rings, an opening page organised as a path, Lines shown as variation boards, Punish mistakes and Tactics, and a daily mixed training session in Train.
- `NEXT.md` still identifies incomplete work: the opening library grid, the full opening path presentation, the corresponding Lines strip on Learn's opening page, coach prompts for line playback, more variation depth, Advanced courses and lesson videos by variation.
- Existing UI rules remain binding: premium minimalism, consistent themes, readable contrast, board-first activity screens, fixed board geometry, reduced-motion support and accessibility.

## 4. Programme principles

1. **One source of truth per fact.** Existing repositories remain the owners of their progress. The coach reads them and stores only new derived state that cannot safely be recomputed.
2. **Evidence before diagnosis.** One blunder or one failed puzzle is not a stable weakness. Every diagnosis needs a sample size and source.
3. **Do not compare unlike measurements.** Puzzle rating, game rating, move accuracy, opening readiness and lesson completion measure different things. They must not be mixed into a single score without a validated model.
4. **The engine judges positions; the coach chooses learning.** Keep chess evaluation, learning content, scheduling and recommendation policy separate.
5. **Offline-first stays offline-first.** No required cloud profile, online AI call or server dependency for the core coaching loop.
6. **Do not duplicate the app.** New work should connect to the current lessons, puzzles, review scheduler, repertoire trainer and position trainer rather than create competing versions.
7. **The user retains control.** A recommendation is guidance, not a compulsory path. The player can open the evidence, choose another activity or dismiss a suggestion without corrupting progress.
8. **Every phase has measurable exit criteria.** A phase is not complete because screens exist or code compiles; it is complete when behaviour, tests and the intended learning outcome are verified.

## 5. Phased implementation plan

### Phase 0 — Stabilise the first Coaching Intelligence slice

**Priority:** P0 · **Dependency:** none · **Purpose:** establish a known-good base before adding the learner model.

Work:

- Complete CI for `phase-14-coaching-intelligence`: unit tests, lint, debug and release assembly, Stockfish packaging and APK artifacts.
- Fix any failures rather than building new capabilities on top of an unverified branch.
- Review the live-flow integration: game and analysis changes should refresh findings without repeated expensive recomputation or blocking Home.
- Verify the Home navigation destinations, especially opening slips, endgame sets and mistake positions, on a phone.
- Check empty states, no-analysis-yet states, imported games, Chess960 positions and a player with no training history.
- Review recommendation ordering and wording with realistic fixtures; ensure no impossible or locked destination is advertised as directly trainable.

**Done when:** CI is green, the APK installs, all recommendation paths work on a device, no feature's progress is lost, and the current selector has deterministic tests for thresholds and fallbacks.

### Phase 1 — Evidence integrity: make sure the inputs are trustworthy

**Priority:** P0 · **Dependency:** Phase 0.

The learner model will only be as good as its input. This phase hardens the most important evidence sources before using them to diagnose skill.

#### 1A. Game-review and accuracy validation

- Create a versioned review-validation fixture set of at least 100–300 games, stratified across rating bands, time controls, game lengths, wins/draws/losses, opening-heavy games and decisive tactical games.
- Include edge cases: book moves, transpositions, forced moves, missed mates, allowing mate, shortening one's own mate, queen and rook blunders, positions where the player is already completely winning or lost, and engine evaluations that change with depth.
- Compare outputs with trusted reference annotations and Chess.com/Lichess review behaviour as external benchmarks. Do not claim to reproduce a proprietary formula exactly.
- Keep the human-facing accuracy concept understandable: a player should not be punished for a sound book move or a forced move, and a game-ending blunder must not be presented as an excellent move because of a misleading raw score.
- Record the engine version, search settings and scoring version used for every analysis result so results can be reproduced.
- Validate that review judgements are stable enough across analysis depths. Where they are not, mark the result as uncertain or require a deeper search.
- The previous Chess.com-style curve fitting based on a tiny fixture set is not sufficient evidence of general accuracy. Treat it as a prototype, not as a validated Chess.com implementation.

#### 1B. Engine identity and runtime reliability

- Make every evaluation identify the engine that actually answered: native Stockfish, online Stockfish or Lite fallback.
- Preserve the existing no-silent-fallback principle. If the engine degrades, communicate it clearly and avoid silently treating a weaker answer as a Stockfish 19 result.
- Test start/stop/restart, overlapping requests, cancellation, process crashes, invalid FENs, empty best moves, Chess960 mode and stale responses.
- Measure startup latency, analysis time, memory, battery impact and thermal behaviour on lower-end devices as well as a modern device.
- Confirm the exact engine build and native binary checks in CI; keep fallback behaviour explicit and observable.

#### 1C. Opening and content integrity

- Keep opening trees legal and position-aware, including transpositions.
- Verify the line selected as a recommendation is actually learnable for that player: not locked, already mastered or unavailable in the current course gate.
- Continue engine checks for opening repertoire moves and alternatives. Remove or explicitly review unsound branches rather than expanding a larger but lower-quality book.
- Separate game frequency from correctness: a popular move is not automatically a good move.
- Track source/licence and build metadata for datasets and content.

**Done when:** the game-review fixtures and engine tests pass, the actual engine identity is visible, and the coach only consumes evidence whose provenance and reliability are known.

### Phase 2 — Define a shared evidence contract

**Priority:** P0 · **Dependency:** Phase 1.

Do not begin with a giant activity log or knowledge graph. Introduce the smallest useful shared model that lets the coach reason across existing systems without copying their databases.

#### 2A. Signal model

Define a typed `LearningSignal` (name can change during implementation) with fields such as:

- `signalId` and competency/skill key;
- source type and source identifier (game, reviewed move, puzzle theme, lesson, repertoire card, position set);
- observed event and outcome (attempted, correct, clean, hinted, revealed, slipped, converted, held);
- timestamp and relevant time window;
- number of opportunities and number of successes/failures;
- position, theme, opening or lesson context where relevant;
- confidence/sample sufficiency and evidence version;
- a supported action destination, or no action if none is safe.

Signals should preserve their source facts. For example, a puzzle solve rate is not the same as a game performance score, and a repertoire recall is not the same as playing an opening correctly in a real game.

#### 2B. Adapters to existing sources

Build adapters for:

- `GameReview` / `GameFindings` and saved game records;
- `TrainingCalculator` and puzzle history/theme attempts;
- `ReviewScheduler` and due mistake items;
- `RepertoireCards`, opening reviews and opening readiness;
- lesson progress and challenge outcomes;
- endgame/middlegame `PositionRecords`;
- existing game and bot performance data where they are reliable.

Adapters should be pure where possible, idempotent, testable and run away from the main thread. Do not make every screen perform its own cross-feature aggregation.

#### 2C. Identity, duplicates and time

- Handle imported-game re-syncs and duplicate PGNs without counting the same evidence twice.
- Distinguish games imported from outside the app from games actually played in the app.
- Use stable source IDs, event IDs and timestamps; define what happens when a source item is deleted or reanalysed.
- Define retention and local reset behaviour consistently with `DataReset`.

**Done when:** every supported signal can be traced back to an existing source, duplicates are not double-counted, and adapter tests prove that source-level facts are preserved.

### Phase 3 — Build the learner model

**Priority:** P0 · **Dependency:** Phase 2.

The learner model should describe what the app has evidence for, not claim to know the player's entire chess strength.

#### 3A. Skill dimensions

Start with a small, explainable set of dimensions that map to existing training content:

- tactical recognition (with separate motifs such as forks, pins, discovered attacks and mating patterns when sample size allows);
- calculation and candidate-move selection;
- recurring decision errors from reviewed games;
- opening knowledge by opening, side and variation;
- endgame conversion and defensive holding by endgame type;
- lesson/course comprehension where quiz evidence exists.

Do not force all of these into one global score. Keep puzzle rating, game rating, accuracy and opening readiness as separate values.

#### 3B. Confidence and recency

For every skill estimate, keep:

- evidence count and time window;
- recent performance versus historical performance;
- confidence level and reason for uncertainty;
- trend (improving, stable, declining, not enough evidence);
- last observed date.

Use minimum samples before displaying a diagnosis. A single event should normally be a note or a prompt to gather more evidence, not a strong diagnosis. Define recency decay per evidence type after validating how often that evidence is generated; do not pick an arbitrary universal decay rate.

#### 3C. Mastery and calibration

- Distinguish exposure, short-term success, durable recall and transfer to real games.
- For opening lines, use the existing Core / Standard / Deep levels and review history; do not replace them with a second mastery table.
- For lessons, separate lesson completion from demonstrated retention.
- For puzzles, account for difficulty, motif coverage, hints and reveals.
- Preserve an explicit `unknown` state. The model must be allowed to say that it does not have enough evidence.

**Done when:** the profile can explain each estimate with its sources, confidence, sample size and last updated date; tests cover sparse, contradictory and stale evidence.

### Phase 4 — Make the coaching policy and daily planner genuinely central

**Priority:** P0 · **Dependency:** Phases 2–3.

Replace the provisional priority chain with a policy that chooses a useful activity across the whole app. It should be deterministic and versioned at first.

#### 4A. Candidate generation

Each source proposes possible learning actions with:

- the weakness or learning goal it addresses;
- the evidence supporting it;
- the required activity and destination;
- whether the activity is available, due, locked, already mastered or missing content;
- expected effort and whether it repeats something done recently.

#### 4B. Ranking policy

The policy should consider:

- due spaced reviews and overdue mistakes;
- strength of evidence and confidence;
- impact and recurrence of the error;
- recency and whether performance is improving;
- learning prerequisites and locked content;
- variety, fatigue and repeated recommendations;
- the player's chosen focus, available session time and preferred activity where those settings exist.

Keep unlike scores separate. Use explicit priority rules or a documented scoring model only after there is a clear calibration method. Add a policy version to diagnostics so a recommendation can be reproduced.

#### 4C. Daily plan lifecycle

A recommendation should move through a lifecycle:

`Candidate → Selected → Started → Completed → Reassessed`

Possible terminal outcomes include improved, still weak, not enough new evidence, dismissed or unavailable. A skipped recommendation must not be counted as a failed training attempt.

- Do not replace due reviews with unrelated new work.
- Avoid presenting the same recommendation repeatedly if the player has already declined it or has just practised it.
- Keep the plan useful for short sessions. One strong next action is better than a long queue of low-confidence work.
- Support a fallback when a destination is unavailable, without pretending the requested training happened.

**Done when:** tests prove deterministic ranking, due-review handling, cooldowns, fallback routes, player override and the lifecycle of an action.

### Phase 5 — Expose the central system in the UI

**Priority:** P0 · **Dependency:** Phase 4 policy contract. A small Home integration can exist earlier, but the full UI should follow a stable model.

The UI should make the central system visible without making the app noisy or adding a new top-level tab.

#### 5A. Home: one clear next action

Show a single coaching recommendation with:

- a plain-language action title;
- one short reason (for example, “You repeated this mistake in 3 of 4 reviewed games”);
- a compact evidence detail or sample count;
- one primary action opening the relevant existing activity;
- a restrained way to inspect why, choose another activity or dismiss it.

Do not show a percentage or confidence badge unless the underlying sample and calibration justify it. Avoid showing a list of five equally important recommendations.

#### 5B. Train: the coordinated daily practice

Train is the place to carry out the plan, not a second dashboard competing with Home.

- Bring together due opening reviews, mistakes, relevant puzzle practice and endgame/middlegame practice when supported by evidence.
- Preserve the approved Today mixed-session direction and the existing spacing rules.
- Keep each task's context visible: what skill it targets and why it appeared today.
- Let the player enter or leave a task without losing the session's progress.

#### 5C. A detailed coaching/progress view

Add a detailed view reachable from Home and Train, preferably within the existing Train or Account navigation rather than as a sixth tab.

It should show:

- skill areas with a trend and evidence count;
- what is solid, what needs work and what remains unknown;
- recurring issues with links back to representative games/positions;
- opening readiness and variation coverage;
- recent training and whether later attempts improved;
- a clear distinction between estimated skill and raw activity counts.

The view should not become a dashboard of dozens of numbers. Prioritise the few findings that change what the player should do next.

#### 5D. Contextual coaching in activities

- Lessons explain the reason for a move and use the existing guided replay / watch-then-play approach.
- Puzzles explain the goal and reveal themes after the solve, without spoiling the task.
- Opening drills can explain why a move matters and why a recurring opponent reply is dangerous.
- Game review connects a mistake to a specific practice action.
- The result screen can show what the session strengthened and what still needs work when there is enough evidence.

#### 5E. Design and accessibility requirements

Follow the approved design-language file: premium minimalism, a single shared visual system, all four themes and light/dark modes, WCAG AA contrast, existing piece/board themes, minimum touch targets, TalkBack descriptions, reduced motion and board stability. Board screens keep their fixed geometry; coaching text must never push or resize the board unexpectedly.

**Done when:** the same recommendation is shown consistently across Home and Train, every action opens a valid destination, the detail view is understandable, and screenshots/tests cover every theme and mode.

### Phase 6 — Close the learning feedback loop

**Priority:** P1 · **Dependency:** Phases 2–5.

The coach must learn from what happens after its recommendation.

- Record whether the player started, completed, used a hint, revealed an answer or abandoned the activity.
- Reassess the same competency using later independent attempts, not just a repeat of the same memorised position.
- For recurring game mistakes, create a deliberate path from the reviewed position into a practice item that can be scheduled and reviewed again. Do not simply open an analysis board and assume the player has learned it.
- Reuse `ReviewScheduler` and existing review stores where possible. If a new record type is required, define migration, deletion, deduplication and DataReset behaviour first.
- Add an interval for the same concept to return, then a later test on a different but related position.
- Mark a weakness as improving only when a later sample supports that conclusion.
- Ask for a simple “not useful” reason only when it materially improves the policy; do not interrupt every activity with surveys.

**Done when:** a recommendation can be followed through to later reassessment, and the system can distinguish completion from durable improvement.

### Phase 7 — Strengthen learning content and transfer to real games

**Priority:** P1 · **Dependency:** Phases 1–6 where the coach uses the new content.

This phase addresses product depth, not just recommendation logic.

#### 7A. Opening coverage and depth

- Finish the approved opening-library grid and opening-page path design.
- Put the same Lines strip on Learn's opening page for openings with courses.
- Keep lines as tappable variation boards; tapping a line should clearly teach it (watch, then play it back), drill a learned line or open the prerequisite course if locked.
- Add the short coach prompts described in `NEXT.md`.
- Expand variation depth carefully. Validate move legality and soundness with Stockfish and keep provenance/licensing alongside generated data.
- Avoid showing “0 lessons” for tree-only openings. Make it obvious that these openings are taught through playable lines even when no text course exists.
- Add Advanced courses and lesson videos by variation after the base path is coherent.
- Add Plans only when the system can support them with verified content.

#### 7B. Custom repertoire

- Let players build or import custom opening lines.
- Validate legal moves, transpositions and side-to-move.
- Offer Stockfish checks and clearly label unsound or dubious branches rather than silently accepting them as theory.
- Reuse the existing repertoire trainer and review scheduler.

#### 7C. Puzzles and tactical transfer

- Use the existing opening-tagged puzzle sets and theme data.
- Improve motif evidence by ensuring enough varied positions per theme and difficulty.
- Test new positions, not only repeated puzzle IDs, to detect actual motif recognition.
- Keep alternative solutions validated by the pack-building engine process.

#### 7D. Endgames and middlegames

- Expand conversion and holding sets with engine-validated goals and alternatives.
- Connect errors from real games to relevant positions and themes only where the mapping is justified.
- Measure success on new positions and later games, not only whether the set was completed.

#### 7E. Lessons

- Prioritise explanations and learner understanding, not passive step completion.
- Add stronger and advanced material by topic once the existing courses are reviewed for gaps and contradictions.
- Retain the watch-then-play experience for opening lines.

**Done when:** new material passes content/engine checks, has clear entry points and prerequisite logic, and contributes a measurable, relevant learning outcome.

### Phase 8 — Portability, diagnostics and offline reliability

**Priority:** P1 · **Dependency:** stable data model and stores.

- Provide a versioned user-controlled backup/export for games, settings, lessons, puzzle progress, review schedules, repertoire cards, position records and coaching state.
- Include a manifest, schema version, checksums and a validation step before restore.
- Make restore atomic or safely recoverable: an invalid backup must not partially overwrite a working profile.
- Test migration from older versions and backup round-trips across app upgrades.
- Keep account credentials, API tokens and secrets out of exports. Clearly explain what is included.
- Continue to work offline. Linked-account imports can fail or pause without breaking on-device training.
- Expand diagnostics for import failures, corrupt content, engine crashes and slow analysis, with user-controlled sharing and no silent upload.

**Done when:** a player can back up the complete local learning state, restore it on a new install and verify that progress and schedules survive.

### Phase 9 — Full UI/UX and interaction audit

**Priority:** P1 · **Dependency:** stable product architecture; run incrementally rather than freezing all feature work.

- Audit every screen against the approved design language and the current five-tab structure.
- Finish the approved Learn opening-library and opening-path work from `NEXT.md` before inventing a new navigation structure.
- Replace awkward or inconsistent pop-ups with full screens or centred dialogs where the interaction requires it; keep transient confirmations as dialogs.
- Review empty, loading, offline, error, locked, mastered and no-evidence states.
- Check every activity on small and large phone sizes, in all themes and light/dark modes.
- Review motion timing and sequencing; respect Reduce Motion and keep board position fixed.
- Audit TalkBack labels, keyboard/controller interactions where relevant, touch-target size, contrast and colour-blind clarity.
- Remove redundant rows, duplicated statistics and screens that merely repeat another feature's information.
- Keep animation purposeful: piece movement, feedback and progress transitions are functional; decorative motion should not compete with the board or coach.

**Done when:** the full screen inventory passes visual, interaction, accessibility, theme and device-size checks; screenshots are regenerated and inspected, not merely generated.

### Phase 10 — Multiplayer and optional network features

**Priority:** P2 · **Dependency:** core learning and portability stable.

- Preserve existing same-phone and nearby play.
- Treat far-away friend play, tournaments, accounts and cloud sync as separate product decisions, not prerequisites for the tutor.
- If remote play is built, specify matchmaking/relay costs, privacy, reconnection, clock authority, cheating considerations and offline fallback first.
- Do not introduce a permanent server dependency into the core teaching loop without a clear, funded reason.

**Done when:** any network feature has a defined operational model and can fail without affecting offline learning.

## 6. UI proposal: how the player experiences the central coach

The central system should be visible in three levels, using the existing navigation.

| Place | What the player sees | What it is for |
|---|---|---|
| Home | One next-best-practice card: action, short reason, evidence sample, primary button | Decide what to do next in seconds |
| Train | Today's coordinated practice session and the reason each task is included | Do the work in a deliberate sequence |
| Coach/progress detail inside Train or Account | Skill areas, evidence, trend, opening readiness, recurring mistakes and reassessment | Understand the longer-term learning picture |
| Learn/Puzzles/activity screens | Context-specific guidance and an explanation of why the activity matters | Understand the task while doing it |

Example Home card:

> **Work on your rook endings**  
> You reached a rook ending a pawn up in 4 reviewed games and converted only 1.  
> **Practise this ending**

This is only shown when the evidence threshold is met. A low-sample observation should be framed as “we need more games to tell” or not surfaced at all.

The central coach should not turn Home into a statistics dashboard, replace the Train session, or force every user through a chatbot. Home shows the next action; Train coordinates practice; the detail view explains the evidence.

## 7. Measurement: how to know the changes work

Use local measurements first. Do not send game history or personal learning data to a server by default.

### Product measures

- Recommendation relevance: how often a chosen action is started and completed.
- Repeat-error rate: whether the same error appears less often in later games.
- Transfer: performance on unfamiliar positions that test the same concept.
- Retention: whether the player can recall a learned line or concept after a delay.
- Training efficiency: improvement per unit of focused practice, not raw minutes or XP.
- Calibration: how often the learner model's confidence is justified by later evidence.
- Recommendation fatigue: repeated dismissal, low relevance or repeated suggestions without progress.

### Evaluation rules

- Compare like with like: same concept, similar difficulty, independent positions and a defined time window.
- Keep sample sizes next to rates. A 100% score from two attempts is not equivalent to a 100% score from fifty.
- Do not use app opens, streaks or XP as proof of chess improvement.
- Keep an internal test cohort of fixtures and synthetic learner histories for deterministic policy tests.
- If external telemetry is ever proposed, make consent, minimisation, purpose and deletion explicit first.

## 8. Recommended execution order and gates

| Order | Phase | Priority | Gate before moving on |
|---:|---|---|---|
| 0 | Stabilise Phase 14 | P0 | CI green + phone smoke test |
| 1 | Evidence integrity (reviews, engine, openings) | P0 | Trusted source facts and reproducible analysis |
| 2 | Shared evidence contract | P0 | Signals trace to sources, no duplicate counting |
| 3 | Learner model | P0 | Confidence, sample size, recency and unknown state tested |
| 4 | Coaching policy and daily planner | P0 | Deterministic decisions and lifecycle tests |
| 5 | UI surfaces | P0 | Home, Train and detail view use the same model |
| 6 | Feedback loop and spaced practice | P1 | Later independent attempts reassess learning |
| 7 | Content depth and transfer | P1 | Content validated and tied to meaningful outcomes |
| 8 | Backup, diagnostics and offline reliability | P1 | Full local backup/restore tested |
| 9 | UI/UX and accessibility audit | P1 | Full screen inventory verified across themes/devices |
| 10 | Multiplayer and network expansion | P2 | Explicit product and operating model |

Some work can proceed in parallel: content checks, engine reliability and UI polish can run as separate tracks once their interfaces are stable. Do not start the UI dashboard before the recommendation contract is stable, or the same logic will be implemented twice in the UI.

## 9. Engineering quality gates

Every implementation phase should follow the repository workflow:

1. Start from the correct current branch and create a phase branch.
2. Inspect relevant existing code and tests before changing it.
3. Keep changes focused; avoid mixing domain, UI and unrelated content in one commit.
4. Add pure unit tests for policy/aggregation and integration tests for feature wiring.
5. Run unit tests, lint, debug/release assembly and the existing Stockfish build checks in GitHub Actions.
6. Inspect CI reports and artifacts; do not call a queued workflow a pass.
7. Install and test the APK on a real device for interactions, performance, orientation, theme and accessibility issues that unit tests cannot prove.
8. Record changed behaviour, known limitations and the source of every new dataset/content item.
9. Open a PR only after the phase's acceptance criteria are met. Do not merge or tag a release without an explicit release decision.

## 10. Explicit non-goals for the first central-coaching release

- No cloud-based AI coach or mandatory user account.
- No generic knowledge graph or all-purpose event platform before the small evidence contract proves necessary.
- No new global chess rating that conflates puzzle rating, game rating, accuracy and repertoire mastery.
- No automatic diagnosis from one blunder, one puzzle or a short sample.
- No feature duplication: do not rebuild lessons, puzzles, openings, spaced repetition or game review inside the coach.
- No sixth navigation tab.
- No remote multiplayer or tournament infrastructure as a dependency of coaching.
- No UI redesign that discards the approved design language or the current five-tab structure.

## 11. Open decisions to resolve with evidence, not assumptions

- Exact skill dimensions and their minimum evidence thresholds.
- Recency/decay rules for each kind of evidence.
- Whether the player-facing detail view belongs inside Train or Account; initial recommendation is a detail route reachable from both.
- How repeated game mistakes become durable practice items without creating duplicate or unreviewable cards.
- What is the minimum game-review depth/accuracy required before an evaluation-derived signal can affect the learner profile.
- Which content areas should be expanded first after the current opening-path work is complete.
- Which export format and optional encryption model best fit an offline, user-controlled backup.

These should be decided after looking at real fixture results, actual UI use and the current data contracts.

## 12. Source basis

This plan uses the supplied project bundle and the current Phase 14 repository work:

- `HANDOFF.md` — current implementation history, Phase 13, opening work, Train and friend features.
- `NEXT.md` — latest remaining design/content work from 19 November 2026.
- `design-language.md` — approved visual and interaction rules from Phase 7.
- `plan.md` — original product vision, architecture principles and historic implementation decisions. Its early four-tab description is historical; the later handoff adds Train as the fifth tab.
- `docs/coaching-intelligence-v1.md` and branch `phase-14-coaching-intelligence` — first deterministic coaching selector and initial evidence thresholds.

Where an older proposal conflicts with a later implementation note, this roadmap follows the later handoff and the current repository structure. This document is a plan; it does not claim that the phases above have already been implemented.