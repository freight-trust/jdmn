/*
 * Copyright 2016 Goldman Sachs.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.gs.dmn.transformation.basic;

import com.gs.dmn.DMNModelRepository;
import com.gs.dmn.DRGElementFilter;
import com.gs.dmn.DRGElementReference;
import com.gs.dmn.feel.analysis.semantics.environment.Environment;
import com.gs.dmn.feel.analysis.semantics.environment.EnvironmentFactory;
import com.gs.dmn.feel.analysis.semantics.type.*;
import com.gs.dmn.feel.analysis.syntax.ast.expression.Expression;
import com.gs.dmn.feel.analysis.syntax.ast.expression.function.FormalParameter;
import com.gs.dmn.feel.analysis.syntax.ast.expression.function.FunctionDefinition;
import com.gs.dmn.feel.lib.StringEscapeUtil;
import com.gs.dmn.feel.synthesis.FEELTranslator;
import com.gs.dmn.feel.synthesis.FEELTranslatorImpl;
import com.gs.dmn.feel.synthesis.expression.JavaExpressionFactory;
import com.gs.dmn.feel.synthesis.expression.NativeExpressionFactory;
import com.gs.dmn.feel.synthesis.type.NativeTypeFactory;
import com.gs.dmn.runtime.*;
import com.gs.dmn.runtime.annotation.AnnotationSet;
import com.gs.dmn.runtime.annotation.DRGElementKind;
import com.gs.dmn.runtime.annotation.ExpressionKind;
import com.gs.dmn.runtime.annotation.HitPolicy;
import com.gs.dmn.runtime.cache.Cache;
import com.gs.dmn.runtime.cache.DefaultCache;
import com.gs.dmn.runtime.external.DefaultExternalFunctionExecutor;
import com.gs.dmn.runtime.external.ExternalFunctionExecutor;
import com.gs.dmn.runtime.external.JavaExternalFunction;
import com.gs.dmn.runtime.interpreter.ImportPath;
import com.gs.dmn.runtime.listener.Arguments;
import com.gs.dmn.runtime.listener.EventListener;
import com.gs.dmn.runtime.listener.LoggingEventListener;
import com.gs.dmn.runtime.listener.NopEventListener;
import com.gs.dmn.serialization.DMNConstants;
import com.gs.dmn.transformation.DMNToJavaTransformer;
import com.gs.dmn.transformation.InputParamUtil;
import com.gs.dmn.transformation.java.CompoundStatement;
import com.gs.dmn.transformation.java.ExpressionStatement;
import com.gs.dmn.transformation.java.Statement;
import com.gs.dmn.transformation.lazy.LazyEvaluationDetector;
import com.gs.dmn.transformation.lazy.LazyEvaluationOptimisation;
import org.apache.commons.lang3.StringUtils;
import org.omg.spec.dmn._20180521.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.JAXBElement;
import java.util.*;
import java.util.stream.Collectors;

public class BasicDMN2JavaTransformer implements BasicDMNToNativeTransformer {
    protected static final Logger LOGGER = LoggerFactory.getLogger(BasicDMN2JavaTransformer.class);

    protected final DMNModelRepository dmnModelRepository;
    protected final EnvironmentFactory environmentFactory;
    protected final NativeTypeFactory nativeTypeFactory;
    private final LazyEvaluationDetector lazyEvaluationDetector;

    private final String javaRootPackage;
    private final boolean onePackage;
    private final boolean caching;
    private final Integer cachingThreshold;
    private final boolean singletonInputData;
    private final boolean parallelStream;

    private final LazyEvaluationOptimisation lazyEvaluationOptimisation;
    private final Set<String> cachedElements;

    protected DMNEnvironmentFactory dmnEnvironmentFactory;
    protected NativeExpressionFactory nativeExpressionFactory;
    protected FEELTranslator feelTranslator;
    protected DMNExpressionToNativeTransformer expressionToNativeTransformer;
    protected final DRGElementFilter drgElementFilter;
    protected final JavaTypeMemoizer nativeTypeMemoizer;

    public BasicDMN2JavaTransformer(DMNModelRepository dmnModelRepository, EnvironmentFactory environmentFactory, NativeTypeFactory nativeTypeFactory, LazyEvaluationDetector lazyEvaluationDetector, Map<String, String> inputParameters) {
        this.dmnModelRepository = dmnModelRepository;
        this.environmentFactory = environmentFactory;
        this.nativeTypeFactory = nativeTypeFactory;
        this.lazyEvaluationDetector = lazyEvaluationDetector;

        // Configuration
        this.javaRootPackage = InputParamUtil.getOptionalParam(inputParameters, "javaRootPackage");
        boolean onePackageDefault = dmnModelRepository.getAllDefinitions().size() == 1;
        this.onePackage = InputParamUtil.getOptionalBooleanParam(inputParameters, "onePackage", "" + onePackageDefault);
        this.caching = InputParamUtil.getOptionalBooleanParam(inputParameters, "caching");
        String cachingThresholdParam = InputParamUtil.getOptionalParam(inputParameters, "cachingThreshold", "1");
        this.cachingThreshold = Integer.parseInt(cachingThresholdParam);
        this.singletonInputData = InputParamUtil.getOptionalBooleanParam(inputParameters, "singletonInputData", "true");
        this.parallelStream = InputParamUtil.getOptionalBooleanParam(inputParameters, "parallelStream", "false");

        // Derived data
        this.lazyEvaluationOptimisation = this.lazyEvaluationDetector.detect(this.dmnModelRepository);
        this.cachedElements = this.dmnModelRepository.computeCachedElements(this.caching, this.cachingThreshold);

        // Helpers
        setNativeExpressionFactory(this);
        setFEELTranslator(this);
        setDMNEnvironmentFactory(this);
        setExpressionToNativeTransformer(this);

        this.drgElementFilter = new DRGElementFilter(this.singletonInputData);
        this.nativeTypeMemoizer = new JavaTypeMemoizer();
    }

    protected void setDMNEnvironmentFactory(BasicDMNToNativeTransformer transformer) {
        this.dmnEnvironmentFactory = new StandardDMNEnvironmentFactory(transformer);
    }

    protected void setNativeExpressionFactory(BasicDMNToNativeTransformer transformer) {
        this.nativeExpressionFactory = new JavaExpressionFactory(transformer);
    }

    private void setExpressionToNativeTransformer(BasicDMNToNativeTransformer transformer) {
        this.expressionToNativeTransformer = new DMNExpressionToNativeTransformer(transformer);
    }

    private void setFEELTranslator(BasicDMNToNativeTransformer transformer) {
        this.feelTranslator = new FEELTranslatorImpl(transformer);
    }

    @Override
    public DMNModelRepository getDMNModelRepository() {
        return this.dmnModelRepository;
    }

    @Override
    public EnvironmentFactory getEnvironmentFactory() {
        return this.environmentFactory;
    }

    @Override
    public DMNEnvironmentFactory getDMNEnvironmentFactory() {
        return this.dmnEnvironmentFactory;
    }

    @Override
    public FEELTranslator getFEELTranslator() {
        return this.feelTranslator;
    }

    @Override
    public NativeTypeFactory getNativeTypeFactory() {
        return this.nativeTypeFactory;
    }

    @Override
    public NativeExpressionFactory getNativeExpressionFactory() {
        return this.nativeExpressionFactory;
    }

    public DMNExpressionToNativeTransformer getExpressionToNativeTransformer() {
        return expressionToNativeTransformer;
    }

    @Override
    public DRGElementFilter getDrgElementFilter() {
        return this.drgElementFilter;
    }

    //
    // Configuration
    //
    @Override
    public boolean isOnePackage() {
        return this.onePackage;
    }

    @Override
    public boolean isSingletonInputData() {
        return this.singletonInputData;
    }

    //
    // TItemDefinition related functions
    //
    @Override
    public boolean isComplexType(TItemDefinition itemDefinition) {
        Type type = toFEELType(itemDefinition);
        return type instanceof ItemDefinitionType
                || type instanceof ListType && ((ListType) type).getElementType() instanceof ItemDefinitionType;
    }

    @Override
    public String itemDefinitionNativeSimpleInterfaceName(TItemDefinition itemDefinition) {
        Type type = toFEELType(itemDefinition);
        // ItemDefinition can be a complex type with isCollection = true
        if (type instanceof ListType && ((ListType) type).getElementType() instanceof ItemDefinitionType) {
            type = ((ListType) type).getElementType();
        }
        if (type instanceof ItemDefinitionType) {
            return upperCaseFirst(((ItemDefinitionType) type).getName());
        } else {
            throw new IllegalArgumentException(String.format("Unexpected type '%s' for ItemDefinition '%s'", type, itemDefinition.getName()));
        }
    }

    @Override
    public String itemDefinitionNativeSimpleInterfaceName(String className) {
        return className.substring(0, className.length() - "Impl".length());
    }

    @Override
    public String itemDefinitionNativeClassName(String interfaceName) {
        return interfaceName + "Impl";
    }

    @Override
    public String itemDefinitionNativeQualifiedInterfaceName(TItemDefinition itemDefinition) {
        Type type = toFEELType(itemDefinition);
        return toNativeType(type);
    }

    @Override
    public String itemDefinitionVariableName(TItemDefinition itemDefinition) {
        String name = itemDefinition.getName();
        if (StringUtils.isBlank(name)) {
            throw new DMNRuntimeException(String.format("Variable name cannot be null. ItemDefinition id '%s'", itemDefinition.getId()));
        }
        return lowerCaseFirst(name);
    }

    @Override
    public String itemDefinitionSignature(TItemDefinition itemDefinition) {
        List<Pair<String, String>> parameters = new ArrayList<>();
        List<TItemDefinition> itemComponents = itemDefinition.getItemComponent();
        this.dmnModelRepository.sortNamedElements(itemComponents);
        for (TItemDefinition child : itemComponents) {
            parameters.add(new Pair<>(itemDefinitionVariableName(child), itemDefinitionNativeQualifiedInterfaceName(child)));
        }
        return parameters.stream().map(p -> this.nativeExpressionFactory.nullableParameter(p.getRight(), p.getLeft())).collect(Collectors.joining(", "));
    }

    @Override
    public String getter(TItemDefinition itemDefinition) {
        return getter(itemDefinitionVariableName(itemDefinition));
    }

    @Override
    public String setter(TItemDefinition itemDefinition) {
        return setter(itemDefinitionVariableName(itemDefinition));
    }

    //
    // TInformationItem related functions
    //
    @Override
    public String informationItemTypeName(TBusinessKnowledgeModel bkm, TInformationItem element) {
        TDefinitions model = this.dmnModelRepository.getModel(bkm);
        Type type = toFEELType(model, QualifiedName.toQualifiedName(model, element.getTypeRef()));
        return toNativeType(type);
    }

    @Override
    public String informationItemVariableName(TInformationItem element) {
        String name = element.getName();
        return lowerCaseFirst(name);
    }

    @Override
    public String parameterVariableName(TInformationItem element) {
        String name = element.getName();
        if (name == null) {
            throw new DMNRuntimeException(String.format("Parameter name cannot be null. Parameter id '%s'", element.getId()));
        }
        return lowerCaseFirst(name);
    }

    @Override
    public String defaultConstructor(String className) {
        return this.nativeExpressionFactory.constructor(className, "");
    }

    @Override
    public String constructor(String className, String arguments) {
        return this.nativeExpressionFactory.constructor(className, arguments);
    }

    //
    // DRGElement related functions
    //
    @Override
    public boolean hasListType(TDRGElement element) {
        Type feelType = drgElementOutputFEELType(element);
        return feelType instanceof ListType;
    }

    @Override
    public String drgElementClassName(TDRGElement element) {
        return upperCaseFirst(element.getName());
    }

    @Override
    public String drgElementVariableName(DRGElementReference<? extends TDRGElement> reference) {
        String name = reference.getElementName();
        if (name == null) {
            throw new DMNRuntimeException(String.format("Variable name cannot be null. Decision id '%s'", reference.getElement().getId()));
        }
        return drgReferenceQualifiedName(reference);
    }

    @Override
    public String drgElementVariableName(TDRGElement element) {
        String name = element.getName();
        if (name == null) {
            throw new DMNRuntimeException(String.format("Variable name cannot be null. Decision id '%s'", element.getId()));
        }
        return lowerCaseFirst(name);
    }

    @Override
    public String drgElementOutputType(DRGElementReference<? extends TDRGElement> reference) {
        return drgElementOutputType(reference.getElement());
    }

    @Override
    public String drgElementOutputType(TDRGElement element) {
        Type type = drgElementOutputFEELType(element);
        return toNativeType(type);
    }

    @Override
    public String drgElementOutputClassName(TDRGElement element) {
        Type type = drgElementOutputFEELType(element);
        if (type instanceof ListType) {
            type = ((ListType) type).getElementType();
        }
        return toNativeType(type);
    }

    @Override
    public Type drgElementOutputFEELType(TDRGElement element) {
        return this.dmnEnvironmentFactory.drgElementOutputFEELType(element);
    }

    @Override
    public Type drgElementOutputFEELType(TDRGElement element, Environment environment) {
        return this.dmnEnvironmentFactory.drgElementOutputFEELType(element, environment);
    }

    @Override
    public Type drgElementVariableFEELType(TDRGElement element) {
        return this.dmnEnvironmentFactory.drgElementVariableFEELType(element);
    }

    @Override
    public Type drgElementVariableFEELType(TDRGElement element, Environment environment) {
        return this.dmnEnvironmentFactory.drgElementVariableFEELType(element, environment);
    }

    @Override
    public String annotation(TDRGElement element, String description) {
        if (StringUtils.isBlank(description)) {
            return "\"\"";
        }
        try {
            Environment environment = makeEnvironment(element);
            Statement statement = this.expressionToNativeTransformer.literalExpressionToNative(element, description, environment);
            return ((ExpressionStatement)statement).getExpression();
        } catch (Exception e) {
            LOGGER.warn(String.format("Cannot process description '%s' for element '%s'", description, element == null ? "" : element.getName()));
            return String.format("\"%s\"", description.replaceAll("\"", "\\\\\""));
        }
    }

    @Override
    public String drgElementSignature(TDRGElement element) {
        DRGElementReference<? extends TDRGElement> reference = this.dmnModelRepository.makeDRGElementReference(element);
        return drgElementSignature(reference);
    }

    @Override
    public String drgElementSignature(DRGElementReference<? extends TDRGElement> reference) {
        TDRGElement element = reference.getElement();
        if (element instanceof TBusinessKnowledgeModel) {
            List<Pair<String, String>> parameters = bkmParameters((DRGElementReference<TBusinessKnowledgeModel>) reference);
            String signature = parameters.stream().map(p -> this.nativeExpressionFactory.nullableParameter(p.getRight(), p.getLeft())).collect(Collectors.joining(", "));
            return augmentSignature(signature);
        } else if (element instanceof TDecision) {
            List<Pair<String, Type>> parameters = inputDataParametersClosure((DRGElementReference<TDecision>) reference);
            String decisionSignature = parameters.stream().map(p -> this.nativeExpressionFactory.nullableParameter(toNativeType(p.getRight()), p.getLeft())).collect(Collectors.joining(", "));
            return augmentSignature(decisionSignature);
        } else {
            throw new DMNRuntimeException(String.format("No supported yet '%s'", element.getClass().getSimpleName()));
        }
    }

    @Override
    public String drgElementArgumentList(TDRGElement element) {
        DRGElementReference<? extends TDRGElement> reference = this.dmnModelRepository.makeDRGElementReference(element);
        return drgElementArgumentList(reference);
    }

    @Override
    public String drgElementArgumentList(DRGElementReference<? extends TDRGElement> reference) {
        TDRGElement element = reference.getElement();
        if (element instanceof TBusinessKnowledgeModel) {
            List<Pair<String, String>> parameters = bkmParameters((DRGElementReference<TBusinessKnowledgeModel>) reference);
            String arguments = parameters.stream().map(p -> String.format("%s", p.getLeft())).collect(Collectors.joining(", "));
            return augmentArgumentList(arguments);
        } else if (element instanceof TDecision) {
            List<Pair<String, Type>> parameters = inputDataParametersClosure((DRGElementReference<TDecision>) reference);
            String arguments = parameters.stream().map(p -> String.format("%s", p.getLeft())).collect(Collectors.joining(", "));
            return augmentArgumentList(arguments);
        } else {
            throw new DMNRuntimeException(String.format("No supported yet '%s'", element.getClass().getSimpleName()));
        }
    }

    @Override
    public String drgElementConvertedArgumentList(TDRGElement element) {
        DRGElementReference<? extends TDRGElement> reference = this.dmnModelRepository.makeDRGElementReference(element);
        return drgElementConvertedArgumentList(reference);
    }

    @Override
    public String drgElementConvertedArgumentList(DRGElementReference<? extends TDRGElement> reference) {
        TDRGElement element = reference.getElement();
        if (element instanceof TBusinessKnowledgeModel) {
            List<Pair<String, String>> parameters = bkmParameters((DRGElementReference<TBusinessKnowledgeModel>) reference);
            String arguments = parameters.stream().map(p -> String.format("%s", p.getLeft())).collect(Collectors.joining(", "));
            return augmentArgumentList(arguments);
        } else if (element instanceof TDecision) {
            List<Pair<String, Type>> parameters = inputDataParametersClosure((DRGElementReference<TDecision>) reference);
            String arguments = parameters.stream().map(p -> String.format("%s", convertDecisionArgument(p.getLeft(), p.getRight()))).collect(Collectors.joining(", "));
            return augmentArgumentList(arguments);
        } else {
            throw new DMNRuntimeException(String.format("No supported yet '%s'", element.getClass().getSimpleName()));
        }
    }

    @Override
    public List<String> drgElementArgumentNameList(TDRGElement element) {
        DRGElementReference<? extends TDRGElement> reference = this.dmnModelRepository.makeDRGElementReference(element);
        return drgElementArgumentNameList(reference);
    }

    @Override
    public List<String> drgElementArgumentNameList(DRGElementReference<? extends TDRGElement> reference) {
        return drgElementArgumentNameList(reference, true);
    }

    @Override
    public List<String> drgElementArgumentNameList(DRGElementReference<? extends TDRGElement> reference, boolean nativeFriendlyName) {
        TDRGElement element = reference.getElement();
        if (element instanceof TBusinessKnowledgeModel) {
            List<Pair<String, String>> parameters = bkmParameters((DRGElementReference<TBusinessKnowledgeModel>) reference, nativeFriendlyName);
            return parameters.stream().map(Pair::getLeft).collect(Collectors.toList());
        } else if (element instanceof TDecisionService) {
            List<Pair<String, Type>> parameters = dsParameters((DRGElementReference<TDecisionService>) reference, nativeFriendlyName);
            return parameters.stream().map(Pair::getLeft).collect(Collectors.toList());
        } else if (element instanceof TDecision) {
            List<Pair<String, Type>> parameters = inputDataParametersClosure((DRGElementReference<TDecision>) reference, nativeFriendlyName);
            return parameters.stream().map(Pair::getLeft).collect(Collectors.toList());
        } else {
            throw new DMNRuntimeException(String.format("No supported yet '%s'", element.getClass().getSimpleName()));
        }
    }

    @Override
    public boolean shouldGenerateApplyWithConversionFromString(TDRGElement element) {
        if (element instanceof TDecision) {
            List<Pair<String, Type>> parameters = inputDataParametersClosure(this.dmnModelRepository.makeDRGElementReference((TDecision) element));
            return parameters.stream().anyMatch(p -> p.getRight() != StringType.STRING);
        } else if (element instanceof TBusinessKnowledgeModel) {
            return false;
        } else {
            throw new DMNRuntimeException(String.format("No supported yet '%s'", element.getClass().getSimpleName()));
        }
    }

    @Override
    public String drgElementSignatureExtraCacheWithConversionFromString(TDRGElement element) {
        return drgElementSignatureExtraCache(drgElementSignatureExtraWithConversionFromString(element));
    }

    @Override
    public String drgElementSignatureExtraWithConversionFromString(TDRGElement element) {
        return drgElementSignatureExtra(drgElementSignatureWithConversionFromString(element));
    }

    @Override
    public String drgElementSignatureWithConversionFromString(TDRGElement element) {
        if (element instanceof TDecision) {
            List<Pair<String, Type>> parameters = inputDataParametersClosure(this.dmnModelRepository.makeDRGElementReference((TDecision) element));
            String decisionSignature = parameters.stream().map(p -> this.nativeExpressionFactory.nullableParameter(toStringNativeType(p.getRight()), p.getLeft())).collect(Collectors.joining(", "));
            return augmentSignature(decisionSignature);
        } else {
            throw new DMNRuntimeException(String.format("No supported yet '%s'", element.getClass().getSimpleName()));
        }
    }

    @Override
    public String drgElementArgumentsExtraCacheWithConversionFromString(TDRGElement element) {
        String arguments = drgElementArgumentsExtraWithConversionFromString(element);
        return drgElementArgumentsExtraCache(arguments);
    }

    private String drgElementArgumentsExtraWithConversionFromString(TDRGElement element) {
        String arguments = drgElementArgumentListWithConversionFromString(element);
        return drgElementArgumentsExtra(arguments);
    }

    @Override
    public String drgElementArgumentsExtraCacheWithConvertedArgumentList(TDRGElement element) {
        String arguments = drgElementArgumentsExtraWithConvertedArgumentList(element);
        return drgElementArgumentsExtraCache(arguments);
    }

    private String drgElementArgumentsExtraWithConvertedArgumentList(TDRGElement element) {
        String arguments = drgElementConvertedArgumentList(element);
        return drgElementArgumentsExtra(arguments);
    }

    @Override
    public String drgElementDefaultArgumentsExtraCacheWithConversionFromString(TDRGElement element) {
        String arguments = drgElementDefaultArgumentsExtraWithConversionFromString(element);
        return drgElementDefaultArgumentsExtraCache(arguments);
    }

    private String drgElementDefaultArgumentsExtraWithConversionFromString(TDRGElement element) {
        String arguments = drgElementArgumentListWithConversionFromString(element);
        return drgElementDefaultArgumentsExtra(arguments);
    }

    @Override
    public String drgElementDefaultArgumentsExtraCache(TDRGElement element) {
        String arguments = drgElementDefaultArgumentsExtra(element);
        return drgElementDefaultArgumentsExtraCache(arguments);
    }

    private String drgElementDefaultArgumentsExtra(TDRGElement element) {
        String arguments = drgElementArgumentList(element);
        return drgElementDefaultArgumentsExtra(arguments);
    }

    @Override
    public String drgElementArgumentListWithConversionFromString(TDRGElement element) {
        if (element instanceof TDecision) {
            List<Pair<String, Type>> parameters = inputDataParametersClosure(this.dmnModelRepository.makeDRGElementReference((TDecision) element));
            String arguments = parameters.stream().map(p -> String.format("%s", this.nativeExpressionFactory.convertDecisionArgumentFromString(p.getLeft(), p.getRight()))).collect(Collectors.joining(", "));
            return augmentArgumentList(arguments);
        } else {
            throw new DMNRuntimeException(String.format("No supported yet '%s'", element.getClass().getSimpleName()));
        }
    }

    @Override
    public String decisionConstructorSignature(TDecision decision) {
        List<DRGElementReference<TDecision>> subDecisionReferences = this.dmnModelRepository.directSubDecisions(decision);
        this.dmnModelRepository.sortNamedElementReferences(subDecisionReferences);
        return subDecisionReferences.stream().map(d -> this.nativeExpressionFactory.decisionConstructorParameter(d)).collect(Collectors.joining(", "));
    }

    @Override
    public String decisionConstructorNewArgumentList(TDecision decision) {
        List<DRGElementReference<TDecision>> subDecisionReferences = this.dmnModelRepository.directSubDecisions(decision);
        this.dmnModelRepository.sortNamedElementReferences(subDecisionReferences);
        return subDecisionReferences
                .stream()
                .map(d -> String.format("%s", defaultConstructor(qualifiedName(d))))
                .collect(Collectors.joining(", "));
    }

    @Override
    public boolean hasDirectSubDecisions(TDecision decision) {
        return !this.dmnModelRepository.directSubDecisions(decision).isEmpty();
    }

    //
    // Evaluate method related functions
    //
    @Override
    public String drgElementEvaluateSignature(TDRGElement element) {
        return drgElementSignatureExtraCache(drgElementSignatureExtra(drgElementDirectSignature(element)));
    }

    @Override
    public String drgElementEvaluateArgumentList(TDRGElement element) {
        return drgElementArgumentsExtraCache(drgElementArgumentsExtra(drgElementDirectArgumentList(element)));
    }

    protected String drgElementDirectSignature(TDRGElement element) {
        if (element instanceof TDecision) {
            List<Pair<String, String>> parameters = directInformationRequirementParameters(element);
            String javaParameters = parameters.stream().map(p -> this.nativeExpressionFactory.nullableParameter(p.getRight(), p.getLeft())).collect(Collectors.joining(", "));
            return augmentSignature(javaParameters);
        } else if (element instanceof TBusinessKnowledgeModel) {
            return drgElementSignature(this.dmnModelRepository.makeDRGElementReference(element));
        } else {
            throw new DMNRuntimeException(String.format("No supported yet '%s'", element.getClass().getSimpleName()));
        }
    }

    protected String drgElementDirectArgumentList(TDRGElement element) {
        if (element instanceof TDecision) {
            List<Pair<String, String>> parameters = directInformationRequirementParameters(element);
            String argumentList = parameters.stream().map(p -> String.format("%s", p.getLeft())).collect(Collectors.joining(", "));
            return augmentArgumentList(argumentList);
        } else if (element instanceof TBusinessKnowledgeModel) {
            return drgElementArgumentList(this.dmnModelRepository.makeDRGElementReference(element));
        } else {
            throw new DMNRuntimeException(String.format("No supported yet '%s'", element.getClass().getSimpleName()));
        }
    }

    private List<Pair<String, String>> directInformationRequirementParameters(TDRGElement element) {
        List<DRGElementReference<? extends TDRGElement>> inputs = directInformationRequirements(element);
        this.dmnModelRepository.sortNamedElementReferences(inputs);

        List<Pair<String, String>> parameters = new ArrayList<>();
        for (DRGElementReference<? extends TDRGElement> reference : inputs) {
            TDRGElement input = reference.getElement();
            if (input instanceof TInputData) {
                TInputData inputData = (TInputData) input;
                String parameterName = inputDataVariableName(reference);
                String parameterNativeType = inputDataType(inputData);
                parameters.add(new Pair<>(parameterName, parameterNativeType));
            } else if (input instanceof TDecision) {
                TDecision subDecision = (TDecision) input;
                String parameterName = drgElementVariableName(reference);
                String parameterNativeType = drgElementOutputType(subDecision);
                parameters.add(new Pair<>(parameterName, lazyEvaluationType(input, parameterNativeType)));
            } else {
                throw new UnsupportedOperationException(String.format("'%s' is not supported yet", input.getClass().getSimpleName()));
            }
        }
        return parameters;
    }

    protected List<DRGElementReference<? extends TDRGElement>> directInformationRequirements(TDRGElement element) {
        List<DRGElementReference<TInputData>> directInputReferences = this.dmnModelRepository.directInputDatas(element);
        List<DRGElementReference<TDecision>> directSubDecisionsReferences = this.dmnModelRepository.directSubDecisions(element);

        List<DRGElementReference<? extends TDRGElement>> inputs = new ArrayList<>();
        inputs.addAll(directInputReferences);
        inputs.addAll(directSubDecisionsReferences);
        return inputs;
    }

    //
    // Comment related functions
    //
    @Override
    public String startElementCommentText(TDRGElement element) {
        if (element instanceof TDecision) {
            return String.format("Start decision '%s'", element.getName());
        } else if (element instanceof TBusinessKnowledgeModel) {
            return String.format("Start BKM '%s'", element.getName());
        } else {
            throw new DMNRuntimeException(String.format("No supported yet '%s'", element.getClass().getSimpleName()));
        }
    }

    @Override
    public String endElementCommentText(TDRGElement element) {
        if (element instanceof TDecision) {
            return String.format("End decision '%s'", element.getName());
        } else if (element instanceof TBusinessKnowledgeModel) {
            return String.format("End BKM '%s'", element.getName());
        } else {
            throw new DMNRuntimeException(String.format("No supported yet '%s'", element.getClass().getSimpleName()));
        }
    }

    @Override
    public String evaluateElementCommentText(TDRGElement element) {
        if (element instanceof TDecision) {
            return String.format("Evaluate decision '%s'", element.getName());
        } else if (element instanceof TBusinessKnowledgeModel) {
            return String.format("Evaluate BKM '%s'", element.getName());
        } else {
            throw new DMNRuntimeException(String.format("No supported yet '%s'", element.getClass().getSimpleName()));
        }
    }

    @Override
    public QualifiedName drgElementOutputTypeRef(TDRGElement element) {
        TDefinitions model = this.dmnModelRepository.getModel(element);
        QualifiedName typeRef = this.dmnModelRepository.outputTypeRef(model, element);
        if (typeRef == null) {
            throw new DMNRuntimeException(String.format("Cannot infer return type for BKM '%s'", element.getName()));
        }
        return typeRef;
    }

    //
    // InputData related functions
    //
    @Override
    public String inputDataVariableName(DRGElementReference<? extends TDRGElement> reference) {
        String name = reference.getElementName();
        if (name == null) {
            throw new DMNRuntimeException(String.format("Variable name cannot be null. InputData id '%s'", reference.getElement().getId()));
        }
        return drgReferenceQualifiedName(reference);
    }

    @Override
    public String inputDataVariableName(TInputData inputData) {
        String name = inputData.getName();
        if (name == null) {
            throw new DMNRuntimeException(String.format("Variable name cannot be null. InputData id '%s'", inputData.getId()));
        }
        String modelName = this.dmnModelRepository.getModelName(inputData);
        return drgReferenceQualifiedName(new ImportPath(), modelName, name);
    }

    @Override
    public Type toFEELType(TInputData inputData) {
        return this.dmnEnvironmentFactory.toFEELType(inputData);
    }

    private String inputDataType(TInputData inputData) {
        Type type = toFEELType(inputData);
        return toNativeType(type);
    }

    //
    // Invocable  related functions
    //
    @Override
    public List<FormalParameter> invocableFEELParameters(TDRGElement invocable) {
        if (invocable instanceof TBusinessKnowledgeModel) {
            return bkmFEELParameters((TBusinessKnowledgeModel) invocable);
        } else if (invocable instanceof TDecisionService) {
            return dsFEELParameters((TDecisionService) invocable);
        } else {
            throw new DMNRuntimeException(String.format("Illegal invocable '%s'", invocable.getClass().getSimpleName()));
        }
    }

    //
    // BKM related functions
    //
    @Override
    public String bkmFunctionName(DRGElementReference<? extends TDRGElement> reference) {
        return bkmFunctionName((TBusinessKnowledgeModel) reference.getElement());
    }

    @Override
    public String bkmFunctionName(TBusinessKnowledgeModel bkm) {
        String name = bkm.getName();
        return bkmFunctionName(name);
    }

    @Override
    public String bkmFunctionName(String name) {
        return nativeFriendlyName(name);
    }

    @Override
    public String bkmQualifiedFunctionName(TBusinessKnowledgeModel bkm) {
        String javaPackageName = qualifiedName(bkm);
        String javaFunctionName = bkmFunctionName(bkm);
        return qualifiedName(javaPackageName, javaFunctionName);
    }

    @Override
    public List<FormalParameter> bkmFEELParameters(TBusinessKnowledgeModel bkm) {
        TDefinitions model = this.dmnModelRepository.getModel(bkm);
        List<FormalParameter> parameters = new ArrayList<>();
        bkm.getEncapsulatedLogic().getFormalParameter().forEach(p -> parameters.add(new FormalParameter(p.getName(), toFEELType(model, QualifiedName.toQualifiedName(model, p.getTypeRef())))));
        return parameters;
    }

    @Override
    public List<String> bkmFEELParameterNames(TBusinessKnowledgeModel bkm) {
        return bkmFEELParameters(bkm).stream().map(FormalParameter::getName).collect(Collectors.toList());
    }

    protected List<Pair<String, String>> bkmParameters(DRGElementReference<TBusinessKnowledgeModel> reference) {
        return bkmParameters(reference, true);
    }

    protected List<Pair<String, String>> bkmParameters(DRGElementReference<TBusinessKnowledgeModel> reference, boolean javaFriendlyName) {
        List<Pair<String, String>> parameters = new ArrayList<>();
        TBusinessKnowledgeModel bkm = reference.getElement();
        TFunctionDefinition encapsulatedLogic = bkm.getEncapsulatedLogic();
        List<TInformationItem> formalParameters = encapsulatedLogic.getFormalParameter();
        for (TInformationItem parameter : formalParameters) {
            String parameterName = javaFriendlyName ? informationItemVariableName(parameter) : parameter.getName();
            String parameterType = informationItemTypeName(bkm, parameter);
            parameters.add(new Pair<>(parameterName, parameterType));
        }
        return parameters;
    }

    //
    // Decision Service related functions
    //
    @Override
    public String dsFunctionName(TDecisionService service) {
        String name = service.getName();
        return dsFunctionName(name);
    }

    @Override
    public String dsFunctionName(String name) {
        return nativeFriendlyName(name);
    }

    @Override
    public List<FormalParameter> dsFEELParameters(TDecisionService service) {
        List<FormalParameter> parameters = new ArrayList<>();
        for (TDMNElementReference er: service.getInputData()) {
            TInputData inputData = getDMNModelRepository().findInputDataByRef(service, er.getHref());
            parameters.add(new FormalParameter(inputData.getName(), toFEELType(inputData)));
        }
        for (TDMNElementReference er: service.getInputDecision()) {
            TDecision decision = getDMNModelRepository().findDecisionByRef(service, er.getHref());
            parameters.add(new FormalParameter(decision.getName(), drgElementOutputFEELType(decision)));
        }
        return parameters;
    }

    @Override
    public List<String> dsFEELParameterNames(TDecisionService service) {
        return dsFEELParameters(service).stream().map(FormalParameter::getName).collect(Collectors.toList());
    }

    private List<Pair<String, Type>> dsParameters(DRGElementReference<TDecisionService> reference, boolean javaFriendlyName) {
        TDecisionService service = reference.getElement();
        List<Pair<String, Type>> parameters = new ArrayList<>();
        for (TDMNElementReference er: service.getInputData()) {
            TInputData inputData = this.dmnModelRepository.findInputDataByRef(service, er.getHref());
            String importName = this.dmnModelRepository.findImportName(service, er);
            String parameterName = javaFriendlyName ? drgElementVariableName(this.dmnModelRepository.makeDRGElementReference(importName, inputData)) : inputData.getName();
            Type parameterType = toFEELType(inputData);
            parameters.add(new Pair<>(parameterName, parameterType));
        }
        for (TDMNElementReference er: service.getInputDecision()) {
            TDecision decision = this.dmnModelRepository.findDecisionByRef(service, er.getHref());
            String importName = this.dmnModelRepository.findImportName(service, er);
            String parameterName = javaFriendlyName ? drgElementVariableName(this.dmnModelRepository.makeDRGElementReference(importName, decision)) : decision.getName();
            Type parameterType = drgElementOutputFEELType(decision);
            parameters.add(new Pair<>(parameterName, parameterType));
        }
        return parameters;
    }

    //
    // Decision related functions
    //
    protected String convertDecisionArgument(String paramName, Type type) {
        if (type instanceof ItemDefinitionType) {
            return this.nativeExpressionFactory.convertToItemDefinitionType(paramName, (ItemDefinitionType) type);
        } else {
            return paramName;
        }
    }

    protected Statement convertExpression(Statement statement, Type expectedType) {
        if (!(statement instanceof ExpressionStatement)) {
            return statement;
        }
        String javaExpression = ((ExpressionStatement)statement).getExpression();
        Type expressionType = ((ExpressionStatement)statement).getExpressionType();
        if ("null".equals(javaExpression)) {
            return new ExpressionStatement(javaExpression, expectedType);
        }
        if (expectedType instanceof ListType && expressionType instanceof ListType) {
            Type expectedElementType = ((ListType) expectedType).getElementType();
            Type expressionElementType = ((ListType) expressionType).getElementType();
            if (expectedElementType instanceof ItemDefinitionType) {
                if (expressionElementType.conformsTo(expectedElementType) || expressionElementType == AnyType.ANY || expressionElementType instanceof ContextType) {
                    String conversionText = this.nativeExpressionFactory.makeListConversion(javaExpression, (ItemDefinitionType) expectedElementType);
                    return new ExpressionStatement(conversionText, expectedType);
                }
            }
        } else if (expectedType instanceof ListType) {
            return new ExpressionStatement(this.nativeExpressionFactory.convertElementToList(javaExpression, expectedType), expectedType);
        } else if (expressionType instanceof ListType) {
            return new ExpressionStatement(this.nativeExpressionFactory.convertListToElement(javaExpression, expectedType), expectedType);
        } else if (expectedType instanceof ItemDefinitionType) {
            if (expressionType.conformsTo(expectedType) || expressionType == AnyType.ANY || expressionType instanceof ContextType) {
                return new ExpressionStatement(this.nativeExpressionFactory.convertToItemDefinitionType(javaExpression, (ItemDefinitionType) expectedType), expectedType);
            }
        }
        return statement;
    }

    @Override
    public String convertMethodName(TItemDefinition itemDefinition) {
        return this.nativeExpressionFactory.convertMethodName(itemDefinition);
    }

    @Override
    public String augmentSignature(String signature) {
        String annotationParameter = this.nativeExpressionFactory.parameter(annotationSetClassName(), annotationSetVariableName());
        if (StringUtils.isBlank(signature)) {
            return annotationParameter;
        } else {
            return String.format("%s, %s", signature, annotationParameter);
        }
    }

    @Override
    public String augmentArgumentList(String arguments) {
        String extra = annotationSetVariableName();
        if (StringUtils.isBlank(arguments)) {
            return extra;
        } else {
            return String.format("%s, %s", arguments, extra);
        }
    }

    @Override
    public List<Pair<String, Type>> inputDataParametersClosure(DRGElementReference<TDecision> reference) {
        return inputDataParametersClosure(reference, true);
    }

    @Override
    public List<Pair<String, Type>> inputDataParametersClosure(DRGElementReference<TDecision> reference, boolean nativeFriendlyName) {
        List<DRGElementReference<TInputData>> allInputDataReferences = this.dmnModelRepository.allInputDatas(reference, this.drgElementFilter);
        this.dmnModelRepository.sortNamedElementReferences(allInputDataReferences);

        List<Pair<String, Type>> parameters = new ArrayList<>();
        for (DRGElementReference<TInputData> inputData : allInputDataReferences) {
            String parameterName = nativeFriendlyName ? inputDataVariableName(inputData) : inputData.getElementName();
            Type parameterType = toFEELType(inputData.getElement());
            parameters.add(new Pair<>(parameterName, parameterType));
        }
        return parameters;
    }

    @Override
    public String drgReferenceQualifiedName(DRGElementReference<? extends TDRGElement> reference) {
        return drgReferenceQualifiedName(reference.getImportPath(), reference.getModelName(), reference.getElementName());
    }

    private String drgReferenceQualifiedName(ImportPath importPath, String modelName, String elementName) {
        Pair<List<String>, String> qName = qualifiedName(importPath, modelName, elementName);

        String javaPrefix = qName.getLeft().stream().map(this::javaModelName).collect(Collectors.joining("_"));
        String javaName = lowerCaseFirst(qName.getRight());
        if (StringUtils.isBlank(javaPrefix)) {
            return javaName;
        } else {
            return String.format("%s_%s", javaPrefix, javaName);
        }
    }

    @Override
    public String bindingName(DRGElementReference<? extends TDRGElement> reference) {
        Pair<List<String>, String> qName = qualifiedName(reference.getImportPath(), reference.getModelName(), reference.getElementName());

        String prefix = String.join(".", qName.getLeft());
        if (StringUtils.isBlank(prefix)) {
            return qName.getRight();
        } else {
            return String.format("%s.%s", prefix, qName.getRight());
        }
    }

    private Pair<List<String>, String> qualifiedName(ImportPath importPath, String modelName, String elementName) {
        if (this.onePackage) {
            return new Pair<>(Collections.emptyList(), elementName);
        } else if (this.singletonInputData) {
            if (ImportPath.isEmpty(importPath)) {
                modelName = "";
            }
            return new Pair<>(Collections.singletonList(modelName), elementName);
        } else {
            return new Pair<>(importPath.getPathElements(), elementName);
        }
    }

    @Override
    public String parameterNativeType(TDefinitions model, TInformationItem element) {
        return parameterType(model, element);
    }

    @Override
    public String parameterNativeType(TDRGElement element) {
        String parameterNativeType;
        if (element instanceof TInputData) {
            parameterNativeType = inputDataType((TInputData) element);
        } else if (element instanceof TDecision) {
            parameterNativeType = drgElementOutputType(element);
        } else {
            throw new UnsupportedOperationException(String.format("'%s' is not supported", element.getClass().getSimpleName()));
        }
        return parameterNativeType;
    }

    private String parameterType(TDefinitions model, TInformationItem element) {
        QualifiedName typeRef = this.dmnModelRepository.variableTypeRef(model, element);
        if (typeRef == null) {
            throw new IllegalArgumentException(String.format("Cannot resolve typeRef for element '%s'", element.getName()));
        }
        Type type = toFEELType(model, typeRef);
        return toNativeType(type);
    }

    @Override
    public boolean isLazyEvaluated(DRGElementReference<? extends TDRGElement> reference) {
        return this.isLazyEvaluated(reference.getElement());
    }

    @Override
    public boolean isLazyEvaluated(TDRGElement element) {
        return this.isLazyEvaluated(element.getName());
    }

    @Override
    public boolean isLazyEvaluated(String name) {
        return this.lazyEvaluationOptimisation.isLazyEvaluated(name);
    }

    @Override
    public String lazyEvaluationType(TDRGElement input, String inputNativeType) {
        return isLazyEvaluated(input) ? String.format("%s<%s>", lazyEvalClassName(), inputNativeType) : inputNativeType;
    }

    @Override
    public String lazyEvaluation(String elementName, String nativeName) {
        return isLazyEvaluated(elementName) ? String.format("%s.getOrCompute()", nativeName) : nativeName;
    }

    @Override
    public String pairClassName() {
        return Pair.class.getName();
    }

    @Override
    public String pairComparatorClassName() {
        return PairComparator.class.getName();
    }

    @Override
    public String argumentsClassName() {
        return Arguments.class.getName();
    }

    @Override
    public String dmnTypeClassName() {
        return DMNType.class.getName();
    }

    @Override
    public String dmnRuntimeExceptionClassName() {
        return DMNRuntimeException.class.getName();
    }

    @Override
    public String lazyEvalClassName() {
        return LazyEval.class.getName();
    }

    @Override
    public String contextClassName() {
        return Context.class.getName();
    }

    @Override
    public String annotationSetClassName() {
        return AnnotationSet.class.getName();
    }

    @Override
    public String annotationSetVariableName() {
        return "annotationSet_";
    }

    @Override
    public String eventListenerClassName() {
        return EventListener.class.getName();
    }

    @Override
    public String eventListenerVariableName() {
        return "eventListener_";
    }

    @Override
    public String defaultEventListenerClassName() {
        return NopEventListener.class.getName();
    }

    @Override
    public String loggingEventListenerClassName() {
        return LoggingEventListener.class.getName();
    }

    @Override
    public String externalExecutorClassName() {
        return ExternalFunctionExecutor.class.getName();
    }

    @Override
    public String externalExecutorVariableName() {
        return "externalExecutor_";
    }

    @Override
    public String defaultExternalExecutorClassName() {
        return DefaultExternalFunctionExecutor.class.getName();
    }

    @Override
    public String cacheInterfaceName() {
        return Cache.class.getName();
    }

    @Override
    public String cacheVariableName() {
        return "cache_";
    }

    @Override
    public String defaultCacheClassName() {
        return DefaultCache.class.getName();
    }

    @Override
    public String drgElementSignatureExtra(DRGElementReference<? extends TDRGElement> reference) {
        String signature = drgElementSignature(reference);
        return drgElementSignatureExtra(signature);
    }

    @Override
    public String drgElementSignatureExtra(TDRGElement element) {
        String signature = drgElementSignature(element);
        return drgElementSignatureExtra(signature);
    }

    @Override
    public String drgElementSignatureExtra(String signature) {
        String listenerParameter = this.nativeExpressionFactory.parameter(eventListenerClassName(), eventListenerVariableName());
        String executorParameter = this.nativeExpressionFactory.parameter(externalExecutorClassName(), externalExecutorVariableName());
        if (StringUtils.isBlank(signature)) {
            return String.format("%s, %s", listenerParameter, executorParameter);
        } else {
            return String.format("%s, %s, %s", signature, listenerParameter, executorParameter);
        }
    }

    @Override
    public String drgElementArgumentsExtra(DRGElementReference<? extends TDRGElement> reference) {
        String arguments = drgElementArgumentList(reference);
        return drgElementArgumentsExtra(arguments);
    }

    @Override
    public String drgElementArgumentsExtra(TDRGElement element) {
        String arguments = drgElementArgumentList(element);
        return drgElementArgumentsExtra(arguments);
    }

    @Override
    public String drgElementArgumentsExtra(String arguments) {
        if (StringUtils.isBlank(arguments)) {
            return String.format("%s, %s", eventListenerVariableName(), externalExecutorVariableName());
        } else {
            return String.format("%s, %s, %s", arguments, eventListenerVariableName(), externalExecutorVariableName());
        }
    }

    @Override
    public String drgElementDefaultArgumentsExtra(String arguments) {
        String loggerArgument = constructor(loggingEventListenerClassName(), "LOGGER");
        String executorArgument = defaultConstructor(defaultExternalExecutorClassName());
        if (StringUtils.isBlank(arguments)) {
            return String.format("%s, %s", loggerArgument, executorArgument);
        } else {
            return String.format("%s, %s, %s", arguments, loggerArgument, executorArgument);
        }
    }

    @Override
    public boolean isCaching() {
        return this.caching;
    }

    @Override
    public boolean isCached(String elementName) {
        if (!this.caching) {
            return false;
        }
        return this.cachedElements.contains(elementName);
    }

    @Override
    public boolean isParallelStream() {
        return this.parallelStream;
    }

    @Override
    public String getStream() {
        return this.isParallelStream() ? "parallelStream()" : "stream()";
    }

    @Override
    public String drgElementSignatureExtraCache(DRGElementReference<? extends TDRGElement> reference) {
        String signature = drgElementSignatureExtra(reference);
        return drgElementSignatureExtraCache(signature);
    }

    @Override
    public String drgElementSignatureExtraCache(TDRGElement element) {
        String signature = drgElementSignatureExtra(element);
        return drgElementSignatureExtraCache(signature);
    }

    @Override
    public String drgElementSignatureExtraCache(String signature) {
        if (!this.caching) {
            return signature;
        }

        String cacheParameter = this.nativeExpressionFactory.parameter(cacheInterfaceName(), cacheVariableName());
        if (StringUtils.isBlank(signature)) {
            return cacheParameter;
        } else {
            return String.format("%s, %s", signature, cacheParameter);
        }
    }

    @Override
    public String drgElementArgumentsExtraCache(DRGElementReference<? extends TDRGElement> reference) {
        String arguments = drgElementArgumentsExtra(reference);
        return drgElementArgumentsExtraCache(arguments);
    }

    @Override
    public String drgElementArgumentsExtraCache(TDRGElement element) {
        String arguments = drgElementArgumentsExtra(element);
        return drgElementArgumentsExtraCache(arguments);
    }

    @Override
    public String drgElementArgumentsExtraCache(String arguments) {
        if (!this.caching) {
            return arguments;
        }

        if (StringUtils.isBlank(arguments)) {
            return String.format("%s", cacheVariableName());
        } else {
            return String.format("%s, %s", arguments, cacheVariableName());
        }
    }

    @Override
    public String drgElementDefaultArgumentsExtraCache(String arguments) {
        if (!this.caching) {
            return arguments;
        }

        String defaultCacheArgument = defaultConstructor(defaultCacheClassName());
        if (StringUtils.isBlank(arguments)) {
            return defaultCacheArgument;
        } else {
            return String.format("%s, %s", arguments, defaultCacheArgument);
        }
    }

    @Override
    public String drgElementAnnotationClassName() {
        return com.gs.dmn.runtime.annotation.DRGElement.class.getName();
    }

    @Override
    public String elementKindAnnotationClassName() {
        return DRGElementKind.class.getName();
    }

    @Override
    public String expressionKindAnnotationClassName() {
        return ExpressionKind.class.getName();
    }

    @Override
    public String drgElementMetadataClassName() {
        return com.gs.dmn.runtime.listener.DRGElement.class.getName();
    }

    @Override
    public String drgElementMetadataFieldName() {
        return "DRG_ELEMENT_METADATA";
    }

    @Override
    public String drgRuleMetadataClassName() {
        return com.gs.dmn.runtime.listener.Rule.class.getName();
    }

    @Override
    public String drgRuleMetadataFieldName() {
        return "drgRuleMetadata";
    }

    @Override
    public String assertClassName() {
        return Assert.class.getName();
    }

    //
    // Decision Table related functions
    //
    @Override
    public String defaultValue(TDRGElement element) {
        return this.expressionToNativeTransformer.defaultValue(element);
    }

    @Override
    public String defaultValue(TDRGElement element, TOutputClause output) {
        return this.expressionToNativeTransformer.defaultValue(element, output);
    }

    @Override
    public String condition(TDRGElement element, TDecisionRule rule) {
        return this.expressionToNativeTransformer.condition(element, rule);
    }

    @Override
    public String outputEntryToNative(TDRGElement element, TLiteralExpression outputEntryExpression, int outputIndex) {
        return this.expressionToNativeTransformer.outputEntryToNative(element, outputEntryExpression, outputIndex);
    }

    @Override
    public String outputClauseClassName(TDRGElement element, TOutputClause outputClause, int index) {
        return this.expressionToNativeTransformer.outputClauseClassName(element, outputClause, index);
    }

    @Override
    public String outputClauseVariableName(TDRGElement element, TOutputClause outputClause) {
        return this.expressionToNativeTransformer.outputClauseVariableName(element, outputClause);
    }

    @Override
    public String outputClausePriorityVariableName(TDRGElement element, TOutputClause outputClause) {
        return this.expressionToNativeTransformer.outputClausePriorityVariableName(element, outputClause);
    }

    @Override
    public String getter(TDRGElement element, TOutputClause output) {
        return this.expressionToNativeTransformer.getter(element, output);
    }

    @Override
    public String setter(TDRGElement element, TOutputClause output) {
        return this.expressionToNativeTransformer.setter(element, output);
    }

    @Override
    public Integer priority(TDRGElement element, TLiteralExpression literalExpression, int outputIndex) {
        return this.expressionToNativeTransformer.priority(element, literalExpression, outputIndex);
    }

    @Override
    public String priorityGetter(TDRGElement element, TOutputClause output) {
        return this.expressionToNativeTransformer.priorityGetter(element, output);
    }

    @Override
    public String prioritySetter(TDRGElement element, TOutputClause output) {
        return this.expressionToNativeTransformer.prioritySetter(element, output);
    }

    @Override
    public HitPolicy hitPolicy(TDRGElement element) {
        return this.expressionToNativeTransformer.hitPolicy(element);
    }

    @Override
    public String aggregation(TDecisionTable decisionTable) {
        return this.expressionToNativeTransformer.aggregation(decisionTable);
    }

    @Override
    public String aggregator(TDRGElement element, TDecisionTable decisionTable, TOutputClause outputClause, String ruleOutputListVariable) {
        return this.expressionToNativeTransformer.aggregator(element, decisionTable, outputClause, ruleOutputListVariable);
    }

    @Override
    public String annotation(TDRGElement element, TDecisionRule rule) {
        return this.expressionToNativeTransformer.annotation(element, rule);
    }

    @Override
    public String annotationEscapedText(TDecisionRule rule) {
        return this.expressionToNativeTransformer.annotationEscapedText(rule);
    }

    @Override
    public String escapeInString(String text) {
        return StringEscapeUtil.escapeInString(text);
    }

    private int nextChar(String text, int i) {
        if (i == text.length()) {
            return -1;
        } else {
            return text.charAt(i + 1);
        }
    }

    //
    // Rule related functions
    //
    @Override
    public String ruleOutputClassName(TDRGElement element) {
        return this.expressionToNativeTransformer.ruleOutputClassName(element);
    }

    @Override
    public String ruleId(List<TDecisionRule> rules, TDecisionRule rule) {
        return this.expressionToNativeTransformer.ruleId(rules, rule);
    }

    @Override
    public String abstractRuleOutputClassName() {
        return this.expressionToNativeTransformer.abstractRuleOutputClassName();
    }

    @Override
    public String ruleOutputListClassName() {
        return this.expressionToNativeTransformer.ruleOutputListClassName();
    }

    @Override
    public String ruleSignature(TDecision decision) {
        return this.expressionToNativeTransformer.ruleSignature(decision);
    }

    @Override
    public String ruleArgumentList(TDecision decision) {
        return this.expressionToNativeTransformer.ruleArgumentList(decision);
    }

    @Override
    public String ruleSignature(TBusinessKnowledgeModel bkm) {
        return this.expressionToNativeTransformer.ruleSignature(bkm);
    }

    @Override
    public String ruleArgumentList(TBusinessKnowledgeModel bkm) {
        return this.expressionToNativeTransformer.ruleArgumentList(bkm);
    }

    @Override
    public String hitPolicyAnnotationClassName() {
        return this.expressionToNativeTransformer.hitPolicyAnnotationClassName();
    }

    @Override
    public String ruleAnnotationClassName() {
        return this.expressionToNativeTransformer.ruleAnnotationClassName();
    }

    //
    // Expression related functions
    //
    @Override
    public Type expressionType(TDRGElement element, JAXBElement<? extends TExpression> jElement, Environment environment) {
        return this.dmnEnvironmentFactory.expressionType(element, jElement, environment);
    }

    @Override
    public Type expressionType(TDRGElement element, TExpression expression, Environment environment) {
        return this.dmnEnvironmentFactory.expressionType(element, expression, environment);
    }

    @Override
    public Type convertType(Type type, boolean convertToContext) {
        return this.dmnEnvironmentFactory.convertType(type, convertToContext);
    }

    @Override
    public Statement expressionToNative(TDRGElement element) {
        TDefinitions model = this.dmnModelRepository.getModel(element);
        TExpression expression = this.dmnModelRepository.expression(element);
        if (expression instanceof TContext) {
            return this.expressionToNativeTransformer.contextExpressionToNative(element, (TContext) expression);
        } else if (expression instanceof TLiteralExpression) {
            Statement statement = this.expressionToNativeTransformer.literalExpressionToNative(element, ((TLiteralExpression) expression).getText());
            Type expectedType = drgElementOutputFEELType(element);
            return convertExpression(statement, expectedType);
        } else if (expression instanceof TInvocation) {
            return this.expressionToNativeTransformer.invocationExpressionToNative(element, (TInvocation) expression);
        } else if (expression instanceof TRelation) {
            return this.expressionToNativeTransformer.relationExpressionToNative(element, (TRelation) expression);
        } else {
            throw new UnsupportedOperationException(String.format("Not supported '%s'", expression.getClass().getSimpleName()));
        }
    }

    @Override
    public Statement expressionToNative(TDRGElement element, TExpression expression, Environment environment) {
        if (expression instanceof TContext) {
            return this.expressionToNativeTransformer.contextExpressionToNative(element, (TContext) expression, environment);
        } else if (expression instanceof TFunctionDefinition) {
            return this.expressionToNativeTransformer.functionDefinitionToNative(element, (TFunctionDefinition) expression, environment);
        } else if (expression instanceof TLiteralExpression) {
            return this.expressionToNativeTransformer.literalExpressionToNative(element, ((TLiteralExpression) expression).getText(), environment);
        } else if (expression instanceof TInvocation) {
            return this.expressionToNativeTransformer.invocationExpressionToNative(element, (TInvocation) expression, environment);
        } else if (expression instanceof TRelation) {
            return this.expressionToNativeTransformer.relationExpressionToNative(element, (TRelation) expression, environment);
        } else {
            throw new UnsupportedOperationException(String.format("Not supported '%s'", expression.getClass().getSimpleName()));
        }
    }

    @Override
    public String literalExpressionToNative(TDRGElement element, String expressionText) {
        Statement statement = this.expressionToNativeTransformer.literalExpressionToNative(element, expressionText);
        return ((ExpressionStatement)statement).getExpression();
    }

    @Override
    public String functionDefinitionToNative(TDRGElement element, FunctionDefinition functionDefinition, boolean convertTypeToContext, String body) {
        return this.expressionToNativeTransformer.functionDefinitionToNative(element, functionDefinition, body, convertTypeToContext);
    }

    @Override
    public boolean isCompoundStatement(Statement stm) {
        return stm instanceof CompoundStatement;
    }

    //
    // Type related functions
    //
    @Override
    public boolean isComplexType(Type type) {
        return type instanceof ItemDefinitionType
                || type instanceof ListType && ((ListType) type).getElementType() instanceof ItemDefinitionType;
    }

    @Override
    public Type toFEELType(TDefinitions model, String typeName) {
        return this.dmnEnvironmentFactory.toFEELType(model, typeName);
    }

    @Override
    public Type toFEELType(TDefinitions model, QualifiedName typeRef) {
        return this.dmnEnvironmentFactory.toFEELType(model, typeRef);
    }

    @Override
    public Type toFEELType(TItemDefinition itemDefinition) {
        return this.dmnEnvironmentFactory.toFEELType(itemDefinition);
    }

    //
    // Common functions
    //
    @Override
    public String toNativeType(TDecision decision) {
        String javaType = this.nativeTypeMemoizer.get(decision);
        if (javaType == null) {
            javaType = toNativeTypeNoCache(decision);
            this.nativeTypeMemoizer.put(decision, javaType);
        }
        return javaType;
    }

    private String toNativeTypeNoCache(TDecision decision) {
        Type type = this.drgElementOutputFEELType(decision);
        return toNativeType(type);
    }

    @Override
    public String toStringNativeType(Type type) {
        return toNativeType(StringType.STRING);
    }

    @Override
    public String toNativeType(Type type) {
        String javaType = this.nativeTypeMemoizer.get(type);
        if (javaType == null) {
            javaType = toNativeTypeNoCache(type);
            this.nativeTypeMemoizer.put(type, javaType);
        }
        return javaType;
    }

    private String toNativeTypeNoCache(Type type) {
        if (type instanceof NamedType) {
            String typeName = ((NamedType) type).getName();
            if (StringUtils.isBlank(typeName)) {
                throw new DMNRuntimeException(String.format("Missing type name in '%s'", type));
            }
            String primitiveType = this.nativeTypeFactory.toNativeType(typeName);
            if (!StringUtils.isBlank(primitiveType)) {
                return primitiveType;
            } else {
                if (type instanceof ItemDefinitionType) {
                    String modelName = ((ItemDefinitionType) type).getModelName();
                    return qualifiedName(nativeTypePackageName(modelName), upperCaseFirst(typeName));
                } else {
                    throw new DMNRuntimeException(String.format("Cannot infer platform type for '%s'", type));
                }
            }
        } else if (type instanceof ContextType) {
            return contextClassName();
        } else if (type instanceof ListType) {
            if (((ListType) type).getElementType() instanceof AnyType) {
                return makeListType(DMNToJavaTransformer.LIST_TYPE);
            } else {
                String elementType = toNativeType(((ListType) type).getElementType());
                return makeListType(DMNToJavaTransformer.LIST_TYPE, elementType);
            }
        } else if (type instanceof FunctionType) {
            if (type instanceof FEELFunctionType) {
                if (((FEELFunctionType) type).isExternal()) {
                    String returnType = toNativeType(((FunctionType) type).getReturnType());
                    return makeFunctionType(JavaExternalFunction.class.getName(), returnType);
                } else {
                    String returnType = toNativeType(((FunctionType) type).getReturnType());
                    return makeFunctionType(LambdaExpression.class.getName(), returnType);
                }
            } else if (type instanceof DMNFunctionType) {
                if (isFEELFunction(((DMNFunctionType) type).getKind())) {
                    String returnType = toNativeType(((FunctionType) type).getReturnType());
                    return makeFunctionType(LambdaExpression.class.getName(), returnType);
                } else if (isJavaFunction(((DMNFunctionType) type).getKind())) {
                    String returnType = toNativeType(((FunctionType) type).getReturnType());
                    return makeFunctionType(JavaExternalFunction.class.getName(), returnType);
                }
                throw new DMNRuntimeException(String.format("Kind is t supported yet", type));
            }
            throw new DMNRuntimeException(String.format("Type %s is not supported yet", type));
        }
        throw new IllegalArgumentException(String.format("Type '%s' is not supported yet", type));
    }

    protected String makeListType(String listType, String elementType) {
        return String.format("%s<%s>", listType, elementType);
    }

    protected String makeListType(String listType) {
        return String.format("%s<? extends Object>", listType);
    }

    protected String makeFunctionType(String name, String returnType) {
        return String.format("%s<%s>", name, returnType);
    }

    @Override
    public String qualifiedName(String pkg, String name) {
        if (StringUtils.isBlank(pkg)) {
            return name;
        } else {
            return pkg + "." + name;
        }
    }

    @Override
    public String qualifiedName(DRGElementReference<? extends TDRGElement> reference) {
        return qualifiedName(reference.getElement());
    }

    @Override
    public String qualifiedName(TDRGElement element) {
        TDefinitions definitions = this.dmnModelRepository.getModel(element);
        String pkg = this.nativeModelPackageName(definitions.getName());
        String name = drgElementClassName(element);
        if (StringUtils.isBlank(pkg)) {
            return name;
        } else {
            return pkg + "." + name;
        }
    }

    @Override
    public String getterName(String name) {
        return String.format("get%s", StringUtils.capitalize(name));
    }

    @Override
    public String getter(String name) {
        return String.format("%s()", getterName(name));
    }

    @Override
    public String setter(String name) {
        return String.format("set%s", StringUtils.capitalize(name));
    }

    @Override
    public String contextGetter(String name) {
        return String.format("get(\"%s\")", name);
    }

    @Override
    public String contextSetter(String name) {
        return String.format("put(\"%s\", ", name);
    }

    @Override
    public String nativeFriendlyVariableName(String name) {
        if (StringUtils.isBlank(name)) {
            throw new DMNRuntimeException("Cannot build variable name from empty string");
        }
        name = this.dmnModelRepository.removeSingleQuotes(name);
        String[] parts = name.split("\\.");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            String firstChar = Character.toString(Character.toLowerCase(part.charAt(0)));
            String partName = nativeFriendlyName(part.length() == 1 ? firstChar : firstChar + part.substring(1));
            if (i != 0) {
                result.append(".");
            }
            result.append(partName);
        }
        return result.toString();
    }

    @Override
    public String nativeFriendlyName(String name) {
        name = this.dmnModelRepository.removeSingleQuotes(name);
        StringBuilder result = new StringBuilder();
        boolean skippedPrevious = false;
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (Character.isAlphabetic(ch) || Character.isDigit(ch) || ch == '_') {
                if (skippedPrevious) {
                    ch = Character.toUpperCase(ch);
                }
                result.append(ch);
                skippedPrevious = false;
            } else {
                skippedPrevious = true;
            }
        }
        return result.toString();
    }

    @Override
    public String upperCaseFirst(String name) {
        name = this.dmnModelRepository.removeSingleQuotes(name);
        String firstChar = Character.toString(Character.toUpperCase(name.charAt(0)));
        return nativeFriendlyName(name.length() == 1 ? firstChar : firstChar + name.substring(1));
    }

    @Override
    public String lowerCaseFirst(String name) {
        name = this.dmnModelRepository.removeSingleQuotes(name);
        String firstChar = Character.toString(Character.toLowerCase(name.charAt(0)));
        return nativeFriendlyName(name.length() == 1 ? firstChar : firstChar + name.substring(1));
    }

    @Override
    public String nativeModelPackageName(String modelName) {
        if (modelName != null && modelName.endsWith(DMNConstants.DMN_FILE_EXTENSION)) {
            modelName = modelName.substring(0, modelName.length() - 4);
        }
        if (this.onePackage) {
            modelName = "";
        }

        String modelPackageName = javaModelName(modelName);
        String elementPackageName = this.javaRootPackage;
        if (StringUtils.isBlank(elementPackageName)) {
            return modelPackageName;
        } else if (!StringUtils.isBlank(modelPackageName)) {
            return elementPackageName + "." + modelPackageName;
        } else {
            return elementPackageName;
        }
    }

    protected String javaModelName(String name) {
        if (StringUtils.isEmpty(name)) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        boolean skippedPrevious = false;
        boolean first = true;
        for (int ch: name.codePoints().toArray()) {
            if (Character.isJavaIdentifierPart(ch)) {
                if (skippedPrevious && !first) {
                    result.append('_');
                }
                result.append((char)ch);
                skippedPrevious = false;
                first = false;
            } else {
                skippedPrevious = true;
            }
        }
        String newName = result.toString().toLowerCase();
        if (newName.isEmpty()) {
            return newName;
        } else {
            if (!Character.isJavaIdentifierStart(newName.codePointAt(0))) {
                return "p_" + newName;
            } else {
                return newName;
            }
        }
    }

    @Override
    public String nativeRootPackageName() {
        if (this.javaRootPackage == null) {
            return "";
        } else {
            return this.javaRootPackage;
        }
    }

    @Override
    public String nativeTypePackageName(String modelName) {
        String modelPackageName = nativeModelPackageName(modelName);
        if (StringUtils.isBlank(modelPackageName)) {
            return DMNToJavaTransformer.DATA_PACKAGE;
        } else {
            return modelPackageName + "." + DMNToJavaTransformer.DATA_PACKAGE;
        }
    }

    @Override
    public DRGElementKind elementKind(TDRGElement element) {
        return DRGElementKind.kindByClass(element.getClass());
    }

    @Override
    public ExpressionKind expressionKind(TDRGElement element) {
        TExpression expression = this.dmnModelRepository.expression(element);
        if (expression != null) {
            return ExpressionKind.kindByClass(expression.getClass());
        }
        return ExpressionKind.OTHER;
    }

    @Override
    public String asEmptyList(TDRGElement element) {
        Type type = drgElementOutputFEELType(element);
        if (type instanceof ListType) {
            return this.nativeExpressionFactory.asList(((ListType) type).getElementType(), "");
        }
        throw new DMNRuntimeException(String.format("Expected List Type found '%s' instead", type));
    }

    @Override
    public String asList(Type elementType, String exp) {
        return this.nativeExpressionFactory.asList(elementType, exp);
    }

    //
    // Environment related functions
    //
    @Override
    public Environment makeEnvironment(TDRGElement element) {
        return this.dmnEnvironmentFactory.makeEnvironment(element);
    }

    @Override
    public Environment makeEnvironment(TDRGElement element, Environment parentEnvironment) {
        return this.dmnEnvironmentFactory.makeEnvironment(element, parentEnvironment);
    }

    @Override
    public Environment makeInputEntryEnvironment(TDRGElement element, Expression inputExpression) {
        return this.dmnEnvironmentFactory.makeInputEntryEnvironment(element, inputExpression);
    }

    @Override
    public Environment makeOutputEntryEnvironment(TDRGElement element, EnvironmentFactory environmentFactory) {
        return this.dmnEnvironmentFactory.makeOutputEntryEnvironment(element, environmentFactory);
    }

    @Override
    public Pair<Environment, Map<TContextEntry, Expression>> makeContextEnvironment(TDRGElement element, TContext context, Environment parentEnvironment) {
        return this.dmnEnvironmentFactory.makeContextEnvironment(element, context, parentEnvironment);
    }

    @Override
    public Environment makeRelationEnvironment(TNamedElement element, TRelation relation, Environment environment) {
        return this.dmnEnvironmentFactory.makeRelationEnvironment(element, relation, environment);
    }

    public Environment makeFunctionDefinitionEnvironment(TNamedElement element, TFunctionDefinition functionDefinition, Environment parentEnvironment) {
        return this.dmnEnvironmentFactory.makeFunctionDefinitionEnvironment(element, functionDefinition, parentEnvironment);
    }

    @Override
    public boolean isFEELFunction(TFunctionKind kind) {
        return kind == null || kind == TFunctionKind.FEEL;
    }

    @Override
    public boolean isJavaFunction(TFunctionKind kind) {
        return kind == TFunctionKind.JAVA;
    }
}
