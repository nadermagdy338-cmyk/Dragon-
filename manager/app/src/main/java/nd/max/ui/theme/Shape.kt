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

package nd.max.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import nd.max.ui.design.MaxRadius

/*
 * Material's five shape slots, expressed in the project's own radius scale.
 *
 * Why this is derived instead of written (measured, not asserted): this file used to declare a
 * *second* shape language — 6 / 10 / 18 / 26 / 32 — while `MaxRadius` declared 12 / 14 / 22 / 28,
 * and `MaxUiMetrics` a third (28 / 18 / 12). Three scales for one product is exactly how two
 * adjacent surfaces end up with different corners, which is the audit's first finding.
 *
 * A Material slot now names the token it stands for:
 *   extraSmall → [MaxRadius.control]  (a chip, a text field, a tag)
 *   small      → [MaxRadius.row]      (a list row, an action row)
 *   medium     → [MaxRadius.group]    (a grouped container, a card)
 *   large      → [MaxRadius.sheet]    (a sheet, a dialog)
 *   extraLarge → [MaxRadius.sheet]    (the same: nothing in the app should be softer)
 *
 * Start/end corners still mirror with the layout direction, which is what keeps RTL honest.
 */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(MaxRadius.control),
    small = RoundedCornerShape(MaxRadius.row),
    medium = RoundedCornerShape(MaxRadius.group),
    large = RoundedCornerShape(MaxRadius.sheet),
    extraLarge = RoundedCornerShape(MaxRadius.sheet)
)
