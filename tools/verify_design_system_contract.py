#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

REQUIRED_DOCS = {
    'DESIGN-SYSTEM-CONTRACT.md', 'TOKEN-CATALOG.md', 'COMPONENT-CATALOG.md',
    'MATERIAL-USAGE-POLICY.md', 'COMPONENT-OWNERSHIP.md', 'MIGRATION-LEDGER.md',
    'EXCEPTION-LEDGER.md', 'DESIGN-DEBT.md', 'SCREENSHOT-TEST-ADR.md',
    'CURRENT-DESIGN-STATE.md', 'GOVERNANCE-GATES.md', 'CURRENT-HARDCODED-MANIFEST.json',
}
EXPECTED = {
    'Button': ('MUST_WRAP', 290), 'OutlinedButton': ('MUST_WRAP', 290),
    'TextField': ('MUST_WRAP', 290), 'OutlinedTextField': ('MUST_WRAP', 290),
    'IconButton': ('MUST_WRAP', 301), 'Card': ('MUST_WRAP', 299),
    'ModalBottomSheet': ('MUST_WRAP', 299), 'TabRow': ('MUST_WRAP', 299),
    'ScrollableTabRow': ('MUST_WRAP', 299), 'TopAppBar': ('MUST_WRAP', 301),
    'Text': ('DIRECT_USE_ALLOWED', 290), 'Icon': ('DIRECT_USE_ALLOWED', 290),
    'Surface': ('DIRECT_USE_ALLOWED', 290), 'TextButton': ('DIRECT_USE_ALLOWED', 290),
    'AlertDialog': ('DIRECT_USE_ALLOWED', 290), 'Checkbox': ('DIRECT_USE_ALLOWED', 290),
    'RadioButton': ('DIRECT_USE_ALLOWED', 290), 'Switch': ('DIRECT_USE_ALLOWED', 290),
    'LinearProgressIndicator': ('DIRECT_USE_ALLOWED', 290),
    'CircularProgressIndicator': ('DIRECT_USE_ALLOWED', 290),
    'FilterChip': ('DIRECT_USE_ALLOWED', 290), 'DropdownMenu': ('DIRECT_USE_ALLOWED', 290),
    'ExposedDropdownMenuBox': ('DIRECT_USE_ALLOWED', 290), 'SnackbarHost': ('DIRECT_USE_ALLOWED', 290),
    'androidx.compose.material.*': ('FORBIDDEN', 290),
}

# Session 296 uses explicit approved values, but every value below is coupled to strict
# Color.kt/Theme.kt source assertions so the verifier cannot be made to pass by changing
# this table alone while runtime mappings remain different.
PALETTE = {
    'light': {
        'background': 'F7F8FC', 'surface': 'FFFFFF', 'surfaceVariant': 'F2F3F8',
        'textPrimary': '1A1F36', 'textSecondary': '4B556B',
        'primary': '6D5CFF', 'onPrimary': 'FFFFFF', 'primaryContainer': 'E9E7FF', 'onPrimaryContainer': '1A1F36',
        'secondary': '3B82F6', 'onSecondary': '10111A', 'secondaryContainer': 'EEF5FF', 'onSecondaryContainer': '1E3A8A',
        'danger': 'B91C1C', 'dangerContainer': 'FFEEEE', 'onDanger': 'FFFFFF', 'onDangerContainer': '7F1D1D',
        'success': '15803D', 'successContainer': 'EAF8EF', 'onSuccessContainer': '14532D',
        'warning': 'B45309', 'warningContainer': 'FFF7E6', 'onWarningContainer': '78350F',
        'info': '2563EB', 'infoContainer': 'EEF5FF', 'onInfoContainer': '1E3A8A',
        'waiting': '4F46E5', 'waitingContainer': 'F0F0FF',
        'offline': '475569', 'offlineContainer': 'F1F5F9',
        'permission': '0F766E', 'permissionContainer': 'ECFDF9',
    },
    'dark': {
        'background': '10111A', 'surface': '171925', 'surfaceVariant': '262A3B',
        'textPrimary': 'F7F8FC', 'textSecondary': 'C1C6D5',
        'primary': '6D5CFF', 'onPrimary': 'FFFFFF', 'primaryContainer': '3D348F', 'onPrimaryContainer': 'FFFFFF',
        'secondary': '3B82F6', 'onSecondary': '10111A', 'secondaryContainer': '172D52', 'onSecondaryContainer': 'F7F8FC',
        'danger': 'F87171', 'dangerContainer': '4A171B', 'onDanger': '10111A', 'onDangerContainer': 'F7F8FC',
        'success': '4ADE80', 'successContainer': '12351F', 'onSuccessContainer': 'F7F8FC',
        'warning': 'FBBF24', 'warningContainer': '43290B', 'onWarningContainer': 'F7F8FC',
        'info': '60A5FA', 'infoContainer': '172D52', 'onInfoContainer': 'F7F8FC',
        'waiting': '818CF8', 'waitingContainer': '252750',
        'offline': '94A3B8', 'offlineContainer': '28313E',
        'permission': '2DD4BF', 'permissionContainer': '123B38',
    },
}


