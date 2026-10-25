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
package com.lowpapin.app.core.domain.handler

import com.lowpapin.app.core.blocking.handler.BlockOptionHandler
import com.lowpapin.app.core.blocking.handler.NoBlockHandler
import com.lowpapin.app.core.domain.BaseTest
import com.lowpapin.app.core.model.BlockingResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoBlockHandlerTest : BaseTest() {

    companion object {
        private const val LIMIT_MILLIS = 30 * 60 * 1000L // 30 minutes
    }

    private val testDispatcher = StandardTestDispatcher()
    private val noBlockHandler: BlockOptionHandler =
        NoBlockHandler()

    @Test
    fun whenEnterContent_neverBlocks() = runTest(testDispatcher) {
        val didBlock = noBlockHandler.onEnterContent(0L)
        assert(!didBlock)
    }

    @Test
    fun whenEnterContent_neverBlocksHugeTimeLimit() = runTest(testDispatcher) {
        val didBlock = noBlockHandler.onEnterContent(LIMIT_MILLIS + 1)
        assert(!didBlock)
    }

    @Test
    fun whenPeriodicCheck_neverBlocks() = runTest(testDispatcher) {
        val result = noBlockHandler.onPeriodicCheck(0L, 1000L)
        assert(result is BlockingResult.Continue)
    }
    @Test
    fun whenExitContent_resetsState() = runTest(testDispatcher) {
        noBlockHandler.onExitContent(0L, 0L)
        // No state to verify directly, but ensure no exceptions occur
    }
}
