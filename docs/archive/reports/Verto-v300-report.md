# Verto v300 Execution Report

**Status:** `IMPLEMENTED_STATIC / BUILD_ENVIRONMENT_BLOCKED`

## Input / inherited state

- Input: `Verto-v299-implemented-static.zip`
- Input SHA-256: `d7e3b74368bdce7267f8bb12ee1c4fa9236af2681c94a4c43caadfb73a7dfc7e`
- Archive entries: `2490`
- Inherited v299 status: `IMPLEMENTED_STATIC / BUILD_ENVIRONMENT_BLOCKED`
- Pre Session-299 Migration Gate: `PASS`, snapshot `30aec9bdad52b5e68c996b7c61bda4b2ae8cbe48aa03fafff8c06902a41960e9`
- Pre Session-300 Migration Gate: `PASS`, rawDp=`22`, rawMotion=`5`, snapshot `c9c63b475b6c656726f10274015589388c79407b2e8885b336e26e5008737838`
- Pre Final Gate: `FAIL` only rawDp=`22` + rawMotion=`5`
- Pre contract verifier: `DESIGN_SYSTEM_CONTRACT PASS 0`

## Raw-dp inventory and migration

- Production raw-dp debt: `22` occurrences / `8` files / `18` unique literal values.
- All 22 occurrences belonged to `feature/shipment/.../presentation/logisticsv2`.
- `VertoAdaptiveTokens` had zero production consumers before v300.
- Local pre-rule: `maxWidth < 360.dp || fontScale >= 1.5f`.
- Manifest: `docs/design-system/DIMENSION-MIGRATION-v300.json`
- Manifest SHA-256: `8b8e8f3bc4bb1fa17ea3642d081a2ad210cc3bae8c3804971ec17c788328f6af`
- Accounting: `22/22`, unclassified=`0`, unmigrated=`0`, numericDrift=`0`, modifierSemanticDrift=`0`, responsiveBranchDrift=`0`.

### 22-record mapping

