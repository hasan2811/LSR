package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.MyApplicationTheme

/**
 * HSE / LSR Brand Logo Component
 *
 * Features the memorable minimalist design requested:
 * - Top: Construction Safety Helmet (Hard Hat) with 3 dome segments & visor brim
 * - Bottom: Precision Industrial Cogwheel / Half-Gear
 * - Monochrome / Single Theme Color: Follows MaterialTheme.colorScheme.primary
 */
@Composable
fun HseBrandLogo(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
    contentDescription: String? = "HSE LSR Inspector Logo"
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_hse_logo),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size)
    )
}

/**
 * Elevated Brand Badge with Container Surface:
 * Perfect for App Bars, Hero Banners, and Splash/Login screens.
 */
@Composable
fun HseBrandBadge(
    modifier: Modifier = Modifier,
    logoSize: Dp = 32.dp,
    containerSize: Dp = 48.dp,
    badgeShape: RoundedCornerShape = RoundedCornerShape(12.dp),
    backgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
    logoTint: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = modifier
            .size(containerSize)
            .clip(badgeShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        HseBrandLogo(
            size = logoSize,
            tint = logoTint,
            contentDescription = null
        )
    }
}

/**
 * Full Brand Header Row with Logo Badge + App Title Typography
 */
@Composable
fun HseBrandHeader(
    modifier: Modifier = Modifier,
    title: String = "LSR INSPECTOR",
    subtitle: String = "Life-Saving Rules Safety Assurance",
    onPrimaryContainer: Boolean = false
) {
    val titleColor = if (onPrimaryContainer) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val subtitleColor = if (onPrimaryContainer) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
    val badgeBg = if (onPrimaryContainer) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer
    val badgeTint = if (onPrimaryContainer) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HseBrandBadge(
            containerSize = 48.dp,
            logoSize = 32.dp,
            backgroundColor = badgeBg,
            logoTint = badgeTint
        )

        Spacer(modifier = Modifier.width(12.dp))

        androidx.compose.foundation.layout.Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                ),
                color = titleColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor,
                maxLines = 1
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HseBrandLogoPreview() {
    MyApplicationTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
            ) {
                HseBrandHeader()
                HseBrandLogo(size = 64.dp)
            }
        }
    }
}
