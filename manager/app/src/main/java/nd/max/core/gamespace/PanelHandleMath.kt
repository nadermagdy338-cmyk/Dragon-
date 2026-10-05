/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.core.gamespace

import kotlin.math.exp

/** سحب «مطّاطي»: يطابق الإصبع قرب الصفر ثم تشتدّ المقاومة حتى يبلغ `limit` ولا يتجاوزه. */
fun rubberBand(distance: Float, limit: Float): Float =
    if (distance <= 0f || limit <= 0f) 0f else limit * (1f - exp(-distance / limit))

/** يُفتح اللوح عند الإفلات إن بلغ السحب العتبة، أو بقذفة سريعة نحو الداخل بعد مسافة دنيا. */
fun pullShouldOpen(inward: Float, inwardVelocity: Float, threshold: Float, minFling: Float, flingSpeed: Float): Boolean =
    inward >= threshold || (inward >= minFling && inwardVelocity >= flingSpeed)

/** موضع المقبض الرأسي كنسبة 0..1 من المدى المتاح؛ `0.5` إن لم يبقَ مدى. */
fun handleFraction(y: Float, min: Float, max: Float): Float =
    if (max <= min) 0.5f else ((y - min) / (max - min)).coerceIn(0f, 1f)

/** عكس [handleFraction]: النسبة المحفوظة إلى بكسل ضمن المدى الحالي. */
fun handleY(fraction: Float, min: Float, max: Float): Float =
    if (max <= min) min else min + fraction.coerceIn(0f, 1f) * (max - min)
