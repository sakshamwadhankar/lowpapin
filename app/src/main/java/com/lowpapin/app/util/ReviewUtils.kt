/*
 * Copyright (C) 2026 Lowpapin
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.lowpapin.app.util

import android.app.Activity
import com.lowpapin.app.feature.home.ReviewPromptResult
import timber.log.Timber

/**
 * No-op in-app review hook.
 *
 * There is no store review integration; the call site in
 * [com.lowpapin.app.ui.MainActivity] always treats the prompt
 * as permanently unavailable.
 */
fun requestAppReview(activity: Activity, onResult: (ReviewPromptResult) -> Unit) {
    Timber.i("In-app review is unavailable")
    onResult(ReviewPromptResult.SkippedPermanent)
}
