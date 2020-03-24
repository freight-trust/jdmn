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
package com.gs.dmn.serialization;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gs.dmn.runtime.serialization.XMLGregorianCalendarDeserializer;
import com.gs.dmn.runtime.serialization.XMLGregorianCalendarSerializer;

import javax.xml.datatype.XMLGregorianCalendar;

public class JsonSerializer {
    public static final ObjectMapper OBJECT_MAPPER;

    static {
        SimpleModule module = new SimpleModule();
        module.addSerializer(XMLGregorianCalendar.class, new XMLGregorianCalendarSerializer());
        module.addDeserializer(XMLGregorianCalendar.class, new XMLGregorianCalendarDeserializer());

        OBJECT_MAPPER = JsonMapper.builder()
                .addModule(module)
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .visibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.NONE)
                .visibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.PUBLIC_ONLY)
                .visibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.PUBLIC_ONLY)
                .visibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.NONE)
                .build();
    }
}