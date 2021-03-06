package model_a;

import java.util.*;
import java.util.stream.Collectors;

@javax.annotation.Generated(value = {"bkm.ftl", "bkm"})
@com.gs.dmn.runtime.annotation.DRGElement(
    namespace = "model_a",
    name = "bkm",
    label = "",
    elementKind = com.gs.dmn.runtime.annotation.DRGElementKind.BUSINESS_KNOWLEDGE_MODEL,
    expressionKind = com.gs.dmn.runtime.annotation.ExpressionKind.LITERAL_EXPRESSION,
    hitPolicy = com.gs.dmn.runtime.annotation.HitPolicy.UNKNOWN,
    rulesCount = -1
)
public class Bkm extends com.gs.dmn.runtime.DefaultDMNBaseDecision {
    public static final com.gs.dmn.runtime.listener.DRGElement DRG_ELEMENT_METADATA = new com.gs.dmn.runtime.listener.DRGElement(
        "model_a",
        "bkm",
        "",
        com.gs.dmn.runtime.annotation.DRGElementKind.BUSINESS_KNOWLEDGE_MODEL,
        com.gs.dmn.runtime.annotation.ExpressionKind.LITERAL_EXPRESSION,
        com.gs.dmn.runtime.annotation.HitPolicy.UNKNOWN,
        -1
    );

    public static final Bkm INSTANCE = new Bkm();

    private Bkm() {
    }

    public static String bkm(java.math.BigDecimal x, com.gs.dmn.runtime.annotation.AnnotationSet annotationSet_, com.gs.dmn.runtime.listener.EventListener eventListener_, com.gs.dmn.runtime.external.ExternalFunctionExecutor externalExecutor_) {
        return INSTANCE.apply(x, annotationSet_, eventListener_, externalExecutor_);
    }

    private String apply(java.math.BigDecimal x, com.gs.dmn.runtime.annotation.AnnotationSet annotationSet_, com.gs.dmn.runtime.listener.EventListener eventListener_, com.gs.dmn.runtime.external.ExternalFunctionExecutor externalExecutor_) {
        try {
            // Start BKM 'bkm'
            long bkmStartTime_ = System.currentTimeMillis();
            com.gs.dmn.runtime.listener.Arguments bkmArguments_ = new com.gs.dmn.runtime.listener.Arguments();
            bkmArguments_.put("x", x);
            eventListener_.startDRGElement(DRG_ELEMENT_METADATA, bkmArguments_);

            // Evaluate BKM 'bkm'
            String output_ = evaluate(x, annotationSet_, eventListener_, externalExecutor_);

            // End BKM 'bkm'
            eventListener_.endDRGElement(DRG_ELEMENT_METADATA, bkmArguments_, output_, (System.currentTimeMillis() - bkmStartTime_));

            return output_;
        } catch (Exception e) {
            logError("Exception caught in 'bkm' evaluation", e);
            return null;
        }
    }

    protected String evaluate(java.math.BigDecimal x, com.gs.dmn.runtime.annotation.AnnotationSet annotationSet_, com.gs.dmn.runtime.listener.EventListener eventListener_, com.gs.dmn.runtime.external.ExternalFunctionExecutor externalExecutor_) {
        return string(x);
    }
}
