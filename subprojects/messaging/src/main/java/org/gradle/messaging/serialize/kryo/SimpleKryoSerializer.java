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

package org.gradle.messaging.serialize.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.sun.imageio.spi.OutputStreamImageOutputStreamSpi;
import org.gradle.messaging.serialize.Serializer;
import org.objenesis.strategy.SerializingInstantiatorStrategy;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created with IntelliJ IDEA. User: szczepan Date: 4/11/13 Time: 8:28 PM To change this template use File | Settings | File Templates.
 */
public class SimpleKryoSerializer<T> implements Serializer<T> {

    private final Kryo kryo;

    public SimpleKryoSerializer() {
        kryo = new Kryo();
        kryo.setInstantiatorStrategy(new SerializingInstantiatorStrategy());
        kryo.register(File.class, new com.esotericsoftware.kryo.Serializer() {
            @Override
            public void write(Kryo kryo, Output output, Object object) {
                kryo.writeObject(output, ((File)object).getAbsolutePath());
            }

            @Override
            public Object read(Kryo kryo, Input input, Class type) {
                String value = kryo.readObject(input, String.class);
                return new File(value);
            }
        });
    }

    public T read(InputStream instr) throws Exception {
        Input input = new Input(instr);
        try {
            return (T) kryo.readClassAndObject(input);
        } finally {
            input.close();
        }
    }

    public void write(OutputStream outstr, T value) throws Exception {
        Output output = new Output(outstr);
        try {
            kryo.writeClassAndObject(output, value);
        } finally {
            output.close();
        }
    }
}
