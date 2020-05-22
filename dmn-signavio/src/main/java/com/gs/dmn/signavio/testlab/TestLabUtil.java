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
package com.gs.dmn.signavio.testlab;

import com.gs.dmn.DMNModelRepository;
import com.gs.dmn.feel.analysis.semantics.type.ContextType;
import com.gs.dmn.feel.analysis.semantics.type.ItemDefinitionType;
import com.gs.dmn.feel.analysis.semantics.type.ListType;
import com.gs.dmn.feel.analysis.semantics.type.Type;
import com.gs.dmn.feel.synthesis.expression.NativeExpressionFactory;
import com.gs.dmn.feel.synthesis.type.NativeTypeFactory;
import com.gs.dmn.runtime.DMNRuntimeException;
import com.gs.dmn.signavio.SignavioDMNModelRepository;
import com.gs.dmn.signavio.testlab.expression.*;
import com.gs.dmn.signavio.transformation.BasicSignavioDMN2JavaTransformer;
import com.gs.dmn.transformation.basic.BasicDMN2JavaTransformer;
import com.gs.dmn.transformation.basic.QualifiedName;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.omg.spec.dmn._20180521.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class TestLabUtil {
    protected static final Logger LOGGER = LoggerFactory.getLogger(TestLabUtil.class);

    private final BasicDMN2JavaTransformer dmnTransformer;
    private final SignavioDMNModelRepository dmnModelRepository;
    private final NativeExpressionFactory expressionFactory;
    private final NativeTypeFactory typeFactory;

    public TestLabUtil(BasicDMN2JavaTransformer dmnTransformer) {
        DMNModelRepository dmnModelRepository = dmnTransformer.getDMNModelRepository();
        if (dmnModelRepository instanceof SignavioDMNModelRepository) {
            this.dmnModelRepository = (SignavioDMNModelRepository) dmnModelRepository;
        } else {
            this.dmnModelRepository = new SignavioDMNModelRepository(dmnModelRepository.getRootDefinitions(), dmnModelRepository.getPrefixNamespaceMappings());
        }
        this.dmnTransformer = dmnTransformer;
        this.expressionFactory = dmnTransformer.getExpressionFactory();
        this.typeFactory = dmnTransformer.getTypeFactory();
    }

    //
    // Delegate methods
    //
    public String drgElementClassName(OutputParameterDefinition outputParameterDefinition) {
        TDecision decision = (TDecision) findDRGElement(outputParameterDefinition);
        return dmnTransformer.drgElementClassName(decision);
    }

    public String drgElementVariableName(OutputParameterDefinition outputParameterDefinition) {
        TDecision decision = (TDecision) findDRGElement(outputParameterDefinition);
        return dmnTransformer.drgElementVariableName(decision);
    }

    public String drgElementOutputType(OutputParameterDefinition outputParameterDefinition) {
        TDecision decision = (TDecision) findDRGElement(outputParameterDefinition);
        return dmnTransformer.drgElementOutputType(decision);
    }

    public String drgElementOutputVariableName(OutputParameterDefinition outputParameterDefinition) {
        TDecision decision = (TDecision) findDRGElement(outputParameterDefinition);
        return dmnTransformer.drgElementVariableName(decision);
    }

    public String drgElementArgumentList(OutputParameterDefinition outputParameterDefinition) {
        TDecision decision = (TDecision) findDRGElement(outputParameterDefinition);
        return dmnTransformer.drgElementArgumentList(decision);
    }

    public String drgElementOutputFieldName(TestLab testLab, int outputIndex) {
        TDecision decision = (TDecision) findDRGElement(testLab.getRootOutputParameter());
        return ((BasicSignavioDMN2JavaTransformer)dmnTransformer).drgElementOutputFieldName(decision, outputIndex);
    }

    public String inputDataVariableName(InputParameterDefinition inputParameterDefinition) {
        TDRGElement element = findDRGElement(inputParameterDefinition);
        if (element instanceof TInputData) {
            return dmnTransformer.inputDataVariableName((TInputData) element);
        } else {
            throw new UnsupportedOperationException(String.format("'%s' not supported", element.getClass().getSimpleName()));
        }
    }

    public String assertClassName() {
        return dmnTransformer.assertClassName();
    }

    public String annotationSetClassName() {
        return dmnTransformer.annotationSetClassName();
    }

    public String annotationSetVariableName() {
        return dmnTransformer.annotationSetVariableName();
    }

    public boolean hasListType(ParameterDefinition parameterDefinition) {
        TDRGElement element = findDRGElement(parameterDefinition);
        return this.dmnTransformer.isList(element);
    }

    public boolean isSimple(Expression expression) {
        return expression instanceof SimpleExpression;
    }

    public boolean isComplex(Expression expression) {
        return expression instanceof ComplexExpression;
    }

    public boolean isList(Expression expression) {
        return expression instanceof ListExpression;
    }

    public String getter(String outputName) {
        return dmnTransformer.getter(outputName);
    }

    public String upperCaseFirst(String name) {
        return dmnTransformer.upperCaseFirst(name);
    }

    public String lowerCaseFirst(String name) {
        return dmnTransformer.lowerCaseFirst(name);
    }

    public String qualifiedName(TestLab testLab, OutputParameterDefinition rootOutputParameter) {
        String pkg = dmnTransformer.javaModelPackageName(rootOutputParameter.getModelName());
        String cls = drgElementClassName(rootOutputParameter);
        return dmnTransformer.qualifiedName(pkg, cls);
    }

    public TDRGElement findDRGElement(ParameterDefinition parameterDefinition) {
        String id = parameterDefinition.getId();
        TDRGElement element = this.dmnModelRepository.findDRGElementById(id);
        if (element == null) {
            element = this.dmnModelRepository.findDRGElementByDiagramAndShapeIds(parameterDefinition.getDiagramId(), parameterDefinition.getShapeId());
        }
        if (element == null) {
            element = this.dmnModelRepository.findDRGElementByLabel(parameterDefinition.getRequirementName(), parameterDefinition.getDiagramId(), parameterDefinition.getShapeId());
        }
        return element;
    }

    // For input parameters
    public String toJavaType(InputParameterDefinition inputParameterDefinition) {
        Type feelType = toFEELType(inputParameterDefinition);
        return dmnTransformer.toJavaType(feelType);
    }

    // For input parameters
    public String toJavaExpression(TestLab testLab, TestCase testCase, int inputIndex) {
        Type inputType = toFEELType(testLab.getInputParameterDefinitions().get(inputIndex));
        Expression inputExpression = testCase.getInputValues().get(inputIndex);
        TDecision decision = (TDecision) findDRGElement(testLab.getRootOutputParameter());

        return toJavaExpression(inputType, inputExpression, decision);
    }

    // For expected values
    public String toJavaExpression(TestLab testLab, Expression expression) {
        Type outputType = toFEELType(testLab.getRootOutputParameter());
        TDecision decision = (TDecision) findDRGElement(testLab.getRootOutputParameter());

        return toJavaExpression(outputType, expression, decision);
    }

    private String toJavaExpression(Type inputType, Expression inputExpression, TDecision decision) {
        if (inputExpression == null) {
            return "null";
        } else if (isSimple(inputExpression)) {
            String feelExpression = inputExpression.toFEELExpression();
            return dmnTransformer.literalExpressionToJava(decision, feelExpression);
        } else if (isList(inputExpression)) {
            List<Expression> expressionList = ((ListExpression) inputExpression).getElements();
            if (expressionList != null) {
                Type elementType = elementType(inputType);
                List<String> elements = expressionList
                        .stream().map(e -> toJavaExpression(elementType, e, decision)).collect(Collectors.toList());
                String exp = String.join(", ", elements);
                return this.expressionFactory.asList(elementType, exp);
            } else {
                return "null";
            }
        } else if (isComplex(inputExpression)) {
            List<Slot> slots = ((ComplexExpression) inputExpression).getSlots();
            if (slots != null) {
                Set<String> members = members(inputType);
                List<Pair<String, Expression>> pairs = new ArrayList<>();
                Set<String> present = new LinkedHashSet<>();
                for (Slot s: slots) {
                    ImmutablePair<String, Expression> pair = new ImmutablePair<>(s.getItemComponentName(), s.getValue());
                    pairs.add(pair);
                    present.add(pair.getLeft());
                }
                // Add the missing members
                for (String member: members) {
                    if (!present.contains(member)) {
                        ImmutablePair<String, Expression> pair = new ImmutablePair<>(member, null);
                        pairs.add(pair);
                    }
                }
                sortParameters(pairs);
                List<String> args = new ArrayList<>();
                for(Pair<String, Expression> p: pairs) {
                    Type memberType = memberType(inputType, p.getLeft());
                    String arg = toJavaExpression(memberType, p.getRight(), decision);
                    args.add(arg);
                }
                String arguments = String.join(", ", args);
                return dmnTransformer.constructor(dmnTransformer.itemDefinitionJavaClassName(dmnTransformer.toJavaType(inputType)), arguments);
            } else {
                return "null";
            }
        } else {
            throw new UnsupportedOperationException(String.format("%s is not supported", inputExpression.getClass().getSimpleName()));
        }
    }

    TItemDefinition elementType(TItemDefinition type) {
        TDefinitions model = this.dmnModelRepository.getModel(type);
        if (type.isIsCollection()) {
            String typeRef = type.getTypeRef();
            if (typeRef != null) {
                return this.dmnModelRepository.lookupItemDefinition(model, QualifiedName.toQualifiedName(model, typeRef));
            }
            List<TItemDefinition> itemComponent = type.getItemComponent();
            if (itemComponent.size() == 1) {
                return itemComponent.get(0);
            } else {
                return type;
            }
        }
        throw new DMNRuntimeException(String.format("Cannot find element type of type '%s'", type.getName()));
    }

    TItemDefinition memberType(TItemDefinition itemDefinition, Slot slot) {
        String id = slot.getId();
        String name = slot.getItemComponentName();
        String label = slot.getName();
        try {
            // Locate element by name, label or id
            List<TItemDefinition> itemComponent = itemDefinition.getItemComponent();
            if (itemComponent == null || itemComponent.isEmpty()) {
                itemDefinition = this.dmnTransformer.getDMNModelRepository().normalize(itemDefinition);
                itemComponent = itemDefinition.getItemComponent();
            }
            if (!StringUtils.isBlank(name)) {
                for(TItemDefinition child: itemComponent) {
                    if (this.dmnModelRepository.sameName(child, name)) {
                        return child;
                    }
                }
            }
            if (!StringUtils.isBlank(label)) {
                for(TItemDefinition child: itemComponent) {
                    if (this.dmnModelRepository.sameLabel(child, label)) {
                        return child;
                    }
                }
            }
            if (!StringUtils.isBlank(id)) {
                for (TItemDefinition child : itemComponent) {
                    if (this.dmnModelRepository.sameId(child, id)) {
                        return child;
                    }
                }
                for(TItemDefinition child: itemComponent) {
                    if (this.dmnModelRepository.idEndsWith(child, id)) {
                        return child;
                    }
                }
                for(TItemDefinition child: itemComponent) {
                    if (sameSlotId(child, id)) {
                        return child;
                    }
                }
            }
            int index = Integer.parseInt(id);
            TItemDefinition memberType = itemDefinition.getItemComponent().get(index);
            if (memberType != null) {
                return memberType;
            }
        } catch (Exception e) {
            throw new DMNRuntimeException(String.format("Cannot find member '(name='%s' label='%s' id='%s') in ItemDefinition '%s'", name, label, id, itemDefinition.getName()), e);
        }
        throw new DMNRuntimeException(String.format("Cannot find member '(name='%s' label='%s' id='%s') in ItemDefinition '%s'", name, label, id, itemDefinition.getName()));
    }

    private boolean sameSlotId(TItemDefinition child, String id) {
        child.getExtensionElements();
        return false;
    }

    private Type elementType(Type type) {
        Type elementType = null;
        if (type instanceof ListType) {
            elementType = ((ListType) type).getElementType();
        }
        if (elementType == null) {
            throw new DMNRuntimeException(String.format("Cannot find element type of type '%s'", type));
        } else {
            return elementType;
        }
    }

    private Set<String> members(Type type) {
        if (type instanceof ItemDefinitionType) {
            return ((ItemDefinitionType) type).getMembers();
        } else if (type instanceof ContextType) {
            return ((ContextType) type).getMembers();
        } else {
            return new LinkedHashSet<>();
        }
    }

    private Type memberType(Type type, String member) {
        Type memberType = null;
        if (type instanceof ItemDefinitionType) {
            memberType = ((ItemDefinitionType) type).getMemberType(member);
        } else if (type instanceof ContextType) {
            memberType = ((ContextType) type).getMemberType(member);
        }
        if (memberType == null) {
            throw new DMNRuntimeException(String.format("Cannot find member '%s' in type '%s'", member, type));
        } else {
            return memberType;
        }
    }

    private List<Pair<String, Expression>> sortParameters(List<Pair<String, Expression>> parameters) {
        parameters.sort(Comparator.comparing(Pair::getLeft));
        return parameters;
    }

    private Type toFEELType(ParameterDefinition parameterDefinition) {
        try {
            TDRGElement element = findDRGElement(parameterDefinition);
            TDefinitions model = this.dmnModelRepository.getModel(element);
            String typeRef = getTypeRef(parameterDefinition);
            return dmnTransformer.toFEELType(model, QualifiedName.toQualifiedName(model, typeRef));
        } catch (Exception e) {
            throw new DMNRuntimeException(String.format("Cannot resolve FEEL type for requirementId requirement '%s' in DM '%s'", parameterDefinition.getId(), parameterDefinition.getModelName()));
        }
    }

    TItemDefinition lookupItemDefinition(ParameterDefinition parameterDefinition) {
        String typeRef = getTypeRef(parameterDefinition);
        TDRGElement element = findDRGElement(parameterDefinition);
        TDefinitions model = this.dmnModelRepository.getModel(element);
        return dmnTransformer.getDMNModelRepository().lookupItemDefinition(model, QualifiedName.toQualifiedName(model, typeRef));
    }

    private String getTypeRef(ParameterDefinition parameterDefinition) {
        TDRGElement element = findDRGElement(parameterDefinition);
        String typeRef;
        if (element instanceof TInputData) {
            typeRef = ((TInputData) element).getVariable().getTypeRef();
        } else if (element instanceof TDecision) {
            typeRef = ((TDecision) element).getVariable().getTypeRef();
        } else {
            throw new UnsupportedOperationException(String.format("Cannot resolve FEEL type for requirementId requirement '%s'. '%s' not supported", parameterDefinition.getId(), element.getClass().getSimpleName()));
        }
        return typeRef;
    }
}
