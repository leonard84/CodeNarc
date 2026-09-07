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
 * Spock 2 unrolls data-driven features by default, so an `@Unroll` without a value is a no-op
 * left over from Spock 1.x. An `@Unroll("...")` with an iteration-name template is never
 * reported. `@Unroll("")` is not such a template - `Unroll.value()` already defaults to the empty
 * String - so that form is reported like the bare one.
 *
 * The rule stays silent when a bare `@Unroll` still has an effect, i.e. when the Specification class that
 * declares the feature carries `@Rollup` - there the bare `@Unroll` re-enables unrolling for that one
 * feature. A `@Rollup` on a superclass is not considered: Spock applies `@Unroll`/`@Rollup` per declaring
 * class, and neither annotation is inheritable. A `@Rollup` next to the `@Unroll` it would cancel out is
 * not considered either, because Spock rejects that combination with an InvalidSpecException.
 *
 * @author Leonard Bruenings
 */
class SpockUnnecessaryUnrollRule extends AbstractSpockRule {

    String name = 'SpockUnnecessaryUnroll'
    int priority = 3
    Class astVisitorClass = SpockUnnecessaryUnrollAstVisitor
}

class SpockUnnecessaryUnrollAstVisitor extends AbstractSpockAstVisitor<SpockUnnecessaryUnrollRule> {

    private static final String UNROLL = 'Unroll'
    private static final String ROLLUP = 'Rollup'
    private static final String MESSAGE =
        "'@Unroll' without a value is redundant - since Spock 2 unrolls by default; remove it, or give it an iteration-name template"

    @Override
    protected void visitClassEx(ClassNode node) {
        super.visitClassEx(node)
        // AbstractSpockAstVisitor gates the method and statement callbacks, but not this one
        if (!SpockUtil.isSpockSpecification(node, rule.specificationSuperclassNames, rule.specificationClassNames)) {
            return
        }
        // Nothing can make a class-level bare '@Unroll' do anything: a '@Rollup' on a superclass does not
        // roll up a subclass, and a '@Rollup' on this class makes Spock throw an InvalidSpecException
        AnnotationNode unroll = findBareUnroll(node)
        if (unroll != null) {
            addViolation(unroll, MESSAGE)
        }
    }

    @Override
    protected void visitMethodEx(MethodNode node) {
        // Only feature methods are unrolled, so only there can an @Unroll be called redundant
        if (SpockUtil.isSpockFeatureMethod(node)) {
            AnnotationNode unroll = findBareUnroll(node)
            // Only the declaring class is inspected: '@Rollup' is not inheritable, so one further up cannot
            // affect this method, and one on the method itself makes Spock throw an InvalidSpecException
            if (unroll != null && !SpockUtil.hasAnnotation(node.declaringClass, ROLLUP)) {
                addViolation(unroll, MESSAGE)
            }
        }
        super.visitMethodEx(node)
    }

    private static AnnotationNode findBareUnroll(AnnotatedNode node) {
        AnnotationNode unroll = SpockUtil.findAnnotation(node, UNROLL)
        if (unroll == null) {
            return null
        }
        // A bare '@Unroll' and an '@Unroll()' both have an empty member map
        if (!unroll.members) {
            return unroll
        }
        // '@Unroll("")' carries no template either: Unroll.value() already defaults to the empty String and
        // Spock's UnrollExtension only uses a pattern when it is non-empty, so the two forms behave the
        // same. Any other member - a constant reference, a GString - cannot be evaluated from the source,
        // so it is not reported.
        Expression value = SpockUtil.getAnnotationMember(unroll, 'value')
        boolean emptyTemplate = value instanceof ConstantExpression && (value as ConstantExpression).value == ''
        return (unroll.members.size() == 1 && emptyTemplate) ? unroll : null
    }
}
