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

import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.stmt.AssertStatement
import org.codehaus.groovy.ast.stmt.DoWhileStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.ForStatement
import org.codehaus.groovy.ast.stmt.IfStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.ast.stmt.SwitchStatement
import org.codehaus.groovy.ast.stmt.TryCatchStatement
import org.codehaus.groovy.ast.stmt.WhileStatement
import org.codenarc.rule.AbstractAstVisitor

/**
 * Abstract superclass for the AST visitors of Spock rules.
 *
 * It provides the bookkeeping that nearly every Spock rule needs:
 * <ul>
 *     <li>the whole class is skipped unless it is a Spock <code>Specification</code>, as configured
 *         by the rule's <code>specificationSuperclassNames</code> / <code>specificationClassNames</code>
 *         properties, so subclasses never have to gate manually</li>
 *     <li>the current Spock block label ({@link #getCurrentLabel()}), reset on method entry and only
 *         updated for top-level statements, because Spock only treats top-level labels as blocks</li>
 *     <li>the nesting depth inside <code>if</code>/<code>for</code>/<code>while</code>/<code>switch</code>/
 *         <code>try</code>/<code>do-while</code> statements ({@link #getNestingDepth()})</li>
 *     <li>whether the current method is a feature method, a fixture method or a plain helper method</li>
 *     <li>the enclosing closure, in particular whether it is the body of one of Spock's
 *         implicit-assertion methods (<code>with</code>, <code>verifyAll</code>, <code>verifyEach</code>)</li>
 * </ul>
 *
 * Note that this class does not traverse anything by itself; it only observes the traversal. A
 * subclass that overrides a <code>visitXxx</code> method without calling <code>super</code> stops the
 * traversal - and therefore the bookkeeping - below that node, exactly as it would with a plain
 * {@link AbstractAstVisitor}.
 *
 * @author Leonard Bruenings
 */
abstract class AbstractSpockAstVisitor<R extends AbstractSpockRule> extends AbstractAstVisitor<R> {

    private boolean spockSpecification = false
    private String spockLabel = null
    private int spockNestingDepth = 0
    private MethodNode spockMethod = null
    private boolean featureMethod = false
    private boolean fixtureMethod = false

    private final Deque<ClosureContext> closureContexts = new ArrayDeque<ClosureContext>()
    private final Map<ClosureExpression, String> implicitAssertionClosures = new IdentityHashMap<ClosureExpression, String>()

    //--------------------------------------------------------------------------
    // API for subclasses
    //--------------------------------------------------------------------------

    /**
     * @return the last Spock block label seen at nesting depth 0 within the current method, or null
     *         if no labelled block has been entered yet. Note that <code>and:</code> is deliberately
     *         not a Spock label - it does not start a new block.
     */
    protected String getCurrentLabel() {
        spockLabel
    }

    /**
     * @return true if the current block label is one where Spock adds implicit assertions
     *         (<code>then:</code>, <code>expect:</code>, <code>filter:</code>)
     */
    protected boolean isInImplicitAssertBlock() {
        SpockUtil.isImplicitAssertBlock(spockLabel)
    }

    /**
     * @return 0 at the statement level of a method, incremented once for every enclosing
     *         <code>if</code>/<code>for</code>/<code>while</code>/<code>switch</code>/<code>try</code>/
     *         <code>do-while</code> statement. Closures do not change the nesting depth.
     */
    protected int getNestingDepth() {
        spockNestingDepth
    }

    /**
     * @return the method currently being visited, or null if not inside a method
     */
    protected MethodNode getCurrentMethod() {
        spockMethod
    }

    /**
     * @return true while visiting a Spock feature method, i.e. a method with at least one Spock block label
     */
    protected boolean isInFeatureMethod() {
        featureMethod
    }

    /**
     * @return true while visiting one of Spock's fixture methods (setup, cleanup, setupSpec, cleanupSpec)
     */
    protected boolean isInFixtureMethod() {
        fixtureMethod
    }

