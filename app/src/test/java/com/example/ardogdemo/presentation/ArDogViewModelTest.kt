package com.example.ardogdemo.presentation

import com.example.ardogdemo.domain.character.MultiModelCountSource
import com.example.ardogdemo.domain.character.MultiModelFormation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArDogViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `remote count is applied through the reducer on init`() {
        val viewModel = ArDogViewModel(MultiModelCountSource { 3 })
        assertEquals(3, viewModel.state.value.multiModelCount)
        assertEquals(3, ArDogReducer.reduce(viewModel.state.value, ArDogIntent.ActivateMultiModel).playerInstanceCount)
    }

    @Test fun `unavailable remote count keeps the default`() {
        val viewModel = ArDogViewModel(MultiModelCountSource { null })
        assertEquals(MultiModelFormation.DEFAULT_COUNT, viewModel.state.value.multiModelCount)
    }

    @Test fun `out of range remote count is clamped`() {
        assertEquals(MultiModelFormation.MAX_COUNT, ArDogViewModel(MultiModelCountSource { 42 }).state.value.multiModelCount)
        assertEquals(MultiModelFormation.MIN_COUNT, ArDogViewModel(MultiModelCountSource { 0 }).state.value.multiModelCount)
    }
}
