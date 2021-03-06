/*
 * Copyright 2019 the original author or authors.
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
package org.codenarc.rule.convention

import org.codenarc.rule.AbstractRuleTestCase
import org.codenarc.rule.Rule
import org.codenarc.rule.Violation
import org.junit.Test

/**
 * Tests for CompileStatic rule.
 *
 * @author Sudhir Nimavat
 */
class CompileStaticRuleTest extends AbstractRuleTestCase {

    boolean skipTestThatUnrelatedCodeHasNoViolations = true

    @Override
    protected Rule createRule() {
        return new CompileStaticRule()
    }

    @Test
    void testClassWithoutExplicitCompileStaticNotAllowed() {
        final SOURCE = 'class Test {  }'

        assertSingleViolation(SOURCE) { Violation violation ->
            violation.rule.priority == 2
            violation.rule.name == 'CompileStatic'
        }
    }

    @Test
    void testClassWithGrailsCompileStaticIsAllowed() {
        final SOURCE = '''
            import grails.compiler.GrailsCompileStatic
            @GrailsCompileStatic
            class Test { }
         '''
        assertNoViolations(SOURCE)
    }

    @Test
    void testClassWithCompileStaticIsAllowed() {
        final SOURCE = '''
            import groovy.transform.CompileStatic
            @CompileStatic
            class Test { }
         '''
        assertNoViolations(SOURCE)
    }

    @Test
    void testClassWithCompileDynamicIsAllowed() {
        final SOURCE = '''
            import groovy.transform.CompileDynamic
            @CompileDynamic
            class Test { }
         '''

        assertNoViolations(SOURCE)
    }

    @Test
    void testInterfaceIsNotChecked() {
        final SOURCE = 'interface Test { }'
        assertNoViolations(SOURCE)
    }

    @Test
    void testInnerClasseIsNotChecked() {
        final SOURCE = '''
            import groovy.transform.CompileDynamic
            @CompileDynamic
            class Test {
                class AnInnerClass { }
            }
         '''
        assertNoViolations(SOURCE)
    }

    @Test
    void testEnum() {
        final SOURCE = 'enum Test { OPTION_ONE, OPTION_TWO  }'

        assertSingleViolation(SOURCE) { Violation violation ->
            violation.rule.priority == 2
            violation.rule.name == 'CompileStatic'
        }

        SOURCE = '''
            import groovy.transform.CompileStatic
            @CompileStatic
            enum Test { OPTION_ONE, OPTION_TWO  }
          '''

        assertNoViolations(SOURCE)
    }

    @Test
    void testAbstractClass() {
        final SOURCE = 'abstract class Test { }'

        assertSingleViolation(SOURCE) { Violation violation ->
            violation.rule.priority == 2
            violation.rule.name == 'CompileStatic'
        }

        SOURCE = '''
            import groovy.transform.CompileStatic
            @CompileStatic
            abstract class Test { }
          '''

        assertNoViolations(SOURCE)
    }
}
