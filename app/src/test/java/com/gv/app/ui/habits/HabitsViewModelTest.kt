package com.gv.app.ui.habits

import com.gv.app.data.api.ApiService
import com.gv.app.domain.model.Habit
import com.gv.app.domain.model.LogRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.BeforeTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HabitsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var api: ApiService

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        api = mockk()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Helper — avoids repeating constructor boilerplate in every test
    private fun habit(id: Int = 1, name: String = "Running", value: Double? = null) =
        Habit(id, name, null, value)

    // ── loadHabits ────────────────────────────────────────────────────────────

    @Test
    fun `loadHabits success sets Success state with returned habits`() = runTest {
        val habits = listOf(habit(value = 5.0))
        coEvery { api.getHabits(any()) } returns habits

        val vm = HabitsViewModel(api)

        val state = assertIs<HabitsUiState.Success>(vm.uiState.value)
        assertEquals(habits, state.habits)
    }

    @Test
    fun `loadHabits network error sets Error state with the exception message`() = runTest {
        coEvery { api.getHabits(any()) } throws RuntimeException("Network error")

        val vm = HabitsViewModel(api)

        val state = assertIs<HabitsUiState.Error>(vm.uiState.value)
        assertEquals("Network error", state.message)
    }

    @Test
    fun `loadHabits error with no message falls back to Unknown error`() = runTest {
        coEvery { api.getHabits(any()) } throws RuntimeException()

        val vm = HabitsViewModel(api)

        val state = assertIs<HabitsUiState.Error>(vm.uiState.value)
        assertEquals("Unknown error", state.message)
    }

    // ── incrementHabit ────────────────────────────────────────────────────────

    @Test
    fun `incrementHabit posts value plus 1 to the API`() = runTest {
        val h = habit(value = 3.0)
        val logged = slot<LogRequest>()
        coEvery { api.getHabits(any()) } returns listOf(h)
        coEvery { api.logHabit(capture(logged)) } just Runs

        val vm = HabitsViewModel(api)
        vm.incrementHabit(h)

        assertEquals(4.0, logged.captured.value)
        assertEquals(h.id, logged.captured.habitId)
    }

    @Test
    fun `incrementHabit treats null logValue as 0`() = runTest {
        val h = habit(value = null)
        val logged = slot<LogRequest>()
        coEvery { api.getHabits(any()) } returns listOf(h)
        coEvery { api.logHabit(capture(logged)) } just Runs

        val vm = HabitsViewModel(api)
        vm.incrementHabit(h)

        assertEquals(1.0, logged.captured.value)
    }

    // ── decrementHabit ────────────────────────────────────────────────────────

    @Test
    fun `decrementHabit posts value minus 1 to the API`() = runTest {
        val h = habit(value = 3.0)
        val logged = slot<LogRequest>()
        coEvery { api.getHabits(any()) } returns listOf(h)
        coEvery { api.logHabit(capture(logged)) } just Runs

        val vm = HabitsViewModel(api)
        vm.decrementHabit(h)

        assertEquals(2.0, logged.captured.value)
        assertEquals(h.id, logged.captured.habitId)
    }

    @Test
    fun `decrementHabit treats null logValue as 0`() = runTest {
        val h = habit(value = null)
        val logged = slot<LogRequest>()
        coEvery { api.getHabits(any()) } returns listOf(h)
        coEvery { api.logHabit(capture(logged)) } just Runs

        val vm = HabitsViewModel(api)
        vm.decrementHabit(h)

        assertEquals(-1.0, logged.captured.value)
    }

    // ── setHabitValue ─────────────────────────────────────────────────────────

    @Test
    fun `setHabitValue posts the exact specified value to the API`() = runTest {
        val h = habit(value = 1.0)
        val logged = slot<LogRequest>()
        coEvery { api.getHabits(any()) } returns listOf(h)
        coEvery { api.logHabit(capture(logged)) } just Runs

        val vm = HabitsViewModel(api)
        vm.setHabitValue(h, 42.0)

        assertEquals(42.0, logged.captured.value)
        assertEquals(h.id, logged.captured.habitId)
    }

    // ── optimistic updates ────────────────────────────────────────────────────

    @Test
    fun `incrementHabit applies optimistic update before the API responds`() = runTest {
        val h = habit(value = 3.0)
        // Block the coroutine by using StandardTestDispatcher instead of the
        // global UnconfinedTestDispatcher so we can observe mid-flight state.
        val pausingDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(pausingDispatcher)

        coEvery { api.getHabits(any()) } returns listOf(h)
        coEvery { api.logHabit(any()) } just Runs

        val vm = HabitsViewModel(api)
        // Drain init's loadHabits so state is Success before we call increment.
        testScheduler.advanceUntilIdle()

        vm.incrementHabit(h)
        // Do NOT advance — the launched coroutine hasn't run yet.
        val state = assertIs<HabitsUiState.Success>(vm.uiState.value)
        assertEquals(4.0, state.habits.first().logValue)

        // Clean up: let the coroutine finish.
        testScheduler.advanceUntilIdle()
    }

    // ── error recovery ────────────────────────────────────────────────────────

    @Test
    fun `API error on increment triggers a reload from the server`() = runTest {
        val h = habit(value = 3.0)
        coEvery { api.getHabits(any()) } returns listOf(h)
        coEvery { api.logHabit(any()) } throws RuntimeException("timeout")

        val vm = HabitsViewModel(api)
        vm.incrementHabit(h)

        // getHabits is called once on init and once more during error recovery.
        coVerify(atLeast = 2) { api.getHabits(any()) }
    }

    // ── date navigation ───────────────────────────────────────────────────────

    @Test
    fun `isToday is true immediately after creation`() = runTest {
        coEvery { api.getHabits(any()) } returns emptyList()

        val vm = HabitsViewModel(api)

        assertTrue(vm.isToday.value)
    }

    @Test
    fun `previousDay marks isToday as false and reloads habits`() = runTest {
        coEvery { api.getHabits(any()) } returns emptyList()

        val vm = HabitsViewModel(api)
        vm.previousDay()

        assertFalse(vm.isToday.value)
        // init + after navigation
        coVerify(atLeast = 2) { api.getHabits(any()) }
    }

    @Test
    fun `nextDay marks isToday as false and reloads habits`() = runTest {
        coEvery { api.getHabits(any()) } returns emptyList()

        val vm = HabitsViewModel(api)
        vm.nextDay()

        assertFalse(vm.isToday.value)
        coVerify(atLeast = 2) { api.getHabits(any()) }
    }

    @Test
    fun `returnToday resets isToday to true after navigating away`() = runTest {
        coEvery { api.getHabits(any()) } returns emptyList()

        val vm = HabitsViewModel(api)
        vm.previousDay()
        vm.returnToday()

        assertTrue(vm.isToday.value)
    }
}
