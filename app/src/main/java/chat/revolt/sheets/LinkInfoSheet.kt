package chat.revolt.sheets

import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import chat.peptide.R
import chat.revolt.components.generic.SheetButton
import chat.revolt.components.generic.SheetEnd
import chat.revolt.internals.Platform
import chat.revolt.ui.theme.ClearRippleTheme
import kotlinx.coroutines.launch

@Composable
fun LinkInfoSheet(url: String, onDismiss: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 4.dp),
    ) {
        CompositionLocalProvider(value = LocalRippleTheme provides ClearRippleTheme) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = {
                        val customTab = CustomTabsIntent
                            .Builder()
                            .setShowTitle(true)
                            .build()

                        try {
                            customTab.launchUrl(context, Uri.parse(url))
                        } catch (e: Exception) {
                            Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.link_type_no_intent),
                                    Toast.LENGTH_SHORT
                                )
                                .show()
                        }
                    })
            ) {
                Text(
                    text = url,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        HorizontalDivider()
    }

    SheetButton(
        headlineContent = {
            Text(
                text = stringResource(id = R.string.link_open)
            )
        },
        leadingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Default.ExitToApp,
                contentDescription = null
            )
        },
        onClick = {
            val customTab = CustomTabsIntent
                .Builder()
                .setShowTitle(true)
                .build()

            try {
                customTab.launchUrl(context, Uri.parse(url))
            } catch (e: Exception) {
                Toast
                    .makeText(
                        context,
                        context.getString(R.string.link_type_no_intent),
                        Toast.LENGTH_SHORT
                    )
                    .show()
            }
        }
    )
    SheetButton(
        headlineContent = {
            Text(
                text = stringResource(id = R.string.copy)
            )
        },
        leadingContent = {
            Icon(
                painter = painterResource(id = R.drawable.ic_content_copy_24dp),
                contentDescription = null
            )
        },
        onClick = {
            coroutineScope.launch {
                clipboardManager.setText(AnnotatedString(url))
                if (Platform.needsShowClipboardNotification()) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.copied),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            onDismiss()
        }
    )
    
    SheetEnd()
}
