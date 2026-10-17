package network.mysterium.provider.ui.components.switcher

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import network.mysterium.provider.ui.theme.Colors

@Composable
fun Switcher(
    modifier: Modifier = Modifier,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val trackColor by animateColorAsState(
        targetValue = if (checked) Colors.primary else Colors.grey200,
        animationSpec = tween(durationMillis = 150),
        label = "trackColor"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 18.dp else 0.dp,
        animationSpec = tween(durationMillis = 150),
        label = "thumbOffset"
    )

    Box(
        modifier = modifier
            .size(width = 38.dp, height = 21.dp)
            .background(
                color = trackColor,
                shape = RoundedCornerShape(50)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onCheckedChange(checked.not())
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(17.dp)
                .background(
                    color = Color.White,
                    shape = CircleShape
                )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SwitcherPreview() {
    Column {
        Switcher(
            checked = false,
            onCheckedChange = {}
        )
        Switcher(
            checked = true,
            onCheckedChange = {}
        )
    }
}
