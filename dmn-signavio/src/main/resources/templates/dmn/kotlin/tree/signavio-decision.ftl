<#--
    Copyright 2016 Goldman Sachs.

    Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.

    You may obtain a copy of the License at
        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations under the License.
-->
<#include "signavio-drgElementCommon.ftl">
<#if javaPackageName?has_content>
package ${javaPackageName}
</#if>

import java.util.*
import java.util.stream.Collectors

@javax.annotation.Generated(value = ["signavio-decision.ftl", "${modelRepository.name(drgElement)}"])
@${transformer.drgElementAnnotationClassName()}(
    namespace = "${javaPackageName}",
    name = "${modelRepository.name(drgElement)}",
    label = "${modelRepository.label(drgElement)}",
    elementKind = ${transformer.elementKindAnnotationClassName()}.${transformer.elementKind(drgElement)},
    expressionKind = ${transformer.expressionKindAnnotationClassName()}.${transformer.expressionKind(drgElement)},
    hitPolicy = ${transformer.hitPolicyAnnotationClassName()}.${transformer.hitPolicy(drgElement)},
    rulesCount = ${modelRepository.rulesCount(drgElement)}
)
class ${javaClassName}(${transformer.decisionConstructorSignature(drgElement)}) : ${decisionBaseClass}() {
    <#if transformer.shouldGenerateApplyWithConversionFromString(drgElement)>
    fun apply(${transformer.drgElementSignatureWithConversionFromString(drgElement)}): ${transformer.drgElementOutputType(drgElement)} {
        return try {
            apply(${transformer.drgElementDefaultArgumentsExtraCacheWithConversionFromString(drgElement)})
        } catch (e: Exception) {
            logError("Cannot apply decision '${javaClassName}'", e)
            null
        }
    }

    <#if transformer.isCaching()>
    fun apply(${transformer.drgElementSignatureExtraWithConversionFromString(drgElement)}): ${transformer.drgElementOutputType(drgElement)} {
        return try {
            val ${transformer.cacheVariableName()} = ${transformer.defaultCacheClassName()}()
            apply(${transformer.drgElementArgumentsExtraCacheWithConversionFromString(drgElement)})
        } catch (e: Exception) {
            logError("Cannot apply decision '${javaClassName}'", e)
            null
        }
    }

    </#if>
    fun apply(${transformer.drgElementSignatureExtraCacheWithConversionFromString(drgElement)}): ${transformer.drgElementOutputType(drgElement)} {
        return try {
            apply(${transformer.drgElementArgumentsExtraCacheWithConversionFromString(drgElement)})
        } catch (e: Exception) {
            logError("Cannot apply decision '${javaClassName}'", e)
            null
        }
    }

    </#if>
    fun apply(${transformer.drgElementSignature(drgElement)}): ${transformer.drgElementOutputType(drgElement)} {
        return apply(${transformer.drgElementDefaultArgumentsExtraCache(drgElement)})
    }

    fun apply(${transformer.drgElementSignatureExtraCache(drgElement)}): ${transformer.drgElementOutputType(drgElement)} {
        <@applyMethodBody drgElement />
    }
    <@evaluateExpressionMethod drgElement />

    companion object {
        val ${transformer.drgElementMetadataFieldName()} : ${transformer.drgElementMetadataClassName()} = ${transformer.drgElementMetadataClassName()}(
            "${javaPackageName}",
            "${modelRepository.name(drgElement)}",
            "${modelRepository.label(drgElement)}",
            ${transformer.elementKindAnnotationClassName()}.${transformer.elementKind(drgElement)},
            ${transformer.expressionKindAnnotationClassName()}.${transformer.expressionKind(drgElement)},
            ${transformer.hitPolicyAnnotationClassName()}.${transformer.hitPolicy(drgElement)},
            ${modelRepository.rulesCount(drgElement)}
        )
    }
}
