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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import nd.max.BuildConfig
import nd.max.R
import nd.max.ui.theme.MaxTextRole


@Preview
@Composable
fun AboutCard() {
    MaxSurface(
        modifier = Modifier.fillMaxWidth()
    ) {
        AboutCardContent()
    }
}

@Composable
fun AboutDialog(dismiss: () -> Unit) {
    Dialog(
        onDismissRequest = { dismiss() }
    ) {
        AboutCard()
    }
}

@Composable
private fun AboutCardContent() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape
            ) {
                AsyncImage(
                    model = R.mipmap.ic_launcher,
                    contentDescription = stringResource(R.string.cd_app_icon),
                    modifier = Modifier.scale(1.4f)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {

                Text(
                    stringResource(id = R.string.app_name),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    BuildConfig.VERSION_NAME,
                    style = MaxTextRole.metadata,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(8.dp))

                val annotatedString = AnnotatedString.fromHtml(
                    htmlString = stringResource(
                        id = R.string.about_source_code,
                        "<b><a href=\"https://t.me/ROBINHOOD_GROUP_RODIN\">Telegram</a></b>"
                    ),
                    linkStyles = TextLinkStyles(
                        style = SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline
                        ),
                        pressedStyle = SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            background = MaterialTheme.colorScheme.secondaryContainer,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                )
                Text(
                    text = annotatedString,
                    style = MaxTextRole.description
                )
            }
        }
    }
}
