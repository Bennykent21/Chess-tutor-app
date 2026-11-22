# Coaching Intelligence — first vertical slice

Status: first implementation, deterministic rules  
Scope: Home's single next-practice recommendation  
Data source: existing on-device progress and game-review data

## Purpose

Chess Tutor already has several analysis and practice systems. The coaching layer should coordinate them, not replace them.

- Stockfish and existing game-review code remain responsible for evaluating chess positions.
- `GameFindings` remains responsible for finding repeated patterns in the player's saved games.
- `TrainingCalculator` remains responsible for puzzle-theme performance.
- Existing mistake, repertoire and endgame trainers remain responsible for teaching and spaced review.
- `CoachingIntelligence` selects one useful next action and explains which evidence triggered it.

The first version is deliberately rule-based and offline. It does not use an LLM, send game history to a server, or store a duplicate player profile.

## Evidence and decision order

The coach evaluates existing evidence in this order:

1. **Scheduled mistake reviews:** recommend the existing mistakes review when at least three items are due.
2. **Repeated game-review mistake:** recommend the saved position when the same mistake appears in at least two reviewed games and accounts for at least half of the reviewed opportunities for that position.
3. **Opening-line slip:** recommend the existing repertoire line trainer when the same deviation appears in at least two games and accounts for at least half of the games that reached that position.
4. **Endgame conversion:** recommend the matching endgame set when at least three games reached that ending while the player was a pawn or more ahead, and fewer than half were won.
5. **Puzzle theme:** recommend the weakest theme when it has at least three attempts and a clean-solve rate below 60%.
6. **Fallback:** use the existing bot recommendation when there is not enough evidence for a targeted training action.

The ordering is explicit, not a learned numerical score. It avoids comparing unlike quantities such as blunder counts and puzzle percentages. Ties within a category are resolved by the higher recurrence share, then the larger sample.

## Current Home integrations

- Repeated mistakes open the existing analysis board at the recorded FEN.
- Opening slips enter the existing repertoire line trainer when the line is available to learn; otherwise they open that repertoire's page.
- Endgame conversion findings open the existing matching endgame set.
- Puzzle weaknesses continue to open the existing theme-filtered puzzle flow.
- Scheduled mistakes continue to open the existing mistakes review.

Game findings are observed from the Room game and analysis flows. They are recomputed when saved games or completed analysis summaries change, so the Home recommendation can refresh without requiring the player to visit the findings screen.

## Limits and safeguards

This is a prioritisation policy, not proof of a player's complete chess ability.

- A repeated pattern is not surfaced from a single occurrence.
- The current game-pattern thresholds come from the existing `GameFindings` output and are intentionally visible in code.
- The endgame signal measures conversion in sampled material-up games; it does not prove the player misunderstands every position of that ending type.
- Puzzle-theme cleanliness is affected by puzzle selection, difficulty and sample size; it is one signal, not a global skill rating.
- If no signal clears its threshold, the coach abstains from diagnosing a weakness and falls back to the existing bot recommendation.
- Existing progress and spaced-review stores remain the source of truth. This slice adds no Room entity or migration.

## Next stages

1. Validate that the selected action is useful and that the player follows it.
2. Track whether subsequent games and practice attempts improve the same signal.
3. Add a derived player-skill profile only after signal definitions, sample sizes and decay rules are specified.
4. Expand cross-feature recommendations to lessons, tactical motifs, opening practice and review scheduling without duplicating their underlying progress.
5. Redesign Home around the resulting coaching plan only after the recommendation policy is stable.

Success should be measured by improvement on later, relevant positions and fewer repeat mistakes, not by the number of recommendations shown or training sessions completed.
