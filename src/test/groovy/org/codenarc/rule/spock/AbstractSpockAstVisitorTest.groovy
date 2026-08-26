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

import org.codehaus.groovy.ast.stmt.AssertStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codenarc.rule.AbstractRuleTestCase
import org.junit.jupiter.api.Test

/**
 * Tests for AbstractSpockAstVisitor.
 *
 * The visitor reports no violations of its own, so these tests drive it through a probe rule whose
 * visitor reports the state of the visitor for every statement it sees.
 *
 * @author Leonard Bruenings
 */
class AbstractSpockAstVisitorTest extends AbstractRuleTestCase<SpockProbeRule> {

    @Test
    void nonSpecification_NoViolations() {
        final SOURCE = '''
            class MyThing {
                def doIt() {
                    given: prepare()
                    then: check()
                }
            }
        '''.stripIndent()
        assertNoViolations(SOURCE)
    }

    @Test
    void specificationClassNames_MatchingClassName_VisitsClass() {
        final SOURCE = '''
            class MySpec {
                def "feature"() {
                    expect: check()
                }
            }
        '''.stripIndent()
        assertNoViolations(SOURCE)

        rule.specificationClassNames = '*Spec'
        assertViolations(SOURCE,
            [line: 4, source: 'expect: check()', message: 'label=expect'])
    }

