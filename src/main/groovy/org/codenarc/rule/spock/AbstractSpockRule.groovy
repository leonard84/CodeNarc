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

import org.codenarc.rule.AbstractAstVisitorRule

/**
 * Abstract superclass for rules that apply to Spock specifications.
 *
 * It holds the two configuration properties that every Spock rule shares, which determine
 * whether a class is treated as a Spock <code>Specification</code>:
 * <ul>
 *     <li><code>specificationSuperclassNames</code> - defaults to <code>"*Specification"</code></li>
 *     <li><code>specificationClassNames</code> - defaults to <code>null</code></li>
 * </ul>
 *
 * The matching AST visitor superclass is {@link AbstractSpockAstVisitor}, which only visits classes
 * that match these two properties.
 *
 * @author Leonard Bruenings
 */
abstract class AbstractSpockRule extends AbstractAstVisitorRule {

    /**
     * A class that extends a class whose name matches one of these (comma-separated, optionally
     * wildcarded) names is considered a Spock Specification.
     */
    String specificationSuperclassNames = '*Specification'

    /**
     * A class whose name matches one of these (comma-separated, optionally wildcarded) names is
     * considered a Spock Specification, regardless of its superclass.
     */
    String specificationClassNames = null
}
