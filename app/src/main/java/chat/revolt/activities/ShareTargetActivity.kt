package chat.revolt.activities

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import chat.peptide.R
import chat.revolt.api.RevoltAPI
import chat.revolt.api.internals.ChannelUtils
import chat.revolt.api.routes.channel.sendMessage
import chat.revolt.api.routes.microservices.autumn.FileArgs
import chat.revolt.api.routes.microservices.autumn.MAX_ATTACHMENTS_PER_MESSAGE
import chat.revolt.api.routes.microservices.autumn.uploadToAutumn
import chat.revolt.api.schemas.ChannelType
import chat.revolt.api.settings.LoadedSettings
import chat.revolt.api.settings.SyncedSettings
import chat.revolt.components.chat.NativeMessageField
import chat.revolt.components.emoji.EmojiPicker
import chat.revolt.components.screens.chat.AttachmentManager
import chat.revolt.components.screens.chat.drawer.ChannelItem
import chat.revolt.components.screens.chat.drawer.ChannelItemIconType
import chat.revolt.components.screens.chat.drawer.DMOrGroupItem
import chat.revolt.persistence.KVStorage
import chat.revolt.screens.chat.views.channel.ChannelScreenActivePane
import chat.revolt.ui.theme.RevoltTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.http.ContentType
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class ShareTargetActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text: String? = intent.getStringExtra(Intent.EXTRA_TEXT)
        val media: List<Uri?> = when (intent?.action) {
            // We receive one of something. Could be text, could be media.
            Intent.ACTION_SEND -> {
                when {
                    // No media if we receive text/plain
                    intent.type == "text/plain" -> {
                        listOf()
                    }

                    // Otherwise, we receive a single Uri
                    else -> {
                        listOf(
                            when {
                                // due to a bug in Android 13 we still use the deprecated method on Android 13, despite the new method being available
                                Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU -> {
                                    intent.getParcelableExtra(
                                        Intent.EXTRA_STREAM,
                                        Parcelable::class.java
                                    ) as? Uri
                                }

                                else -> {
                                    @Suppress("DEPRECATION")
                                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                                }
                            }
                        )
                    }
                }
            }

            // We receive multiple URIs, definitely media
            Intent.ACTION_SEND_MULTIPLE -> {
                try {
                    val bundle: ArrayList<Uri>? = when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                            @Suppress("UNCHECKED_CAST")
                            intent.getParcelableArrayListExtra(
                                Intent.EXTRA_STREAM,
                                Parcelable::class.java
                            ) as? ArrayList<Uri>
                        }

                        else -> {
                            @Suppress("DEPRECATION")
                            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                        }
                    }

                    bundle ?: listOf()
                } catch (e: Exception) {
                    Log.e("ShareTargetActivity", "Failed to get multiple URIs", e)
                    listOf()
                }
            }

            // We don't know what we're receiving
            else -> {
                Toast.makeText(
                    this,
                    getString(R.string.share_target_invalid_intent),
                    Toast.LENGTH_SHORT
                ).show()

                finish()
                return
            }
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            ShareTargetScreen(
                text = text,
                media = media.filterNotNull(),
                onFinished = { finish() }
            )
        }
    }
}

