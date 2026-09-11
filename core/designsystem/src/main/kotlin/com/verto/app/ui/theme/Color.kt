package com.verto.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Raw palette. Screens should use MaterialTheme, Verto colors, or semantic aliases below.
private val DarkBackground = Color(0xFF10111A)
private val DarkSurface = Color(0xFF171925)
private val DarkCard = Color(0xFF1E2130)
private val DarkCardAlt = Color(0xFF262A3B)
private val DarkBorder = Color(0xFF373C52)
private val DarkTextPrimary = Color(0xFFF7F8FC)
private val DarkTextSecondary = Color(0xFFC1C6D5)
private val DarkTextMuted = Color(0xFF8D94A8)

private val LightBackground = Color(0xFFF7F8FC)
private val LightSurface = Color(0xFFFFFFFF)
private val LightCard = Color(0xFFFFFFFF)
private val LightCardAlt = Color(0xFFF2F3F8)
private val LightBorder = Color(0xFFE6E8F0)
private val LightTextPrimary = Color(0xFF1A1F36)
private val LightTextSecondary = Color(0xFF4B556B)
private val LightTextMuted = Color(0xFF8A93A8)
internal val LightErrorContainer = Color(0xFFFFEEEE)
private val LightDanger = Color(0xFFB91C1C)
private val DarkDanger = Color(0xFFF87171)

// Brand palette.
val AccentPrimary = Color(0xFF6D5CFF)
val AccentLight = Color(0xFFE9E7FF)
val AccentDim = Color(0xFF3D348F)
val AccentBlue = Color(0xFF3B82F6)
val AccentBlueDim = Color(0xFF172D52)

val GoldPrimary = Color(0xFFC9A84C)
val GoldLight = Color(0xFFE8C96A)
val GoldDim = Color(0xFF6A5520)
val WhatsAppBrand = Color(0xFF25D366)

// Semantic palette.
val StatusRed = Color(0xFFEF4444)
val StatusRedDim = Color(0xFF4A171B)
val StatusGreen = Color(0xFF22C55E)
val StatusGreenDim = Color(0xFF12351F)
val StatusOrange = Color(0xFFF59E0B)
val StatusOrangeDim = Color(0xFF43290B)
val StatusInfo = Color(0xFF3B82F6)
val StatusInfoDim = Color(0xFF172D52)
val StatusWaiting = Color(0xFF6366F1)
val StatusWaitingDim = Color(0xFF252750)
val StatusOffline = Color(0xFF64748B)
val StatusOfflineDim = Color(0xFF28313E)
val StatusPermission = Color(0xFF14B8A6)
val StatusPermissionDim = Color(0xFF123B38)
val StatusGrey = Color(0xFF94A3B8)
val StatusGreyDim = Color(0xFF2A303B)

val GradientStart = AccentPrimary
val GradientEnd = Color(0xFF8B5CF6)

data class VertoColors(
    val bgDeep: Color,
    val bgSurface: Color,
    val bgCard: Color,
    val bgCardAlt: Color,
    val borderColor: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val waiting: Color,
    val waitingContainer: Color,
    val offline: Color,
    val offlineContainer: Color,
    val permission: Color,
    val permissionContainer: Color,
    val disabled: Color,
    val disabledContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val onDanger: Color,
    val onSecondary: Color,
    val borderStrong: Color = borderColor,
    val disabledSurface: Color = disabledContainer,
    val disabledContent: Color = disabled,
    val focusIndicator: Color = AccentPrimary,
    val onSurface: Color = textPrimary,
    val onSurfaceVariant: Color = textSecondary,
    val onPrimary: Color = Color.White,
    val onSuccessContainer: Color = textPrimary,
    val onWarningContainer: Color = textPrimary,
    val onDangerContainer: Color = textPrimary,
    val onInfoContainer: Color = textPrimary,
)

val DarkVertoColors = VertoColors(
    bgDeep = DarkBackground,
    bgSurface = DarkSurface,
    bgCard = DarkCard,
    bgCardAlt = DarkCardAlt,
    borderColor = DarkBorder,
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    textMuted = DarkTextMuted,
    success = Color(0xFF4ADE80),
    successContainer = StatusGreenDim,
    warning = Color(0xFFFBBF24),
    warningContainer = StatusOrangeDim,
    info = Color(0xFF60A5FA),
    infoContainer = StatusInfoDim,
    waiting = Color(0xFF818CF8),
    waitingContainer = StatusWaitingDim,
    offline = Color(0xFF94A3B8),
    offlineContainer = StatusOfflineDim,
    permission = Color(0xFF2DD4BF),
    permissionContainer = StatusPermissionDim,
    disabled = Color(0xFF64748B),
    disabledContainer = StatusGreyDim,
    danger = DarkDanger,
    dangerContainer = StatusRedDim,
    onDanger = DarkBackground,
    onSecondary = DarkBackground,
    borderStrong = Color(0xFF59627E),
    disabledSurface = StatusGreyDim,
    disabledContent = Color(0xFFB4BAC8),
    focusIndicator = Color(0xFFB8B0FF),
    onSurface = DarkTextPrimary,
    onSurfaceVariant = DarkTextSecondary,
    onPrimary = Color.White,
    onSuccessContainer = DarkTextPrimary,
    onWarningContainer = DarkTextPrimary,
    onDangerContainer = DarkTextPrimary,
    onInfoContainer = DarkTextPrimary,
)

