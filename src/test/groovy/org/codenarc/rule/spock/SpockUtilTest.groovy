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

import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codenarc.source.SourceString
import org.codenarc.test.AbstractTestCase
import org.junit.jupiter.api.Test

/**
 * Tests for the helper methods of SpockUtil that are not covered by a rule test.
 *
 * @author Leonard Bruenings
 */
class SpockUtilTest extends AbstractTestCase {

    private static final String ANNOTATED_SOURCE = '''
        @Stepwise
        class BaseSpec extends spock.lang.Specification {
        }

        class MySpec extends BaseSpec {
            @IgnoreRest
            def "annotated feature"() {
                expect: true
            }

            def "plain feature"() {
                expect: true
            }
        }

        class UnrelatedSpec extends SomeSpecFromAnotherFile {
            def "plain feature"() {
                expect: true
            }
        }
    '''.stripIndent()

    @Test
    void hasAnnotation_AnnotationOnMethod_True() {
        assert SpockUtil.hasAnnotation(method(ANNOTATED_SOURCE, 'MySpec', 'annotated feature'), 'IgnoreRest')
        assert !SpockUtil.hasAnnotation(method(ANNOTATED_SOURCE, 'MySpec', 'plain feature'), 'IgnoreRest')
        assert !SpockUtil.hasAnnotation(null, 'IgnoreRest')
    }

    @Test
    void findAnnotation_MatchingSimpleName_ReturnsAnnotationNode() {
        def annotation = SpockUtil.findAnnotation(classNode(ANNOTATED_SOURCE, 'BaseSpec'), 'Stepwise')
        assert annotation != null
        assert annotation.classNode.nameWithoutPackage == 'Stepwise'
        assert SpockUtil.findAnnotation(classNode(ANNOTATED_SOURCE, 'MySpec'), 'Stepwise') == null
    }

    @Test
    void getAnnotationMember_ImplicitValueMember_ReturnsExpression() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                @Timeout(5)
                def "feature"() {
                    expect: true
                }
            }
        '''.stripIndent()
        def annotation = SpockUtil.findAnnotation(method(SOURCE, 'MySpec', 'feature'), 'Timeout')
        Expression member = SpockUtil.getAnnotationMember(annotation, 'value')
        assert member instanceof ConstantExpression
        assert (member as ConstantExpression).value == 5
        assert SpockUtil.getAnnotationMember(annotation, 'unit') == null
        assert SpockUtil.getAnnotationMember(null, 'value') == null
    }

    @Test
    void hasAnnotationOnMethodOrClass_AnnotationOnSuperclassInSameSourceUnit_True() {
        assert SpockUtil.hasAnnotationOnMethodOrClass(method(ANNOTATED_SOURCE, 'MySpec', 'annotated feature'), 'IgnoreRest')
        assert SpockUtil.hasAnnotationOnMethodOrClass(method(ANNOTATED_SOURCE, 'MySpec', 'plain feature'), 'Stepwise')
        assert !SpockUtil.hasAnnotationOnMethodOrClass(method(ANNOTATED_SOURCE, 'MySpec', 'plain feature'), 'IgnoreRest')
        assert !SpockUtil.hasAnnotationOnMethodOrClass(null, 'Stepwise')
    }

    @Test
    void hasAnnotationOnMethodOrClass_SuperclassInAnotherSourceUnit_False() {
        // The superclass cannot be resolved without a classpath, so absence of evidence must stay silent
        assert !SpockUtil.hasAnnotationOnMethodOrClass(method(ANNOTATED_SOURCE, 'UnrelatedSpec', 'plain feature'), 'Stepwise')
    }

    @Test
    void isFixtureMethod_SpockFixtureMethods_True() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                def setup() { }
                def cleanup() { }
                def setupSpec() { }
                def cleanupSpec() { }
                def setup(int withParameter) { }
                def helper() { }
                def "feature"() {
                    expect: true
                }
            }
        '''.stripIndent()
        assert SpockUtil.isFixtureMethod(method(SOURCE, 'MySpec', 'setup'))
        assert SpockUtil.isFixtureMethod(method(SOURCE, 'MySpec', 'cleanup'))
        assert SpockUtil.isFixtureMethod(method(SOURCE, 'MySpec', 'setupSpec'))
        assert SpockUtil.isFixtureMethod(method(SOURCE, 'MySpec', 'cleanupSpec'))
        assert !SpockUtil.isFixtureMethod(methods(SOURCE, 'MySpec', 'setup').find { it.parameters.length == 1 })
        assert !SpockUtil.isFixtureMethod(method(SOURCE, 'MySpec', 'helper'))
        assert !SpockUtil.isFixtureMethod(null)
    }

    @Test
    void mockCreation_AllFactoryMethods_AreRecognized() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                def "feature"() {
                    given:
                    def a = Mock(Service)
                    def b = Stub(Service)
                    def c = Spy(Service)
                    def d = GroovyMock(Service)
                    def e = GroovyStub(Service)
                    def f = GroovySpy(Service)
                    def g = service()
                    def h = 42

                    expect:
                    true
                }
            }
        '''.stripIndent()
        def expressions = statementExpressions(SOURCE, 'MySpec', 'feature')
        assert expressions.collect { SpockUtil.mockKind(rightHandSide(it)) } ==
            ['Mock', 'Stub', 'Spy', 'GroovyMock', 'GroovyStub', 'GroovySpy', null, null]
        assert SpockUtil.isMockCreation(rightHandSide(expressions[0]))
        assert !SpockUtil.isMockCreation(rightHandSide(expressions[6]))
        assert !SpockUtil.isMockCreation(null)
    }

    @Test
    void mockedType_TypeArgumentOrDeclaredType_IsResolved() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                def "feature"() {
                    given:
                    def a = Mock(Service)
                    Service b = Mock()
                    def c = Stub(Service) { getName() >> 'x' }
                    def d = Mock()
                    def e = service()

                    expect:
                    true
                }
            }
        '''.stripIndent()
        def expressions = statementExpressions(SOURCE, 'MySpec', 'feature')
        assert expressions.collect { SpockUtil.mockedType(it)?.nameWithoutPackage } ==
            ['Service', 'Service', 'Service', null, null]
    }

    //--------------------------------------------------------------------------
    // Helper methods
    //--------------------------------------------------------------------------

    private static ModuleNode ast(String source) {
        new SourceString(source).ast
    }

    private static ClassNode classNode(String source, String className) {
        ast(source).classes.find { it.nameWithoutPackage == className }
    }

    private static List<MethodNode> methods(String source, String className, String methodName) {
        classNode(source, className).methods.findAll { it.name == methodName }
    }

    private static MethodNode method(String source, String className, String methodName) {
        methods(source, className, methodName).first()
    }

    private static List<Expression> statementExpressions(String source, String className, String methodName) {
        BlockStatement code = method(source, className, methodName).code as BlockStatement
        code.statements.findAll { it instanceof ExpressionStatement }
            .collect { (it as ExpressionStatement).expression }
            .findAll { it.class.simpleName == 'DeclarationExpression' }
    }

    private static Expression rightHandSide(Expression declaration) {
        declaration.rightExpression
    }
}
