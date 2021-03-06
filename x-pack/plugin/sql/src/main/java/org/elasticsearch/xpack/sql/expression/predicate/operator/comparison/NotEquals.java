/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.sql.expression.predicate.operator.comparison;

import org.elasticsearch.xpack.sql.expression.Expression;
import org.elasticsearch.xpack.sql.expression.predicate.BinaryOperator;
import org.elasticsearch.xpack.sql.expression.predicate.operator.comparison.BinaryComparisonProcessor.BinaryComparisonOperation;
import org.elasticsearch.xpack.sql.tree.Location;
import org.elasticsearch.xpack.sql.tree.NodeInfo;

public class NotEquals extends BinaryComparison implements BinaryOperator.Negateable {

    public NotEquals(Location location, Expression left, Expression right) {
        super(location, left, right, BinaryComparisonOperation.NEQ);
    }

    @Override
    protected NodeInfo<NotEquals> info() {
        return NodeInfo.create(this, NotEquals::new, left(), right());
    }

    @Override
    protected NotEquals replaceChildren(Expression newLeft, Expression newRight) {
        return new NotEquals(location(), newLeft, newRight);
    }

    @Override
    public NotEquals swapLeftAndRight() {
        return new NotEquals(location(), right(), left());
    }

    @Override
    public BinaryOperator<?, ?, ?, ?> negate() {
        return new Equals(location(), left(), right());
    }
}
