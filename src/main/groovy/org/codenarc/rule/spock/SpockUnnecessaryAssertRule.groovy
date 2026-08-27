/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codenarc.rule.spock

import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.stmt.AssertStatement

/**
 * Spock treats every top-level expression of a then, expect or filter block - and every expression of a
 * `with`, `verifyAll` or `verifyEach` closure, at any nesting level - as an implicit condition. Writing
 * `assert` there is redundant: it compiles to the same condition, and it hides the fact that the
 * surrounding block is an assertion block.
 *
 * This rule is the inverse of SpockMissingAssert, which demands an explicit `assert` exactly where
 * this rule does not report: inside if/for/while/switch/try statements of a then, expect or filter
 * block, where Spock does not add implicit conditions. An `assert` that carries a message is never
 * reported, because an implicit condition cannot carry one.
 *
 * @author Leonard Bruenings
 */
class SpockUnnecessaryAssertRule extends AbstractSpockRule {

    String name = 'SpockUnnecessaryAssert'
    int priority = 3
    Class astVisitorClass = SpockUnnecessaryAssertAstVisitor
}

class SpockUnnecessaryAssertAstVisitor extends AbstractSpockAstVisitor<SpockUnnecessaryAssertRule> {

    @Override
    void visitAssertStatement(AssertStatement statement) {
        // Updates the current label - the label may be attached to this very statement - and traverses
        super.visitAssertStatement(statement)
        if (!inFeatureMethod || hasMessage(statement) || !inImplicitAssertionContext) {
            return
        }
        addViolation(statement, describeContext())
    }

    private String describeContext() {
        if (inClosure) {
            return "'assert' is redundant in a '${enclosingImplicitAssertionMethod}' block - Spock treats every expression as an implicit condition"
        }
        "'assert' is redundant in a '${currentLabel}:' block - Spock treats top-level expressions as implicit conditions"
    }

    private static boolean hasMessage(AssertStatement statement) {
        Expression messageExpression = statement.messageExpression
        // An assert without a message carries a ConstantExpression holding null, not a null expression
        if (messageExpression == null) {
            return false
        }
        return !(messageExpression instanceof ConstantExpression && (messageExpression as ConstantExpression).value == null)
    }
}
