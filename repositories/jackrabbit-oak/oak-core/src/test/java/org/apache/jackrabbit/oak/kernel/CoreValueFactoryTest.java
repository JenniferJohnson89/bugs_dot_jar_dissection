/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.kernel;

import org.apache.jackrabbit.mk.core.MicroKernelImpl;
import org.apache.jackrabbit.mk.json.JsopReader;
import org.apache.jackrabbit.mk.json.JsopTokenizer;
import org.apache.jackrabbit.oak.api.CoreValue;
import org.apache.jackrabbit.oak.api.CoreValueFactory;
import org.junit.Before;
import org.junit.Test;

import javax.jcr.PropertyType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

/**
 * CoreValueUtilTest...
 */
public class CoreValueFactoryTest {

    private final CoreValueFactory valueFactory =
            new CoreValueFactoryImpl(new MicroKernelImpl());

    private Map<CoreValue, String> singleValueMap;

    private Map<String, List<CoreValue>> mvValueMap;

    @Before
    public void setUp() throws IOException {
        singleValueMap = new HashMap<CoreValue, String>();
        singleValueMap.put(valueFactory.createValue("abc"), "\"abc\"");
        singleValueMap.put(valueFactory.createValue("a:bc"), "\"a:bc\"");
        singleValueMap.put(valueFactory.createValue("a:bc"), "\"a:bc\"");
        singleValueMap.put(valueFactory.createValue("boo:abc"), "\"str:boo:abc\"");
        singleValueMap.put(valueFactory.createValue("str:abc"), "\"str:str:abc\"");
        singleValueMap.put(valueFactory.createValue("str:"), "\"str:str:\"");

        singleValueMap.put(valueFactory.createValue(true), "true");
        singleValueMap.put(valueFactory.createValue(false), "false");

        singleValueMap.put(valueFactory.createValue(12345), "12345");
        singleValueMap.put(valueFactory.createValue(1.23), "\"dou:1.23\"");
        BigDecimal decimal = new BigDecimal("12345678901234567890");
        singleValueMap.put(valueFactory.createValue(decimal), "\"dec:" + decimal.toString() + '\"');

        singleValueMap.put(valueFactory.createValue("2012-05-01T12:00.000:00GMT", PropertyType.DATE), "\"dat:2012-05-01T12:00.000:00GMT\"");

        singleValueMap.put(valueFactory.createValue("jcr:primaryType", PropertyType.NAME), "\"nam:jcr:primaryType\"");
        singleValueMap.put(valueFactory.createValue("/jcr:system", PropertyType.PATH), "\"pat:/jcr:system\"");

        singleValueMap.put(valueFactory.createValue("http://jackrabbit.apache.org", PropertyType.URI), "\"uri:http://jackrabbit.apache.org\"");

        String uuid = UUID.randomUUID().toString();
        singleValueMap.put(valueFactory.createValue(uuid, PropertyType.REFERENCE), "\"ref:" +uuid+ '\"');
        singleValueMap.put(valueFactory.createValue(uuid, PropertyType.WEAKREFERENCE), "\"wea:" +uuid+ '\"');

        CoreValue binary = valueFactory.createValue(new ByteArrayInputStream("123".getBytes()));
        singleValueMap.put(binary, "\"bin:"+ binary.getString()+ '\"');

        // multi valued properties
        mvValueMap = new HashMap<String, List<CoreValue>>();
        mvValueMap.put("[]", Collections.<CoreValue>emptyList());

        List<CoreValue> strValues = new ArrayList<CoreValue>();
        strValues.add(valueFactory.createValue("abc"));
        strValues.add(valueFactory.createValue("a:bc"));
        strValues.add(valueFactory.createValue("boo:abc"));
        strValues.add(valueFactory.createValue("str:abc"));
        strValues.add(valueFactory.createValue("str:"));
        mvValueMap.put("[\"abc\",\"a:bc\",\"str:boo:abc\",\"str:str:abc\",\"str:str:\"]", strValues);

        List<CoreValue> boValues = new ArrayList<CoreValue>();
        boValues.add(valueFactory.createValue(true));
        boValues.add(valueFactory.createValue(false));
        mvValueMap.put("[true,false]", boValues);

        List<CoreValue> longs = new ArrayList<CoreValue>();
        longs.add(valueFactory.createValue(1));
        longs.add(valueFactory.createValue(2));
        longs.add(valueFactory.createValue(3));
        mvValueMap.put("[1,2,3]", longs);

        List<CoreValue> doubles = new ArrayList<CoreValue>();
        doubles.add(valueFactory.createValue(1.23));
        mvValueMap.put("[\"dou:1.23\"]", doubles);

        List<CoreValue> decimals = new ArrayList<CoreValue>();
        decimals.add(valueFactory.createValue(decimal));
        decimals.add(valueFactory.createValue(decimal));
        mvValueMap.put("[\"dec:" + decimal.toString() + "\",\"dec:" + decimal.toString() + "\"]", decimals);

        List<CoreValue> dates = Collections.singletonList(valueFactory.createValue("2012-05-01T12:00.000:00GMT", PropertyType.DATE));
        mvValueMap.put("[\"dat:2012-05-01T12:00.000:00GMT\"]", dates);

        List<CoreValue> names = Collections.singletonList(valueFactory.createValue("jcr:primaryType", PropertyType.NAME));
        mvValueMap.put("[\"nam:jcr:primaryType\"]", names);

        List<CoreValue> paths = new ArrayList<CoreValue>();
        paths.add(valueFactory.createValue("/jcr:system", PropertyType.PATH));
        paths.add(valueFactory.createValue("../../content", PropertyType.PATH));
        mvValueMap.put("[\"pat:/jcr:system\",\"pat:../../content\"]", paths);

        List<CoreValue> uris = Collections.singletonList(valueFactory.createValue("http://jackrabbit.apache.org", PropertyType.URI));
        mvValueMap.put("[\"uri:http://jackrabbit.apache.org\"]", uris);

        List<CoreValue> refs = new ArrayList<CoreValue>();
        refs.add(valueFactory.createValue(uuid, PropertyType.REFERENCE));
        mvValueMap.put("[\"ref:" +uuid+ "\"]", refs);

        List<CoreValue> wr = new ArrayList<CoreValue>();
        wr.add(valueFactory.createValue(uuid, PropertyType.WEAKREFERENCE));
        mvValueMap.put("[\"wea:" +uuid+ "\"]", wr);

        mvValueMap.put("[\"bin:"+ binary.getString()+ "\"]", Collections.singletonList(binary));
    }

    @Test
    public void testToJsonValue() throws IOException {
        for (CoreValue v : singleValueMap.keySet()) {
            String json = singleValueMap.get(v);
            assertEquals(json, CoreValueMapper.toJsonValue(v));
        }
    }

    @Test
    public void testFromJsonValue() throws IOException {
        for (CoreValue v : singleValueMap.keySet()) {
            String json = singleValueMap.get(v);
            JsopReader reader = new JsopTokenizer(json);
            assertEquals(v, CoreValueMapper.fromJsopReader(reader, valueFactory));
        }
    }

    @Test
    public void testToJsonArray() throws IOException {
        for (String json : mvValueMap.keySet()) {
            List<CoreValue> values = mvValueMap.get(json);
            assertEquals(json, CoreValueMapper.toJsonArray(values));
        }
    }

    @Test
    public void testListFromJsopReader() throws IOException {
        for (String json : mvValueMap.keySet()) {
            List<CoreValue> values = mvValueMap.get(json);
            JsopReader reader = new JsopTokenizer(json);
            if (reader.matches('[')) {
                assertEquals(values, CoreValueMapper.listFromJsopReader(reader, valueFactory));
            }
        }
    }

}