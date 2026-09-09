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
 * Tests for SpockMissingReasonRule
 *
 * @author Leonard Bruenings
 */
class SpockMissingReasonRuleTest extends AbstractRuleTestCase<SpockMissingReasonRule> {

    @Test
    void ruleProperties_AreValid() {
        assert rule.priority == 3
        assert rule.name == 'SpockMissingReason'
        assert !rule.checkConditionalAnnotations
        assert rule.annotationNames == 'Ignore, PendingFeature, Isolated'
        assert rule.reasonRegex == null
        assert rule.reasonRegexAnnotationNames == 'Ignore, PendingFeature'
        assert rule.specificationSuperclassNames == '*Specification'
        assert rule.specificationClassNames == null
    }

    //--------------------------------------------------------------------------
    // Skip annotations without a reason
    //--------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = ['@Ignore', '@Ignore()', '@Ignore("")', '@Ignore("   ")'])
    void ignore_NoReason_SingleViolation(String annotation) {
        final SOURCE = """
            class MySpec extends spock.lang.Specification {
                ${annotation}
                def "feature"() {
                    expect: false
                }
            }
        """.stripIndent()
        assertSingleViolation(SOURCE, 3, annotation, featureMessage('Ignore'))
    }

    @Test
    void pendingFeature_NoReason_SingleViolation() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                @PendingFeature
                def "feature"() {
                    expect: false
                }
            }
        '''.stripIndent()
        assertSingleViolation(SOURCE, 3, '@PendingFeature', pendingMessage('PendingFeature'))
    }

    @Test
    void pendingFeature_OnlyExceptionsMember_SingleViolation() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                @PendingFeature(exceptions = [IllegalStateException])
                def "feature"() {
                    expect: false
                }
            }
        '''.stripIndent()
        assertSingleViolation(SOURCE, 3, '@PendingFeature', pendingMessage('PendingFeature'))
    }

    @Test
    void isolated_NoReason_SingleViolation() {
        final SOURCE = '''
            @Isolated
            class MySpec extends spock.lang.Specification {
                def "feature"() {
                    expect: false
                }
            }
        '''.stripIndent()
        assertSingleViolation(SOURCE, 2, '@Isolated', isolationMessage('Isolated'))
    }

    @Test
    void classLevelIgnore_NoReason_SingleViolation() {
        final SOURCE = '''
            @Ignore
            class MySpec extends spock.lang.Specification {
                def "feature"() {
                    expect: false
                }
            }
        '''.stripIndent()
        assertSingleViolation(SOURCE, 2, '@Ignore', specificationMessage('Ignore'))
    }

    @Test
    void twoBareAnnotationsOnOneMethod_TwoViolations() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                @Ignore
                @PendingFeature
                def "feature"() {
                    expect: false
                }
            }
        '''.stripIndent()
        assertViolations(SOURCE,
            [line: 3, source: '@Ignore', message: featureMessage('Ignore')],
            [line: 4, source: '@PendingFeature', message: pendingMessage('PendingFeature')])
    }

    @Test
    void fullyQualifiedSpockIgnore_NoReason_SingleViolation() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                @spock.lang.Ignore
                def "feature"() {
                    expect: false
                }
            }
        '''.stripIndent()
        assertSingleViolation(SOURCE, 3, '@spock.lang.Ignore', featureMessage('Ignore'))
    }

    @Test
    void importedSpockIgnore_NoReason_SingleViolation() {
        final SOURCE = '''
            import spock.lang.Ignore

            class MySpec extends spock.lang.Specification {
                @Ignore
                def "feature"() {
                    expect: false
                }
            }
        '''.stripIndent()
        assertSingleViolation(SOURCE, 5, '@Ignore', featureMessage('Ignore'))
    }

    @Test
    void starImportedSpockIgnore_NoReason_SingleViolation() {
        final SOURCE = '''
            import spock.lang.*

            class MySpec extends spock.lang.Specification {
                @Ignore
                def "feature"() {
                    expect: false
                }
            }
        '''.stripIndent()
        assertSingleViolation(SOURCE, 5, '@Ignore', featureMessage('Ignore'))
    }

    //--------------------------------------------------------------------------
    // Do not flag - a reason is stated
    //--------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = [
        '@Ignore("flaky on Windows, see #1790")',
        '@Ignore(value = "flaky on Windows, see #1790")',
        '@PendingFeature(reason = "coalescing not implemented yet")',
    ])
    void annotationWithReason_NoViolations(String annotation) {
        final SOURCE = """
            class MySpec extends spock.lang.Specification {
                ${annotation}
                def "feature"() {
                    expect: false
                }
            }
        """.stripIndent()
        assertNoViolations(SOURCE)
    }

    @Test
    void classLevelAnnotationsWithReason_NoViolations() {
        final SOURCE = '''
            @Ignore("the whole subsystem is being rewritten, see #1791")
            @Isolated("mutates the shared registry")
            class MySpec extends spock.lang.Specification {
                def "feature"() {
                    expect: false
                }
            }
        '''.stripIndent()
        assertNoViolations(SOURCE)
    }

    @Test
    void gstringReason_NoViolations() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                @Ignore("flaky on ${System.getProperty('os.name')}")
                def "feature"() {
                    expect: false
                }
            }
        '''.stripIndent()
        assertNoViolations(SOURCE)
    }

    @Test
    void constantReferenceReason_NoViolations() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                private static final String REASON = 'flaky on Windows, see #1790'

                @Ignore(REASON)
                def "feature"() {
                    expect: false
                }

                @PendingFeature(reason = Reasons.NOT_IMPLEMENTED)
                def "other feature"() {
                    expect: false
                }
            }
        '''.stripIndent()
        assertNoViolations(SOURCE)
    }

    //--------------------------------------------------------------------------
    // Do not flag - out of scope annotations
    //--------------------------------------------------------------------------

    @Test
    void retry_NoViolations() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                @Retry
                @Retry(count = 3)
                def "feature"() {
                    expect: false
                }
            }
        '''.stripIndent()
        assertNoViolations(SOURCE)
    }

    @Test
    void otherSpockAnnotations_NoViolations() {
        final SOURCE = '''
            @Stepwise
            class MySpec extends spock.lang.Specification {
                @Shared
                List items = []

                @IgnoreRest
                @Unroll
                @Timeout(5)
                @Subject
                def "feature"() {
                    expect: false
                }
            }
        '''.stripIndent()
        assertNoViolations(SOURCE)
    }

    //--------------------------------------------------------------------------
    // Conditional annotations
    //--------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = ['@IgnoreIf({ os.windows })', '@Requires({ os.linux })', '@PendingFeatureIf({ os.windows })'])
    void conditionalAnnotation_DefaultConfiguration_NoViolations(String annotation) {
        final SOURCE = """
            class MySpec extends spock.lang.Specification {
                ${annotation}
                def "feature"() {
                    expect: false
                }
            }
        """.stripIndent()
        assertNoViolations(SOURCE)
    }

    @Test
    void ignoreIf_CheckConditionalAnnotationsEnabled() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                @IgnoreIf({ os.windows })
                def "feature"() {
                    expect: false
                }
            }
        '''.stripIndent()
        assertNoViolations(SOURCE)

        rule.checkConditionalAnnotations = true
        assertSingleViolation(SOURCE, 3, '@IgnoreIf', conditionalMessage('IgnoreIf'))
    }

    @Test
    void pendingFeatureIf_CheckConditionalAnnotationsEnabled() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                @PendingFeatureIf({ os.windows })
                def "feature"() {
                    expect: false
                }
            }
        '''.stripIndent()
        assertNoViolations(SOURCE)

        rule.checkConditionalAnnotations = true
        assertSingleViolation(SOURCE, 3, '@PendingFeatureIf', pendingConditionMessage('PendingFeatureIf'))
    }

    @ParameterizedTest
    @ValueSource(strings = [
        '@Requires(value = { os.linux }, reason = "the daemon only runs on Linux")',
        '@PendingFeatureIf(value = { os.windows }, reason = "no coalescing on Windows")',
    ])
    void conditionalAnnotationWithReason_NoViolations(String annotation) {
        final SOURCE = """
            class MySpec extends spock.lang.Specification {
                ${annotation}
                def "feature"() {
                    expect: false
                }
            }
        """.stripIndent()
        rule.checkConditionalAnnotations = true
        assertNoViolations(SOURCE)
    }

    @Test
    void conditionalAnnotation_ListedInAnnotationNames_StillNeedsTheFlag() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                @PendingFeatureIf({ os.windows })
                def "feature"() {
                    expect: false
                }
            }
        '''.stripIndent()
        // a conditional annotation is governed by checkConditionalAnnotations alone
        rule.annotationNames = 'Ignore, PendingFeature, PendingFeatureIf, Isolated'
        assertNoViolations(SOURCE)
    }

    @Test
    void requires_CheckConditionalAnnotationsEnabledWithReason_NoViolations() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                @Requires(value = { os.linux }, reason = "the daemon only runs on Linux")
                def "feature"() {
                    expect: false
                }
            }
        '''.stripIndent()
        rule.checkConditionalAnnotations = true
        assertNoViolations(SOURCE)
    }

    //--------------------------------------------------------------------------
    // annotationNames configuration
    //--------------------------------------------------------------------------

    @Test
    void annotationNames_CustomAnnotation() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                @Quarantined
                def "feature"() {
                    expect: false
                }
            }
        '''.stripIndent()
        assertNoViolations(SOURCE)

        rule.annotationNames = 'Ignore, PendingFeature, Isolated, Quarantined'
        assertSingleViolation(SOURCE, 3, '@Quarantined', featureMessage('Quarantined'))
    }

    @ParameterizedTest
    @ValueSource(strings = ['@Quarantined("flaky, see #1790")', '@Quarantined(reason = "flaky, see #1790")'])
    void annotationNames_CustomAnnotationWithReasonInEitherMember_NoViolations(String annotation) {
        final SOURCE = """
            class MySpec extends spock.lang.Specification {
                ${annotation}
                def "feature"() {
                    expect: false
                }
            }
        """.stripIndent()
        rule.annotationNames = 'Quarantined'
        assertNoViolations(SOURCE)
    }

    @Test
    void annotationNames_NarrowedDown_NoViolationsForRemovedAnnotation() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                @PendingFeature
                def "feature"() {
                    expect: false
                }
            }
        '''.stripIndent()
        rule.annotationNames = 'Ignore'
        assertNoViolations(SOURCE)
    }

    //--------------------------------------------------------------------------
    // reasonRegex configuration
    //--------------------------------------------------------------------------

    @Test
    void reasonRegex_NotSet_ReasonContentIsNotChecked() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                @Ignore("flaky")
                def "feature"() {
                    expect: false
                }
            }
        '''.stripIndent()
        assertNoViolations(SOURCE)
    }

    @ParameterizedTest
    @ValueSource(strings = ['SPOCK-1234', 'Flaky, see SPOCK-1234', 'SPOCK-1234 - flaky on Windows'])
    void reasonRegex_ReasonMatchesAnywhere_NoViolations(String reason) {
        final SOURCE = """
            class MySpec extends spock.lang.Specification {
                @Ignore("${reason}")
                def "feature"() {
                    expect: false
                }
            }
        """.stripIndent()
        rule.reasonRegex = /SPOCK-\d+/
        assertNoViolations(SOURCE)
    }

    @Test
    void reasonRegex_ReasonDoesNotMatch_SingleViolation() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                @Ignore("flaky")
                def "feature"() {
                    expect: false
                }
            }
        '''.stripIndent()
        rule.reasonRegex = /SPOCK-\d+/
        assertSingleViolation(SOURCE, 3, '@Ignore', regexMessage('Ignore', /SPOCK-\d+/))
    }

    @Test
    void reasonRegex_PendingFeatureReasonDoesNotMatch_SingleViolation() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                @PendingFeature(reason = "not implemented")
                def "feature"() {
                    expect: false
                }
            }
        '''.stripIndent()
        rule.reasonRegex = /SPOCK-\d+/
        assertSingleViolation(SOURCE, 3, '@PendingFeature', regexMessage('PendingFeature', /SPOCK-\d+/))
    }

    @Test
    void reasonRegex_ClassLevelReasonDoesNotMatch_SingleViolation() {
        final SOURCE = '''
            @Ignore("flaky")
            class MySpec extends spock.lang.Specification {
                def "feature"() {
                    expect: false
                }
            }
        '''.stripIndent()
        rule.reasonRegex = /SPOCK-\d+/
        assertSingleViolation(SOURCE, 2, '@Ignore', regexMessage('Ignore', /SPOCK-\d+/))
    }

    @Test
    void reasonRegex_NoReasonAtAll_ReportsOnlyTheMissingReasonViolation() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                @Ignore
                def "feature"() {
                    expect: false
                }
            }
        '''.stripIndent()
        rule.reasonRegex = /SPOCK-\d+/
        assertSingleViolation(SOURCE, 3, '@Ignore', featureMessage('Ignore'))
    }

    @ParameterizedTest
    @ValueSource(strings = ['@Ignore("flaky, see ${issue}")', '@Ignore(Reasons.FLAKY)'])
    void reasonRegex_ReasonIsNotAStringLiteral_NoViolations(String annotation) {
        final SOURCE = """
            class MySpec extends spock.lang.Specification {
                ${annotation}
                def "feature"() {
                    expect: false
                }
            }
        """.stripIndent()
        rule.reasonRegex = /SPOCK-\d+/
        assertNoViolations(SOURCE)
    }

    @ParameterizedTest
    @ValueSource(strings = ['@PendingFeatureIf(value = { os.windows }, reason = "no native lib")', '@Isolated("needs the shared port")'])
    void reasonRegex_AnnotationNotOptedIn_NoViolations(String annotation) {
        // checked for a stated reason, but absent from reasonRegexAnnotationNames
        final SOURCE = """
            class MySpec extends spock.lang.Specification {
                ${annotation}
                def "feature"() {
                    expect: false
                }
            }
        """.stripIndent()
        rule.reasonRegex = /SPOCK-\d+/
        rule.checkConditionalAnnotations = true
        assertNoViolations(SOURCE)
    }

    @Test
    void reasonRegex_ConditionalAnnotationOptedIn() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                @IgnoreIf(value = { os.windows }, reason = "no native lib")
                def "feature"() {
                    expect: false
                }
            }
        '''.stripIndent()
        rule.reasonRegex = /SPOCK-\d+/
        rule.reasonRegexAnnotationNames = 'Ignore, PendingFeature, IgnoreIf'
        // reasonRegex only reaches an annotation that is checked in the first place
        assertNoViolations(SOURCE)

        rule.checkConditionalAnnotations = true
        assertSingleViolation(SOURCE, 3, '@IgnoreIf', regexMessage('IgnoreIf', /SPOCK-\d+/))
    }

    @Test
    void reasonRegex_CustomAnnotationNeedsExplicitOptIn() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                @Quarantined("flaky")
                def "feature"() {
                    expect: false
                }
            }
        '''.stripIndent()
        rule.annotationNames = 'Ignore, Quarantined'
        rule.reasonRegex = /SPOCK-\d+/
        // being checked for a reason does not by itself subject the reason to reasonRegex
        assertNoViolations(SOURCE)

        rule.reasonRegexAnnotationNames = 'Ignore, Quarantined'
        assertSingleViolation(SOURCE, 3, '@Quarantined', regexMessage('Quarantined', /SPOCK-\d+/))
    }

    @Test
    void reasonRegex_NarrowedDown_NoViolationsForRemovedAnnotation() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                @PendingFeature(reason = "not implemented")
                def "feature"() {
                    expect: false
                }
            }
        '''.stripIndent()
        rule.reasonRegex = /SPOCK-\d+/
        rule.reasonRegexAnnotationNames = 'Ignore'
        assertNoViolations(SOURCE)
    }

    @Test
    void reasonRegex_UrlPattern_SingleViolation() {
        final SOURCE = '''
            class MySpec extends spock.lang.Specification {
                @Ignore("see https://github.com/CodeNarc/CodeNarc/issues/1")
                def "first feature"() {
                    expect: false
                }

                @Ignore("flaky")
                def "second feature"() {
                    expect: false
                }
            }
        '''.stripIndent()
        rule.reasonRegex = 'https?://'
        assertSingleViolation(SOURCE, 8, '@Ignore', regexMessage('Ignore', 'https?://'))
    }

    //--------------------------------------------------------------------------
    // Specification detection
    //--------------------------------------------------------------------------

    @Test
    void nonSpecification_NoViolations() {
        final SOURCE = '''
            @Ignore
            class MyThing {
                @Ignore
                void doIt() { }
            }
        '''.stripIndent()
        assertNoViolations(SOURCE)
    }

    @Test
    void innerClassThatIsNotASpecification_ReportsEachAnnotationExactlyOnce() {
        final SOURCE = '''
            @Ignore
            class MySpec extends spock.lang.Specification {
                @Ignore
                def "feature"() {
                    expect: false
                }

                static class Helper {
                    @Ignore
                    void doIt() { }
                }
            }
        '''.stripIndent()
        assertViolations(SOURCE,
            [line: 2, source: '@Ignore', message: specificationMessage('Ignore')],
            [line: 4, source: '@Ignore', message: featureMessage('Ignore')])
    }

    @Test
    void specificationClassNames_SpecSuffix() {
        final SOURCE = '''
            class MySpec {
                @Ignore
                def "feature"() {
                    expect: false
                }
            }
        '''.stripIndent()
        assertNoViolations(SOURCE)

        rule.specificationClassNames = '*Spec'
        assertSingleViolation(SOURCE, 3, '@Ignore', featureMessage('Ignore'))
    }

    @Override
    protected SpockMissingReasonRule createRule() {
        new SpockMissingReasonRule()
    }

    private static String featureMessage(String annotationName) {
        "@${annotationName} without a reason - state why the feature is disabled"
    }

    private static String specificationMessage(String annotationName) {
        "@${annotationName} without a reason - state why the specification is disabled"
    }

    private static String regexMessage(String annotationName, String regex) {
        "@${annotationName} reason does not match ${regex}"
    }

    private static String pendingMessage(String annotationName) {
        "@${annotationName} without a reason - state why the feature is expected to fail"
    }

    private static String pendingConditionMessage(String annotationName) {
        "@${annotationName} without a reason - state why the condition makes the feature fail"
    }

    private static String isolationMessage(String annotationName) {
        "@${annotationName} without a reason - state why the specification must run in isolation"
    }

    private static String conditionalMessage(String annotationName) {
        "@${annotationName} without a reason - state why the condition disables the feature"
    }
}
