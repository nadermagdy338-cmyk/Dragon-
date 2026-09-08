/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nd.max.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import nd.max.R

/**
 * Three-role type system, each face picked for what this app actually is: a
 * root-level hardware console, not a generic content app.
 *
 * - Display (Space Grotesk): a geometric technical face with just enough
 *   personality in its letterforms to read as "engineered," used for titles,
 *   presets, and anything that should feel like a control-panel label.
 * - Body (Manrope): a warmer, highly legible geometric sans for the actual
 *   reading text — descriptions, safety notes, settings copy.
 * - Mono (JetBrains Mono): reserved for *live values* — MHz, percentages,
 *   core counts, temperatures. Real instrument panels separate the readout
 *   from the label typographically; this app does the same. See
 *   MonoValueText() and the mono styles below.
 *
 * All three are downloaded on-device via Google Play services' font provider
 * (no font binaries bundled into the APK), matching how large first-party
 * Compose apps (e.g. Jetchat) ship custom type.
 */

private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private fun googleFontFamily(name: String) = FontFamily(
    Font(googleFont = GoogleFont(name), fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont(name), fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = GoogleFont(name), fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = GoogleFont(name), fontProvider = googleFontProvider, weight = FontWeight.Bold)
)

val DisplayFontFamily = googleFontFamily("Space Grotesk")
val BodyFontFamily = googleFontFamily("Manrope")
val MonoFontFamily = FontFamily(
    Font(googleFont = GoogleFont("JetBrains Mono"), fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = GoogleFont("JetBrains Mono"), fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = GoogleFont("JetBrains Mono"), fontProvider = googleFontProvider, weight = FontWeight.Bold)
)
