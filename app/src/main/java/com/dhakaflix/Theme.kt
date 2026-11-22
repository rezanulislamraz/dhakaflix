package com.dhakaflix

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// রঙের তালিকা: পুরো অ্যাপের সব রঙ এই ফাইল থেকে আসে
val Bg = Color(0xFF0A0B0D)        // ব্যাকগ্রাউন্ড (প্রায় কালো)
val Rail = Color(0xFF0E0F12)      // সাইড রেল / বটম বার
val CardDark = Color(0xFF15171B)  // কার্ড
val CardLift = Color(0xFF1F2329)  // ফোকাস করা কার্ড/বাটন
val Lime = Color(0xFFC6FF00)      // নিয়ন লাইম অ্যাকসেন্ট
val OnLime = Color(0xFF0A0B0D)    // লাইমের ওপর লেখার রঙ
val TextHead = Color(0xFFFFFFFF)  // শিরোনাম (সাদা)
val TextBody = Color(0xFF9AA0A6)  // সাধারণ লেখা (ধূসর)
val Danger = Color(0xFFFF5470)

// ফন্ট: Google Sans ফাইল res/font ফোল্ডারে দিলে নিচের লাইনটি বদলে
// FontFamily(Font(R.font.google_sans_regular), Font(R.font.google_sans_bold, FontWeight.Bold)) দিন
val AppFont: FontFamily = FontFamily.SansSerif

private fun t(size: Int, line: Int, w: FontWeight) =
    TextStyle(fontFamily = AppFont, fontWeight = w, fontSize = size.sp, lineHeight = line.sp)

val AppTypography = Typography(
    displayLarge = t(48, 54, FontWeight.Bold),
    displayMedium = t(38, 44, FontWeight.Bold),
    displaySmall = t(30, 36, FontWeight.Bold),
    headlineLarge = t(28, 34, FontWeight.Bold),
    headlineMedium = t(24, 30, FontWeight.SemiBold),
    headlineSmall = t(20, 26, FontWeight.SemiBold),
    titleLarge = t(20, 26, FontWeight.SemiBold),
    titleMedium = t(16, 22, FontWeight.Medium),
    titleSmall = t(14, 20, FontWeight.Medium),
    bodyLarge = t(16, 24, FontWeight.Normal),
    bodyMedium = t(14, 21, FontWeight.Normal),
    bodySmall = t(12, 18, FontWeight.Normal),
    labelLarge = t(14, 20, FontWeight.SemiBold),
    labelMedium = t(12, 16, FontWeight.Medium),
    labelSmall = t(11, 14, FontWeight.Medium),
)

private val Scheme = darkColorScheme(
    primary = Lime, onPrimary = OnLime,
    background = Bg, onBackground = TextHead,
    surface = CardDark, onSurface = TextHead,
    surfaceVariant = CardLift, onSurfaceVariant = TextBody,
    secondary = Lime, onSecondary = OnLime,
)

@Composable
fun DhakaFlixTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, content = content)
}
