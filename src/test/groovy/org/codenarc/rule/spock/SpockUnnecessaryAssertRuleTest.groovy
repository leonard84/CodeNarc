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

import org.codenarc.rule.AbstractRuleTestCase
import org.codenarc.rule.Rule
import org.codenarc.source.SourceString
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Tests for SpockUnnecessaryAssertRule
 *
 * @author Leonard Bruenings
 */
class SpockUnnecessaryAssertRuleTest extends AbstractRuleTestCase<SpockUnnecessaryAssertRule> {

    @Test
    void ruleProperties_AreValid() {
        assert rule.priority == 3
        assert rule.name == 'SpockUnnecessaryAssert'
        assert rule.specificationSuperclassNames == '*Specification'
        assert rule.specificationClassNames == null
    }

    @ParameterizedTest
    @ValueSource(strings = ['then', 'expect', 'filter'])
    void assert_TopLevelOfImplicitAssertionBlock_SingleViolation(String label) {
        final SOURCE = """
            class MySpec extends spock.lang.Specification {
                def "feature"() {
                    ${label}:
                    assert result == 42
                }
            }
        """.stripIndent()
        assertSingleViolation(SOURCE, 5, 'assert result == 42', blockMessage("${label}"))
    }

    @ParameterizedTest
    @ValueSource(strings = ['given', 'when', 'cleanup', 'setup'])
    void assert_BlockWithoutImplicitAssertions_NoViolations(String label) {
        final SOURCE = """
            class MySpec extends spock.lang.Specification {
                def "feature"() {
                    ${label}:
                    assert result == 42

                    expect:
                    true
                }
            }
        """.stripIndent()
        assertNoViolations(SOURCE)
    }

