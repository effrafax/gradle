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

package org.gradle.messaging.serialize.kryo

import spock.lang.Specification

/**
 * Created with IntelliJ IDEA.
 * User: szczepan
 * Date: 4/11/13
 * Time: 8:02 PM
 * To change this template use File | Settings | File Templates.
 */
class SimpleKryoSerializerTest extends Specification {

    def serializer = new SimpleKryoSerializer()
    def out = new ByteArrayOutputStream()

    def "string"() {
        expect:
        serializer.write(out, "foo bar")
        "foo bar" == serializer.read(new ByteArrayInputStream(out.toByteArray()))
    }

    def "file"() {
        expect:
        serializer.write(out, new File("foo"))
        new File("foo").absoluteFile == serializer.read(new ByteArrayInputStream(out.toByteArray()))
    }

    def "some class"() {
        expect:
        serializer.write(out, new Foo(x: 11, y: 12))
        11 == serializer.read(new ByteArrayInputStream(out.toByteArray())).x
        2 == serializer.read(new ByteArrayInputStream(out.toByteArray())).y
    }

    static class Foo implements Serializable {
        int x = 1
        transient int y = 2
    }
}
