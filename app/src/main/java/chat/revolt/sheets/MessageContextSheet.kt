package chat.revolt.sheets

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import chat.peptide.R
import chat.revolt.api.REVOLT_APP
import chat.revolt.api.RevoltAPI
import chat.revolt.api.internals.PermissionBit
import chat.revolt.api.internals.Roles
import chat.revolt.api.internals.has
import chat.revolt.api.routes.channel.deleteMessage
import chat.revolt.api.routes.channel.react
import chat.revolt.callbacks.UiCallbacks
import chat.revolt.components.chat.Message
import chat.revolt.components.generic.SheetButton
import chat.revolt.components.generic.SheetEnd
import chat.revolt.internals.Platform
import chat.revolt.internals.extensions.BottomSheetInsets
import chat.revolt.ui.theme.ClearRippleTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageContextSheet(
    messageId: String,
    onHideSheet: suspend () -> Unit,
    onReportMessage: () -> Unit
) {
    val message = RevoltAPI.messageCache[messageId]
    if (message == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        return
    }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var showShareSheet by remember { mutableStateOf(false) }
    var showReactSheet by remember { mutableStateOf(false) }
    var showDeleteMessageConfirmation by remember { mutableStateOf(false) }

    if (showShareSheet) {
        val shareSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            sheetState = shareSheetState,
            onDismissRequest = {
                showShareSheet = false
            },
            windowInsets = BottomSheetInsets
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
            ) {
                SheetButton(
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_content_copy_24dp),
                            contentDescription = null
                        )
                    },
                    headlineContent = {
                        Text(
                            text = stringResource(id = R.string.message_context_sheet_actions_copy)
                        )
                    },
                    onClick = {
                        if (message.content.isNullOrEmpty()) {
                            coroutineScope.launch {
                                shareSheetState.hide()
                                onHideSheet()
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.message_context_sheet_actions_copy_failed_empty
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            return@SheetButton
                        }

                        if (Platform.needsShowClipboardNotification()) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.copied),
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        coroutineScope.launch {
                            shareSheetState.hide()
                        }
                        coroutineScope.launch {
                            clipboardManager.setText(AnnotatedString(message.content))
                            onHideSheet()
                        }
                    }
                )

                SheetButton(
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_link_variant_24dp),
                            contentDescription = null
                        )
                    },
                    headlineContent = {
                        Text(
                            text = stringResource(
                                id = R.string.message_context_sheet_actions_copy_link
                            )
                        )
                    },
                    onClick = {
                        if (message.content.isNullOrEmpty()) {
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.message_context_sheet_actions_copy_failed_empty
                                ),
                                Toast.LENGTH_SHORT
                            ).show()

                            coroutineScope.launch {
                                shareSheetState.hide()
                            }
                            coroutineScope.launch {
                                onHideSheet()
                            }

                            return@SheetButton
                        }

                        val server = RevoltAPI.serverCache.values.find { server ->
                            server.channels?.contains(message.channel) ?: false
                        }
                        val messageLink =
                            "$REVOLT_APP/server/${server?.id}/channel/${message.channel}/${message.id}"

                        clipboardManager.setText(AnnotatedString(messageLink))
                        if (Platform.needsShowClipboardNotification()) {
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.message_context_sheet_actions_copy_link_copied
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        coroutineScope.launch {
                            shareSheetState.hide()
                        }
                        coroutineScope.launch {
                            onHideSheet()
                        }
                    }
                )

                SheetButton(
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_content_copy_id_24dp),
                            contentDescription = null
                        )
                    },
                    headlineContent = {
                        Text(
                            text = stringResource(
                                id = R.string.message_context_sheet_actions_copy_id
                            )
                        )
                    },
                    onClick = {
                        if (message.id == null) return@SheetButton

                        clipboardManager.setText(AnnotatedString(message.id))

                        if (Platform.needsShowClipboardNotification()) {
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.message_context_sheet_actions_copy_id_copied
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        coroutineScope.launch {
                            shareSheetState.hide()
                        }
                        coroutineScope.launch {
                            onHideSheet()
                        }
                    }
                )
            }

            SheetEnd()
        }
    }

    if (showReactSheet) {
        val reactSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            sheetState = reactSheetState,
            onDismissRequest = {
                showReactSheet = false
            },
            windowInsets = BottomSheetInsets
        ) {
            ReactSheet(messageId) {
                if (it == null) return@ReactSheet

                coroutineScope.launch {
                    message.channel?.let { channelId ->
                        react(channelId, messageId, it)
                    }

                    reactSheetState.hide()
                    onHideSheet()
                }
            }
        }
    }

    if (showDeleteMessageConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showDeleteMessageConfirmation = false
            },
            title = {
                Text(
                    text = stringResource(R.string.message_context_sheet_actions_delete_confirmation_title)
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.message_context_sheet_actions_delete_confirmation_body)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteMessageConfirmation = false
                        coroutineScope.launch {
                            onHideSheet()
                            message.channel?.let { channelId ->
                                deleteMessage(channelId, messageId)
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.message_context_sheet_actions_delete_confirmation_yes))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteMessageConfirmation = false
                    }
                ) {
                    Text(stringResource(R.string.message_context_sheet_actions_delete_confirmation_no))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
    ) {
        CompositionLocalProvider(value = LocalRippleTheme provides ClearRippleTheme) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 4.dp),
            ) {
                Message(
                    message = message.copy(
                        tail = false,
                        masquerade = null
                    )
                )

                HorizontalDivider()
            }
        }

        SheetButton(
            leadingContent = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_reply_24dp),
                    contentDescription = null
                )
            },
            headlineContent = {
                Text(
                    text = stringResource(id = R.string.message_context_sheet_actions_reply),
                )
            },
            onClick = {
                coroutineScope.launch {
                    UiCallbacks.replyToMessage(messageId)
                    onHideSheet()
                }
            }
        )

        SheetButton(
            leadingContent = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_hamburger_plus_24dp),
                    contentDescription = null
                )
            },
            headlineContent = {
                Text(
                    text = stringResource(id = R.string.message_context_sheet_actions_react),
                )
            },
            onClick = {
                showReactSheet = true
            }
        )

        if (message.author == RevoltAPI.selfId) {
            SheetButton(
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null
                    )
                },
                headlineContent = {
                    Text(
                        text = stringResource(id = R.string.message_context_sheet_actions_edit),
                    )
                },
                onClick = {
                    coroutineScope.launch {
                        UiCallbacks.editMessage(messageId)
                        onHideSheet()
                    }
                }
            )
        }

        SheetButton(
            leadingContent = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_eye_off_24dp),
                    contentDescription = null
                )
            },
            headlineContent = {
                Text(
                    text = stringResource(id = R.string.message_context_sheet_actions_mark_unread),
                )
            },
            onClick = {
                Toast.makeText(
                    context,
                    context.getString(R.string.comingsoon_toast),
                    Toast.LENGTH_SHORT
                ).show()

                coroutineScope.launch {
                    onHideSheet()
                }
            }
        )

        SheetButton(
            leadingContent = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_share_24dp),
                    contentDescription = null,
                )
            },
            headlineContent = {
                Text(
                    text = stringResource(id = R.string.share),
                )
            },
            onClick = {
                showShareSheet = true
            }
        )

        if (
            (message.channel?.let {
                val channel = RevoltAPI.channelCache[it] ?: return@let null
                Roles.permissionFor(
                    channel,
                    RevoltAPI.userCache[RevoltAPI.selfId]
                )
            } ?: 0) has PermissionBit.ManageMessages || message.author == RevoltAPI.selfId
        ) {
            SheetButton(
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null
                    )
                },
                headlineContent = {
                    Text(
                        text = stringResource(id = R.string.message_context_sheet_actions_delete),
                    )
                },
                dangerous = true,
                onClick = {
                    showDeleteMessageConfirmation = true
                }
            )
        }

        if (message.author != RevoltAPI.selfId) {
            SheetButton(
                leadingContent = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_flag_24dp),
                        contentDescription = null
                    )
                },
                headlineContent = {
                    Text(
                        text = stringResource(id = R.string.message_context_sheet_actions_report),
                    )
                },
                dangerous = true,
                onClick = {
                    coroutineScope.launch {
                        onReportMessage()
                    }
                },
            )
        }

        SheetEnd()
    }
}
