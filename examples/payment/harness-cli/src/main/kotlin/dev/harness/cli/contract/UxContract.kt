package dev.harness.cli.contract

import dev.harness.core.PaymentPhase
import kotlinx.serialization.Serializable

@Serializable enum class Verdict { PASS, FAIL, REVIEW, UNSUPPORTED, NOT_RUN }
@Serializable enum class ChangeClass { A, B, C, D }
@Serializable enum class Platform { Android, Ios, Desktop }
@Serializable enum class ObservationSource { Synthetic, Runtime }

@Serializable
data class UxContract(
    val schemaVersion: Int,
    val screenId: String,
    val pattern: String,
    val requiredInformation: Set<String>,
    val requiredStates: Set<PaymentPhase>,
    val allowResubmitWhenUnknown: Boolean,
    val originalReference: String?,
    val approvedBaselines: Map<String, String> = emptyMap(),
    val unresolvedPolicies: List<String> = emptyList(),
)

@Serializable
data class Finding(
    val ruleId: String,
    val verdict: Verdict,
    val problem: String,
    val changeClass: ChangeClass,
    val evidence: List<String>,
    val acceptanceTest: String,
    val automaticFixAllowed: Boolean = false,
)

object ContractValidator {
    fun validate(contract: UxContract): List<Finding> = buildList {
        fun failure(id: String, problem: String, change: ChangeClass = ChangeClass.D) {
            add(Finding(id, Verdict.FAIL, problem, change, listOf("contract:${contract.screenId}"), "contract.validate"))
        }
        if (contract.schemaVersion != 1) failure("SCHEMA-VERSION", "Unsupported schema version")
        if (contract.screenId.isBlank()) failure("SCREEN-ID", "Screen ID is required")
        if (contract.pattern != "payment_confirmation") failure("PATTERN-UNSUPPORTED", "Only payment_confirmation is implemented")
        val missing = setOf("amount", "recipient", "paymentMethod") - contract.requiredInformation
        if (missing.isNotEmpty()) failure("PAYMENT-INFORMATION", "Missing required information: ${missing.sorted()}")
        if (!contract.requiredStates.containsAll(PaymentPhase.entries)) failure("PAYMENT-STATES", "Every payment outcome must be represented", ChangeClass.B)
        if (contract.allowResubmitWhenUnknown) failure("PAYMENT-UNKNOWN-NO-RESUBMIT", "Unknown outcomes require lookup; retry semantics need a product decision")
        if (contract.unresolvedPolicies.isNotEmpty()) add(Finding("POLICY-UNRESOLVED", Verdict.REVIEW,
            "Unresolved policies: ${contract.unresolvedPolicies.joinToString()}", ChangeClass.D,
            listOf("contract:${contract.screenId}"), "product-policy-review"))
    }
}

@Serializable
data class Bounds(val x: Double, val y: Double, val width: Double, val height: Double) {
    fun valid() = listOf(x, y, width, height).all { it.isFinite() } && width > 0 && height > 0
    fun contains(other: Bounds) = other.x >= x && other.y >= y &&
        other.x + other.width <= x + width && other.y + other.height <= y + height
    fun intersects(other: Bounds) = x < other.x + other.width && x + width > other.x &&
        y < other.y + other.height && y + height > other.y
}

@Serializable
data class UiNode(
    val id: String,
    val text: String,
    val label: String,
    val bounds: Bounds,
    val interactive: Boolean = false,
    val enabled: Boolean = true,
    val clipped: Boolean = false,
    val action: String? = null,
)

/** Bounds are logical dp (Android/Desktop) or pt (iOS), never raw screenshot pixels. */
@Serializable
data class UiObservation(
    val screenId: String,
    val platform: Platform,
    val source: ObservationSource,
    val evidenceId: String,
    val viewport: Bounds,
    val phase: PaymentPhase,
    val nodes: List<UiNode>,
    val occlusions: List<Bounds> = emptyList(),
)

object UxInspector {
    fun inspect(contract: UxContract, observation: UiObservation): List<Finding> = buildList {
        fun finding(id: String, problem: String, change: ChangeClass = ChangeClass.A, auto: Boolean = false) {
            add(Finding(id, Verdict.FAIL, problem, change, listOf(observation.evidenceId), "ui.inspect:$id", auto))
        }
        if (observation.screenId != contract.screenId || observation.evidenceId.isBlank() ||
            !observation.viewport.valid() || observation.occlusions.any { !it.valid() } ||
            observation.nodes.any { !it.bounds.valid() } ||
            observation.nodes.map { it.id }.toSet().size != observation.nodes.size) {
            finding("OBSERVATION-INVALID", "Mismatched screen, missing evidence, duplicate nodes or invalid bounds", ChangeClass.C)
            return@buildList
        }
        for (id in contract.requiredInformation.sorted()) {
            val node = observation.nodes.find { it.id == id }
            if (node == null || node.text.isBlank()) finding("INFORMATION-MISSING", "$id is absent", ChangeClass.C)
            else if (node.clipped || !observation.viewport.contains(node.bounds) || observation.occlusions.any { it.intersects(node.bounds) })
                finding("INFORMATION-CLIPPED", "$id is clipped or obscured")
        }
        val controls = observation.nodes.filter { it.interactive }
        val minSize = if (observation.platform == Platform.Ios) 44.0 else 48.0
        for (node in controls) {
            if (node.label.isBlank()) finding("CONTROL-LABEL", "${node.id} has no accessible label")
            if (node.text.isNotBlank() && !node.label.contains(node.text))
                finding("LABEL-IN-NAME", "${node.id} accessible label does not include its visible text")
            if (node.bounds.width < minSize || node.bounds.height < minSize)
                finding("TOUCH-TARGET", "${node.id} is smaller than $minSize logical units")
            if (node.enabled && (!observation.viewport.contains(node.bounds) || observation.occlusions.any { it.intersects(node.bounds) }))
                finding("ACTION-REACHABLE", "${node.id} is outside the viewport or obscured")
        }
        for ((index, node) in controls.withIndex()) {
            for (other in controls.drop(index + 1)) if (node.bounds.intersects(other.bounds))
                finding("TOUCH-OVERLAP", "${node.id} overlaps ${other.id}", ChangeClass.C)
        }
        if (observation.phase == PaymentPhase.AwaitingConfirmation) {
            if (controls.any { it.enabled && it.action == "payment.submit" })
                finding("PAYMENT-UNKNOWN-NO-RESUBMIT", "Unresolved payment exposes an enabled submit action", ChangeClass.D)
            if (controls.none { it.enabled && it.action == "payment.lookup" })
                finding("PAYMENT-LOOKUP-MISSING", "Unresolved payment has no result lookup action", ChangeClass.B)
        }
    }
}
