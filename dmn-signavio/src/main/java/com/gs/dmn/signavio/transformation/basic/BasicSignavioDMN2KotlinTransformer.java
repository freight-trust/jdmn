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
package com.gs.dmn.signavio.transformation.basic;

import com.gs.dmn.DMNModelRepository;
import com.gs.dmn.DRGElementReference;
import com.gs.dmn.feel.analysis.semantics.environment.EnvironmentFactory;
import com.gs.dmn.feel.synthesis.expression.KotlinExpressionFactory;
import com.gs.dmn.feel.synthesis.type.NativeTypeFactory;
import com.gs.dmn.transformation.basic.BasicDMNToNativeTransformer;
import com.gs.dmn.transformation.lazy.LazyEvaluationDetector;
import org.omg.spec.dmn._20180521.model.TDRGElement;
import org.omg.spec.dmn._20180521.model.TItemDefinition;

import java.util.Map;

public class BasicSignavioDMN2KotlinTransformer extends BasicSignavioDMN2JavaTransformer {
    public BasicSignavioDMN2KotlinTransformer(DMNModelRepository dmnModelRepository, EnvironmentFactory environmentFactory, NativeTypeFactory nativeTypeFactory, LazyEvaluationDetector lazyEvaluationDetector, Map<String, String> inputParameters) {
        super(dmnModelRepository, environmentFactory, nativeTypeFactory, lazyEvaluationDetector, inputParameters);
    }

    @Override
    protected void setNativeExpressionFactory(BasicDMNToNativeTransformer transformer) {
        this.nativeExpressionFactory = new KotlinExpressionFactory(this);
    }

    // Types
    @Override
    public String itemDefinitionNativeQualifiedInterfaceName(TItemDefinition itemDefinition) {
        return this.nativeTypeFactory.nullableType(super.itemDefinitionNativeQualifiedInterfaceName(itemDefinition));
    }

    @Override
    public String itemDefinitionNativeClassName(String interfaceName) {
        if (interfaceName.endsWith("?")) {
            return interfaceName.replace("?", "Impl?");
        } else {
            return interfaceName + "Impl";
        }
    }

    @Override
    protected String makeListType(String listType, String elementType) {
        return this.nativeTypeFactory.nullableType(String.format("%s<%s>", listType, this.nativeTypeFactory.nullableType(elementType)));
    }

    @Override
    protected String makeListType(String listType) {
        return this.nativeTypeFactory.nullableType(String.format("%s<Any?>", listType));
    }

    @Override
    protected String makeFunctionType(String name, String returnType) {
        return this.nativeTypeFactory.nullableType(String.format("%s<%s>", name, this.nativeTypeFactory.nullableType(returnType)));
    }

    @Override
    public String drgElementOutputType(DRGElementReference<? extends TDRGElement> reference) {
        return this.nativeTypeFactory.nullableType(super.drgElementOutputType(reference.getElement()));
    }

    @Override
    public String lazyEvaluation(String elementName, String nativeName) {
        return isLazyEvaluated(elementName) ? String.format("%s?.getOrCompute()", nativeName) : nativeName;
    }
}