- `v300-001` `88.dp` → `LogisticsV2Tokens.stationProofUploadMinHeight`; resolved `88dp == 88dp`; `heightIn(min)` preserved; `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningV237StationStep.kt:282→282`.
- `v300-002` `20.dp` → `VertoSize.iconMedium`; resolved `20dp == 20dp`; `size` preserved; `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningPurchaseStep.kt:272→272`.
- `v300-003` `18.dp` → `VertoSize.iconSmall`; resolved `18dp == 18dp`; `size` preserved; `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningPurchaseStep.kt:287→287`.
- `v300-004` `2.dp` → `VertoStroke.progress`; resolved `2dp == 2dp`; `strokeWidth` preserved; `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningPurchaseStep.kt:287→287`.
- `v300-005` `430.dp` → `LogisticsV2Tokens.invoicePickerMaxHeight`; resolved `430dp == 430dp`; `heightIn(max)` preserved; `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningPurchaseSheet.kt:157→157`.
- `v300-006` `360.dp` → `VertoAdaptiveTokens.shouldStackInlineContent(maxWidth, LocalDensity.current.fontScale)`; resolved `360dp == 360dp`; `responsive decision` preserved; `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningPurchaseCard.kt:115→116`.
- `v300-007` `18.dp` → `VertoSize.iconSmall`; resolved `18dp == 18dp`; `size` preserved; `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningPurchaseCard.kt:193→194`.
- `v300-008` `20.dp` → `VertoSize.iconMedium`; resolved `20dp == 20dp`; `size` preserved; `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningPurchaseCard.kt:227→228`.
- `v300-009` `36.dp` → `LogisticsV2Tokens.planningProgressNodeSize`; resolved `36dp == 36dp`; `size` preserved; `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsPlanningScaffold.kt:197→197`.
- `v300-010` `20.dp` → `VertoSize.iconMedium`; resolved `20dp == 20dp`; `size` preserved; `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsPlanningScaffold.kt:203→203`.
- `v300-011` `92.dp` → `LogisticsV2Tokens.transportChoiceMinHeight`; resolved `92dp == 92dp`; `heightIn(min)` preserved; `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningV237Steps.kt:184→184`.
- `v300-012` `26.dp` → `LogisticsV2Tokens.transportChoiceIconSize`; resolved `26dp == 26dp`; `size` preserved; `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningV237Steps.kt:200→200`.
- `v300-013` `19.dp` → `LogisticsV2Tokens.routeConnectorStartInset`; resolved `19dp == 19dp`; `padding(start)` preserved; `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningV237Steps.kt:236→236`.
- `v300-014` `24.dp` → `LogisticsV2Tokens.routeConnectorHeight`; resolved `24dp == 24dp`; `height` preserved; `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningV237Steps.kt:236→236`.
- `v300-015` `38.dp` → `LogisticsV2Tokens.routeNodeSize`; resolved `38dp == 38dp`; `size` preserved; `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningV237Steps.kt:313→313`.
- `v300-016` `560.dp` → `LogisticsV2Tokens.movementDialogMaxHeight`; resolved `560dp == 560dp`; `heightIn(max)` preserved; `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsV239MovementPreparation.kt:152→152`.
- `v300-017` `36.dp` → `LogisticsV2Tokens.customsHeaderIconSize`; resolved `36dp == 36dp`; `size` preserved; `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningV238Steps.kt:115→116`.
- `v300-018` `72.dp` → `LogisticsV2Tokens.documentUploadMinHeight`; resolved `72dp == 72dp`; `heightIn(min)` preserved; `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningV238Steps.kt:188→189`.
- `v300-019` `8.dp` → `LogisticsV2Tokens.reviewCustomsMarkerSize`; resolved `8dp == 8dp`; `size` preserved; `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningV238Steps.kt:304→305`.
- `v300-020` `14.dp` → `LogisticsV2Tokens.reviewTerminalDotSize`; resolved `14dp == 14dp`; `size(if terminal)` preserved; `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningV238Steps.kt:356→357`.
- `v300-021` `12.dp` → `LogisticsV2Tokens.reviewStationDotSize`; resolved `12dp == 12dp`; `size(if terminal)` preserved; `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningV238Steps.kt:356→357`.
- `v300-022` `48.dp` → `VertoSize.minTouchTarget`; resolved `48dp == 48dp`; `heightIn(min)` preserved; `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningV238Steps.kt:412→413`.

## Adaptive contract proof

- `VertoAdaptiveTokens.inlineStackThreshold = 360.dp`.
- `VertoAdaptiveTokens.largeContentFontScale = 1.5f`.
- Decision: strict `availableWidth < 360dp || fontScale >= 1.5f`.
- `BoxWithConstraints` and local available-width basis are preserved.
- Existing 599/839/840 breakpoints are unchanged.
- Boundary matrix preserved: 320/1.0=true, 359/1.0=true, 360/1.0=false, 412/1.0=false, 412/1.49=false, 412/1.5=true, 412/2.0=true.
- Core unit test assertions added for the complete matrix.
- Android UI test authored at `LogisticsV300ResponsiveTest.kt` for available widths 320/360/412 and 412+fontScale1.5; it exercises `SelectedInvoicePlanningCard` and validates content/action visibility plus Row/Column geometry. Runtime execution was BLOCKED by Gradle bootstrap.

## Changed production Kotlin files

- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningV237StationStep.kt` — `223026a110c3c8dcf5d1fc803c91daf731981eca63a03afaef2aad6febe434be`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningPurchaseStep.kt` — `c53d5ee7c9af82b0e13b7a5cdea06ed0a91c2eecad6a65d4761f529b8f93336b`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningPurchaseSheet.kt` — `f1e3ae3da50407b0347dba912f451ec601eadc379e1ef60405cd898a914d5e7c`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningPurchaseCard.kt` — `3aa22c816f5f552468c0e69005060efa49fda2cf8227af1577c95af117b00e66`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsPlanningScaffold.kt` — `a0b47a3c4934f5a88f1d378fd544e35d685447643cef2be6c7f19405d776d197`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningV237Steps.kt` — `66264a0859fcfd617d226aef492380e6d95b22b3eb5fb3a5c5a3c2172356253b`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsV239MovementPreparation.kt` — `fbbbef4831a36a069e5667ce4f683d79c459895a97454d6a8df7b4cf492fec19`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningV238Steps.kt` — `788e917cc8eb069d8ab35f8c00adf9f693c1fd2df29c1b7f7e88ad5aac740955`
- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsV2DesignTokens.kt` — `175807ef6a2d2dd515e3c3a93ea40baade54c10bae65d73dfdcabe9503fd7e8a`
- `core/designsystem/src/main/kotlin/com/verto/app/ui/theme/FoundationTokens.kt` — `c1ce5a2eee2e206861ad84e5c44841b26656f22608c4fbddbbdb4a4f55135990`

- Production Kotlin modified: `10` existing files; new production Kotlin files: `0`.
- New test file: `feature/shipment/src/androidTest/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsV300ResponsiveTest.kt`.
- Historical `LogisticsV242UiQualityTest.kt` remains byte-identical: `9d476d4cb7641663035bea0774a3f333c173a745fece68ae1a867de1efd75b19`.

## Post static gates

- Session-300 Migration Gate: `PASS`.
- Post snapshot: `3507b73f34060fb4ac57430a687f6f924ac09a70f65038ab9559ffe2fc4e61e7`.
- Diff Gate: `PASS`.
- Contract verifier: `DESIGN_SYSTEM_CONTRACT PASS 0`.
- Final Gate: expected `FAIL` only because `rawMotionDurations=5` (reserved for session 301).
- `rawDpOutsideApprovedTokenFiles=0`.
- `rawMotionDurations=5` unchanged.
- `rawMaterialMustWrap=0`; `forbiddenMaterial=0`.
- `hardcodedUserFacingStringsFull=0`; `legacyFocusedHardcodedCount=0`; `coreVisibleLiteralDebt=0`.
- `rawSpOutsideApprovedTokenFiles=0`; `rawColorOutsideApprovedTokenFiles=0`.
- `coreBoundaryViolations=0`; DS domain strings=`0`; external DS domain refs=`0`; duplicate guarded primitives=`0`; active exceptions=`0`.
- Current Design State + hardcoded manifest synchronized to the post snapshot; hardcoded findings remain zero.

## Protected governance / architecture

- All §68 protected hashes verified byte-identical after execution.
- No Gradle/dependency/resource/scanner/baseline/final-zero/material-policy/exception-ledger changes.
- `DesignTokens.kt`, `ComponentTokens.kt`, `SharedComponents.kt`, `CanonicalComponents.kt`, and `MaterialWrappers.kt` remain byte-identical.

## Compile / test status

- `./gradlew --version` attempted as the Gradle bootstrap prerequisite.
- Result: `BLOCKED` before Gradle startup because the wrapper attempted `https://services.gradle.org/distributions/gradle-8.9-bin.zip` and failed with `java.net.UnknownHostException: services.gradle.org`.
- Therefore Core compile, Shipment compile, App integration compile, unit tests, and Android responsive runtime tests are **not claimed PASS**.

## Verdict

`Session 300 = IMPLEMENTED_STATIC / BUILD_ENVIRONMENT_BLOCKED`

Static contract closure is complete: raw production dp is zero and the responsive rule is centralized without numeric, modifier, or branch drift. The remaining Final Gate failure is exactly the five inherited motion literals reserved for session 301.

Output archive SHA-256 is recorded in the companion `Verto-v300-implemented-static.zip.sha256` after packaging (embedding that hash here would be self-referential).