    @Test
    void assert_WhereBlock_NoViolations() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                def "feature"() {
                    expect:
                    value > 0

                    where:
                    assert values != null
                    value << values
                }
            }
        '''.stripIndent()
        assertNoViolations(SOURCE)
    }

    @ParameterizedTest
    @ValueSource(strings = [':', ','])
    void assert_WithMessage_NoViolations(String separator) {
        // Groovy accepts both separators for the assert message, and both produce the same AST
        final SOURCE = """
            class MySpec extends spock.lang.Specification {
                def "feature"() {
                    then:
                    assert result.valid ${separator} "after \$stimulus"
                }
            }
        """.stripIndent()
        assertNoViolations(SOURCE)
    }

    @Test
    void assert_NestedInsideIf_NoViolations() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                def "feature"() {
                    expect:
                    if (condition) {
                        assert result == 42
                    }
                    for (item in items) {
                        assert item.valid
                    }
                    while (more) {
                        assert next()
                    }
                    try {
                        assert risky()
                    } catch (Exception ignored) {
                        assert handled()
                    }
                    switch (kind) {
                        case 1:
                            assert one()
                            break
                    }
                }
            }
        '''.stripIndent()
        assertNoViolations(SOURCE)
    }

    @Test
    void assert_InHelperMethod_NoViolations() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                def "feature"() {
                    expect:
                    checkItems(items)
                }

                private void checkItems(items) {
                    assert items.size() == 3
                    with(items) {
                        assert first().valid
                    }
                }
            }
        '''.stripIndent()
        assertNoViolations(SOURCE)
    }

    @Test
    void assert_InFixtureMethod_NoViolations() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                def setup() {
                    assert database.available
                }

                def "feature"() {
                    expect:
                    true
                }
            }
        '''.stripIndent()
        assertNoViolations(SOURCE)
    }

    @ParameterizedTest
    @ValueSource(strings = ['with', 'verifyAll', 'verifyEach'])
    void assert_TopLevelOfImplicitAssertionClosure_SingleViolation(String method) {
        final SOURCE = """
            class MySpec extends spock.lang.Specification {
                def "feature"() {
                    expect:
                    ${method}(ship) {
                        assert registry == 'NCC 1701'
                    }
                }
            }
        """.stripIndent()
        assertSingleViolation(SOURCE, 6, "assert registry == 'NCC 1701'", closureMessage("${method}"))
    }

    @ParameterizedTest
    @ValueSource(strings = ['with', 'verifyAll', 'verifyEach'])
    void assert_NestedInsideIfInImplicitAssertionClosure_SingleViolation(String method) {
        final SOURCE = """
            class MySpec extends spock.lang.Specification {
                def "feature"() {
                    expect:
                    ${method}(ship) {
                        if (warpCapable) {
                            assert warpFactor > 1
                        }
                    }
                }
            }
        """.stripIndent()
        assertSingleViolation(SOURCE, 7, 'assert warpFactor > 1', closureMessage("${method}"))
    }

    @Test
    void assert_DeeplyNestedInWithClosure_Violations() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                def "feature"() {
                    expect:
                    with(ship) {
                        for (engine in engines) {
                            if (engine.enabled) {
                                assert engine.online
                            }
                        }
                        switch (registry) {
                            case 'NCC 1701':
                                assert warpCapable
                                break
                        }
                        try {
                            assert crew.size() > 0
                        } catch (Exception ignored) {
                            assert logged
                        }
                        while (docking) {
                            assert clamped
                        }
                    }
                }
            }
        '''.stripIndent()
        assertViolationLines(rule, SOURCE, [8, 13, 17, 19, 22])
    }

    @Test
    void assert_InStubClosure_NoViolations() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                def "feature"() {
                    given:
                    def service = Mock(Service)

                    when:
                    run(service)

                    then:
                    1 * service.handle(_) >> { args -> assert args[0] == 42 }
                    service.other() >> { assert alwaysChecked() }
                }
            }
        '''.stripIndent()
        assertNoViolations(SOURCE)
    }

    @Test
    void assert_InGroovyWithClosureOnExplicitReceiver_NoViolations() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                def "feature"() {
                    expect:
                    ship.with {
                        assert registry == 'NCC 1701'
                    }
                }
            }
        '''.stripIndent()
        assertNoViolations(SOURCE)
    }

    @Test
    void assert_InEachClosure_NoViolations() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                def "feature"() {
                    expect:
                    items.each { assert it.valid }
                    items.collect { assert it.valid }
                }
            }
        '''.stripIndent()
        assertNoViolations(SOURCE)
    }

    @Test
    void assert_InEachClosureInsideWithClosure_NoViolations() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                def "feature"() {
                    expect:
                    with(ship) {
                        crew.each {
                            assert it.certified
                        }
                    }
                }
            }
        '''.stripIndent()
        assertNoViolations(SOURCE)
    }

    @Test
    void nonSpecification_NoViolations() {
        final SOURCE = '''
            class MyThing {
                def doIt() {
                    then:
                    assert result == 42
                }
            }
        '''.stripIndent()
        assertNoViolations(SOURCE)
    }

    @Test
    void specificationClassNames_SpecSuffix_SingleViolation() {
        final SOURCE = '''
            class MySpec {
                def "feature"() {
                    then:
                    assert result == 42
                }
            }
        '''.stripIndent()
        assertNoViolations(SOURCE)

        rule.specificationClassNames = '*Spec'
        assertSingleViolation(SOURCE, 5, 'assert result == 42', blockMessage('then'))
    }

    @Test
    void realisticSpec_NoRuleReportsTheSameLineTwice() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                def "feature"() {
                    given:
                    def items = [1, 2, 3]

                    when:
                    def result = process(items)

                    then:
                    result.valid
                    assert result.count == 3
                    with(result) {
                        assert size == 3
                    }
                    if (result.detailed) {
                        result.entries.size() == 3
                    }
                    items.each { assert it > 0 }
                }
            }
        '''.stripIndent()
        assertViolationLines(rule, SOURCE, [12, 14])
        assertViolationLines(new SpockMissingAssertRule(), SOURCE, [17])
        assertViolationLines(new SpockUseVerifyEachRule(), SOURCE, [19])
    }

    @Override
    protected SpockUnnecessaryAssertRule createRule() {
        new SpockUnnecessaryAssertRule()
    }

    private static String blockMessage(String label) {
        "'assert' is redundant in a '${label}:' block - Spock treats top-level expressions as implicit conditions"
    }

    private static String closureMessage(String method) {
        "'assert' is redundant in a '${method}' block - Spock treats every expression as an implicit condition"
    }

    private static void assertViolationLines(Rule ruleToApply, String source, List<Integer> expectedLines) {
        def violations = ruleToApply.applyTo(new SourceString(source))
        assert violations*.lineNumber.sort() == expectedLines, "Rule ${ruleToApply.name} reported ${violations}"
    }
}
