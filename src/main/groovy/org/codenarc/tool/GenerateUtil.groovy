/*
 * Copyright 2011 the original author or authors.
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

import org.codenarc.ruleset.RuleSets
import org.codenarc.ruleset.XmlFileRuleSet
import org.codenarc.util.io.ClassPathResource

/**
 * Contains static utility methods related to the Generate* tools.
 *
 * @author Chris Mair
  */
class GenerateUtil {

    private static final String RULE_EXTRA_INFO_FILE = 'codenarc-rule-extrainfo.properties'
    private static Properties ruleExtraInformation

    /**
     * The rules that the generated files must not list for a rule set, keyed by rule set path.
     *
     * A rule set keeps a disabled entry for a rule that has moved to another rule set, so that an existing
     * configuration referring to that rule by name still resolves. The generated files must list such a rule
     * only once, under the rule set that it has moved to.
     */
    protected static final Map<String, List<String>> RULES_EXCLUDED_FROM_GENERATED_FILES = [
        'rulesets/junit.xml': ['SpockIgnoreRestUsed', 'SpockMissingAssert', 'SpockUseVerifyEach'].asImmutable(),
    ].asImmutable()

    static Properties getRuleExtraInformation() {
        if (ruleExtraInformation) {
            return ruleExtraInformation
        }
        ruleExtraInformation = new Properties()
        ruleExtraInformation.load(ClassPathResource.getInputStream(RULE_EXTRA_INFO_FILE))
        ruleExtraInformation
    }

    static List getRulesFromXmlRuleSet(String ruleSetPath) {
        def ruleSet = new XmlFileRuleSet(ruleSetPath)
        sortRules(excludeRulesNotToBeGenerated(ruleSetPath, ruleSet.rules))
    }

    /**
     * Filter out the rules that the generated files must not list for a rule set.
     * @param ruleSetPath - the path of the rule set that the rules come from
     * @param rules - the rules configured by that rule set
     * @return the rules that the generated files must list for that rule set
     * @see #RULES_EXCLUDED_FROM_GENERATED_FILES
     */
    static List excludeRulesNotToBeGenerated(String ruleSetPath, List rules) {
        def excludedRuleNames = RULES_EXCLUDED_FROM_GENERATED_FILES[ruleSetPath] ?: []
        rules.findAll { rule -> !(rule.name in excludedRuleNames) }
    }

    static List createSortedListOfAllRules() {
        def allRules = []
        RuleSets.ALL_RULESET_FILES.each { ruleSetPath ->
            def ruleSetRules = getRulesFromXmlRuleSet(ruleSetPath)
            allRules.addAll(ruleSetRules)
        }
        sortRules(allRules)
    }

    static List sortRules(List rules) {
        def allRules = []
        allRules.addAll(rules)
        allRules.sort { rule -> rule.name }
    }

}
