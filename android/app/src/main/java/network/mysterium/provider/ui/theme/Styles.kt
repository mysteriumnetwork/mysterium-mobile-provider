package network.mysterium.provider.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Colors {
    val primary = Color(0xFFD64495)
    val bgGradient = listOf(
        Color(0xFFe9f7ff),
        Color(0xFFf4e0eb)
    )
    val primaryBg = Color.White
    val cardBg = Color(0xFFFAFAFA)
    val serviceRunningBg = Color(0xFFF1F5F9)
    val serviceRunningDot = Color(0xFF2EE199)
    val serviceNotRunningBg = Color(0xFFFAFAFA)
    val serviceNotRunningDot = Color(0xFF94A2B8)
    val balanceBg = Color(0xFFE9F7FF)
    val blue100 = Color(0xFFE9F7FF)
    val blue200 = Color(0xFFD3E8F2)
    val blue600 = Color(0xFF254E62)
    val blue700 = Color(0xFF0D3A4F)
    val grey100 = Color(0xFFF1F1F1)
    val grey200 = Color(0xFFD5DADC)
    val grey300 = Color(0xFFBDC3C5)
    val grey400 = Color(0xFFA1A1AA)
    val grey500 = Color(0xFF6A7377)
    val grey800 = Color(0xFF101212)
    val pink400 = Color(0xFFD64495)
    val pink600 = Color(0xFFA01663)
    val red500 = Color(0xFFEF4444)
    val red50 = Color(0xFFFEF2F2)
    val slate400 = Color(0xFF94A2B8)
    val borders = Color(0xFFDAE2E8)
}

object Styles {
    val background = Brush.horizontalGradient(Colors.bgGradient)
}

object TextStyles {
    val splashHeader = TextStyle(
        fontFamily = FontFamily.OpenSans,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 46.sp,
        lineHeight = 62.64.sp,
    )
    val navigationHeader = TextStyle(
        fontFamily = FontFamily.DMSans,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp
    )
    val body = TextStyle(
        fontFamily = FontFamily.DMSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 18.sp
    )
    val body3 = TextStyle(
        fontFamily = FontFamily.DMSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.4.sp
    )
    val body3Bold = TextStyle(
        fontFamily = FontFamily.DMSans,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 22.4.sp
    )
    val label = TextStyle(
        fontFamily = FontFamily.DMSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 15.sp
    )
    val hint = TextStyle(
        fontFamily = FontFamily.DMSans,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 13.sp
    )
    val button = TextStyle(
        fontFamily = FontFamily.DMSans,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 25.6.sp
    )
    val button2 = TextStyle(
        fontFamily = FontFamily.DMSans,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 22.4.sp
    )
    val header = TextStyle(
        fontFamily = FontFamily.DMSans,
        fontWeight = FontWeight.Black,
        fontSize = 20.sp,
        lineHeight = 25.sp
    )
    val highDescriptions = TextStyle(
        fontFamily = FontFamily.DMSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 32.83.sp
    )
    val terms = TextStyle(
        fontFamily = FontFamily.DMSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 25.6.sp
    )
    val balance = navigationHeader
}

object Paddings {
    val tiny = 2.dp
    val small = 8.dp
    val default = 18.dp
    val logoDescription = 33.dp
    val splashLogo = 60.dp
    val continueButton = PaddingValues(
        top = 0.dp,
        start = default,
        end = default,
        bottom = default
    )
    val primaryButton = PaddingValues(default)
    val secondaryButton = PaddingValues(
        horizontal = 23.dp,
        vertical = 5.dp
    )
    val card = PaddingValues(
        horizontal = 26.dp,
        vertical = 30.dp
    )
    val cardButton = PaddingValues(
        horizontal = 26.dp,
        vertical = 23.dp
    )
    val service = 13.dp
    val serviceDot = 10.dp
}

object Corners {
    val card = 40.dp
    val default = 16.dp
    val small = 8.dp
}
