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
import org.codenarc.ruleset.XmlFileRuleSet
import org.codenarc.test.AbstractTestCase
import org.junit.jupiter.api.Test

/**
 * Tests for GenerateUtil
 *
 * @author Chris Mair
 * @author Leonard Bruenings
  */
class GenerateUtilTest extends AbstractTestCase {

    private static final String RULE_SET_WITH_EXCLUDED_RULES = 'rulesets/junit.xml'

    private final StubRule rule = new StubRule(name: 'SomeRule')
    private final StubRule otherRule = new StubRule(name: 'SomeOtherRule')

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
    void testGetRulesFromXmlRuleSet_ExcludesTheRulesExcludedForThatRuleSet() {
        def ruleNames = GenerateUtil.getRulesFromXmlRuleSet(RULE_SET_WITH_EXCLUDED_RULES)*.name
        assert ruleNames.contains('JUnitAssertAlwaysFails')
        assert !ruleNames.contains('SpockMissingAssert')
    }

    @Test
    void testGetRulesFromXmlRuleSet_KeepsARuleThatOnlyAnotherRuleSetExcludes() {
        def ruleNames = GenerateUtil.getRulesFromXmlRuleSet('rulesets/spock.xml')*.name
        assert ruleNames.contains('SpockMissingAssert')
    }

    @Test
    void testGetRulesFromXmlRuleSet_KeepsRulesThatTheirOwnClassDisablesByDefault() {
        def ruleNames = GenerateUtil.getRulesFromXmlRuleSet('rulesets/grails.xml')*.name
        assert ruleNames.contains('GrailsPublicControllerMethod')
    }

    @Test
    void testExcludeRulesNotToBeGenerated_RemovesTheRulesExcludedForThatRuleSet() {
        def excludedRuleName = GenerateUtil.RULES_EXCLUDED_FROM_GENERATED_FILES[RULE_SET_WITH_EXCLUDED_RULES].first()
        def excludedRule = new StubRule(name: excludedRuleName)

        assert GenerateUtil.excludeRulesNotToBeGenerated(RULE_SET_WITH_EXCLUDED_RULES, [rule, excludedRule]) == [rule]
    }

    @Test
    void testExcludeRulesNotToBeGenerated_KeepsAllRulesForARuleSetWithoutExclusions() {
        assert GenerateUtil.excludeRulesNotToBeGenerated('rulesets/basic.xml', [rule, otherRule]) == [rule, otherRule]
    }

    @Test
    void testExcludeRulesNotToBeGenerated_EmptyList() {
        assert GenerateUtil.excludeRulesNotToBeGenerated(RULE_SET_WITH_EXCLUDED_RULES, []) == []
    }

    @Test
    void testRulesExcludedFromGeneratedFiles_EachExcludedRuleIsStillDefinedByItsRuleSet() {
        GenerateUtil.RULES_EXCLUDED_FROM_GENERATED_FILES.each { ruleSetPath, excludedRuleNames ->
            def ruleNames = new XmlFileRuleSet(ruleSetPath).rules*.name
            excludedRuleNames.each { excludedRuleName ->
                assert excludedRuleName in ruleNames,
                    "$ruleSetPath no longer defines $excludedRuleName; remove it from RULES_EXCLUDED_FROM_GENERATED_FILES"
            }
        }
    }

    @Test
    void testRulesExcludedFromGeneratedFiles_EachExcludedRuleIsGeneratedForAnotherRuleSet() {
        def allRuleNames = GenerateUtil.createSortedListOfAllRules()*.name
        GenerateUtil.RULES_EXCLUDED_FROM_GENERATED_FILES.values().flatten().each { excludedRuleName ->
            assert excludedRuleName in allRuleNames
        }
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

