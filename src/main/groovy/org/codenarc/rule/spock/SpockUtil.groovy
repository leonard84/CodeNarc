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

import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.DeclarationExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.TupleExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.AssertStatement
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codenarc.util.WildcardPattern

import java.util.regex.Pattern

/**
 * Utility methods for Spock rule classes. This class is not intended for general use.
 *
 * @author Leonard Bruenings
 */
class SpockUtil {

    // Intentionally omitting 'and', as it doesn't have any semantic impact
    static final List<String> SPOCK_LABELS = ['given', 'when', 'then', 'expect', 'where', 'cleanup', 'setup', 'combined', 'filter']

    static final List<String> LABELS_WITH_IMPLICIT_ASSERTIONS = ['then', 'expect', 'filter']

    static final List<String> METHODS_WITH_IMPLICIT_ASSERTIONS = ['with', 'verifyAll', 'verifyEach']

    static final List<String> METHODS_FOR_COLLECTION_ITERATION = ['each', 'eachWithIndex', 'times']

    static final List<String> FIXTURE_METHOD_NAMES = ['setup', 'cleanup', 'setupSpec', 'cleanupSpec']

    static final List<String> MOCK_FACTORY_METHODS = ['Mock', 'Stub', 'Spy', 'GroovyMock', 'GroovyStub', 'GroovySpy']

    private static final List<Pattern> BOOLEAN_METHOD_PATTERNS = [
        ~/^is(\p{Lu}.*)?/,
        ~/^has(\p{Lu}.*)?/,
        ~/^asBoolean$/,
        ~/^any(\p{Lu}.*)?/,
        ~/^contains(\p{Lu}.*)?/,
        ~/^every(\p{Lu}.*)?/,
        ~/^equals(\p{Lu}.*)?/,
    ]

    private static final Set<String> BOOLEAN_OPERATORS = [
        '==', '!=', '<', '<=', '>', '>=', '===', '!==',  // relational
        '&&', '||',                                      // logical
        '==~',                                           // regex
        'instanceof',                                    // type check
        'in',                                            // membership
    ] as Set

    static boolean isSpockSpecification(ClassNode classNode, String specificationSuperclassNames, String specificationClassNames) {
        def superClassPattern = new WildcardPattern(specificationSuperclassNames)
        def classNamePattern = new WildcardPattern(specificationClassNames, false)
        return superClassPattern.matches(classNode.superClass.name) || classNamePattern.matches(classNode.name)
    }

    static boolean isBooleanExpression(ExpressionStatement statement) {
        // Handles literals & casts / coercion operators
        if (statement.expression.type.name == 'boolean' || statement.expression.type.name == 'Boolean') {
            return true
        }
        // Handles binary expressions
        if (statement.expression instanceof BinaryExpression) {
            BinaryExpression binaryExpression = statement.expression as BinaryExpression
            if (binaryExpression.operation.text in BOOLEAN_OPERATORS) {
                return true
            }
        }
        var variableAndMethod = getVariableAndMethod(statement)
        var method = variableAndMethod.v2
        // Heuristic: assume that methods whose name matches BOOLEAN_METHOD_PATTERNS return a boolean
        return method != null && BOOLEAN_METHOD_PATTERNS.any { it -> method.value.toString().matches(it) }
    }

    static boolean isImplicitAssertBlock(String label) {
        return label in LABELS_WITH_IMPLICIT_ASSERTIONS
    }

    static boolean isSpockFeatureMethod(MethodNode node) {
        if (node.code instanceof BlockStatement) {
            BlockStatement block = (BlockStatement) node.code
            // To be considered as a feature method by Spock, the method must have at least one statement label.
            // More details can be found in org.spockframework.compiler.SpecParser.isFeatureMethod() at
            // https://github.com/spockframework/spock/blob/52e7688b3f89533857006539e5905c9b4121f32b/spock-core/src/main/java/org/spockframework/compiler/SpecParser.java#LL153C5-L153C5
            return block.statements.any(s -> s.statementLabels != null && !s.statementLabels.intersect(SPOCK_LABELS).isEmpty())
        }
        return false
    }

    static Tuple2<VariableExpression, ConstantExpression> getVariableAndMethod(ExpressionStatement statement) {
        var variable = null
        var method = null
        if (statement.expression instanceof MethodCallExpression) {
            MethodCallExpression methodCall = statement.expression as MethodCallExpression
            if (methodCall.objectExpression instanceof VariableExpression) {
                variable = methodCall.objectExpression as VariableExpression
            }
            if (methodCall.method instanceof ConstantExpression) {
                method = methodCall.method as ConstantExpression
            }
        }
        return Tuple.tuple(variable, method)
    }

    static String getMethodName(MethodCallExpression methodCall) {
        if (methodCall.method instanceof ConstantExpression) {
            return (methodCall.method as ConstantExpression).value?.toString()
        }
        return null
    }

    static ClosureExpression getClosureArgument(MethodCallExpression methodCall) {
        def args = methodCall.arguments
        if (args.expressions) {
            def lastArg = args.expressions.last()
            if (lastArg instanceof ClosureExpression) {
                return lastArg as ClosureExpression
            }
        }
        return null
    }

    static boolean closureContainsAssertions(ClosureExpression closure, boolean checkBooleanExpressions) {
        if (!(closure.code instanceof BlockStatement)) {
            return false
        }
        BlockStatement block = closure.code as BlockStatement
        return block.statements.any { Statement stmt ->
            if (stmt instanceof AssertStatement) {
                return true
            }
            if (checkBooleanExpressions && stmt instanceof ExpressionStatement) {
                return isBooleanExpression(stmt as ExpressionStatement)
            }
            return false
        }
    }

