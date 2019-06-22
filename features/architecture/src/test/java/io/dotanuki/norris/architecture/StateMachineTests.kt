package io.dotanuki.norris.architecture

import io.dotanuki.coroutines.testutils.CoroutinesTestHelper
import io.dotanuki.coroutines.testutils.CoroutinesTestHelper.Companion.runWithTestScope
import io.dotanuki.coroutines.testutils.collectForTesting
import io.dotanuki.norris.architecture.ViewState.Failed
import io.dotanuki.norris.architecture.ViewState.Loading
import io.dotanuki.norris.architecture.ViewState.Success
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal class StateMachineTests {

    private lateinit var machine: StateMachine<String>

    @get:Rule val helper = CoroutinesTestHelper()

    @Before fun `before each test`() {
        machine = StateMachine(
            container = StateContainer.Unbounded(),
            executor = TaskExecutor.Synchronous(helper.scope)
        )
    }

    @Test fun `should generate states, successful execution`() {
        runWithTestScope(helper.scope) {

            // Given
            val emissions = machine.states().collectForTesting()

            // When
            machine.forward(::successfulExecution).join()

            // Then
            val expectedStates = listOf(
                Loading.FromEmpty,
                Success(MESSAGE)
            )

            assertThat(emissions).isEqualTo(expectedStates)
        }
    }

    @Test fun `should generate states, error execution`() {
        runWithTestScope(helper.scope) {

            // Given
            val emissions = machine.states().collectForTesting()

            // When
            machine.forward(::brokenExecution).join()

            // Then
            val expectedStates = listOf(
                Loading.FromEmpty,
                Failed(ERROR)
            )

            assertThat(emissions).isEqualTo(expectedStates)
        }
    }

    @Test fun `should generate states, with previous execution`() {
        runWithTestScope(helper.scope) {

            // Given
            val emissions = machine.states().collectForTesting()

            // When
            machine.forward(::successfulExecution).join()
            machine.forward(::successfulExecution).join()

            // Then
            val expectedStates = listOf(
                Loading.FromEmpty,
                Success(MESSAGE),
                Loading.FromPrevious(MESSAGE),
                Success(MESSAGE)
            )

            assertThat(emissions).isEqualTo(expectedStates)
        }
    }

    @Test fun `should generate states, ignoring previous broken execution`() {
        runWithTestScope(helper.scope) {

            // Given
            val emissions = machine.states().collectForTesting()

            // When
            machine.forward(::brokenExecution).join()
            machine.forward(::successfulExecution).join()

            // Then
            val expectedStates = listOf(
                Loading.FromEmpty,
                Failed(ERROR),
                Success(MESSAGE)
            )

            assertThat(emissions).isEqualTo(expectedStates)
        }
    }

    private suspend fun successfulExecution(): String {
        return suspendCoroutine { continuation ->
            continuation.resume(MESSAGE)
        }
    }

    private suspend fun brokenExecution(): String {
        return suspendCoroutine { continuation ->
            continuation.resumeWithException(ERROR)
        }
    }

    private companion object {
        const val MESSAGE = "Kotlin is awesome"
        val ERROR = IllegalStateException("Ouch")
    }
}