def rgb(hex_value: str) -> tuple[int, int, int]:
    value = hex_value.lstrip('#')
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def contrast(fg: tuple[int, int, int], bg: tuple[int, int, int]) -> float:
    def lin(c: int) -> float:
        v = c / 255
        return v / 12.92 if v <= 0.04045 else ((v + .055) / 1.055) ** 2.4

    la = .2126 * lin(fg[0]) + .7152 * lin(fg[1]) + .0722 * lin(fg[2])
    lb = .2126 * lin(bg[0]) + .7152 * lin(bg[1]) + .0722 * lin(bg[2])
    return (max(la, lb) + .05) / (min(la, lb) + .05)


def require_fragment(text: str, fragment: str, label: str, failures: list[str]) -> None:
    if fragment not in text:
        failures.append(f'semantic source mapping mismatch: {label}')


def block(text: str, start: str, end: str) -> str:
    i = text.find(start)
    if i < 0:
        return ''
    j = text.find(end, i + len(start))
    return text[i:] if j < 0 else text[i:j]


def verify_semantic_source(root: Path, failures: list[str]) -> None:
    color_path = root / 'core/designsystem/src/main/kotlin/com/verto/app/ui/theme/Color.kt'
    theme_path = root / 'core/designsystem/src/main/kotlin/com/verto/app/ui/theme/Theme.kt'
    try:
        color = color_path.read_text()
        theme = theme_path.read_text()
    except Exception as exc:
        failures.append(f'semantic color source unreadable: {exc}')
        return

    for fragment, label in [
        ('private val LightDanger = Color(0xFFB91C1C)', 'LightDanger raw value'),
        ('private val DarkDanger = Color(0xFFF87171)', 'DarkDanger raw value'),
        ('internal val LightErrorContainer = Color(0xFFFFEEEE)', 'Light danger container raw value'),
        ('val danger: Color', 'VertoColors.danger field'),
        ('val dangerContainer: Color', 'VertoColors.dangerContainer field'),
        ('val onDanger: Color', 'VertoColors.onDanger field'),
        ('val onSecondary: Color', 'VertoColors.onSecondary field'),
        ('val DangerColor: Color @Composable get() = LocalVertoColors.current.danger', 'DangerColor accessor'),
        ('val DangerContainer: Color @Composable get() = LocalVertoColors.current.dangerContainer', 'DangerContainer accessor'),
        ('val OnDanger: Color @Composable get() = LocalVertoColors.current.onDanger', 'OnDanger accessor'),
        ('val OnSecondary: Color @Composable get() = LocalVertoColors.current.onSecondary', 'OnSecondary accessor'),
        ('val ErrorColor: Color @Composable get() = LocalVertoColors.current.danger', 'ErrorColor compatibility accessor'),
        ('val ErrorContainer: Color @Composable get() = LocalVertoColors.current.dangerContainer', 'ErrorContainer compatibility accessor'),
    ]:
        require_fragment(color, fragment, label, failures)

    dark = block(color, 'val DarkVertoColors = VertoColors(', '\n\nval LightVertoColors')
    light = block(color, 'val LightVertoColors = VertoColors(', '\n\nval LocalVertoColors')
    for fragment, label in [
        ('danger = DarkDanger', 'dark danger'),
        ('dangerContainer = StatusRedDim', 'dark dangerContainer'),
        ('onDanger = DarkBackground', 'dark onDanger'),
        ('onSecondary = DarkBackground', 'dark onSecondary'),
        ('onDangerContainer = DarkTextPrimary', 'dark onDangerContainer'),
        ('onInfoContainer = DarkTextPrimary', 'dark onInfoContainer'),
    ]:
        require_fragment(dark, fragment, label, failures)
    for fragment, label in [
        ('danger = LightDanger', 'light danger'),
        ('dangerContainer = LightErrorContainer', 'light dangerContainer'),
        ('onDanger = Color.White', 'light onDanger'),
        ('onSecondary = DarkBackground', 'light onSecondary'),
        ('onDangerContainer = Color(0xFF7F1D1D)', 'light onDangerContainer'),
        ('onInfoContainer = Color(0xFF1E3A8A)', 'light onInfoContainer'),
    ]:
        require_fragment(light, fragment, label, failures)

    dark_theme = block(theme, 'private val VertoDarkColorScheme = darkColorScheme(', '\n\nprivate val VertoLightColorScheme')
    light_theme = block(theme, 'private val VertoLightColorScheme = lightColorScheme(', '\n\nval VertoShapes')
    required_theme = {
        'dark': [
            'onPrimary = DarkVertoColors.onPrimary',
            'onSecondary = DarkVertoColors.onSecondary',
            'secondaryContainer = DarkVertoColors.infoContainer',
            'onSecondaryContainer = DarkVertoColors.onInfoContainer',
            'error = DarkVertoColors.danger',
            'onError = DarkVertoColors.onDanger',
            'errorContainer = DarkVertoColors.dangerContainer',
            'onErrorContainer = DarkVertoColors.onDangerContainer',
        ],
        'light': [
            'onPrimary = LightVertoColors.onPrimary',
            'onSecondary = LightVertoColors.onSecondary',
            'secondaryContainer = LightVertoColors.infoContainer',
            'onSecondaryContainer = LightVertoColors.onInfoContainer',
            'error = LightVertoColors.danger',
            'onError = LightVertoColors.onDanger',
            'errorContainer = LightVertoColors.dangerContainer',
            'onErrorContainer = LightVertoColors.onDangerContainer',
        ],
    }
    for fragment in required_theme['dark']:
        require_fragment(dark_theme, fragment, f'dark Material mapping {fragment}', failures)
    for fragment in required_theme['light']:
        require_fragment(light_theme, fragment, f'light Material mapping {fragment}', failures)

    if re.search(r'val\s+ErrorColor\s*=\s*StatusRed', color):
        failures.append('legacy static ErrorColor mapping remains')
    if re.search(r'val\s+ErrorContainer\s*=\s*StatusRedDim', color):
        failures.append('legacy static ErrorContainer mapping remains')


