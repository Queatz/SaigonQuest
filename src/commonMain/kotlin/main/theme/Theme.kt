package main.theme

import org.jetbrains.compose.web.css.Color

object Theme {
    object colors {
        val background = Color("#faf8f6")
        val backgroundDark = Color("#000000")
        val onBackground = Color("#000000")
        val onBackgroundDark = Color("#ffffff")
        val primary = Color("#2F5E5E")
        val primaryDark = Color("#1F3F3F")
        val secondary = Color("#995A90")
        val secondaryDark = Color("#572831")
        val onPrimary = Color("#ffffff")
        val surface = Color("#F5E6E0")
        val surfaceVariant = Color("#F8EBE6")
        val surfaceVariantDark = Color("#333333")
        val surfaceDark = Color("#121212")
        val onSurfaceDark = Color("#ffffff")
        val onSurfaceVariantDark = Color("#D3D3D3")
        val onSurface = Color("#000000")
        val onSurfaceVariant = Color("#404040")
        val outline = Color("#D3D3D3")
        val outlineDark = Color("#808080")
        val success = Color("#4CAF50")
        val successDark = Color("#2E7D32")
        val error = Color("#EF5350")
        val errorDark = Color("#C62828")
        // Overlays / scrims
        val overlayScrim = Color("rgba(0, 0, 0, 0.5)")
        val overlayScrimDark = Color("rgba(255, 255, 255, 0.2)")

        // Translucent panel backgrounds (glass effect)
        val panelGlass = Color("rgba(255, 255, 255, 0.7)")
        val panelGlassDark = Color("rgba(0, 0, 0, 0.85)")

        // Text roles beyond primary
        val textSecondary = Color("#666666")
        val textSecondaryDark = Color("#cccccc")
        val textMuted = Color("rgba(0, 0, 0, 0.55)")
        val textMutedDark = Color("rgba(255, 255, 255, 0.6)")

        // Subtle borders on translucent panels
        val panelBorderSubtle = Color("rgba(0, 0, 0, 0.15)")
        val panelBorderSubtleDark = Color("rgba(255, 255, 255, 0.2)")

        // Accents for info/warn/success states
        val accentInfo = Color("#a6c8ff")
        val accentWarn = Color("#ffc999")
        val accentSuccess = Color("#b7ff99")
        val accentInfoStrong = Color("rgba(80, 160, 255, 0.9)")
        val accentWarnStrong = Color("rgba(255, 160, 80, 0.9)")
        val accentSuccessStrong = Color("rgba(160, 255, 80, 0.9)")
        val accentInfoSoft = Color("rgba(80, 160, 255, 0.15)")
        val accentInfoBorder = Color("rgba(80, 160, 255, 0.35)")
        val accentWarnSoft = Color("rgba(255, 160, 80, 0.15)")
        val accentSuccessSoft = Color("rgba(160, 255, 80, 0.15)")

        // Toast backgrounds
        val toastSuccessBg = Color("rgba(0, 150, 0, 0.7)")
        val toastErrorBg = Color("rgba(255, 0, 0, 0.7)")

        // Action/control backgrounds
        val controlBg = Color("#ffffff")
        val controlBgDark = Color("#000000")

        // Generic alpha whites and blacks (for borders, spinners, shadows)
        val white04 = Color("rgba(255, 255, 255, 0.04)")
        val white05 = Color("rgba(255, 255, 255, 0.05)")
        val white06 = Color("rgba(255, 255, 255, 0.06)")
        val white08 = Color("rgba(255, 255, 255, 0.08)")
        val white10 = Color("rgba(255, 255, 255, 0.10)")
        val white12 = Color("rgba(255, 255, 255, 0.12)")
        val white15 = Color("rgba(255, 255, 255, 0.15)")
        val white20 = Color("rgba(255, 255, 255, 0.20)")
        val white30 = Color("rgba(255, 255, 255, 0.30)")
        val white35 = Color("rgba(255, 255, 255, 0.35)")
        val white50 = Color("rgba(255, 255, 255, 0.50)")
        val white70 = Color("rgba(255, 255, 255, 0.70)")
        val white80 = Color("rgba(255, 255, 255, 0.80)")
        val white90 = Color("rgba(255, 255, 255, 0.90)")
        val white95 = Color("rgba(255, 255, 255, 0.95)")

        val black20 = Color("rgba(0, 0, 0, 0.20)")
        val black30 = Color("rgba(0, 0, 0, 0.30)")
        val black50 = Color("rgba(0, 0, 0, 0.50)")
        val black70 = Color("rgba(0, 0, 0, 0.70)")

        // Shadows
        val shadowLight = Color("rgba(0, 0, 0, 0.2)")
        val shadowMedium = Color("rgba(0, 0, 0, 0.3)")
        val shadowStrong = Color("rgba(0, 0, 0, 0.5)")

        // Soft state backgrounds and borders
        val successSoftBg = Color("rgba(76, 175, 80, 0.15)")
        val successSoftBorder = Color("rgba(76, 175, 80, 0.35)")
        val errorSoftBg = Color("rgba(239, 83, 80, 0.15)")
        val errorSoftBorder = Color("rgba(239, 83, 80, 0.35)")

        // Node editor specific accents
        val nodeAccentPrimary = Color("rgba(0, 140, 255, 0.8)")
        val accentInfoBlueStrong = Color("rgba(100, 200, 255, 0.9)")
        val accentInfoBlueGlow = Color("rgba(100, 200, 255, 0.5)")

        // Notification backgrounds
        val notifReadBg = Color("rgba(255, 255, 255, 0.6)")
        val notifUnreadBg = Color("rgba(255, 255, 255, 0.8)")
        val notifReadBgDark = Color("rgba(40, 40, 40, 0.6)")
        val notifUnreadBgDark = Color("rgba(60, 60, 60, 0.8)")

        // Loading overlays
        val loadingPrimaryBg = Color("rgba(0, 120, 255, 0.7)")
        val loadingSecondaryBg = Color("rgba(255, 255, 255, 0.2)")
        val loadingDots = Color("#505050")
        val loadingMask = Color("rgba(200, 200, 200, 0.5)")

        // Node-type colors
        val nodeEvent = Color("rgba(70, 130, 180, 0.8)")
        val nodeDialogue = Color("rgba(60, 179, 113, 0.8)")
        val nodeAction = Color("rgba(178, 34, 34, 0.8)")
        val nodeInput = Color("rgba(128, 0, 128, 0.8)")
        val nodeDefault = Color("rgba(100, 100, 100, 0.8)")
    }
}
