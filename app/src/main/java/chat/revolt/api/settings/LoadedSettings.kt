package chat.revolt.api.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import chat.revolt.api.schemas.AndroidSpecificSettingsSpecialEmbedSettings
import chat.revolt.ui.theme.Theme
import chat.revolt.ui.theme.getDefaultTheme

enum class MessageReplyStyle {
    None,
    SwipeFromEnd,
    DoubleTap
}

typealias SpecialEmbedSettings = AndroidSpecificSettingsSpecialEmbedSettings

object LoadedSettings {
    var theme by mutableStateOf(getDefaultTheme())
    var messageReplyStyle by mutableStateOf(MessageReplyStyle.SwipeFromEnd)
    var avatarRadius by mutableIntStateOf(50)
    var experimentsEnabled by mutableStateOf(false)
    var specialEmbedSettings by mutableStateOf(SpecialEmbedSettings())

    fun hydrateWithSettings(settings: SyncedSettings) {
        this.theme = settings.android.theme?.let { Theme.valueOf(it) } ?: getDefaultTheme()
        this.messageReplyStyle =
            settings.android.messageReplyStyle?.let { MessageReplyStyle.valueOf(it) }
                ?: MessageReplyStyle.SwipeFromEnd
        this.avatarRadius = settings.android.avatarRadius ?: 50
        this.specialEmbedSettings = settings.android.specialEmbedSettings ?: SpecialEmbedSettings()
    }

    fun reset() {
        theme = getDefaultTheme()
        messageReplyStyle = MessageReplyStyle.SwipeFromEnd
        avatarRadius = 50
        specialEmbedSettings = SpecialEmbedSettings()
    }
}
