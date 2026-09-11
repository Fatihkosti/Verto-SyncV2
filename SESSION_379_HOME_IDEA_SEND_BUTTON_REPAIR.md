# Session 379 — Home Idea Send Button Repair

## Scope
Home idea capture card only.

## Root cause
`HomeIdeaCapture.kt` kept the submit button inside a `HorizontalPager` constrained to `286.dp`, so the button was clipped below the pager viewport on the rendered home screen.

## Repair
- Removed the submit button from each pager page.
- Added one persistent submit button below the pager and above the page indicators.
- Submission still uses the currently selected category.
- Preserved loading/disabled behavior and all existing text capture behavior.

## Verification
Static source verification: PASS.
Gradle compile attempt: BLOCKED_ENVIRONMENT because the Gradle 8.9 distribution is not cached and network access to `services.gradle.org` is unavailable.
