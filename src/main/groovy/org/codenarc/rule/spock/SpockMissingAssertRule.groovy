/*
 * Copyright 2023 the original author or authors.
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

import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.DeclarationExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.stmt.AssertStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement

/**
 * Spock treats all expressions on the first level of a then or expect block as an implicit assertion. However,
 * everything inside if/for/switch/... blocks is not an implicit assert, just a useless comparison - unless it is inside
 * a `with`, `verifyAll` or `verifyEach` closure, which turns every expression of its closure into a condition, nested
 * ones included.
 *
 * This rule finds such expressions, where an explicit call to assert would be required. Please note that the rule might
 * produce false positives, as it relies on method names to determine whether an expression has a boolean type or not.
 *
 * @author Jean André Gauthier
 * @author Daniel Clausen
  */
class SpockMissingAssertRule extends AbstractSpockRule {

    String name = 'SpockMissingAssert'
    int priority = 3
    Class astVisitorClass = SpockMissingAssertAstVisitor
}

class SpockMissingAssertAstVisitor extends AbstractSpockAstVisitor<SpockMissingAssertRule> {

    @Override
    void visitConstructorOrMethod(MethodNode node, boolean isConstructor) {
        // Do not inspect fixture / helper methods
        if (SpockUtil.isSpockFeatureMethod(node)) {
            super.visitConstructorOrMethod(node, isConstructor)
        }
    }

    @Override
    void visitDeclarationExpression(DeclarationExpression expression) {
        // Do not inspect declaration expressions
    }

    @Override
    void visitAssertStatement(AssertStatement statement) {
        // Do not inspect assert expressions
    }

    @Override
    void visitExpressionStatement(ExpressionStatement statement) {
        updateCurrentLabel(statement)
        // Do not inspect content in with/verifyAll methods
        if (isMethodsWithImplicitAssertionsExpression(statement)) {
            return
        }
        boolean isInLabelWithImplicitAssertions = inImplicitAssertBlock
        boolean isInTopLevel = nestingDepth == 0
        boolean isBoolean = SpockUtil.isBooleanExpression(statement)
        if (isInLabelWithImplicitAssertions && !isInTopLevel && isBoolean) {
            addViolation(statement, "'${currentLabel}:' might contain a boolean expression in a nested statement, which is not implicitly asserted")
        }
        visitCollectionIterationMethods(statement)
    }

    private static boolean isMethodsWithImplicitAssertionsExpression(ExpressionStatement statement) {
        var variableAndMethod = SpockUtil.getVariableAndMethod(statement)
        var variable = variableAndMethod.v1
        var method = variableAndMethod.v2
        // To keep things simple, we only consider methods called on this
        return variable != null && variable.name == 'this' && method != null && SpockUtil.METHODS_WITH_IMPLICIT_ASSERTIONS.contains(method.value)
    }

    private void visitCollectionIterationMethods(ExpressionStatement statement) {
        // Inspect the arguments from collection iteration methods (i.e loop equivalents)
        if (isCollectionIterationMethods(statement) && statement.expression instanceof MethodCallExpression) {
            MethodCallExpression methodCallExpression = statement.expression as MethodCallExpression
            methodCallExpression.arguments.visit(this)
        }
    }

    private static boolean isCollectionIterationMethods(ExpressionStatement statement) {
        var variableAndMethod = SpockUtil.getVariableAndMethod(statement)
        var method = variableAndMethod.v2
        // Heuristic: assume that methods whose name matches METHODS_FOR_COLLECTION_ITERATION are equivalent to loops
        return method != null && SpockUtil.METHODS_FOR_COLLECTION_ITERATION.contains(method.value)
    }
}
