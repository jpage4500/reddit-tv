package com.jpage4500.reddittv.ui.dialog

import androidx.compose.runtime.Composable
import com.jpage4500.reddittv.data.Post
import com.jpage4500.reddittv.ui.setting.SettingItem
import org.jetbrains.compose.resources.*
import reddittv.composeapp.generated.resources.*

/**
 * a post's options, from a long press on its tile or card: open it, go to its community, and join
 * or leave that community. [isJoined] is whether the user's communities include it; signed out,
 * joining asks to sign in instead ([onJoin] is left to decide)
 *
 * [waitForOkRelease]: opened by holding the remote's OK, which is still down
 */
@Composable
fun PostOptionsDialog(
    post: Post,
    isJoined: Boolean,
    isSignedIn: Boolean,
    showCommunity: Boolean,
    waitForOkRelease: Boolean,
    onOpen: () -> Unit,
    onCommunity: () -> Unit,
    onJoin: (join: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sub = post.subreddit
    BaseDialog(
        title = "r/$sub",
        waitForOkRelease = waitForOkRelease,
        onDismiss = onDismiss,
    ) {
        SettingItem(
            icon = Res.drawable.icon_forward,
            labelRes = Res.string.post_open,
            onClick = {
                onDismiss()
                onOpen()
            },
        )
        if (showCommunity) {
            SettingItem(
                icon = Res.drawable.icon_chevron_right,
                labelText = stringResource(Res.string.post_view_community, sub),
                onClick = {
                    onDismiss()
                    onCommunity()
                },
            )
        }
        SettingItem(
            icon = if (isJoined) Res.drawable.minus else Res.drawable.plus,
            labelText = stringResource(
                when {
                    !isSignedIn -> Res.string.post_sign_in_to_join
                    isJoined -> Res.string.post_leave
                    else -> Res.string.post_join
                },
                sub,
            ),
            showDivider = false,
            onClick = {
                onDismiss()
                onJoin(!isJoined)
            },
        )
    }
}