    /**
     * @param node - the annotated node (class, method, field, ...)
     * @param simpleName - the annotation name without its package, e.g. 'IgnoreRest'
     * @return true if the node carries an annotation with that simple name
     */
    static boolean hasAnnotation(AnnotatedNode node, String simpleName) {
        return findAnnotation(node, simpleName) != null
    }

    /**
     * @param node - the annotated node (class, method, field, ...)
     * @param simpleName - the annotation name without its package, e.g. 'IgnoreRest'
     * @return the first annotation with that simple name, or null if there is none
     */
    static AnnotationNode findAnnotation(AnnotatedNode node, String simpleName) {
        return node?.annotations?.find { AnnotationNode annotation ->
            annotation.classNode.nameWithoutPackage == simpleName
        }
    }

    /**
     * Return true if the method itself, its declaring class, or any superclass of the declaring class
     * <em>declared in the same source file</em> carries the annotation.
     *
     * CodeNarc runs without a classpath, so a superclass that lives in another file cannot be
     * resolved and is therefore not inspected. Absence of evidence must not produce a violation.
     *
     * @param node - the method
     * @param simpleName - the annotation name without its package, e.g. 'Stepwise'
     * @return true if the annotation was found on the method or on a resolvable enclosing class
     */
    static boolean hasAnnotationOnMethodOrClass(MethodNode node, String simpleName) {
        if (node == null) {
            return false
        }
        if (hasAnnotation(node, simpleName)) {
            return true
        }
        ModuleNode module = node.declaringClass?.module
        ClassNode currentClass = node.declaringClass
        Set<String> visitedClassNames = [] as Set
        while (currentClass != null && visitedClassNames.add(currentClass.name)) {
            if (hasAnnotation(currentClass, simpleName)) {
                return true
            }
            currentClass = findSuperClassInSameSourceUnit(currentClass, module)
        }
        return false
    }

    /**
     * @param annotation - the annotation, may be null
     * @param member - the member name; use 'value' for the implicit member
     * @return the member's value expression, or null if the annotation does not set that member
     */
    static Expression getAnnotationMember(AnnotationNode annotation, String member) {
        return annotation?.getMember(member)
    }

    /**
     * @param node - the method
     * @return true if the method is one of Spock's fixture methods (setup, cleanup, setupSpec,
     *         cleanupSpec) declared without parameters
     */
    static boolean isFixtureMethod(MethodNode node) {
        return node != null && node.name in FIXTURE_METHOD_NAMES && (node.parameters == null || node.parameters.length == 0)
    }

    /**
     * @param expression - the expression to inspect
     * @return true if the expression creates a Spock mock, i.e. a call to Mock/Stub/Spy/GroovyMock/
     *         GroovyStub/GroovySpy, with or without a type argument and with or without a trailing
     *         initializer closure
     */
    static boolean isMockCreation(Expression expression) {
        return mockKind(expression) != null
    }

    /**
     * @param expression - the expression to inspect
     * @return the name of the mock factory method ('Mock', 'Stub', 'Spy', 'GroovyMock', 'GroovyStub',
     *         'GroovySpy'), or null if the expression does not create a mock
     */
    static String mockKind(Expression expression) {
        if (!(expression instanceof MethodCallExpression)) {
            return null
        }
        MethodCallExpression methodCall = expression as MethodCallExpression
        String methodName = getMethodName(methodCall)
        return methodName in MOCK_FACTORY_METHODS ? methodName : null
    }

    /**
     * Determine the type being mocked. Pass either the mock creation expression itself, or the
     * declaration expression that assigns it, in which case the declared type of the variable is used
     * as a fallback: <code>Foo foo = Mock()</code>.
     *
     * @param expression - a mock creation expression or a declaration whose right-hand side creates a mock
     * @return the mocked type, or null if it cannot be determined without a classpath
     */
    static ClassNode mockedType(Expression expression) {
        Expression mockExpression = expression
        ClassNode declaredType = null
        if (expression instanceof DeclarationExpression) {
            DeclarationExpression declaration = expression as DeclarationExpression
            mockExpression = declaration.rightExpression
            if (declaration.leftExpression instanceof VariableExpression) {
                VariableExpression variable = declaration.leftExpression as VariableExpression
                declaredType = variable.dynamicTyped ? null : variable.type
            }
        }
        if (!isMockCreation(mockExpression)) {
            return null
        }
        ClassNode typeArgument = findClassArgument(mockExpression as MethodCallExpression)
        return typeArgument ?: declaredType
    }

    private static ClassNode findSuperClassInSameSourceUnit(ClassNode classNode, ModuleNode module) {
        ClassNode superClass = classNode.superClass
        if (superClass == null || module == null) {
            return null
        }
        // At the CONVERSION compiler phase the superclass reference is not resolved yet, so look the
        // class up by name among the classes declared in the same source unit.
        return module.classes.find { ClassNode candidate ->
            candidate.name == superClass.name || candidate.nameWithoutPackage == superClass.nameWithoutPackage
        }
    }

    private static ClassNode findClassArgument(MethodCallExpression methodCall) {
        Expression arguments = methodCall.arguments
        if (!(arguments instanceof TupleExpression)) {
            return null
        }
        for (Expression argument : (arguments as TupleExpression).expressions) {
            if (argument instanceof ClassExpression) {
                return (argument as ClassExpression).type
            }
            // At the CONVERSION compiler phase a bare type name is still an unresolved VariableExpression
            if (argument instanceof VariableExpression) {
                String name = (argument as VariableExpression).name
                if (name && Character.isUpperCase(name.charAt(0))) {
                    return ClassHelper.make(name)
                }
            }
        }
        return null
    }

    private SpockUtil() { }
}
