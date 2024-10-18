package chat.revolt.sheets

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import chat.peptide.R
import chat.revolt.activities.InviteActivity
import chat.revolt.api.REVOLT_APP
import chat.revolt.components.generic.FormTextField
import chat.revolt.components.generic.SheetButton
import chat.revolt.components.generic.SheetEnd
import chat.revolt.components.generic.SheetHeaderPadding

@Composable
fun AddServerSheet() {
    val context = LocalContext.current

    val joinFromInviteModalOpen = remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
    ) {
        if (joinFromInviteModalOpen.value) {
            JoinFromInviteModal(
                onDismiss = {
                    joinFromInviteModalOpen.value = false
                }
            )
        }

        SheetHeaderPadding {
            Text(
                text = stringResource(id = R.string.add_server_sheet_title),
                style = MaterialTheme.typography.headlineSmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SheetButton(
            headlineContent = {
                Text(stringResource(id = R.string.add_server_sheet_join_by_invite))
            },
            leadingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Default.ExitToApp,
                    contentDescription = null
                )
            },
            onClick = {
                joinFromInviteModalOpen.value = true
            }
        )

        SheetButton(
            headlineContent = {
                Text(stringResource(id = R.string.add_server_sheet_create_new))
            },
            leadingContent = {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null
                )
            },
            onClick = {
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.add_server_sheet_create_new_modal_under_construction
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    SheetEnd()
}

@Composable
fun JoinFromInviteModal(onDismiss: () -> Unit) {
    val context = LocalContext.current

    val inviteCode = remember { mutableStateOf("") }

    val inviteActivityResult = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("InviteActivity", "Result: $result")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(id = R.string.add_server_sheet_join_by_invite_modal_title))
        },
        text = {
            Column {
                Text(
                    text = stringResource(
                        id = R.string.add_server_sheet_join_by_invite_modal_description
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                FormTextField(
                    label = stringResource(
                        id = R.string.add_server_sheet_join_by_invite_modal_hint
                    ),
                    value = inviteCode.value,
                    onChange = {
                        inviteCode.value = it
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val intent = Intent(context, InviteActivity::class.java)
                    intent.data = if (inviteCode.value.startsWith("https://")) {
                        inviteCode.value.toUri()
                    } else {
                        "https://$REVOLT_APP/invite/${inviteCode.value}".toUri()
                    }
                    inviteActivityResult.launch(intent)
                }
            ) {
                Text(
                    text = stringResource(id = R.string.add_server_sheet_join_by_invite_modal_join)
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismiss()
                }
            ) {
                Text(text = stringResource(id = R.string.cancel))
            }
        }
    )
}
