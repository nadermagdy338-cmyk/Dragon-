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
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.roundToInt
import kotlinx.coroutines.launch


@Composable
fun CustomBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val context = LocalContext.current
    val settingsPrefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val isBlurEnabled = settingsPrefs.getBoolean("expressive_blur_ui", false)
    val hazeState = LocalAppHazeState.current

    val coroutineScope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    
    val density = LocalDensity.current
    // **والكيبورد لا يغطّي الورقة (تكملة ٢٠٦ — نصّ المالك: «يجب أن تظهر النافذة المنبثقة في زر
    // البحث فوق الكيبورد»).**
    //
    // كانت الحاشية السفلى `navigationBars + 20dp` وحدها، وهي كافية حين لا كيبورد. فإذا فُتحت
    // ورقة فيها حقل نصّ (مُوجِّد الشاشات · مُدير العمليات · محرّر القيم · مُنتقي اللغة · سجلّات)
    // ظهرت اللوحة **فوق** أسفل الشاشة، فحجبت الورقةَ كلّها أو حجبت نتائجها — وهو ما رآه المالك.
    //
    // والعلاج في سطر واحد: أرضية الورقة = **أكبر** الحاشيتين لا مجموعهما.
    //   • بلا كيبورد: حاشية شريط التنقل (السلوك نفسه قبل هذه الجولة — لا تغيير).
    //   • ومعه: ارتفاع اللوحة نفسه، فترتفع الورقة معها.
    //   • و`maxOf` لا الجمع: اللوحة تشغل منطقة شريط التنقل حين تعلو، والجمع يرفع الورقة
    //     مقدار شريط التنقل زيادةً — أي فراغٌ ميّت بين الورقة والكيبورد.
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val extraBottomPadding = maxOf(navBarBottom, imeBottom) + 20.dp

    LaunchedEffect(visible) {
        if (visible) {
            dragOffset.snapTo(0f)
        }
    }

    if (visible) {
        BackHandler(onBack = onDismiss)
    }


    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(250)),
        exit = fadeOut(animationSpec = tween(200)),
        modifier = Modifier.zIndex(100f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
    }


    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it }, 
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
        ),
        exit = slideOutVertically(
            targetOffsetY = { it }, 
            animationSpec = tween(250, easing = FastOutSlowInEasing)
        ),
        modifier = Modifier.zIndex(101f)
    ) {

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier

                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .offset { 
                        IntOffset(
                            x = 0, 
                            y = dragOffset.value.roundToInt()
                        ) 
                    }
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .then(
                        if (isBlurEnabled && hazeState != null) {
                            Modifier.hazeEffect(state = hazeState) { blurEffect { blurRadius = 24.dp } }
                        } else Modifier
                    )
                    .background(
                        if (isBlurEnabled) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)
                        else MaterialTheme.colorScheme.surfaceContainer
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (dragOffset.value > 250f) {
                                        onDismiss()
                                    } else {
                                        coroutineScope.launch {
                                            dragOffset.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
                                            )
                                        }
                                    }
                                },
                                onDragCancel = {
                                    coroutineScope.launch {
                                        dragOffset.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 400f))
                                    }
                                }
                            ) { change, dragAmount ->
                                change.consume()
                                coroutineScope.launch {
                                    dragOffset.snapTo(maxOf(0f, dragOffset.value + dragAmount))
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(5.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape)
                    )
                }
                

                // **وحاشية سفلية حقيقية لا `Spacer` في آخر العمود:** كان الفراغ يُرسم بعنصر
                // في آخر المحتوى، فيكبر العمود بمقداره ثم يُقتطع من أعلاه (الورقة ملتصقة بالقاع)
                // — ومع كيبورد مفتوح كان العنوان وحقل البحث يُدفعان **خارج الشاشة** بدل أن
                // تُقلّص القائمة. والحاشية هنا تُنقص الارتفاع المتاح للمحتوى، فتُمرَّر القوائم
                // بـ`heightIn` كما هي وتُقرأ. **والسطح نفسه لم يتغيّر:** الخلفية والقُصاص خارج
                // هذه الحاشية، فالورقة تبقى بعرض الشاشة والزاوية المستديرة كما كانت.
                Column(modifier = Modifier.padding(bottom = extraBottomPadding)) {
                    content()
                }
            }
        }
    }
}
