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
package com.gs.dmn.signavio.rdf2dmn.json.expression;

import com.gs.dmn.signavio.rdf2dmn.json.Context;
import com.gs.dmn.signavio.rdf2dmn.json.Visitor;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class List extends Expression {
    private final java.util.List<Expression> value = new ArrayList<>();

    public java.util.List<Expression> getValue() {
        return value;
    }

    @Override
    public String toString() {
        String list = value.stream().map(Object::toString).collect(Collectors.joining(", "));
        return String.format("%s(%s)", this.getClass().getSimpleName(), list);
    }

    @Override
    public String accept(Visitor visitor, Context params) {
        return visitor.visit(this, params);
    }

}
