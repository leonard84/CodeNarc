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
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression

/**
 * A disabled feature with no stated reason stays disabled: nobody re-enables an @Ignore without knowing
 * why it was turned off, so the behaviour it covered silently stops being tested. Every Spock skip
 * annotation accepts a reason for that purpose.
 *
 * This rule reports Spock's @Ignore, @PendingFeature and @Isolated - on a feature method or on the
 * specification class - when the annotation states no reason. A reason that is not a
 * String literal (a GString, a constant reference, ...) always counts as present; only a missing or
 * blank literal is reported.
 *
 * @Requires, @IgnoreIf and @PendingFeatureIf accept a reason too, but a self-explanatory condition such
 * as <code>@IgnoreIf({ os.windows })</code> does not need one. They are therefore only checked when
 * <code>checkConditionalAnnotations</code> is enabled.
 *
 * <code>reasonRegex</code> additionally requires the reason to match a pattern, such as an issue id or a
 * link, for the annotations named in <code>reasonRegexAnnotationNames</code>.
 *
 * @Retry is deliberately not handled here; it has no reason member.
 *
 * @author Leonard Bruenings
 */
class SpockMissingReasonRule extends AbstractSpockRule {

    String name = 'SpockMissingReason'
    int priority = 3
    Class astVisitorClass = SpockMissingReasonAstVisitor

    /**
     * The (comma-separated) simple names of the skip annotations that must state a reason. Teams can add
     * their own project-specific skip annotations here. Setting this property replaces the default list, so
     * include <code>Ignore, PendingFeature, Isolated</code> if those should still be checked.
     */
    String annotationNames = 'Ignore, PendingFeature, Isolated'

    /**
     * If true, the conditional annotations - @Requires, @IgnoreIf and @PendingFeatureIf - must state a
     * reason as well. They are governed by this property alone; listing them in
     * <code>annotationNames</code> has no effect.
     */
    boolean checkConditionalAnnotations = false

    /**
     * If set, a stated reason must contain a match for this regular expression - use it to require an issue
     * id (<code>SPOCK-\d+</code>) or a link (<code>https?://</code>). Unset by default, which leaves the
     * content of a reason unchecked.
     */
    String reasonRegex

    /**
     * The (comma-separated) simple names of the annotations that <code>reasonRegex</code> applies to; only
     * relevant when that property is set. The conditional annotations and <code>@Isolated</code> are excluded
     * by default: their reason explains a standing condition
     * (<code>@IgnoreIf(value = { os.windows }, reason = 'no native lib on Windows')</code>) or an execution
     * constraint (<code>@Isolated('needs the shared port')</code>), which is not work to be tracked. Setting
     * this property replaces the default list, so include <code>Ignore, PendingFeature</code> if those
     * should still be checked.
     */
    String reasonRegexAnnotationNames = 'Ignore, PendingFeature'
}

class SpockMissingReasonAstVisitor extends AbstractSpockAstVisitor<SpockMissingReasonRule> {

    private static final List<String> CONDITIONAL_ANNOTATION_NAMES = ['Requires', 'IgnoreIf', 'PendingFeatureIf'].asImmutable()

    /**
     * What the reason has to explain, for every annotation this rule knows about; %s is the annotated
     * target. These annotations do not all mean the same thing - @Isolated disables nothing, it forces the
     * specification to run on its own, and a pending feature is expected to fail rather than skipped - so
     * the advice follows the annotation. One added through <code>annotationNames</code> falls back to
     * <code>DEFAULT_ADVICE</code>.
     */
    private static final Map<String, String> ADVICE = [
        Ignore: 'state why the %s is disabled',
        PendingFeature: 'state why the %s is expected to fail',
        PendingFeatureIf: 'state why the condition makes the %s fail',
        Isolated: 'state why the %s must run in isolation',
        Requires: 'state why the condition disables the %s',
        IgnoreIf: 'state why the condition disables the %s',
    ].asImmutable()

    private static final String DEFAULT_ADVICE = 'state why the %s is disabled'

