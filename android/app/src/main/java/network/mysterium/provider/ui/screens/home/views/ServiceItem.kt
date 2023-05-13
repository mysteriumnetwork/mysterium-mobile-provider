package network.mysterium.provider.ui.screens.home.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import network.mysterium.node.model.NodeRunnerService
import network.mysterium.provider.ui.theme.Colors
import network.mysterium.provider.ui.theme.Corners
import network.mysterium.provider.ui.theme.Paddings
import network.mysterium.provider.ui.theme.TextStyles

@Composable
fun ServiceItem(
    modifier: Modifier = Modifier,
    service: NodeRunnerService
) {
    Box(
        modifier = modifier
            .background(
                color = service.status.bgColor,
                shape = RoundedCornerShape(Corners.default)
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .padding(Paddings.serviceDot)
                .align(Alignment.TopStart)
                .size(10.dp)
                .background(
                    color = service.status.dotColor,
                    shape = CircleShape
                )
        )
        Text(
            modifier = Modifier.padding(Paddings.default),
            text = service.name,
            style = TextStyles.label,
            color = service.status.textColor
        )
    }
}

private val NodeRunnerService.Status.bgColor: Color
    get() = when (this) {
        NodeRunnerService.Status.NOT_RUNNING,
        NodeRunnerService.Status.STARTING -> {
            Colors.serviceNotRunningBg
        }
        NodeRunnerService.Status.RUNNING -> {
            Colors.serviceRunningBg
        }
    }

private val NodeRunnerService.Status.textColor: Color
    get() = when (this) {
        NodeRunnerService.Status.NOT_RUNNING,
        NodeRunnerService.Status.STARTING -> {
            Colors.textDisabled
        }
        NodeRunnerService.Status.RUNNING -> {
            Colors.textPrimary
        }
    }

private val NodeRunnerService.Status.dotColor: Color
    get() = when (this) {
        NodeRunnerService.Status.NOT_RUNNING,
        NodeRunnerService.Status.STARTING -> {
            Colors.serviceNotRunningDot
        }
        NodeRunnerService.Status.RUNNING -> {
            Colors.serviceRunningDot
        }
    }

@Preview(showBackground = true)
@Composable
private fun ServiceItemPreview() {
    Column(
        modifier = Modifier.background(Colors.primaryBg),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ServiceItem(service = NodeRunnerService("name", NodeRunnerService.Status.RUNNING))
        ServiceItem(service = NodeRunnerService("name", NodeRunnerService.Status.NOT_RUNNING))
        ServiceItem(service = NodeRunnerService("name", NodeRunnerService.Status.STARTING))
    }
}