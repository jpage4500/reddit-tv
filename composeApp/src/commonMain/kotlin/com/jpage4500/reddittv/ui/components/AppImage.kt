package com.jpage4500.reddittv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.jpage4500.reddittv.util.LoggerConfig
import com.jpage4500.reddittv.util.PlatformType
import com.jpage4500.reddittv.util.Utils
import reddittv.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.*
import reddittv.composeapp.generated.resources.*

private val log = LoggerConfig.createLogger("AppImage")

// url prefix for a file bundled under composeResources/files/
const val URL_PREFIX_FILES = "files/"

/**
 * generic image component
 * handles bundled drawables ([res]), bundled files ("files/…"), remote URLs and in-memory bitmaps.
 *
 * [res] draws through `painterResource`, which decodes at the drawable's full native size on the
 * calling thread and caches the result forever — fine for the icons it's used for, wrong for
 * anything full-screen. a large bundled image belongs under `files/` so it loads through Coil.
 */
@Composable
fun AppImage(
    url: String? = null,
    res: DrawableResource? = null,
    bitmap: ImageBitmap? = null,
    modifier: Modifier = Modifier.fillMaxSize(),
    color: Color? = null,
    alpha: Float = DefaultAlpha,
    contentScale: ContentScale = ContentScale.Fit,
    onError: ((Throwable) -> Unit)? = null,
    onSuccess: ((width: Int, height: Int) -> Unit)? = null,
) {
    val colorFilter = if (color != null) ColorFilter.tint(color) else null

    // in-memory bitmap — not tinted, it's a full-color image
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = stringResource(Res.string.cd_image),
            modifier = modifier,
            alpha = alpha,
            contentScale = contentScale
        )
        return
    }

    // local resource - Coil can't fetch a bundled drawable, so draw it via painterResource
    if (res != null) {
        Image(
            painter = painterResource(res),
            contentDescription = stringResource(Res.string.cd_image),
            colorFilter = colorFilter,
            modifier = modifier,
            alpha = alpha,
            contentScale = contentScale
        )
        return
    }

    // bail on blank or "NA" sentinels — empty model crashes Coil's fetcher on Windows
    val rawUrl = url?.takeIf { it.isNotBlank() && !it.equals("NA", true) } ?: return
    val isLocalFile = rawUrl.startsWith(URL_PREFIX_FILES)
    val isDesktop = Utils.isPlatform(PlatformType.DESKTOP)

    // standard images (Coil AsyncImage)
    //
    // a bundled file under `files/` is handed over as a **uri**, not as bytes. Coil asks its keyers
    // for a memory-cache key and ships none for a ByteArray, so a bytes model is never memory-cached
    // at all: every crossfade, every swipe and every rotation tick re-reads the file out of the app
    // package and decodes it again, and holds the raw bytes in composition state in between. a uri
    // gets a stable key and is read once. desktop is the exception — inside a jDeploy JAR the uri is
    // a `jar:` one, which Coil's file fetcher can't open, so there it still extracts the bytes itself
    var imageModel by remember(rawUrl) {
        mutableStateOf<Any?>(
            when {
                !isLocalFile -> rawUrl
                isDesktop -> null
                else -> localUri(rawUrl)
            }
        )
    }

    if (isLocalFile && isDesktop) {
        LaunchedEffect(rawUrl) {
            try {
                // safely extract from the jDeploy JAR
                imageModel = Res.readBytes(rawUrl)
            } catch (e: Exception) {
                log.e("AppImage: ERROR reading local bytes: url=$rawUrl, error: ${e.message}")
                imageModel = localUri(rawUrl) // absolute fallback
            }
        }
    }

    if (imageModel != null) {
        AsyncImage(
            model = imageModel,
            contentDescription = rawUrl,
            modifier = modifier,
            contentScale = contentScale,
            alpha = alpha,
            colorFilter = colorFilter,
            onSuccess = { result ->
                onSuccess?.invoke(result.result.image.width, result.result.image.height)
            },
            onError = { state ->
                // failures are logged centrally by the ImageLoader EventListener (ImageLoaderConfig)
                onError?.invoke(state.result.throwable)
            }
        )
    }
}

/**
 * the platform uri for a bundled file under `files/`, or null if there isn't one.
 *
 * null rather than a throw because a missing name is a *supported* outcome here: `files/` has no
 * resource index to test a name against, so a bundled name may or may not have a file behind it.
 */
private fun localUri(path: String): String? = try {
    Res.getUri(path)
} catch (e: Exception) {
    log.e("AppImage: ERROR resolving local uri: url=$path, error: ${e.message}")
    null
}
