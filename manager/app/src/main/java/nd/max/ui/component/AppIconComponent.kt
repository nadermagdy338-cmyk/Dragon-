/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nd.max.ui.component


import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nd.max.ui.viewmodel.ApplistViewmodel


object AppIconCache {
    private val cache = LruCache<String, ImageBitmap>(150)

    fun get(packageName: String): ImageBitmap? = synchronized(cache) { cache.get(packageName) }

    fun clear() = synchronized(cache) { cache.evictAll() }


    /**
     * Rasterises a drawable into the cache, once per [cacheKey].
     *
     * Shared by both entry points below so they cannot drift in how they draw **or** in which key
     * they store under. The bug that shape prevents is silent: one caller writing under a name the
     * other never reads, so the icon is rebuilt on every recomposition with nothing to show for it.
     */
    private suspend fun rasterize(
        drawable: Drawable,
        cacheKey: String,
        targetSizePx: Int
    ): ImageBitmap = withContext(Dispatchers.IO) {
        get(cacheKey)?.let { return@withContext it }

        val bitmap = Bitmap.createBitmap(targetSizePx, targetSizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawable.setBounds(0, 0, targetSizePx, targetSizePx)
        drawable.draw(canvas)

        val imageBitmap = bitmap.asImageBitmap()

        synchronized(cache) {
            cache.put(cacheKey, imageBitmap)
        }

        return@withContext imageBitmap
    }

    suspend fun loadIcon(pm: PackageManager, appInfo: ApplicationInfo, targetSizePx: Int): ImageBitmap {
        val drawable = try {
            appInfo.loadIcon(pm) ?: pm.defaultActivityIcon
        } catch (e: Exception) {
            pm.defaultActivityIcon
        }
        return rasterize(drawable, appInfo.packageName, targetSizePx)
    }

    /**
     * The same load, from a package name alone.
     *
     * Added for the activity launcher, whose index keeps *names* rather than `ApplicationInfo`
     * objects. Its rows drew a letter instead of the app icon, justified in a comment as the most
     * expensive thing an app index can do — but that reasoning predates this cache: a bitmap is
     * rasterised once per package, on `Dispatchers.IO`, and every later row reads it from the
     * `LruCache` in O(1) with no package-manager call at all. With the cache in place the letter
     * is not a performance decision any more, it is just a missing icon.
     */
    suspend fun loadIcon(pm: PackageManager, packageName: String, targetSizePx: Int): ImageBitmap {
        get(packageName)?.let { return it }
        val drawable = withContext(Dispatchers.IO) {
            try {
                pm.getApplicationInfo(packageName, 0).loadIcon(pm)
            } catch (e: Exception) {
                null
            }
        } ?: pm.defaultActivityIcon
        return rasterize(drawable, packageName, targetSizePx)
    }
}

/**
 * App icon resolved from a package name — the overload the app index needs.
 *
 * Same cache and same rasterisation path as the [ApplistViewmodel.AppInfo] overload, so a row
 * here and a row on the apps list show the identical bitmap and pay for it once.
 */
@Composable
fun AppIconImage(
    packageName: String,
    size: Dp = 40.dp,
    contentDescription: String? = null,
) {
    val pm = LocalContext.current.packageManager
    val density = LocalDensity.current
    val targetSizePx = remember(size, density) { with(density) { size.roundToPx() } }

    var appBitmap by remember(packageName) {
        mutableStateOf(AppIconCache.get(packageName))
    }

    LaunchedEffect(packageName, targetSizePx) {
        if (appBitmap == null) {
            try {
                appBitmap = AppIconCache.loadIcon(pm, packageName, targetSizePx)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(modifier = Modifier.size(size)) {
        Crossfade(
            targetState = appBitmap,
            animationSpec = tween(durationMillis = 200),
            label = "IconFade"
        ) { icon ->
            if (icon == null) {
                PlaceHolderBox(Modifier.fillMaxSize())
            } else {
                Image(
                    bitmap = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp)),
                )
            }
        }
    }
}


@Composable
fun AppIconImage(
    app: ApplistViewmodel.AppInfo,
    size: Dp = 40.dp
) {
    val context = LocalContext.current
    val pm = context.packageManager
    

    val density = LocalDensity.current
    val targetSizePx = remember(size, density) {
        with(density) { size.roundToPx() }
    }
    

    var appBitmap by remember(app.packageName) { 
        mutableStateOf(AppIconCache.get(app.packageName)) 
    }


    LaunchedEffect(app.packageName, targetSizePx) {
        if (appBitmap == null) {
            app.packageInfo.applicationInfo?.let { appInfo ->
                try {
                    appBitmap = AppIconCache.loadIcon(pm, appInfo, targetSizePx)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    Box(modifier = Modifier.size(size)) {
        Crossfade(
            targetState = appBitmap,
            animationSpec = tween(durationMillis = 200),
            label = "IconFade"
        ) { icon ->
            if (icon == null) {

                PlaceHolderBox(Modifier.fillMaxSize())
            } else {

                Image(
                    bitmap = icon,
                    contentDescription = app.label,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                )
            }
        }
    }
}

@Composable
private fun PlaceHolderBox(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))

            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)) 
    )
}
