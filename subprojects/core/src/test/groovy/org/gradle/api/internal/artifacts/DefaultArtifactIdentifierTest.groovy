/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts

import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId

class DefaultArtifactIdentifierTest extends Specification {

    def "equals"() {
        def base = new DefaultArtifactIdentifier(newId("org", "lib", "1.0"), "someArtifact", "jar", "jar", "")
        def same = new DefaultArtifactIdentifier(newId("org", "lib", "1.0"), "someArtifact", "jar", "jar", "")

        def _id = new DefaultArtifactIdentifier(newId("org", "lib", "2.0"), "someArtifact", "jar", "jar", "")
        def _name = new DefaultArtifactIdentifier(newId("org", "lib", "1.0"), "foo", "jar", "jar", "")
        def _type = new DefaultArtifactIdentifier(newId("org", "lib", "1.0"), "someArtifact", "bar", "jar", "")
        def _extension = new DefaultArtifactIdentifier(newId("org", "lib", "1.0"), "someArtifact", "jar", "xxx", "")
        def _classifier = new DefaultArtifactIdentifier(newId("org", "lib", "1.0"), "someArtifact", "jar", "jar", "sources")

        expect:
        base == same
        same == base

        base != _id
        base != _name
        base != _type
        base != _extension
        base != _classifier
    }
}