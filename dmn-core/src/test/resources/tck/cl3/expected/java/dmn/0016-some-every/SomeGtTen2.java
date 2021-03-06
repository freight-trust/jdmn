
import java.util.*;
import java.util.stream.Collectors;

@javax.annotation.Generated(value = {"decision.ftl", "someGtTen2"})
@com.gs.dmn.runtime.annotation.DRGElement(
    namespace = "",
    name = "someGtTen2",
    label = "",
    elementKind = com.gs.dmn.runtime.annotation.DRGElementKind.DECISION,
    expressionKind = com.gs.dmn.runtime.annotation.ExpressionKind.LITERAL_EXPRESSION,
    hitPolicy = com.gs.dmn.runtime.annotation.HitPolicy.UNKNOWN,
    rulesCount = -1
)
public class SomeGtTen2 extends com.gs.dmn.runtime.DefaultDMNBaseDecision {
    public static final com.gs.dmn.runtime.listener.DRGElement DRG_ELEMENT_METADATA = new com.gs.dmn.runtime.listener.DRGElement(
        "",
        "someGtTen2",
        "",
        com.gs.dmn.runtime.annotation.DRGElementKind.DECISION,
        com.gs.dmn.runtime.annotation.ExpressionKind.LITERAL_EXPRESSION,
        com.gs.dmn.runtime.annotation.HitPolicy.UNKNOWN,
        -1
    );

    public SomeGtTen2() {
    }

    public Boolean apply(String priceTable2, com.gs.dmn.runtime.annotation.AnnotationSet annotationSet_) {
        try {
            return apply((priceTable2 != null ? com.gs.dmn.serialization.JsonSerializer.OBJECT_MAPPER.readValue(priceTable2, new com.fasterxml.jackson.core.type.TypeReference<List<type.TItemPrice>>() {}) : null), annotationSet_, new com.gs.dmn.runtime.listener.LoggingEventListener(LOGGER), new com.gs.dmn.runtime.external.DefaultExternalFunctionExecutor());
        } catch (Exception e) {
            logError("Cannot apply decision 'SomeGtTen2'", e);
            return null;
        }
    }

    public Boolean apply(String priceTable2, com.gs.dmn.runtime.annotation.AnnotationSet annotationSet_, com.gs.dmn.runtime.listener.EventListener eventListener_, com.gs.dmn.runtime.external.ExternalFunctionExecutor externalExecutor_) {
        try {
            return apply((priceTable2 != null ? com.gs.dmn.serialization.JsonSerializer.OBJECT_MAPPER.readValue(priceTable2, new com.fasterxml.jackson.core.type.TypeReference<List<type.TItemPrice>>() {}) : null), annotationSet_, eventListener_, externalExecutor_);
        } catch (Exception e) {
            logError("Cannot apply decision 'SomeGtTen2'", e);
            return null;
        }
    }

    public Boolean apply(List<type.TItemPrice> priceTable2, com.gs.dmn.runtime.annotation.AnnotationSet annotationSet_) {
        return apply(priceTable2, annotationSet_, new com.gs.dmn.runtime.listener.LoggingEventListener(LOGGER), new com.gs.dmn.runtime.external.DefaultExternalFunctionExecutor());
    }

    public Boolean apply(List<type.TItemPrice> priceTable2, com.gs.dmn.runtime.annotation.AnnotationSet annotationSet_, com.gs.dmn.runtime.listener.EventListener eventListener_, com.gs.dmn.runtime.external.ExternalFunctionExecutor externalExecutor_) {
        try {
            // Start decision 'someGtTen2'
            long someGtTen2StartTime_ = System.currentTimeMillis();
            com.gs.dmn.runtime.listener.Arguments someGtTen2Arguments_ = new com.gs.dmn.runtime.listener.Arguments();
            someGtTen2Arguments_.put("priceTable2", priceTable2);
            eventListener_.startDRGElement(DRG_ELEMENT_METADATA, someGtTen2Arguments_);

            // Evaluate decision 'someGtTen2'
            Boolean output_ = evaluate(priceTable2, annotationSet_, eventListener_, externalExecutor_);

            // End decision 'someGtTen2'
            eventListener_.endDRGElement(DRG_ELEMENT_METADATA, someGtTen2Arguments_, output_, (System.currentTimeMillis() - someGtTen2StartTime_));

            return output_;
        } catch (Exception e) {
            logError("Exception caught in 'someGtTen2' evaluation", e);
            return null;
        }
    }

    protected Boolean evaluate(List<type.TItemPrice> priceTable2, com.gs.dmn.runtime.annotation.AnnotationSet annotationSet_, com.gs.dmn.runtime.listener.EventListener eventListener_, com.gs.dmn.runtime.external.ExternalFunctionExecutor externalExecutor_) {
        return booleanOr((List)priceTable2.stream().map(i -> numericGreaterThan(((java.math.BigDecimal)(i != null ? i.getPrice() : null)), number("10"))).collect(Collectors.toList()));
    }
}
