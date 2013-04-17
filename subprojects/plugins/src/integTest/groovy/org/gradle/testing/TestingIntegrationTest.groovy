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
package org.gradle.testing

import org.apache.commons.lang.RandomStringUtils
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import org.gradle.internal.os.OperatingSystem
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Timeout
import spock.lang.Unroll

/**
 * General tests for the JVM testing infrastructure that don't deserve their own test class.
 */
class TestingIntegrationTest extends AbstractIntegrationSpec {

    @Timeout(30)
    @Issue("http://issues.gradle.org/browse/GRADLE-1948")
    def "test interrupting its own thread does not kill test execution"() {
        given:
        buildFile << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile "junit:junit:4.11" }
        """

        and:
        file("src/test/java/SomeTest.java") << """
            import org.junit.*;

            public class SomeTest {
                @Test public void foo() {
                    Thread.currentThread().interrupt();
                }
            }
        """

        when:
        run "test"

        then:
        ":test" in nonSkippedTasks
    }

    @IgnoreIf({ OperatingSystem.current().isWindows() })
    def "can use long paths for workindDir"() {
        given:
        // windows can handle a path up to 260 characters
        // we create a path that is 260 +1 (offset + "/" + randompath)
        def pathoffset = 260 - testDirectory.getAbsolutePath().length()
        def alphanumeric = RandomStringUtils.randomAlphanumeric(pathoffset)
        def testWorkingDir = testDirectory.createDir("$alphanumeric")

        buildFile << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile "junit:junit:4.11" }
            test.workingDir = "${testWorkingDir.toURI()}"
        """

        and:
        file("src/test/java/SomeTest.java") << """
            import org.junit.*;

            public class SomeTest {
                @Test public void foo() {
                    System.out.println(new java.io.File(".").getAbsolutePath());
                }
            }
        """

        when:
        run "test"

        then:
        ":test" in nonSkippedTasks
    }

    @Issue("http://issues.gradle.org/browse/GRADLE-2313")
    @Unroll
    "can clean test after extracting class file with #framework"() {
        when:
        buildFile << """
            apply plugin: "java"
            repositories.mavenCentral()
            dependencies { testCompile "$dependency" }
            test { $framework() }
        """
        and:
        file("src/test/java/SomeTest.java") << """
            public class SomeTest extends $superClass {
            }
        """
        then:
        succeeds "clean", "test"

        and:
        file("build/tmp/test").exists() // ensure we extracted classes

        where:
        framework   | dependency                | superClass
        "useJUnit"  | "junit:junit:4.11"        | "org.junit.runner.Result"
        "useTestNG" | "org.testng:testng:6.3.1" | "org.testng.Converter"
    }

    @Timeout(30)
    @Issue("http://issues.gradle.org/browse/GRADLE-2527")
    def "test class detection works for custom test tasks"() {
        given:
        buildFile << """
                apply plugin:'java'
                repositories{ mavenCentral() }

                sourceSets{
	                othertests{
		                java.srcDir file('src/othertests/java')
	                    resources.srcDir file('src/othertests/resources')
	                }
                }

                dependencies{
	                othertestsCompile "junit:junit:4.11"
                }

                task othertestsTest(type:Test){
	                useJUnit()
	                classpath = sourceSets.othertests.runtimeClasspath
	                testClassesDir = sourceSets.othertests.output.classesDir
	            }
            """

        and:
        file("src/othertests/java/AbstractTestClass.java") << """
                import junit.framework.TestCase;
                public abstract class AbstractTestClass extends TestCase {
                }
            """

        file("src/othertests/java/TestCaseExtendsAbstractClass.java") << """
                import junit.framework.Assert;
                public class TestCaseExtendsAbstractClass extends AbstractTestClass{
                    public void testTrue() {
                        Assert.assertTrue(true);
                    }
                }
            """

        when:
        run "othertestsTest"
        then:
        def result = new JUnitXmlTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted("TestCaseExtendsAbstractClass")
    }
}