---
layout: default
title: CodeNarc - Spock Rules
---

# Spock Rules  ("*rulesets/spock.xml*")

*This rule set was introduced in CodeNarc 4.1.0.* The **SpockIgnoreRestUsed**, **SpockMissingAssert** and
**SpockUseVerifyEach** rules moved here from the [JUnit](./codenarc-rules-junit.html) rule set; their entries in
*rulesets/junit.xml* are disabled by default and will be removed in a future release.


## SpockIgnoreRestUsed Rule

*Since CodeNarc 0.14*

If Spock's `@IgnoreRest` annotation appears on any method, all non-annotated test methods are not executed.
This behaviour is almost always unintended. It's fine to use @IgnoreRest locally during development, but when
committing code, it should be removed.

The *specificationClassNames* and *specificationSuperclassNames* properties determine which classes are considered
Spock *Specification* classes.

| Property                    | Description            | Default Value    |
|-----------------------------|------------------------|------------------|
| specificationClassNames     | Specifies one or more (comma-separated) class names that should be treated as Spock Specification classes. The class names may optionally contain wildcards (*,?), e.g. "*Spec". | `null` |
| specificationSuperclassNames| Specifies one or more (comma-separated) class names that should be treated as Spock Specification superclasses. In other words, a class that extends a matching class name is considered a Spock Specification . The class names may optionally contain wildcards (*,?), e.g. "*Spec". | "*Specification" |

Example of violations:

```
    public class MySpec extends spock.lang.Specification {
        @spock.lang.IgnoreRest
        def "my first feature"() {
            expect: false
        }

        def "my second feature"() {
            given: def a = 2

            when: a *= 2

            then: a == 4
        }
    }
```


## SpockMissingAssert Rule

*Since CodeNarc 3.3.0*

Spock treats all expressions on the first level of a then or expect block as an implicit assertion.
However, everything inside if/for/switch/... blocks is not an implicit assert, just a useless comparison (unless wrapped by a `with` or `verifyAll`).

This rule finds such expressions, where an explicit call to `assert` would be required. Please note that the rule might
produce false positives, as it relies on method names to determine whether an expression has a boolean type or not.

Example of violations:

```
    public class MySpec extends spock.lang.Specification {
        def "test passes - does not behave as expected"() {
            expect:
            if (true) {
                true == false // violation - is inside an if block, and therefore not treated as an implicit assertion by spock
            }
        }

        def "test fails - behaves as expected"() {
            expect:
            if (true) {
                with(new Object()) {
                    true == false // no violation - expressions in with are treated as implicit assertions by spock
                }
            }
        }
    }
```

| Property                    | Description            | Default Value    |
|-----------------------------|------------------------|------------------|
| specificationClassNames     | Specifies one or more (comma-separated) class names that should be treated as Spock Specification classes. The class names may optionally contain wildcards (*,?), e.g. "*Spec". | `null` |
| specificationSuperclassNames| Specifies one or more (comma-separated) class names that should be treated as Spock Specification superclasses. In other words, a class that extends a matching class name is considered a Spock Specification . The class names may optionally contain wildcards (*,?), e.g. "*Spec". | "*Specification" |


## SpockUseVerifyEach Rule

*Since CodeNarc 3.7.0*

Checks for `.every`, `.each`, `.eachWithIndex`, or `.forEach` calls containing assertions in Spock specifications
that should use Spock 2.4's `verifyEach` instead. Using `verifyEach` provides better per-item failure diagnostics
by collecting all failures individually rather than failing fast on the first item.

In `then:` and `expect:` blocks, the rule detects:
- `.every { booleanExpr }` — implicit boolean assertion
- `.each`/`.eachWithIndex`/`.forEach` with closures containing `assert` statements or boolean expressions

When `checkAllBlocks` is enabled (the default), the rule also detects `.each`/`.eachWithIndex`/`.forEach` with
explicit `assert` statements in other blocks (e.g., `given:`, `when:`) and in helper methods.

Example of violations:

```
    class MySpec extends spock.lang.Specification {
        def "test"() {
            given:
            def list = [1, 2, 3]

            expect:
            list.every { it > 0 }                   // violation - use verifyEach(list) { it > 0 }
            list.each { assert it > 0 }              // violation - use verifyEach(list) { it > 0 }
            list.eachWithIndex { item, i ->
                assert item > 0                      // violation - use verifyEach(list) { item, i -> item > 0 }
            }
            list.forEach { assert it > 0 }           // violation - use verifyEach(list) { it > 0 }
        }
    }
```

| Property                    | Description            | Default Value    |
|-----------------------------|------------------------|------------------|
| checkAllBlocks              | If `true`, also checks blocks other than `then:` and `expect:` (and helper methods) for `.each`/`.eachWithIndex`/`.forEach` with explicit `assert` statements. | `true` |
| specificationClassNames     | Specifies one or more (comma-separated) class names that should be treated as Spock Specification classes. The class names may optionally contain wildcards (*,?), e.g. "*Spec". | `null` |
| specificationSuperclassNames| Specifies one or more (comma-separated) class names that should be treated as Spock Specification superclasses. In other words, a class that extends a matching class name is considered a Spock Specification . The class names may optionally contain wildcards (*,?), e.g. "*Spec". | "*Specification" |

**NOTE:** This rule requires Spock 2.4+ which introduces the `verifyEach` method. If you are using an older version of Spock, disable this rule.

