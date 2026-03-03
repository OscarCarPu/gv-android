package com.gv.app.ui.login

import com.gv.app.data.api.ApiService
import com.gv.app.data.local.TokenManager
import com.gv.app.domain.model.LoginRequest
import com.gv.app.domain.model.TokenResponse
import com.gv.app.domain.model.TwoFactorRequest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import kotlin.test.BeforeTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var api: ApiService
    private lateinit var tokenManager: TokenManager

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        api = mockk()
        tokenManager = mockk {
            every { saveToken(any()) } just Runs
            every { clearToken() } just Runs
            every { tokenFlow } returns MutableStateFlow(null)
        }
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() = LoginViewModel(api, tokenManager)

    private fun errorBody(json: String) =
        json.toResponseBody("application/json".toMediaType())

    // ── submitPassword ────────────────────────────────────────────────────────

    @Test
    fun `submitPassword success transitions to AwaitingTwoFactor with temp token`() = runTest {
        coEvery { api.login(any()) } returns Response.success(TokenResponse("tmp-token"))

        val vm = vm()
        vm.submitPassword("secret")

        val state = assertIs<LoginUiState.AwaitingTwoFactor>(vm.uiState.value)
        assertEquals("tmp-token", state.tempToken)
        assertNull(state.errorMessage)
    }

    @Test
    fun `submitPassword sends correct request payload`() = runTest {
        val captured = slot<LoginRequest>()
        coEvery { api.login(capture(captured)) } returns Response.success(TokenResponse("t"))

        val vm = vm()
        vm.submitPassword("my-password")

        assertEquals("my-password", captured.captured.password)
    }

    @Test
    fun `submitPassword HTTP error transitions to Error with server message`() = runTest {
        coEvery { api.login(any()) } returns Response.error(
            401, errorBody("""{"error":"Invalid password"}""")
        )

        val vm = vm()
        vm.submitPassword("wrong")

        val state = assertIs<LoginUiState.Error>(vm.uiState.value)
        assertEquals("Invalid password", state.message)
    }

    @Test
    fun `submitPassword network error transitions to Error with exception message`() = runTest {
        coEvery { api.login(any()) } throws RuntimeException("Connection refused")

        val vm = vm()
        vm.submitPassword("secret")

        val state = assertIs<LoginUiState.Error>(vm.uiState.value)
        assertEquals("Connection refused", state.message)
    }

    // ── submitTwoFactorCode ───────────────────────────────────────────────────

    @Test
    fun `submitTwoFactorCode success saves token and transitions to Success`() = runTest {
        coEvery { api.login(any()) } returns Response.success(TokenResponse("tmp"))
        coEvery { api.login2fa(any()) } returns Response.success(TokenResponse("full-token"))

        val vm = vm()
        vm.submitPassword("secret")
        vm.submitTwoFactorCode("123456")

        assertIs<LoginUiState.Success>(vm.uiState.value)
        verify { tokenManager.saveToken("full-token") }
    }

    @Test
    fun `submitTwoFactorCode sends correct request payload`() = runTest {
        val captured = slot<TwoFactorRequest>()
        coEvery { api.login(any()) } returns Response.success(TokenResponse("tmp-tok"))
        coEvery { api.login2fa(capture(captured)) } returns Response.success(TokenResponse("full"))

        val vm = vm()
        vm.submitPassword("secret")
        vm.submitTwoFactorCode("654321")

        assertEquals("tmp-tok", captured.captured.token)
        assertEquals("654321", captured.captured.code)
    }

    @Test
    fun `submitTwoFactorCode HTTP error preserves temp token with error message`() = runTest {
        coEvery { api.login(any()) } returns Response.success(TokenResponse("tmp"))
        coEvery { api.login2fa(any()) } returns Response.error(
            401, errorBody("""{"error":"Invalid code"}""")
        )

        val vm = vm()
        vm.submitPassword("secret")
        vm.submitTwoFactorCode("000000")

        val state = assertIs<LoginUiState.AwaitingTwoFactor>(vm.uiState.value)
        assertEquals("tmp", state.tempToken)
        assertEquals("Invalid code", state.errorMessage)
    }

    @Test
    fun `submitTwoFactorCode network error preserves temp token with error message`() = runTest {
        coEvery { api.login(any()) } returns Response.success(TokenResponse("tmp"))
        coEvery { api.login2fa(any()) } throws RuntimeException("timeout")

        val vm = vm()
        vm.submitPassword("secret")
        vm.submitTwoFactorCode("123456")

        val state = assertIs<LoginUiState.AwaitingTwoFactor>(vm.uiState.value)
        assertEquals("tmp", state.tempToken)
        assertEquals("timeout", state.errorMessage)
    }

    @Test
    fun `submitTwoFactorCode is no-op when not in AwaitingTwoFactor state`() = runTest {
        val vm = vm()
        vm.submitTwoFactorCode("123456")

        assertIs<LoginUiState.Idle>(vm.uiState.value)
    }

    // ── clearError ────────────────────────────────────────────────────────────

    @Test
    fun `clearError from Error state transitions to Idle`() = runTest {
        coEvery { api.login(any()) } returns Response.error(
            401, errorBody("""{"error":"bad"}""")
        )

        val vm = vm()
        vm.submitPassword("wrong")
        assertIs<LoginUiState.Error>(vm.uiState.value)

        vm.clearError()
        assertIs<LoginUiState.Idle>(vm.uiState.value)
    }

    @Test
    fun `clearError from AwaitingTwoFactor clears errorMessage but preserves temp token`() = runTest {
        coEvery { api.login(any()) } returns Response.success(TokenResponse("tmp"))
        coEvery { api.login2fa(any()) } returns Response.error(
            401, errorBody("""{"error":"bad code"}""")
        )

        val vm = vm()
        vm.submitPassword("secret")
        vm.submitTwoFactorCode("000000")

        val before = assertIs<LoginUiState.AwaitingTwoFactor>(vm.uiState.value)
        assertEquals("bad code", before.errorMessage)

        vm.clearError()

        val after = assertIs<LoginUiState.AwaitingTwoFactor>(vm.uiState.value)
        assertEquals("tmp", after.tempToken)
        assertNull(after.errorMessage)
    }
}
