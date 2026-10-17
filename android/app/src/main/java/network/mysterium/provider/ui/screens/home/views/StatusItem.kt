package network.mysterium.provider.ui.screens.home.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import network.mysterium.node.model.NodeStatus
import network.mysterium.provider.R
import network.mysterium.provider.ui.theme.Colors
import network.mysterium.provider.ui.theme.Corners
import network.mysterium.provider.ui.theme.Paddings
import network.mysterium.provider.ui.theme.TextStyles

@Composable
fun StatusItem(
    modifier: Modifier = Modifier,
    status: NodeStatus
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = Colors.balanceBg,
                shape = RoundedCornerShape(Corners.default)
            )
            .padding(Paddings.card),
        verticalArrangement = Arrangement.spacedBy(Paddings.small)
    ) {
        StatusRow(label = stringResource(id = R.string.status_online), isActive = status == NodeStatus.ONLINE)
        StatusRow(label = stringResource(id = R.string.status_connecting), isActive = status == NodeStatus.CONNECTING)
        StatusRow(label = stringResource(id = R.string.status_no_network), isActive = status == NodeStatus.NO_NETWORK)
        StatusRow(label = stringResource(id = R.string.status_paused), isActive = status == NodeStatus.PAUSED)
        StatusRow(label = stringResource(id = R.string.status_failed), isActive = status == NodeStatus.FAILED)
        StatusRow(label = stringResource(id = R.string.status_unregistering), isActive = status == NodeStatus.UNREGISTERED)
    }
}

@Composable
private fun StatusRow(
    label: String,
    isActive: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .alpha(if (isActive) 1f else 0.15f)
                .background(
                    color = if (isActive) Colors.primary else Colors.grey500,
                    shape = CircleShape
                )
        )
        Text(
            modifier = Modifier
                .weight(1f)
                .padding(start = Paddings.small),
            text = label,
            style = TextStyles.body3Bold,
            color = if (isActive) Colors.blue700 else Colors.grey500
        )
        Text(
            text = if (isActive) stringResource(id = R.string.status_active) else "",
            style = TextStyles.body3,
            color = Colors.grey500
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StatusItemPreview() {
    Column {
        StatusItem(status = NodeStatus.ONLINE)
        StatusItem(status = NodeStatus.CONNECTING)
        StatusItem(status = NodeStatus.FAILED)
    }
}
