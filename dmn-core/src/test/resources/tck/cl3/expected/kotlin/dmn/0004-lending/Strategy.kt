
import java.util.*
import java.util.stream.Collectors

@javax.annotation.Generated(value = ["decision.ftl", "Strategy"])
@com.gs.dmn.runtime.annotation.DRGElement(
    namespace = "",
    name = "Strategy",
    label = "",
    elementKind = com.gs.dmn.runtime.annotation.DRGElementKind.DECISION,
    expressionKind = com.gs.dmn.runtime.annotation.ExpressionKind.DECISION_TABLE,
    hitPolicy = com.gs.dmn.runtime.annotation.HitPolicy.UNIQUE,
    rulesCount = 3
)
class Strategy(val bureauCallType : BureauCallType = BureauCallType(), val eligibility : Eligibility = Eligibility()) : com.gs.dmn.runtime.DefaultDMNBaseDecision() {
    fun apply(applicantData: String?, requestedProduct: String?, annotationSet_: com.gs.dmn.runtime.annotation.AnnotationSet): String? {
        return try {
            apply(applicantData?.let({ com.gs.dmn.serialization.JsonSerializer.OBJECT_MAPPER.readValue(it, object : com.fasterxml.jackson.core.type.TypeReference<type.TApplicantDataImpl>() {}) }), requestedProduct?.let({ com.gs.dmn.serialization.JsonSerializer.OBJECT_MAPPER.readValue(it, object : com.fasterxml.jackson.core.type.TypeReference<type.TRequestedProductImpl>() {}) }), annotationSet_, com.gs.dmn.runtime.listener.LoggingEventListener(LOGGER), com.gs.dmn.runtime.external.DefaultExternalFunctionExecutor())
        } catch (e: Exception) {
            logError("Cannot apply decision 'Strategy'", e)
            null
        }
    }

    fun apply(applicantData: String?, requestedProduct: String?, annotationSet_: com.gs.dmn.runtime.annotation.AnnotationSet, eventListener_: com.gs.dmn.runtime.listener.EventListener, externalExecutor_: com.gs.dmn.runtime.external.ExternalFunctionExecutor): String? {
        return try {
            apply(applicantData?.let({ com.gs.dmn.serialization.JsonSerializer.OBJECT_MAPPER.readValue(it, object : com.fasterxml.jackson.core.type.TypeReference<type.TApplicantDataImpl>() {}) }), requestedProduct?.let({ com.gs.dmn.serialization.JsonSerializer.OBJECT_MAPPER.readValue(it, object : com.fasterxml.jackson.core.type.TypeReference<type.TRequestedProductImpl>() {}) }), annotationSet_, eventListener_, externalExecutor_)
        } catch (e: Exception) {
            logError("Cannot apply decision 'Strategy'", e)
            null
        }
    }

    fun apply(applicantData: type.TApplicantData?, requestedProduct: type.TRequestedProduct?, annotationSet_: com.gs.dmn.runtime.annotation.AnnotationSet): String? {
        return apply(applicantData, requestedProduct, annotationSet_, com.gs.dmn.runtime.listener.LoggingEventListener(LOGGER), com.gs.dmn.runtime.external.DefaultExternalFunctionExecutor())
    }

    fun apply(applicantData: type.TApplicantData?, requestedProduct: type.TRequestedProduct?, annotationSet_: com.gs.dmn.runtime.annotation.AnnotationSet, eventListener_: com.gs.dmn.runtime.listener.EventListener, externalExecutor_: com.gs.dmn.runtime.external.ExternalFunctionExecutor): String? {
        try {
            // Start decision 'Strategy'
            val strategyStartTime_ = System.currentTimeMillis()
            val strategyArguments_ = com.gs.dmn.runtime.listener.Arguments()
            strategyArguments_.put("applicantData", applicantData)
            strategyArguments_.put("requestedProduct", requestedProduct)
            eventListener_.startDRGElement(DRG_ELEMENT_METADATA, strategyArguments_)

            // Apply child decisions
            val bureauCallType: String? = this.bureauCallType.apply(applicantData, annotationSet_, eventListener_, externalExecutor_)
            val eligibility: String? = this.eligibility.apply(applicantData, requestedProduct, annotationSet_, eventListener_, externalExecutor_)

            // Evaluate decision 'Strategy'
            val output_: String? = evaluate(bureauCallType, eligibility, annotationSet_, eventListener_, externalExecutor_)

            // End decision 'Strategy'
            eventListener_.endDRGElement(DRG_ELEMENT_METADATA, strategyArguments_, output_, (System.currentTimeMillis() - strategyStartTime_))

            return output_
        } catch (e: Exception) {
            logError("Exception caught in 'Strategy' evaluation", e)
            return null
        }
    }

