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
package org.codenarc.tool

import static org.codenarc.test.TestUtil.shouldFail

import org.codenarc.rule.Rule
import org.codenarc.rule.StubRule
import org.codenarc.test.AbstractTestCase
import org.junit.jupiter.api.Test

/**
 * Tests for GenerateUtil
 *
 * @author Chris Mair
 * @author Leonard Bruenings
  */
class GenerateUtilTest extends AbstractTestCase {

    private final StubRule rule = new StubRule(name: 'SomeRule')
    private final StubRule ruleSetDisabledRule = new StubRule(name: 'SomeRuleSetDisabledRule', enabled: false)
    private final DisabledByDefaultStubRule disabledByDefaultRule = new DisabledByDefaultStubRule(name: 'SomeDisabledByDefaultRule')
    private final DeprecatedStubRule deprecatedRule = new DeprecatedStubRule(name: 'SomeRule')

    @Test
    void testGetRuleExtraInformation_ReturnsExpectedValuesFromPropertiesFile() {
        def ruleExtraInformation = GenerateUtil.getRuleExtraInformation()
        assert ruleExtraInformation['AbcMetric'] == 'Requires the GMetrics jar'
        assert ruleExtraInformation['CrapMetric'] == 'Requires the GMetrics jar and a Cobertura coverage file'
        assert ruleExtraInformation['CyclomaticComplexity'] == 'Requires the GMetrics jar'
    }

    @Test
    void testGetRuleExtraInformation_ReturnsNullForRuleNotInPropertiesFile() {
        def ruleExtraInformation = GenerateUtil.getRuleExtraInformation()
        assert ruleExtraInformation['UnknownRule'] == null
    }

    @Test
    void testGetRuleExtraInformation_CachesAndReturnsSameInstanceAcrossCalls() {
        def ruleExtraInformation1 = GenerateUtil.getRuleExtraInformation()
        def ruleExtraInformation2 = GenerateUtil.getRuleExtraInformation()
        assert ruleExtraInformation1.is(ruleExtraInformation2)
    }

    @Test
    void testGetRulesFromXmlRuleSet_ReturnsRulesFromRuleSetSortedByName() {
        def rules = GenerateUtil.getRulesFromXmlRuleSet('rulesets/basic.xml')

        def ruleNames = rules*.name
        assert ruleNames.contains('EmptyCatchBlock')
        assert ruleNames == ruleNames.sort(false)
    }

    @Test
    void testGetRulesFromXmlRuleSet_NullPath_ThrowsAssertionError() {
        shouldFail(AssertionError) { GenerateUtil.getRulesFromXmlRuleSet(null) }
    }

    @Test
    void testGetRulesFromXmlRuleSet_UnknownPath_ThrowsException() {
        shouldFail { GenerateUtil.getRulesFromXmlRuleSet('rulesets/DoesNotExist.xml') }
    }

    @Test
    void testCreateSortedListOfAllRules_ReturnsAllRulesFromAllPredefinedRuleSetsSortedByName() {
        def rules = GenerateUtil.createSortedListOfAllRules()

        def ruleNames = rules*.name
        assert ruleNames.contains('EmptyCatchBlock')
        assert ruleNames.contains('UnnecessaryBigDecimalInstantiation')
        assert ruleNames == ruleNames.sort(false)
    }

    @Test
    void testGetRulesFromXmlRuleSet_ExcludesRulesThatTheRuleSetDisables() {
        def ruleNames = GenerateUtil.getRulesFromXmlRuleSet('rulesets/junit.xml')*.name
        assert ruleNames.contains('JUnitAssertAlwaysFails')
        assert !ruleNames.contains('SpockMissingAssert')
    }

    @Test
    void testGetRulesFromXmlRuleSet_KeepsRulesThatTheirOwnClassDisablesByDefault() {
        def ruleNames = GenerateUtil.getRulesFromXmlRuleSet('rulesets/grails.xml')*.name
        assert ruleNames.contains('GrailsPublicControllerMethod')
    }

    @Test
    void testExcludeRulesNotToBeGenerated_RemovesRulesAnnotatedWithDeprecated() {
        assert GenerateUtil.excludeRulesNotToBeGenerated([rule, deprecatedRule]) == [rule]
    }

    @Test
    void testExcludeRulesNotToBeGenerated_RemovesRulesThatTheRuleSetEntryDisables() {
        assert GenerateUtil.excludeRulesNotToBeGenerated([rule, ruleSetDisabledRule]) == [rule]
    }

    @Test
    void testExcludeRulesNotToBeGenerated_KeepsRulesThatTheirOwnClassDisablesByDefault() {
        assert GenerateUtil.excludeRulesNotToBeGenerated([rule, disabledByDefaultRule]) == [rule, disabledByDefaultRule]
    }

    @Test
    void testExcludeRulesNotToBeGenerated_KeepsAllRulesWhenNoneIsDisabledOrDeprecated() {
        assert GenerateUtil.excludeRulesNotToBeGenerated([rule]) == [rule]
    }

    @Test
    void testExcludeRulesNotToBeGenerated_EmptyList() {
        assert GenerateUtil.excludeRulesNotToBeGenerated([]) == []
    }

    @Test
    void testCreateSortedListOfAllRules_ContainsNoDeprecatedRules() {
        def rules = GenerateUtil.createSortedListOfAllRules()
        assert rules
        assert rules.every { r -> !r.class.isAnnotationPresent(Deprecated) }
    }

    @Test
    void testCreateSortedListOfAllRules_RuleNamesAreUnique() {
        def ruleNames = GenerateUtil.createSortedListOfAllRules()*.name
        assert ruleNames.size() == ruleNames.toSet().size()
    }

    @Test
    void testSortRules_ReturnsRulesSortedByName() {
        def ruleB = [getName: { 'RuleB' }] as Rule
        def ruleC = [getName: { 'RuleC' }] as Rule
        def ruleA = [getName: { 'RuleA' }] as Rule

        def sorted = GenerateUtil.sortRules([ruleB, ruleC, ruleA])
        assert sorted == [ruleA, ruleB, ruleC]
    }

    @Test
    void testSortRules_EmptyList_ReturnsEmptyList() {
        assert GenerateUtil.sortRules([]) == []
    }

    @Test
    void testSortRules_DoesNotModifyInputList() {
        def ruleB = [getName: { 'RuleB' }] as Rule
        def ruleA = [getName: { 'RuleA' }] as Rule
        def input = [ruleB, ruleA]

        GenerateUtil.sortRules(input)

        assert input == [ruleB, ruleA]
    }

}

class DisabledByDefaultStubRule extends StubRule {

    DisabledByDefaultStubRule() {
        enabled = false
    }

}

@Deprecated
class DeprecatedStubRule extends StubRule { }