val LightVertoColors = VertoColors(
    bgDeep = LightBackground,
    bgSurface = LightSurface,
    bgCard = LightCard,
    bgCardAlt = LightCardAlt,
    borderColor = LightBorder,
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    textMuted = LightTextMuted,
    success = Color(0xFF15803D),
    successContainer = Color(0xFFEAF8EF),
    warning = Color(0xFFB45309),
    warningContainer = Color(0xFFFFF7E6),
    info = Color(0xFF2563EB),
    infoContainer = Color(0xFFEEF5FF),
    waiting = Color(0xFF4F46E5),
    waitingContainer = Color(0xFFF0F0FF),
    offline = Color(0xFF475569),
    offlineContainer = Color(0xFFF1F5F9),
    permission = Color(0xFF0F766E),
    permissionContainer = Color(0xFFECFDF9),
    disabled = Color(0xFF94A3B8),
    disabledContainer = Color(0xFFF1F3F6),
    danger = LightDanger,
    dangerContainer = LightErrorContainer,
    onDanger = Color.White,
    onSecondary = DarkBackground,
    borderStrong = Color(0xFF9AA3B8),
    disabledSurface = Color(0xFFF1F3F6),
    disabledContent = Color(0xFF667085),
    focusIndicator = AccentPrimary,
    onSurface = LightTextPrimary,
    onSurfaceVariant = LightTextSecondary,
    onPrimary = Color.White,
    onSuccessContainer = Color(0xFF14532D),
    onWarningContainer = Color(0xFF78350F),
    onDangerContainer = Color(0xFF7F1D1D),
    onInfoContainer = Color(0xFF1E3A8A),
)

val LocalVertoColors = staticCompositionLocalOf { LightVertoColors }

val BgDeep: Color @Composable get() = LocalVertoColors.current.bgDeep
val BgSurface: Color @Composable get() = LocalVertoColors.current.bgSurface
val BgCard: Color @Composable get() = LocalVertoColors.current.bgCard
val BgCardAlt: Color @Composable get() = LocalVertoColors.current.bgCardAlt
val BorderColor: Color @Composable get() = LocalVertoColors.current.borderColor
val TextPrimary: Color @Composable get() = LocalVertoColors.current.textPrimary
val TextSecondary: Color @Composable get() = LocalVertoColors.current.textSecondary
val TextMuted: Color @Composable get() = LocalVertoColors.current.textMuted
val SuccessColor: Color @Composable get() = LocalVertoColors.current.success
val SuccessContainer: Color @Composable get() = LocalVertoColors.current.successContainer
val WarningColor: Color @Composable get() = LocalVertoColors.current.warning
val WarningContainer: Color @Composable get() = LocalVertoColors.current.warningContainer
val InfoColor: Color @Composable get() = LocalVertoColors.current.info
val InfoContainer: Color @Composable get() = LocalVertoColors.current.infoContainer
val WaitingColor: Color @Composable get() = LocalVertoColors.current.waiting
val WaitingContainer: Color @Composable get() = LocalVertoColors.current.waitingContainer
val OfflineColor: Color @Composable get() = LocalVertoColors.current.offline
val OfflineContainer: Color @Composable get() = LocalVertoColors.current.offlineContainer
val PermissionColor: Color @Composable get() = LocalVertoColors.current.permission
val PermissionContainer: Color @Composable get() = LocalVertoColors.current.permissionContainer
val DisabledColor: Color @Composable get() = LocalVertoColors.current.disabled
val DisabledContainer: Color @Composable get() = LocalVertoColors.current.disabledContainer
val BorderStrong: Color @Composable get() = LocalVertoColors.current.borderStrong
val DisabledSurface: Color @Composable get() = LocalVertoColors.current.disabledSurface
val DisabledContent: Color @Composable get() = LocalVertoColors.current.disabledContent
val FocusIndicator: Color @Composable get() = LocalVertoColors.current.focusIndicator
val OnSurface: Color @Composable get() = LocalVertoColors.current.onSurface
val OnSurfaceVariant: Color @Composable get() = LocalVertoColors.current.onSurfaceVariant
val OnPrimary: Color @Composable get() = LocalVertoColors.current.onPrimary
val OnSuccessContainer: Color @Composable get() = LocalVertoColors.current.onSuccessContainer
val OnWarningContainer: Color @Composable get() = LocalVertoColors.current.onWarningContainer
val DangerColor: Color @Composable get() = LocalVertoColors.current.danger
val DangerContainer: Color @Composable get() = LocalVertoColors.current.dangerContainer
val OnDanger: Color @Composable get() = LocalVertoColors.current.onDanger
val OnDangerContainer: Color @Composable get() = LocalVertoColors.current.onDangerContainer
val OnInfoContainer: Color @Composable get() = LocalVertoColors.current.onInfoContainer
val OnSecondary: Color @Composable get() = LocalVertoColors.current.onSecondary

// Compatibility aliases used by existing screens.
val AccentMain = AccentPrimary
val TextOnAccent = Color.White
val ErrorColor: Color @Composable get() = LocalVertoColors.current.danger
val ErrorContainer: Color @Composable get() = LocalVertoColors.current.dangerContainer
