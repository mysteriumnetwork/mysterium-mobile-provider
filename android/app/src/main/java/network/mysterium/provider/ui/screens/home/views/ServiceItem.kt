package network.mysterium.provider.ui.screens.home.views

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import network.mysterium.node.model.NodeServiceType
import network.mysterium.provider.extensions.bgColor
import network.mysterium.provider.extensions.dotColor
import network.mysterium.provider.extensions.nameResId
import network.mysterium.provider.extensions.textColor
import network.mysterium.provider.ui.theme.Colors
import network.mysterium.provider.ui.theme.Corners
import network.mysterium.provider.ui.theme.Paddings
import network.mysterium.provider.ui.theme.TextStyles

@Composable
fun ServiceItem(
    modifier: Modifier = Modifier,
    service: NodeServiceType
) {
    Box(
        modifier = modifier
            .background(
                color = service.state.bgColor,
                shape = RoundedCornerShape(Corners.default)
            )
            .border(
                width = 1.dp,
                color = Colors.borders,
                shape = RoundedCornerShape(Corners.default),
            ),
        contentAlignment = Alignment.Center
    ) {
        StatusDot(
            modifier = Modifier
                .padding(Paddings.serviceDot)
                .align(Alignment.TopStart),
            state = service.state
        )
        Text(
            modifier = Modifier.padding(Paddings.small),
            text = stringResource(id = service.id.nameResId),
            style = TextStyles.body3Bold,
            color = service.state.textColor,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StatusDot(
    modifier: Modifier = Modifier,
    state: NodeServiceType.State
) {
    val isRunning = state == NodeServiceType.State.RUNNING

    // Subtle breathing effect on the running dot so the grid feels alive.
    val transition = rememberInfiniteTransition(label = "statusDot")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRunning) 0.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = modifier
            .size(10.dp)
            .alpha(if (isRunning) pulse else 1f)
            .background(
                color = state.dotColor,
                shape = CircleShape
            )
    )
}

@Preview(showBackground = true)
@Composable
private fun ServiceItemPreview() {
    Column(
        modifier = Modifier.background(Colors.primaryBg),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ServiceItem(service = NodeServiceType(NodeServiceType.Service.WIREGUARD, NodeServiceType.State.RUNNING))
        ServiceItem(service = NodeServiceType(NodeServiceType.Service.WIREGUARD, NodeServiceType.State.NOT_RUNNING))
        ServiceItem(service = NodeServiceType(NodeServiceType.Service.WIREGUARD, NodeServiceType.State.STARTING))
    }
}