    private inline fun evaluate(bureauCallType: String?, eligibility: String?, annotationSet_: com.gs.dmn.runtime.annotation.AnnotationSet, eventListener_: com.gs.dmn.runtime.listener.EventListener, externalExecutor_: com.gs.dmn.runtime.external.ExternalFunctionExecutor): String? {
        // Apply rules and collect results
        val ruleOutputList_ = com.gs.dmn.runtime.RuleOutputList()
        ruleOutputList_.add(rule0(bureauCallType, eligibility, annotationSet_, eventListener_, externalExecutor_))
        ruleOutputList_.add(rule1(bureauCallType, eligibility, annotationSet_, eventListener_, externalExecutor_))
        ruleOutputList_.add(rule2(bureauCallType, eligibility, annotationSet_, eventListener_, externalExecutor_))

        // Return results based on hit policy
        var output_: String?
        if (ruleOutputList_.noMatchedRules()) {
            // Default value
            output_ = null
        } else {
            val ruleOutput_: com.gs.dmn.runtime.RuleOutput? = ruleOutputList_.applySingle(com.gs.dmn.runtime.annotation.HitPolicy.UNIQUE)
            output_ = ruleOutput_?.let({ (ruleOutput_ as StrategyRuleOutput).strategy })
        }

        return output_
    }

    @com.gs.dmn.runtime.annotation.Rule(index = 0, annotation = "")
    private fun rule0(bureauCallType: String?, eligibility: String?, annotationSet_: com.gs.dmn.runtime.annotation.AnnotationSet, eventListener_: com.gs.dmn.runtime.listener.EventListener, externalExecutor_: com.gs.dmn.runtime.external.ExternalFunctionExecutor): com.gs.dmn.runtime.RuleOutput {
        // Rule metadata
        val drgRuleMetadata: com.gs.dmn.runtime.listener.Rule = com.gs.dmn.runtime.listener.Rule(0, "")

        // Rule start
        eventListener_.startRule(DRG_ELEMENT_METADATA, drgRuleMetadata)

        // Apply rule
        var output_: StrategyRuleOutput = StrategyRuleOutput(false)
        if (true == (stringEqual(eligibility, "INELIGIBLE"))) {
            // Rule match
            eventListener_.matchRule(DRG_ELEMENT_METADATA, drgRuleMetadata)

            // Compute output
            output_.setMatched(true)
            output_.strategy = "DECLINE"

            // Add annotation
            annotationSet_.addAnnotation("Strategy", 0, "")
        }

        // Rule end
        eventListener_.endRule(DRG_ELEMENT_METADATA, drgRuleMetadata, output_)

        return output_
    }

    @com.gs.dmn.runtime.annotation.Rule(index = 1, annotation = "")
    private fun rule1(bureauCallType: String?, eligibility: String?, annotationSet_: com.gs.dmn.runtime.annotation.AnnotationSet, eventListener_: com.gs.dmn.runtime.listener.EventListener, externalExecutor_: com.gs.dmn.runtime.external.ExternalFunctionExecutor): com.gs.dmn.runtime.RuleOutput {
        // Rule metadata
        val drgRuleMetadata: com.gs.dmn.runtime.listener.Rule = com.gs.dmn.runtime.listener.Rule(1, "")

        // Rule start
        eventListener_.startRule(DRG_ELEMENT_METADATA, drgRuleMetadata)

        // Apply rule
        var output_: StrategyRuleOutput = StrategyRuleOutput(false)
        if (true == booleanAnd(
            (stringEqual(eligibility, "ELIGIBLE")),
            booleanOr((stringEqual(bureauCallType, "FULL")), (stringEqual(bureauCallType, "MINI")))
        )) {
            // Rule match
            eventListener_.matchRule(DRG_ELEMENT_METADATA, drgRuleMetadata)

            // Compute output
            output_.setMatched(true)
            output_.strategy = "BUREAU"

            // Add annotation
            annotationSet_.addAnnotation("Strategy", 1, "")
        }

        // Rule end
        eventListener_.endRule(DRG_ELEMENT_METADATA, drgRuleMetadata, output_)

        return output_
    }

    @com.gs.dmn.runtime.annotation.Rule(index = 2, annotation = "")
    private fun rule2(bureauCallType: String?, eligibility: String?, annotationSet_: com.gs.dmn.runtime.annotation.AnnotationSet, eventListener_: com.gs.dmn.runtime.listener.EventListener, externalExecutor_: com.gs.dmn.runtime.external.ExternalFunctionExecutor): com.gs.dmn.runtime.RuleOutput {
        // Rule metadata
        val drgRuleMetadata: com.gs.dmn.runtime.listener.Rule = com.gs.dmn.runtime.listener.Rule(2, "")

        // Rule start
        eventListener_.startRule(DRG_ELEMENT_METADATA, drgRuleMetadata)

        // Apply rule
        var output_: StrategyRuleOutput = StrategyRuleOutput(false)
        if (true == booleanAnd(
            (stringEqual(eligibility, "ELIGIBLE")),
            (stringEqual(bureauCallType, "NONE"))
        )) {
            // Rule match
            eventListener_.matchRule(DRG_ELEMENT_METADATA, drgRuleMetadata)

            // Compute output
            output_.setMatched(true)
            output_.strategy = "THROUGH"

            // Add annotation
            annotationSet_.addAnnotation("Strategy", 2, "")
        }

        // Rule end
        eventListener_.endRule(DRG_ELEMENT_METADATA, drgRuleMetadata, output_)

        return output_
    }


    companion object {
        val DRG_ELEMENT_METADATA : com.gs.dmn.runtime.listener.DRGElement = com.gs.dmn.runtime.listener.DRGElement(
            "",
            "Strategy",
            "",
            com.gs.dmn.runtime.annotation.DRGElementKind.DECISION,
            com.gs.dmn.runtime.annotation.ExpressionKind.DECISION_TABLE,
            com.gs.dmn.runtime.annotation.HitPolicy.UNIQUE,
            3
        )
    }
}