    /**
     * @return true while visiting a method of a Specification that is neither a feature method nor a
     *         fixture method, i.e. a plain helper method
     */
    protected boolean isInHelperMethod() {
        spockMethod != null && !featureMethod && !fixtureMethod
    }

    /**
     * @return true if the traversal is currently inside at least one closure
     */
    protected boolean isInClosure() {
        !closureContexts.isEmpty()
    }

    /**
     * @return the name of the method whose closure argument is the innermost enclosing closure, if
     *         that method is one of Spock's implicit-assertion methods ('with', 'verifyAll',
     *         'verifyEach'); null for any other closure and when not inside a closure at all
     */
    protected String getEnclosingImplicitAssertionMethod() {
        closureContexts.peek()?.implicitAssertionMethod
    }

    /**
     * @return true if the innermost enclosing closure is the body of a 'with', 'verifyAll' or
     *         'verifyEach' call
     */
    protected boolean isInImplicitAssertionClosure() {
        enclosingImplicitAssertionMethod != null
    }

    /**
     * @return true where Spock evaluates an expression as an implicit condition: for a top-level
     *         statement of a <code>then:</code>/<code>expect:</code>/<code>filter:</code> block, or for
     *         <em>every</em> statement of a 'with'/'verifyAll'/'verifyEach' closure body - Spock's
     *         implicit-assertion methods are not limited to the top level of their closure, they also
     *         cover statements nested inside <code>if</code>/<code>for</code>/<code>while</code>/
     *         <code>switch</code>/<code>try</code>. Only a nested closure that is not another
     *         implicit-assertion closure ends the context. This is the predicate that decides whether an
     *         <code>assert</code> is redundant.
     */
    protected boolean isInImplicitAssertionContext() {
        ClosureContext context = closureContexts.peek()
        if (context != null) {
            return context.implicitAssertionMethod != null
        }
        return inImplicitAssertBlock && spockNestingDepth == 0
    }

    /**
     * Record the Spock block label of the given statement, if it carries one and the statement is at
     * the top level of the method. Subclasses that override a <code>visitXxx</code> method without
     * delegating to <code>super</code> must call this to keep {@link #getCurrentLabel()} accurate.
     *
     * @param statement - the statement whose statement labels are inspected
     */
    protected void updateCurrentLabel(Statement statement) {
        // Spock only treats top-level labels as blocks
        if (spockNestingDepth != 0 || !closureContexts.isEmpty()) {
            return
        }
        List<String> labels = statement.statementLabels
        if (labels != null) {
            Collection<String> spockLabels = labels.intersect(SpockUtil.SPOCK_LABELS)
            if (spockLabels.size() > 0) {
                spockLabel = spockLabels.last()
            }
        }
    }

    /**
     * Run the given closure with the nesting depth increased by one.
     *
     * @param callVisitorMethod - the traversal to perform
     */
    protected void withIncreasedNestingDepth(Closure callVisitorMethod) {
        spockNestingDepth++
        try {
            callVisitorMethod()
        } finally {
            spockNestingDepth--
        }
    }

    //--------------------------------------------------------------------------
    // Bookkeeping
    //--------------------------------------------------------------------------

    @Override
    protected void visitClassEx(ClassNode node) {
        spockSpecification = SpockUtil.isSpockSpecification(node, rule.specificationSuperclassNames, rule.specificationClassNames)
        super.visitClassEx(node)
    }

    @Override
    protected void visitClassComplete(ClassNode node) {
        super.visitClassComplete(node)
        spockSpecification = false
        implicitAssertionClosures.clear()
        closureContexts.clear()
    }

    @Override
    protected boolean shouldVisitMethod(MethodNode node) {
        spockSpecification && super.shouldVisitMethod(node)
    }

    @Override
    void visitField(FieldNode node) {
        if (spockSpecification) {
            super.visitField(node)
        }
    }

    @Override
    void visitProperty(PropertyNode node) {
        if (spockSpecification) {
            super.visitProperty(node)
        }
    }

    @Override
    protected void visitObjectInitializerStatements(ClassNode node) {
        if (spockSpecification) {
            super.visitObjectInitializerStatements(node)
        }
    }