    /**
     * The member that holds the reason, for every annotation this rule knows about. An annotation that is
     * not listed here has been added by the user through the <code>annotationNames</code> property; for
     * those, a value in either member counts as a reason.
     */
    private static final Map<String, String> REASON_MEMBERS = [
        Ignore: 'value',
        PendingFeature: 'reason',
        PendingFeatureIf: 'reason',
        Isolated: 'value',
        Requires: 'reason',
        IgnoreIf: 'reason',
    ].asImmutable()

    private static final List<String> FALLBACK_REASON_MEMBERS = ['value', 'reason'].asImmutable()

    @Override
    protected void visitClassEx(ClassNode node) {
        super.visitClassEx(node)
        // visitClassEx runs for every class; only a Specification is of interest here
        if (SpockUtil.isSpockSpecification(node, rule.specificationSuperclassNames, rule.specificationClassNames)) {
            checkAnnotations(node, 'specification')
        }
    }

    @Override
    protected void visitMethodEx(MethodNode node) {
        // Only reached for a Specification - AbstractSpockAstVisitor.shouldVisitMethod() gates on that
        checkAnnotations(node, 'feature')
        super.visitMethodEx(node)
    }

    private void checkAnnotations(AnnotatedNode node, String target) {
        node.annotations.each { AnnotationNode annotation ->
            checkAnnotation(annotation, target)
        }
    }

    private void checkAnnotation(AnnotationNode annotation, String target) {
        String simpleName = annotation.classNode.nameWithoutPackage
        boolean conditional = simpleName in CONDITIONAL_ANNOTATION_NAMES
        if (conditional) {
            if (!rule.checkConditionalAnnotations) {
                return
            }
        } else if (!(simpleName in configuredAnnotationNames())) {
            return
        }
        Expression reason = findStatedReason(annotation, simpleName)
        if (reason == null) {
            addViolation(annotation, "@${simpleName} without a reason - ${adviceFor(simpleName, target)}")
            return
        }
        checkReasonAgainstRegex(annotation, simpleName, reason)
    }

    private static String adviceFor(String simpleName, String target) {
        return String.format(ADVICE[simpleName] ?: DEFAULT_ADVICE, target)
    }

    /**
     * Reports a stated reason that does not match <code>reasonRegex</code>. Only called for an annotation
     * that is already being checked, so listing a conditional annotation in
     * <code>reasonRegexAnnotationNames</code> has no effect unless <code>checkConditionalAnnotations</code>
     * is enabled as well.
     */
    private void checkReasonAgainstRegex(AnnotationNode annotation, String simpleName, Expression reason) {
        if (!rule.reasonRegex || !(simpleName in configuredNames(rule.reasonRegexAnnotationNames))) {
            return
        }
        // Only a String literal can be matched. The value of a GString or a constant reference is unknown at
        // the CONVERSION compiler phase, so it counts as satisfied
        if (!(reason instanceof ConstantExpression)) {
            return
        }
        String statedReason = (reason as ConstantExpression).value
        if (!(statedReason =~ rule.reasonRegex)) {
            addViolation(annotation, "@${simpleName} reason does not match ${rule.reasonRegex}")
        }
    }

    private List<String> configuredAnnotationNames() {
        configuredNames(rule.annotationNames)
    }

    private static List<String> configuredNames(String value) {
        value?.tokenize(',')*.trim()?.findAll { it } ?: []
    }

    /**
     * The expression that states the reason, or null when none of the reason members holds one.
     */
    private static Expression findStatedReason(AnnotationNode annotation, String simpleName) {
        List<String> members = REASON_MEMBERS.containsKey(simpleName) ? [REASON_MEMBERS[simpleName]] : FALLBACK_REASON_MEMBERS
        return members.findResult { String member ->
            Expression expression = SpockUtil.getAnnotationMember(annotation, member)
            isReasonStated(expression) ? expression : null
        }
    }

    private static boolean isReasonStated(Expression expression) {
        if (expression == null) {
            return false
        }
        if (expression instanceof ConstantExpression) {
            Object value = (expression as ConstantExpression).value
            return value != null && !value.toString().trim().isEmpty()
        }
        // A GString, a constant reference or any other expression counts as a stated reason
        return true
    }
}
