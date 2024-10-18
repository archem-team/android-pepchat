package chat.revolt.components.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.peptide.R
import chat.revolt.api.REVOLT_FILES
import chat.revolt.api.schemas.Server
import chat.revolt.api.schemas.ServerFlags
import chat.revolt.api.schemas.has
import chat.revolt.components.generic.IconPlaceholder
import chat.revolt.components.generic.RemoteImage

@Composable
fun ServerOverview(server: Server) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large),
        contentAlignment = Alignment.BottomStart
    ) {
        server.banner?.let {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.25f))
                    .height(166.dp)
                    .fillMaxWidth()
            )

            RemoteImage(
                url = "$REVOLT_FILES/banners/${it.id}",
                description = null,
                modifier = Modifier
                    .height(166.dp)
                    .fillMaxWidth(),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
                    .height(166.dp)
                    .fillMaxWidth()
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            server.icon?.let {
                RemoteImage(
                    url = "$REVOLT_FILES/icons/${it.id}/server.png?max_side=256",
                    description = null,
                    modifier = Modifier
                        .clip(CircleShape)
                        .height(48.dp)
                        .width(48.dp),
                    contentScale = ContentScale.Crop
                )
            } ?: run {
                IconPlaceholder(
                    name = server.name ?: stringResource(R.string.unknown),
                    modifier = Modifier
                        .clip(CircleShape)
                        .height(48.dp)
                        .width(48.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            CompositionLocalProvider(LocalContentColor provides Color.White) {
                if (server.flags has ServerFlags.Official) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_revolt_decagram_24dp),
                        contentDescription = stringResource(R.string.server_flag_official),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(24.dp)
                    )
                }
                if (server.flags has ServerFlags.Verified) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_check_decagram_24dp),
                        contentDescription = stringResource(R.string.server_flag_verified),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(24.dp)
                    )
                }

                Text(
                    text = server.name ?: stringResource(R.string.unknown),
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 16.sp
                )
            }
        }
    }
}