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
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Tests for SpockUnnecessaryUnrollRule
 *
 * @author Leonard Bruenings
 */
class SpockUnnecessaryUnrollRuleTest extends AbstractRuleTestCase<SpockUnnecessaryUnrollRule> {

    private static final String MESSAGE =
        "'@Unroll' without a value is redundant - since Spock 2 unrolls by default; remove it, or give it an iteration-name template"

    @Test
    void ruleProperties_AreValid() {
        assert rule.priority == 3
        assert rule.name == 'SpockUnnecessaryUnroll'
        assert rule.specificationSuperclassNames == '*Specification'
        assert rule.specificationClassNames == null
    }

    @ParameterizedTest
    @ValueSource(strings = [
        '@Unroll',
        '@Unroll()',
        '@spock.lang.Unroll',
        '@spock.lang.Unroll()',
        // Unroll.value() defaults to the empty String, so an explicit "" is the same as the bare form
        '@Unroll("")',
        '@Unroll(value = "")',
    ])
    void unroll_BareOnFeatureMethod_SingleViolation(String annotation) {
        final SOURCE = """
            class MySpec extends spock.lang.Specification {
                ${annotation}
                def "node version #version parses"() {
                    expect:
                    parse(version)

                    where:
                    version << ['1.0', '2.0']
                }
            }
        """.stripIndent()
        assertSingleViolation(SOURCE, 3, annotation, MESSAGE)
    }

    @ParameterizedTest
    @ValueSource(strings = ['@Unroll', '@Unroll()', '@spock.lang.Unroll'])
    void unroll_BareOnClass_SingleViolation(String annotation) {
        final SOURCE = """
            ${annotation}
            class MySpec extends spock.lang.Specification {
                def "node version #version parses"() {
                    expect:
                    parse(version)

                    where:
                    version << ['1.0', '2.0']
                }
            }
        """.stripIndent()
        assertSingleViolation(SOURCE, 2, annotation, MESSAGE)
    }

    @Test
    void unroll_BareOnClassAndOnMethod_TwoViolations() {
        final SOURCE = '''
            @Unroll
            class MySpec extends spock.lang.Specification {
                @Unroll
                def "node version #version parses"() {
                    expect:
                    parse(version)

                    where:
                    version << ['1.0', '2.0']
                }
            }
        '''.stripIndent()
        assertViolations(SOURCE,
            [line: 2, source: '@Unroll', message: MESSAGE],
            [line: 4, source: '@Unroll', message: MESSAGE])
    }

    @ParameterizedTest
    @ValueSource(strings = [
        '@Unroll("#operator on #a and #b yields #expected")',
        '@Unroll(value = "#operator on #a and #b yields #expected")',
        '@spock.lang.Unroll("#version")',
        // Cannot be evaluated from the source, so it is not reported
        '@Unroll(TEMPLATE)',
        '@Unroll(value = TEMPLATE)',
    ])
    void unroll_WithMemberValue_NoViolations(String annotation) {
        final SOURCE = """
            class MySpec extends spock.lang.Specification {
                ${annotation}
                def "arithmetic operators evaluate their operands"() {
                    expect:
                    evaluate(operator, a, b) == expected

                    where:
                    operator | a | b | expected
                    '+'      | 1 | 2 | 3
                }
            }
        """.stripIndent()
        assertNoViolations(SOURCE)
    }

    /**
     * Guards the '@Unroll(TEMPLATE)' cases above against passing because the source failed to parse:
     * the bare '@Unroll' further down is still reported.
     */
    @Test
    void unroll_WithConstantReferenceMember_OnlyTheBareOneReported() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                private static final String TEMPLATE = '#version parses'

                @Unroll(TEMPLATE)
                def "first feature #version"() {
                    expect:
                    parse(version)

                    where:
                    version << ['1.0']
                }