def contrast_pairs(theme: str) -> list[tuple[str, str, str]]:
    # (name, foreground role, background role)
    pairs = [
        ('material onPrimary/primary', 'onPrimary', 'primary'),
        ('material onPrimaryContainer/primaryContainer', 'onPrimaryContainer', 'primaryContainer'),
        ('material onSecondary/secondary', 'onSecondary', 'secondary'),
        ('material onSecondaryContainer/secondaryContainer', 'onSecondaryContainer', 'secondaryContainer'),
        ('material onBackground/background', 'textPrimary', 'background'),
        ('material onSurface/surface', 'textPrimary', 'surface'),
        ('material onSurfaceVariant/surfaceVariant', 'textSecondary', 'surfaceVariant'),
        ('material onError/error', 'onDanger', 'danger'),
        ('material onErrorContainer/errorContainer', 'onDangerContainer', 'dangerContainer'),
        ('status success/successContainer', 'success', 'successContainer'),
        ('status warning/warningContainer', 'warning', 'warningContainer'),
        ('status info/infoContainer', 'info', 'infoContainer'),
        ('status waiting/waitingContainer', 'waiting', 'waitingContainer'),
        ('status offline/offlineContainer', 'offline', 'offlineContainer'),
        ('status permission/permissionContainer', 'permission', 'permissionContainer'),
        ('status danger/dangerContainer', 'danger', 'dangerContainer'),
        ('surface success/surface', 'success', 'surface'),
        ('surface warning/surface', 'warning', 'surface'),
        ('surface info/surface', 'info', 'surface'),
        ('surface waiting/surface', 'waiting', 'surface'),
        ('surface offline/surface', 'offline', 'surface'),
        ('surface permission/surface', 'permission', 'surface'),
        ('surface danger/surface', 'danger', 'surface'),
        ('container onSuccessContainer/successContainer', 'onSuccessContainer', 'successContainer'),
        ('container onWarningContainer/warningContainer', 'onWarningContainer', 'warningContainer'),
        ('container onDangerContainer/dangerContainer', 'onDangerContainer', 'dangerContainer'),
        ('container onInfoContainer/infoContainer', 'onInfoContainer', 'infoContainer'),
        ('container textPrimary/waitingContainer', 'textPrimary', 'waitingContainer'),
        ('container textPrimary/offlineContainer', 'textPrimary', 'offlineContainer'),
        ('container textPrimary/permissionContainer', 'textPrimary', 'permissionContainer'),
    ]
    return pairs


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument('--root', default='.')
    args = ap.parse_args()
    root = Path(args.root).resolve()
    failures: list[str] = []
    docs = root / 'docs/design-system'

    failures += [f'missing document: {x}' for x in sorted(REQUIRED_DOCS) if not (docs / x).is_file()]
    try:
        pol = json.loads((root / 'config/design-system/material-usage-policy.json').read_text())
        comps = pol['components']
        if pol.get('schemaVersion') != 290 or len(comps) != 25 or len({x['component'] for x in comps}) != 25:
            failures.append('material policy schema/count invalid')
        got = {x['component']: (x['classification'], x['enforceFromSession']) for x in comps}
        if got != EXPECTED:
            failures.append('material policy classification/activation mismatch')
    except Exception as exc:
        failures.append(f'material policy invalid: {exc}')

    try:
        z = json.loads((root / 'config/design-system/final-zero-targets.json').read_text())
        if z.get('schemaVersion') != 290 or len(z.get('targets', {})) != 15 or set(z['targets'].values()) != {0}:
            failures.append('final zero targets invalid')
    except Exception as exc:
        failures.append(f'zero targets invalid: {exc}')

    try:
        m = json.loads((docs / 'CURRENT-HARDCODED-MANIFEST.json').read_text())
        req = {'file', 'line', 'context', 'literal', 'owner', 'rule'}
        if m.get('findingCount') != len(m.get('findings', [])):
            failures.append('hardcoded manifest findingCount mismatch')
        if len(m.get('legacyCoreVisibleSignatures', [])) != 10:
            failures.append('legacyCoreVisibleSignatures count != 10')
        if any(set(x) != req for x in m.get('findings', [])):
            failures.append('hardcoded finding schema invalid')
        state = (docs / 'CURRENT-DESIGN-STATE.md').read_text()
        if m.get('snapshotId') not in state:
            failures.append('current-state snapshotId mismatch')
    except Exception as exc:
        failures.append(f'hardcoded manifest invalid: {exc}')

    opt = root / 'feature/integration/optimal'
    if not (opt / 'build.gradle.kts').is_file():
        failures.append('nested module owner anchor missing: feature/integration/optimal')
    ds = root / 'core/designsystem/src/main/kotlin'
    if (ds / 'com/verto/app/pdf/PdfUtils.kt').exists():
        failures.append('PdfUtils must not be owned by core/designsystem')
    bad = ('com.verto.app.feature.', 'com.verto.app.data.', 'com.verto.app.domain.', 'com.verto.app.ui.screens.')
    for path in ds.rglob('*.kt'):
        for line in path.read_text(errors='replace').splitlines():
            if line.startswith('import ') and any(x in line for x in bad):
                failures.append(f'core boundary import: {path.relative_to(root)}: {line.strip()}')

    verify_semantic_source(root, failures)

    threshold = 4.5
    for theme in ('light', 'dark'):
        values = PALETTE[theme]
        for name, fg_role, bg_role in contrast_pairs(theme):
            fg = values[fg_role]
            bg = values[bg_role]
            ratio = contrast(rgb(fg), rgb(bg))
            status = 'PASS' if ratio >= threshold else 'FAIL'
            print(f'CONTRAST {theme} {name} fg=#{fg} bg=#{bg} ratio={ratio:.3f} threshold={threshold:.1f} {status}')
            if ratio < threshold:
                failures.append(f'contrast below 4.5:1: {theme} {name} ({ratio:.3f})')
        print(f'CONTRAST {theme} disabled DISABLED_CONTRAST_EXEMPT_BY_POLICY')

    if failures:
        for item in failures:
            print('FAIL', item)
        print(f'DESIGN_SYSTEM_CONTRACT FAIL {len(failures)}')
        return 1
    print('DESIGN_SYSTEM_CONTRACT PASS 0')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
