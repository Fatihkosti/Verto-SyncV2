# Material Usage Policy

Machine source of truth: `config/design-system/material-usage-policy.json`.

| Component | Classification | Enforce from | Replacement |
|---|---|---:|---|
| Button | MUST_WRAP | 290 | VertoButton |
| OutlinedButton | MUST_WRAP | 290 | VertoOutlinedButton |
| TextField | MUST_WRAP | 290 | VertoTextField |
| OutlinedTextField | MUST_WRAP | 290 | VertoOutlinedTextField |
| IconButton | MUST_WRAP | 301 | VertoIconButton |
| Card | MUST_WRAP | 299 | VertoCard |
| ModalBottomSheet | MUST_WRAP | 299 | VertoBottomSheet |
| TabRow | MUST_WRAP | 299 | VertoTabRow |
| ScrollableTabRow | MUST_WRAP | 299 | VertoScrollableTabRow |
| TopAppBar | MUST_WRAP | 301 | VertoTopAppBar |
| Text | DIRECT_USE_ALLOWED | 290 | direct use with Theme/Tokens |
| Icon | DIRECT_USE_ALLOWED | 290 | direct use with Theme/Tokens |
| Surface | DIRECT_USE_ALLOWED | 290 | direct use with Theme/Tokens |
| TextButton | DIRECT_USE_ALLOWED | 290 | direct text action |
| AlertDialog | DIRECT_USE_ALLOWED | 290 | non-confirmation only; confirmation uses VertoConfirmationDialog |
| Checkbox | DIRECT_USE_ALLOWED | 290 | direct tokenized use |
| RadioButton | DIRECT_USE_ALLOWED | 290 | direct tokenized use |
| Switch | DIRECT_USE_ALLOWED | 290 | direct tokenized use |
| LinearProgressIndicator | DIRECT_USE_ALLOWED | 290 | inline progress only |
| CircularProgressIndicator | DIRECT_USE_ALLOWED | 290 | inline progress only |
| FilterChip | DIRECT_USE_ALLOWED | 290 | direct use when no stable wrapper contract exists |
| DropdownMenu | DIRECT_USE_ALLOWED | 290 | platform menu primitive |
| ExposedDropdownMenuBox | DIRECT_USE_ALLOWED | 290 | platform selection primitive |
| SnackbarHost | DIRECT_USE_ALLOWED | 290 | host primitive |
| androidx.compose.material.* | FORBIDDEN | 290 | androidx.compose.material3.* |
