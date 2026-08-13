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

import org.codenarc.rule.Rule
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
        sortRules(excludeRulesNotToBeGenerated(ruleSet.rules))
    }

    /**
     * Filter out the rules that must not show up in the generated files:
     *  - a rule that its rule set entry disables. A rule set keeps such an entry only to document that the
     *    rule used to be part of it; the rule is enabled in the rule set it has moved to, so listing both
     *    entries would list the rule twice. A rule that its own class disables by default, such as
     *    GrailsPublicControllerMethod, is still part of its rule set and is kept.
     *  - a rule whose class is annotated with @Deprecated. Such a class is only a compatibility shim for
     *    a rule that has moved to another package, and carries the same rule name as the rule it extends.
     * @param rules - the rules to filter
     * @return the rules that the generated files must list
     */
    static List excludeRulesNotToBeGenerated(List rules) {
        rules.findAll { rule -> !isDisabledByRuleSet(rule) && !rule.class.isAnnotationPresent(Deprecated) }
    }

    /**
     * @param rule - a rule as configured by its rule set
     * @return true only if the rule set entry turned off a rule that its class enables by default
     */
    private static boolean isDisabledByRuleSet(Rule rule) {
        !rule.enabled && rule.class.getDeclaredConstructor().newInstance().enabled
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