    @Override
    void visitConstructorOrMethod(MethodNode node, boolean isConstructor) {
        if (!spockSpecification) {
            return
        }
        MethodNode previousMethod = spockMethod
        boolean previousFeatureMethod = featureMethod
        boolean previousFixtureMethod = fixtureMethod
        String previousLabel = spockLabel
        int previousNestingDepth = spockNestingDepth
        spockMethod = node
        featureMethod = !isConstructor && SpockUtil.isSpockFeatureMethod(node)
        fixtureMethod = !isConstructor && SpockUtil.isFixtureMethod(node)
        spockLabel = null
        spockNestingDepth = 0
        try {
            super.visitConstructorOrMethod(node, isConstructor)
        } finally {
            spockMethod = previousMethod
            featureMethod = previousFeatureMethod
            fixtureMethod = previousFixtureMethod
            spockLabel = previousLabel
            spockNestingDepth = previousNestingDepth
        }
    }

    @Override
    void visitExpressionStatement(ExpressionStatement statement) {
        if (!spockSpecification) {
            return
        }
        updateCurrentLabel(statement)
        super.visitExpressionStatement(statement)
    }

    @Override
    void visitAssertStatement(AssertStatement statement) {
        if (!spockSpecification) {
            return
        }
        updateCurrentLabel(statement)
        super.visitAssertStatement(statement)
    }

    @Override
    void visitDoWhileLoop(DoWhileStatement statement) {
        if (!spockSpecification) {
            return
        }
        updateCurrentLabel(statement)
        withIncreasedNestingDepth {
            super.visitDoWhileLoop(statement)
        }
    }

    @Override
    void visitForLoop(ForStatement statement) {
        if (!spockSpecification) {
            return
        }
        updateCurrentLabel(statement)
        withIncreasedNestingDepth {
            super.visitForLoop(statement)
        }
    }

    @Override
    void visitIfElse(IfStatement statement) {
        if (!spockSpecification) {
            return
        }
        updateCurrentLabel(statement)
        withIncreasedNestingDepth {
            super.visitIfElse(statement)
        }
    }

    @Override
    void visitSwitch(SwitchStatement statement) {
        if (!spockSpecification) {
            return
        }
        updateCurrentLabel(statement)
        withIncreasedNestingDepth {
            super.visitSwitch(statement)
        }
    }

    @Override
    void visitTryCatchFinally(TryCatchStatement statement) {
        if (!spockSpecification) {
            return
        }
        updateCurrentLabel(statement)
        withIncreasedNestingDepth {
            super.visitTryCatchFinally(statement)
        }
    }

    @Override
    void visitWhileLoop(WhileStatement statement) {
        if (!spockSpecification) {
            return
        }
        updateCurrentLabel(statement)
        withIncreasedNestingDepth {
            super.visitWhileLoop(statement)
        }
    }

    @Override
    void visitMethodCallExpression(MethodCallExpression call) {
        if (!spockSpecification) {
            return
        }
        String methodName = SpockUtil.getMethodName(call)
        // Only an unqualified call is Spock's own with/verifyAll/verifyEach; 'obj.with { }' is Groovy's
        // Object.with, which does not turn its closure body into implicit conditions
        if (call.implicitThis && methodName in SpockUtil.METHODS_WITH_IMPLICIT_ASSERTIONS) {
            ClosureExpression closure = SpockUtil.getClosureArgument(call)
            if (closure != null) {
                implicitAssertionClosures.put(closure, methodName)
            }
        }
        super.visitMethodCallExpression(call)
    }

    @Override
    void visitClosureExpression(ClosureExpression expression) {
        if (!spockSpecification) {
            return
        }
        closureContexts.push(new ClosureContext(implicitAssertionClosures.get(expression)))
        try {
            super.visitClosureExpression(expression)
        } finally {
            closureContexts.pop()
        }
    }

    private static class ClosureContext {

        final String implicitAssertionMethod

        ClosureContext(String implicitAssertionMethod) {
            this.implicitAssertionMethod = implicitAssertionMethod
        }
    }
}