@HiltViewModel
class ShareTargetScreenViewModel @Inject constructor(
    private val kvStorage: KVStorage,
) : ViewModel() {
    var apiIsReady by mutableStateOf(false)

    var messageContent by mutableStateOf("")
    var attachments = mutableStateListOf<FileArgs>()
    var attachmentsUploading by mutableStateOf(false)
    var attachmentProgress by mutableFloatStateOf(0f)
    var activeBottomPane by mutableStateOf<ChannelScreenActivePane>(ChannelScreenActivePane.None)

    suspend fun isLoggedIn(): Boolean {
        return kvStorage.get("sessionToken") != null
    }

    suspend fun initialiseAPI() {
        if (!RevoltAPI.isLoggedIn()) {
            val token = kvStorage.get("sessionToken") ?: return
            RevoltAPI.loginAs(token)
            RevoltAPI.initialize()
        }
        apiIsReady = true
    }

    fun send(channelId: String, onFinished: () -> Unit) {
        viewModelScope.launch {
            val attachmentIds = arrayListOf<String>()
            val takenAttachments = attachments.take(MAX_ATTACHMENTS_PER_MESSAGE)
            val totalTaken = takenAttachments.size

            takenAttachments.forEachIndexed { index, it ->
                try {
                    val id = uploadToAutumn(
                        it.file,
                        it.filename,
                        "attachments",
                        ContentType.parse(it.contentType),
                        onProgress = { current, total ->
                            attachmentProgress =
                                ((current.toFloat() / total.toFloat()) / totalTaken.toFloat()) + (index.toFloat() / totalTaken.toFloat())
                        }
                    )
                    attachmentIds.add(id)
                } catch (e: Exception) {
                    return@launch
                }
            }

            sendMessage(
                channelId = channelId,
                content = messageContent,
                attachments = attachmentIds
            )

            onFinished()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareTargetScreen(
    text: String?,
    media: List<Uri>?,
    onFinished: () -> Unit = {},
    viewModel: ShareTargetScreenViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (!viewModel.isLoggedIn()) {
            Toast.makeText(
                context,
                context.getString(R.string.share_target_login_first),
                Toast.LENGTH_SHORT
            ).show()

            onFinished()
            return@LaunchedEffect
        }

        viewModel.initialiseAPI()
    }

    LaunchedEffect(Unit) {
        media?.forEach { uri ->
            DocumentFile.fromSingleUri(context, uri)?.let { file ->
                val mFile = File(context.cacheDir, file.name ?: "attachment")

                mFile.outputStream().use { output ->
                    @Suppress("Recycle")
                    context.contentResolver.openInputStream(uri)?.copyTo(output)
                }

                viewModel.attachments.add(
                    FileArgs(
                        file = mFile,
                        contentType = file.type ?: "application/octet-stream",
                        filename = file.name ?: "attachment",
                        pickerIdentifier = null
                    )
                )
            }
        }

        text?.let {
            viewModel.messageContent = it
        }
    }

    var channelSearchContent by remember { mutableStateOf("") }
    var selectedChannel by rememberSaveable { mutableStateOf<String?>(null) }

    RevoltTheme(
        requestedTheme = LoadedSettings.theme,
        colourOverrides = SyncedSettings.android.colourOverrides
    ) {
        Scaffold(
            topBar = {
                TopAppBar(title = {
                    Text(text = stringResource(R.string.share))
                })
            }
        ) { pv ->
            Surface(
                modifier = Modifier
                    .padding(pv)
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize()
            ) {
                if (!viewModel.apiIsReady) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(48.dp)
                        )
                    }
                    return@Surface
                }

                Column {
                    OutlinedTextField(
                        value = channelSearchContent,
                        onValueChange = {
                            channelSearchContent = it
                        },
                        label = {
                            Text(text = stringResource(R.string.share_target_search_channels))
                        },
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    )

                    Box(
                        modifier = Modifier.weight(1f)
                    ) {
                        val filteredChannels = RevoltAPI.channelCache.values.asSequence().filter {
                            it.name?.contains(
                                channelSearchContent,
                                ignoreCase = true
                            ) == true
                                    || ChannelUtils.resolveName(it)
                                ?.contains(
                                    channelSearchContent,
                                    ignoreCase = true
                                ) == true
                        }

                        LazyColumn {
                            items(filteredChannels.count()) {
                                val channel = filteredChannels.elementAt(it)

                                when (channel.channelType) {
                                    ChannelType.Group, ChannelType.DirectMessage -> DMOrGroupItem(
                                        channel = channel,
                                        partner = ChannelUtils.resolveDMPartner(channel)?.let { u ->
                                            RevoltAPI.userCache[u]
                                        },
                                        isCurrent = selectedChannel == channel.id,
                                        hasUnread = false,
                                        onDestinationChanged = { selectedChannel = channel.id },
                                        onOpenChannelContextSheet = {}
                                    )

                                    else -> ChannelItem(
                                        iconType = ChannelItemIconType.Channel(
                                            channel.channelType ?: ChannelType.TextChannel
                                        ),
                                        channel = channel,
                                        isCurrent = selectedChannel == channel.id,
                                        onDestinationChanged = { selectedChannel = channel.id },
                                        onOpenChannelContextSheet = {},
                                        appendServerName = true
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }

                    Column {
                        AnimatedVisibility(viewModel.attachments.isNotEmpty()) {
                            AttachmentManager(
                                attachments = viewModel.attachments,
                                uploading = viewModel.attachmentsUploading,
                                uploadProgress = viewModel.attachmentProgress,
                                onRemove = {},
                                canRemove = false
                            )
                        }

                        NativeMessageField(
                            value = viewModel.messageContent,
                            onValueChange = { viewModel.messageContent = it },
                            canAttach = false,
                            forceSendButton = viewModel.attachments.isNotEmpty(),
                            disabled = viewModel.attachmentsUploading,
                            onAddAttachment = {},
                            onCommitAttachment = {},
                            onPickEmoji = {
                                if (viewModel.activeBottomPane is ChannelScreenActivePane.EmojiPicker) {
                                    viewModel.activeBottomPane = ChannelScreenActivePane.None
                                } else {
                                    viewModel.activeBottomPane = ChannelScreenActivePane.EmojiPicker
                                }
                            },
                            onSendMessage = {
                                if (selectedChannel == null) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.share_target_select_channel),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@NativeMessageField
                                } else {
                                    viewModel.send(selectedChannel!!) {
                                        onFinished()
                                    }
                                }
                            },
                            channelType = RevoltAPI.channelCache[selectedChannel]?.channelType
                                ?: ChannelType.TextChannel,
                            channelName = RevoltAPI.channelCache[selectedChannel]?.name ?: "",
                        )

                        AnimatedVisibility(viewModel.activeBottomPane is ChannelScreenActivePane.EmojiPicker) {
                            BackHandler(enabled = viewModel.activeBottomPane == ChannelScreenActivePane.EmojiPicker) {
                                viewModel.activeBottomPane = ChannelScreenActivePane.None
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.5f)
                                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                                    .padding(4.dp)
                            ) {
                                EmojiPicker {
                                    viewModel.messageContent += " $it"
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}