    @Test
    void currentLabel_AndLabel_DoesNotChangeCurrentLabel() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                def "feature"() {
                    given: prepare()
                    and: prepareMore()
                    when: act()
                    then: check()
                    and: checkMore()
                }
            }
        '''.stripIndent()
        assertViolations(SOURCE,
            [line: 4, message: ['label=given', 'implicitBlock=false', 'implicitContext=false']],
            [line: 5, message: ['label=given', 'implicitBlock=false', 'implicitContext=false']],
            [line: 6, message: ['label=when', 'implicitBlock=false', 'implicitContext=false']],
            [line: 7, message: ['label=then', 'implicitBlock=true', 'implicitContext=true']],
            [line: 8, message: ['label=then', 'implicitBlock=true', 'implicitContext=true']])
    }

    @Test
    void currentLabel_LabelOnAssertStatement_IsTracked() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                def "feature"() {
                    expect: assert check()
                }
            }
        '''.stripIndent()
        assertViolations(SOURCE,
            [line: 4, message: ['label=expect', 'implicitBlock=true', 'implicitContext=true']])
    }

    @Test
    void nestingDepth_NestedStatements_IncrementPerLevel() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                def "feature"() {
                    expect: outer()
                    if (condition) {
                        inner()
                        for (item in items) {
                            deeper()
                        }
                    }
                }
            }
        '''.stripIndent()
        assertViolations(SOURCE,
            [line: 4, message: ['label=expect', 'depth=0', 'implicitContext=true']],
            [line: 6, message: ['label=expect', 'depth=1', 'implicitContext=false']],
            [line: 8, message: ['label=expect', 'depth=2', 'implicitContext=false']])
    }

    @Test
    void nestingDepth_LabelInsideNestedStatement_DoesNotChangeCurrentLabel() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                def "feature"() {
                    expect: outer()
                    for (item in items) {
                        then: inner()
                    }
                }
            }
        '''.stripIndent()
        assertViolations(SOURCE,
            [line: 4, message: ['label=expect', 'depth=0']],
            [line: 6, message: ['label=expect', 'depth=1']])
    }

    @Test
    void currentLabel_MethodEntry_ResetsLabel() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                def "first"() {
                    expect: one()
                }
                def "second"() {
                    two()
                    expect: three()
                }
            }
        '''.stripIndent()
        assertViolations(SOURCE,
            [line: 4, message: ['label=expect', 'method=first']],
            [line: 7, message: ['label=null', 'method=second']],
            [line: 8, message: ['label=expect', 'method=second']])
    }

    @Test
    void methodKind_FeatureFixtureAndHelperMethods_AreDistinguished() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                def setup() {
                    prepare()
                }
                def "feature"() {
                    expect: check()
                }
                private void helper() {
                    help()
                }
            }
        '''.stripIndent()
        assertViolations(SOURCE,
            [line: 4, message: ['feature=false', 'fixture=true', 'helper=false', 'method=setup']],
            [line: 7, message: ['feature=true', 'fixture=false', 'helper=false', 'method=feature']],
            [line: 10, message: ['feature=false', 'fixture=false', 'helper=true', 'method=helper']])
    }

    @Test
    void closures_ImplicitAssertionClosures_AreTracked() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                def "feature"() {
                    expect:
                    with(ship) {
                        registry == 'NCC 1701'
                    }
                    ship.crew.each {
                        it.certified()
                    }
                    verifyAll(ship) {
                        if (broken) {
                            repaired()
                        }
                    }
                }
            }
        '''.stripIndent()
        assertViolations(SOURCE,
            [line: 5, message: ['depth=0', 'closureReceiver=null', 'implicitContext=true']],
            [line: 6, message: ['depth=0', 'closureReceiver=with', 'implicitContext=true']],
            [line: 8, message: ['depth=0', 'closureReceiver=null', 'implicitContext=true']],
            [line: 9, message: ['depth=0', 'closureReceiver=null', 'implicitContext=false']],
            [line: 11, message: ['depth=0', 'closureReceiver=null', 'implicitContext=true']],
            [line: 13, message: ['depth=1', 'closureReceiver=verifyAll', 'implicitContext=true']])
    }

    @Test
    void closures_NestedStatementsOfImplicitAssertionClosures_AreImplicitConditions() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                def "feature"() {
                    expect:
                    with(ship) {
                        if (warpCapable) {
                            for (engine in engines) {
                                engine.online
                            }
                        }
                        crew.each {
                            it.certified()
                        }
                    }
                }
            }
        '''.stripIndent()
        assertViolations(SOURCE,
            [line: 5, message: ['depth=0', 'closureReceiver=null', 'implicitContext=true']],
            [line: 8, message: ['depth=2', 'closureReceiver=with', 'implicitContext=true']],
            [line: 11, message: ['depth=0', 'closureReceiver=with', 'implicitContext=true']],
            [line: 12, message: ['depth=0', 'closureReceiver=null', 'implicitContext=false']])
    }

    @Test
    void closures_ClosureDepth_CountsOnlyEnclosingClosures() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                def "feature"() {
                    expect:
                    top()
                    if (ready) {
                        nested()
                    }
                    ship.crew.each {
                        it.certified()
                        it.assignments.each {
                            it.valid()
                        }
                    }
                }
            }
        '''.stripIndent()
        assertViolations(SOURCE,
            [line: 5, message: ['inClosure=false', 'closureDepth=0', 'depth=0']],
            [line: 7, message: ['inClosure=false', 'closureDepth=0', 'depth=1']],
            [line: 9, message: ['inClosure=false', 'closureDepth=0', 'depth=0']],
            [line: 10, message: ['inClosure=true', 'closureDepth=1', 'depth=0']],
            [line: 11, message: ['inClosure=true', 'closureDepth=1', 'depth=0']],
            [line: 12, message: ['inClosure=true', 'closureDepth=2', 'depth=0']])
    }

    @Test
    void closures_InImplicitAssertionClosure_OnlyForSpockImplicitAssertionMethods() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                def "feature"() {
                    expect:
                    with(ship) {
                        registry == 'NCC 1701'
                        crew.each {
                            it.certified()
                        }
                    }
                    ship.with {
                        registry == 'NCC 1701'
                    }
                    ship.crew.each {
                        it.certified()
                    }
                }
            }
        '''.stripIndent()
        assertViolations(SOURCE,
            [line: 5, message: ['inClosure=false', 'implicitClosure=false', 'closureReceiver=null']],
            [line: 6, message: ['inClosure=true', 'implicitClosure=true', 'closureReceiver=with']],
            [line: 7, message: ['inClosure=true', 'implicitClosure=true', 'closureReceiver=with']],
            [line: 8, message: ['inClosure=true', 'implicitClosure=false', 'closureReceiver=null']],
            [line: 11, message: ['inClosure=false', 'implicitClosure=false', 'closureReceiver=null']],
            [line: 12, message: ['inClosure=true', 'implicitClosure=false', 'closureReceiver=null']],
            [line: 14, message: ['inClosure=false', 'implicitClosure=false', 'closureReceiver=null']],
            [line: 15, message: ['inClosure=true', 'implicitClosure=false', 'closureReceiver=null']])
    }

    @Override
    protected SpockProbeRule createRule() {
        new SpockProbeRule()
    }
}

/**
 * Probe rule that reports the state of AbstractSpockAstVisitor for every statement it visits.
 */
class SpockProbeRule extends AbstractSpockRule {

    String name = 'SpockProbe'
    int priority = 3
    Class astVisitorClass = SpockProbeAstVisitor
}

class SpockProbeAstVisitor extends AbstractSpockAstVisitor<SpockProbeRule> {

    @Override
    void visitExpressionStatement(ExpressionStatement statement) {
        super.visitExpressionStatement(statement)
        addViolation(statement, describeState())
    }

    @Override
    void visitAssertStatement(AssertStatement statement) {
        super.visitAssertStatement(statement)
        addViolation(statement, describeState())
    }

    private String describeState() {
        "label=$currentLabel depth=$nestingDepth feature=$inFeatureMethod fixture=$inFixtureMethod " +
            "helper=$inHelperMethod method=${currentMethod?.name} closureReceiver=$enclosingImplicitAssertionMethod " +
            "inClosure=$inClosure closureDepth=$closureDepth implicitClosure=$inImplicitAssertionClosure " +
            "implicitBlock=$inImplicitAssertBlock implicitContext=$inImplicitAssertionContext"
    }
}