                @Unroll
                def "second feature #version"() {
                    expect:
                    parse(version)

                    where:
                    version << ['1.0']
                }
            }
        '''.stripIndent()
        assertSingleViolation(SOURCE, 14, '@Unroll', MESSAGE)
    }

    @Test
    void unroll_WithEmptyMemberValueOnClass_SingleViolation() {
        final SOURCE = '''
            @Unroll("")
            class MySpec extends spock.lang.Specification {
                def "feature"() {
                    expect:
                    parse(version)

                    where:
                    version << ['1.0']
                }
            }
        '''.stripIndent()
        assertSingleViolation(SOURCE, 2, '@Unroll("")', MESSAGE)
    }

    @Test
    void unroll_WithMemberValueOnClass_NoViolations() {
        final SOURCE = '''
            @Unroll("#version parses")
            class MySpec extends spock.lang.Specification {
                def "feature"() {
                    expect:
                    parse(version)

                    where:
                    version << ['1.0']
                }
            }
        '''.stripIndent()
        assertNoViolations(SOURCE)
    }

    @Test
    void unroll_BareOnMethodWithRollupOnClass_NoViolations() {
        final SOURCE = '''
            @Rollup
            class MySpec extends spock.lang.Specification {
                @Unroll
                def "node version #version parses"() {
                    expect:
                    parse(version)

                    where:
                    version << ['1.0', '2.0']
                }
            }
        '''.stripIndent()
        assertNoViolations(SOURCE)
    }

    @Test
    void unroll_BareOnMethodWithFullyQualifiedRollupOnClass_NoViolations() {
        final SOURCE = '''
            @spock.lang.Rollup
            class MySpec extends spock.lang.Specification {
                @Unroll
                def "node version #version parses"() {
                    expect:
                    parse(version)

                    where:
                    version << ['1.0', '2.0']
                }
            }
        '''.stripIndent()
        assertNoViolations(SOURCE)
    }

    /** '@Rollup' is not inheritable, so the one on the base spec does not roll up MySpec's own features. */
    @Test
    void unroll_BareOnMethodWithRollupOnSuperclass_SingleViolation() {
        final SOURCE = '''
            @Rollup
            abstract class BaseSpecification extends spock.lang.Specification {
            }

            class MySpec extends BaseSpecification {
                @Unroll
                def "node version #version parses"() {
                    expect:
                    parse(version)

                    where:
                    version << ['1.0', '2.0']
                }
            }
        '''.stripIndent()
        assertSingleViolation(SOURCE, 7, '@Unroll', MESSAGE)
    }

    @Test
    void unroll_BareOnClassWithRollupOnSuperclass_SingleViolation() {
        final SOURCE = '''
            @Rollup
            abstract class BaseSpecification extends spock.lang.Specification {
            }

            @Unroll
            class MySpec extends BaseSpecification {
                def "node version #version parses"() {
                    expect:
                    parse(version)

                    where:
                    version << ['1.0', '2.0']
                }
            }
        '''.stripIndent()
        assertSingleViolation(SOURCE, 6, '@Unroll', MESSAGE)
    }

    /** A feature declared in the base spec is checked against the base spec's own '@Rollup'. */
    @Test
    void unroll_BareOnMethodOfSuperclassCarryingRollup_NoViolations() {
        final SOURCE = '''
            @Rollup
            abstract class BaseSpecification extends spock.lang.Specification {
                @Unroll
                def "node version #version parses"() {
                    expect:
                    parse(version)

                    where:
                    version << ['1.0', '2.0']
                }
            }

            class MySpec extends BaseSpecification {
            }
        '''.stripIndent()
        assertNoViolations(SOURCE)
    }

    /** Spock rejects the combination with an InvalidSpecException, so the '@Rollup' is not taken into account. */
    @Test
    void unroll_BareOnMethodThatAlsoCarriesRollup_SingleViolation() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                @Unroll
                @Rollup
                def "node version #version parses"() {
                    expect:
                    parse(version)

                    where:
                    version << ['1.0', '2.0']
                }
            }
        '''.stripIndent()
        assertSingleViolation(SOURCE, 3, '@Unroll', MESSAGE)
    }

    @Test
    void rollup_OnItsOwn_NoViolations() {
        final SOURCE = '''
            @Rollup
            class MySpec extends spock.lang.Specification {
                @Rollup
                def "node version #version parses"() {
                    expect:
                    parse(version)

                    where:
                    version << ['1.0', '2.0']
                }
            }
        '''.stripIndent()
        assertNoViolations(SOURCE)
    }

    @Test
    void unroll_OnHelperMethodWithoutSpockLabels_NoViolations() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                @Unroll
                private void checkVersion(String version) {
                    assert parse(version)
                }

                @Unroll
                abstract void templateMethod()

                def "feature"() {
                    expect:
                    checkVersion('1.0')
                }
            }
        '''.stripIndent()
        assertNoViolations(SOURCE)
    }

    @Test
    void unroll_OnClassThatIsNotASpecification_NoViolations() {
        final SOURCE = '''
            @Unroll
            class MyThing {
                @Unroll
                def "node version #version parses"() {
                    expect:
                    parse(version)

                    where:
                    version << ['1.0', '2.0']
                }
            }
        '''.stripIndent()
        assertNoViolations(SOURCE)
    }

    @Test
    void unroll_NonSpecificationClassInTheSameFile_OnlyTheSpecificationIsChecked() {
        final SOURCE = '''
            @Unroll
            class MyHelper {
            }

            @Unroll
            class MySpec extends spock.lang.Specification {
                def "feature"() {
                    expect:
                    true
                }
            }
        '''.stripIndent()
        assertSingleViolation(SOURCE, 6, '@Unroll', MESSAGE)
    }

    @Test
    void specificationClassNames_SpecSuffix_SingleViolation() {
        final SOURCE = '''
            class MySpec {
                @Unroll
                def "node version #version parses"() {
                    expect:
                    parse(version)

                    where:
                    version << ['1.0', '2.0']
                }
            }
        '''.stripIndent()
        assertNoViolations(SOURCE)

        rule.specificationClassNames = '*Spec'
        assertSingleViolation(SOURCE, 3, '@Unroll', MESSAGE)
    }

    @Override
    protected SpockUnnecessaryUnrollRule createRule() {
        new SpockUnnecessaryUnrollRule()
    }
}
