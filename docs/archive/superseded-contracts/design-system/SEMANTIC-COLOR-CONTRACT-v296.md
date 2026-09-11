# Semantic Color Contract — v296

All small semantic foreground/background pairs in this contract use a minimum contrast threshold of `4.5:1`.

| Role | Light FG | Light BG | Ratio | Dark FG | Dark BG | Ratio | Usage |
|---|---|---|---:|---|---|---:|---|
| Primary | `#FFFFFF` | `#6D5CFF` | 4.540:1 | `#FFFFFF` | `#6D5CFF` | 4.540:1 | Filled primary content |
| Secondary | `#10111A` | `#3B82F6` | 5.111:1 | `#10111A` | `#3B82F6` | 5.111:1 | Filled secondary content |
| Error / Danger | `#B91C1C` | `#FFEEEE` | 5.766:1 | `#F87171` | `#4A171B` | 5.311:1 | Danger accent on danger container |
| Success | `#15803D` | `#EAF8EF` | 4.579:1 | `#4ADE80` | `#12351F` | 7.751:1 | Success accent on success container |
| Warning | `#B45309` | `#FFF7E6` | 4.711:1 | `#FBBF24` | `#43290B` | 8.060:1 | Warning accent on warning container |
| Info | `#2563EB` | `#EEF5FF` | 4.711:1 | `#60A5FA` | `#172D52` | 5.386:1 | Info accent on info container |
| Waiting | `#4F46E5` | `#F0F0FF` | 5.573:1 | `#818CF8` | `#252750` | 4.742:1 | Waiting accent on waiting container |
| Offline | `#475569` | `#F1F5F9` | 6.917:1 | `#94A3B8` | `#28313E` | 5.122:1 | Offline accent on offline container |
| Permission | `#0F766E` | `#ECFDF9` | 5.209:1 | `#2DD4BF` | `#123B38` | 6.603:1 | Permission accent on permission container |
| Surface text | `#1A1F36` | `#FFFFFF` | 16.239:1 | `#F7F8FC` | `#171925` | 16.459:1 | Primary text on surface |
| Surface variant text | `#4B556B` | `#F2F3F8` | 6.742:1 | `#C1C6D5` | `#262A3B` | 8.333:1 | Secondary text on surfaceVariant |

## Container content pairs

| Pair | Light ratio | Dark ratio |
|---|---:|---:|
| OnDangerContainer / DangerContainer | 8.930:1 | 13.842:1 |
| OnSuccessContainer / SuccessContainer | 8.318:1 | 12.727:1 |
| OnWarningContainer / WarningContainer | 8.510:1 | 12.679:1 |
| OnInfoContainer / InfoContainer | 9.441:1 | 12.902:1 |
| OnSecondaryContainer / SecondaryContainer | 9.441:1 | 12.902:1 |

## Runtime mapping

- `ErrorColor` → active `VertoColors.danger`.
- `ErrorContainer` → active `VertoColors.dangerContainer`.
- Material3 `error/onError/errorContainer/onErrorContainer` map to the same active Danger contract.
- `OnSecondary` is `#10111A` in both themes for `AccentBlue #3B82F6`.
- Disabled content is `DISABLED_CONTRAST_EXEMPT_BY_POLICY`.
- Alpha is permitted for decorative borders/backgrounds. Small semantic label text remains opaque when alpha compositing would violate the threshold.
