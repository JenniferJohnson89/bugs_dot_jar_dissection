/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.oak.query.ast;

import org.apache.jackrabbit.oak.api.CoreValue;
import org.apache.jackrabbit.oak.query.index.FilterImpl;

public class UpperCaseImpl extends DynamicOperandImpl {

    private final DynamicOperandImpl operand;

    public UpperCaseImpl(DynamicOperandImpl operand) {
        this.operand = operand;
    }

    public DynamicOperandImpl getOperand() {
        return operand;
    }

    @Override
    boolean accept(AstVisitor v) {
        return v.visit(this);
    }

    @Override
    public String toString() {
        return "UPPER(" + operand + ')';
    }

    @Override
    public CoreValue currentValue() {
        CoreValue v = operand.currentValue();
        if (v == null) {
            return null;
        }
        String value = v.getString();
        return query.getValueFactory().createValue(value.toUpperCase());
    }

    @Override
    public void apply(FilterImpl f, Operator operator, CoreValue v) {
        // ignore
        // TODO UPPER(x) conditions: can use IS NOT NULL?
    }

}
