package chat.revolt.components.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.peptide.R
import chat.revolt.activities.RevoltTweenFloat
import chat.revolt.activities.RevoltTweenInt
import chat.revolt.api.REVOLT_FILES
import chat.revolt.api.RevoltAPI
import chat.revolt.api.schemas.User
import chat.revolt.components.generic.UserAvatar

@Composable
fun StackedUserAvatars(users: List<String>, amount: Int = 3, serverId: String?) {
    Box(
        modifier = Modifier
            .size(16.dp + (8.dp * minOf(users.size, amount)), 16.dp)
    ) {
        users.take(amount).forEachIndexed { index, userId ->
            val user = RevoltAPI.userCache[userId]
            val maybeMember = serverId?.let { RevoltAPI.members.getMember(serverId, userId) }

            UserAvatar(
                avatar = user?.avatar,
                userId = userId,
                username = user?.let { User.resolveDefaultName(it) }
                    ?: stringResource(id = R.string.unknown),
                rawUrl = maybeMember?.avatar?.let { "$REVOLT_FILES/avatars/${it.id}?max_side=256" },
                size = 16.dp,
                modifier = Modifier
                    .offset(
                        x = (index * 8).dp
                    )
            )
        }
    }
}

@Composable
fun TypingIndicator(users: List<String>, serverId: String?) {
    fun typingMessageResource(): Int {
        return when (users.size) {
            0 -> R.string.typing_blank
            1 -> R.string.typing_one
            in 2..4 -> R.string.typing_many
            else -> R.string.typing_several
        }
    }

    AnimatedVisibility(
        visible = users.isNotEmpty(),
        enter = slideInVertically(
            animationSpec = RevoltTweenInt,
            initialOffsetY = { it }
        ) + fadeIn(animationSpec = RevoltTweenFloat),
        exit = slideOutVertically(
            animationSpec = RevoltTweenInt,
            targetOffsetY = { it }
        ) + fadeOut(animationSpec = RevoltTweenFloat)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f))
                .padding(vertical = 8.dp, horizontal = 16.dp)
        ) {
            StackedUserAvatars(users = users, serverId = serverId)

            Text(
                text = stringResource(
                    id = typingMessageResource(),
                    users.joinToString { userId ->
                        RevoltAPI.userCache[userId]?.let { u ->
                            val maybeMember =
                                serverId?.let { RevoltAPI.members.getMember(serverId, userId) }
                            
                            maybeMember?.nickname ?: User.resolveDefaultName(u)
                        } ?: userId
                    }
                ),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
