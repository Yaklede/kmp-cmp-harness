package dev.harness.core

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable

interface PaymentRepository {
    suspend fun submit(request: PaymentRequest): PaymentOutcome
    suspend fun lookup(operationId: String): PaymentOutcome
}

@Serializable
data class PaymentEvent(val atMillis: Long, val revision: Long, val kind: String, val phase: PaymentPhase, val operationId: String?)

data class DispatchReceipt(val accepted: Boolean, val revision: Long, val operationId: String?)

/** Single writer; effects may suspend without allowing a second submission. */
class PaymentStore(
    private val repository: PaymentRepository,
    parentScope: CoroutineScope,
    private val nextId: () -> String,
    private val clockMillis: () -> Long,
    initialState: PaymentState = PaymentState(),
    private val timeoutMillis: Long = 10_000,
) {
    private val job = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + job)
    private data class Envelope(val intent: PaymentIntent? = null, val action: PaymentAction? = null,
        val receipt: CompletableDeferred<DispatchReceipt>? = null)
    private val inbox = Channel<Envelope>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow(initialState.restored())
    val state: StateFlow<PaymentState> = mutableState.asStateFlow()
    private val mutableEvents = MutableStateFlow<List<PaymentEvent>>(emptyList())
    val events: StateFlow<List<PaymentEvent>> = mutableEvents.asStateFlow()

    init {
        require(timeoutMillis > 0)
        scope.launch {
            for (envelope in inbox) {
                val before = state.value
                val action = envelope.action ?: PaymentAction.User(requireNotNull(envelope.intent),
                    if (envelope.intent == PaymentIntent.Submit && before.canSubmit) nextId() else null)
                val transition = PaymentReducer.reduce(before, action)
                mutableState.value = transition.state
                val accepted = before != transition.state
                val kind = if (action is PaymentAction.Completed) "operation.completed" else if (accepted) "intent.accepted" else "intent.ignored"
                mutableEvents.value = (events.value + PaymentEvent(clockMillis(), state.value.revision,
                    kind, state.value.phase, state.value.operationId)).takeLast(256)
                envelope.receipt?.complete(DispatchReceipt(accepted, state.value.revision, state.value.operationId))
                transition.effect?.let { effect -> scope.launch { execute(effect) } }
            }
        }
    }

    suspend fun dispatch(intent: PaymentIntent): DispatchReceipt {
        val result = CompletableDeferred<DispatchReceipt>(job)
        inbox.send(Envelope(intent = intent, receipt = result))
        return result.await()
    }

    private suspend fun execute(effect: PaymentEffect) {
        val id = when (effect) {
            is PaymentEffect.Submit -> effect.request.operationId
            is PaymentEffect.Lookup -> effect.operationId
        }
        val result = try {
            withTimeoutOrNull(timeoutMillis) {
                when (effect) {
                    is PaymentEffect.Submit -> repository.submit(effect.request)
                    is PaymentEffect.Lookup -> repository.lookup(effect.operationId)
                }
            } ?: PaymentOutcome.Unknown
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A transport exception cannot establish that a payment failed.
            PaymentOutcome.Unknown
        }
        inbox.send(Envelope(action = PaymentAction.Completed(id, result, effect is PaymentEffect.Lookup)))
    }

    fun close() { inbox.close(); job.cancel() }
}
