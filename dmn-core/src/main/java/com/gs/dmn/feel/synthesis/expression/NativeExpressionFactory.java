/*
 Copyright 2016.
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at
   http://www.apache.org/licenses/LICENSE-2.0
 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.
*/
package com.gs.dmn.feel.synthesis.expression;

import com.gs.dmn.DRGElementReference;
import com.gs.dmn.feel.analysis.semantics.type.FunctionType;
import com.gs.dmn.feel.analysis.semantics.type.ItemDefinitionType;
import com.gs.dmn.feel.analysis.semantics.type.Type;
import com.gs.dmn.feel.analysis.syntax.ast.expression.function.Conversion;
import com.gs.dmn.runtime.Pair;
import org.omg.spec.dmn._20180521.model.TDecision;
import org.omg.spec.dmn._20180521.model.TItemDefinition;

import java.util.List;
import java.util.stream.Collectors;

public interface NativeExpressionFactory {
    //
    // Constructor
    //
    String constructor(String className, String arguments);

    default String defaultConstructor(String className) {
        return constructor(className, "");
    }

    String fluentConstructor(String className, String addMethods);

    String functionalInterfaceConstructor(String functionalInterface, String returnType, String applyMethod);

    //
    // Selection
    //
    String makeItemDefinitionAccessor(String javaType, String source, String memberName);

    String makeItemDefinitionSelectExpression(String source, String memberName, String memberType);

    String makeContextAccessor(String javaType, String source, String memberName);

    String makeCollectionMap(String source, String filter);

    String makeContextSelectExpression(String contextClassName, String source, String memberName);

    //
    // Expressions
    //
    String makeCollectionLogicFilter(String source, String parameterName, String filter);

    String makeCollectionNumericFilter(String javaElementType, String source, String filter);

    String makeIfExpression(String condition, String thenExp, String elseExp);

    String makeForExpression(List<Pair<String, String>> domainIterators, String body);

    String makeSomeExpression(String list);

    String makeEveryExpression(String list);

    //
    // Decision Table aggregators
    //
    String makeMinAggregator(String ruleOutputListVariableName, String decisionRuleOutputClassName, String outputClauseVariableName);

    String makeMaxAggregator(String ruleOutputListVariableName, String decisionRuleOutputClassName, String outputClauseVariableName);

    String makeCountAggregator(String ruleOutputListVariableName);

    String makeSumAggregator(String ruleOutputListVariableName, String decisionRuleOutputClassName, String outputClauseVariableName);

    //
    // Return
    //
    String makeReturn(String expression);

    //
    // Assignment
    //
    String makeVariableAssignment(String type, String name, String expression);

    String makeMemberAssignment(String complexTypeVariable, String memberName, String value);

    String makeContextMemberAssignment(String complexTypeVariable, String memberName, String value);

    //
    // Equality
    //
    String makeEquality(String left, String right);

    //
    // Functions
    //
    String makeApplyInvocation(String javaFunctionCode, String argumentsText);

    String applyMethod(FunctionType functionType, String signature, boolean convertTypeToContext, String body);

    String makeExternalExecutorCall(String externalExecutorVariableName, String className, String methodName, String arguments, String returnJavaType);

    //
    // Parameters
    //
    String nullableParameter(String parameterType, String parameterName);

    String parameter(String parameterType, String parameterName);

    String decisionConstructorParameter(DRGElementReference<TDecision> d);

    //
    // Literal
    //
    String trueConstant();

    String falseConstant();

    //
    // Conversions
    //
    String convertListToElement(String expression, Type type);

    String asList(Type elementType, String exp);

    String asElement(String exp);

    String convertElementToList(String expression, Type type);

    String makeListConversion(String javaExpression, ItemDefinitionType expectedElementType);

    String convertToItemDefinitionType(String expression, ItemDefinitionType type);

    String convertMethodName(TItemDefinition itemDefinition);

    String convertDecisionArgumentFromString(String paramName, Type type);

    String conversionFunction(Conversion conversion, String javaType);

    default List<String> optimizeAndArguments(List<String> conditionParts) {
        List<String> result = conditionParts.stream().filter(c -> !trueConstant().equals(c)).collect(Collectors.toList());
        if (result.size() == 0) {
            result.add(trueConstant());
        }
        return result;
    }
}
