/*
 * Copyright 2013 Mozilla Foundation
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mozilla.bagheera.producer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Properties;
import java.util.UUID;

import kafka.api.FetchRequest;
import kafka.javaapi.consumer.SimpleConsumer;
import kafka.javaapi.message.ByteBufferMessageSet;
import kafka.javaapi.producer.ProducerData;
import kafka.message.Message;
import kafka.message.MessageAndOffset;
import kafka.producer.ProducerConfig;
import kafka.server.KafkaConfig;
import kafka.server.KafkaServer;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.protobuf.ByteString;
import com.mozilla.bagheera.BagheeraProto.BagheeraMessage;

public class ProducerTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private String KAFKA_DIR;
    private static final int BATCH_SIZE = 10;
    private static final int MAX_MESSAGE_SIZE = 500;
    private static final int GOOD_MESSAGE_SIZE = 100;
    private static final int BAD_MESSAGE_SIZE = 1000;
    private static final int KAFKA_BROKER_ID = 0;
    private static final int KAFKA_BROKER_PORT = 9090;
    private static final String KAFKA_TOPIC = "test";

    private KafkaServer server;

    @Before
    public void setup() throws IOException {
        // We get a NoClassDefFoundError without this.
        if (!new File("./target/classes/com/mozilla/bagheera/BagheeraProto.class").exists()) {
            fail("You must run 'mvn compile' before the tests will run properly from Eclipse");
        }

//        File tempDir = Files.createTempDir();
//        KAFKA_DIR = tempDir.getCanonicalPath();
//        KAFKA_DIR = folder.newFolder("kafka").getCanonicalPath();
        KAFKA_DIR = "/tmp/testkafka";

        System.out.println("Using kafka temp dir: " + KAFKA_DIR);

        startServer();

    }

    private void startServer() {
        stopServer();
        Properties props = new Properties();
        props.setProperty("hostname", "localhost");
        props.setProperty("port", String.valueOf(KAFKA_BROKER_PORT));
        props.setProperty("brokerid", String.valueOf(KAFKA_BROKER_ID));
        props.setProperty("log.dir", KAFKA_DIR);
        props.setProperty("enable.zookeeper", "false");

        // flush every message.
        props.setProperty("log.flush.interval", "1");

        server = new KafkaServer(new KafkaConfig(props));
        server.startup();
    }

    private void stopServer() {
        if (server != null) {
            server.shutdown();
            server.awaitShutdown();
            server = null;
        }
    }

    @After
    public void shutdown() {
        System.out.println("After tests, kafka dir still exists? " + new File(KAFKA_DIR).exists());
        stopServer();
    }

    @Test
    public void testAsyncBatch() throws IOException, InterruptedException {

//        produceData();
//        startServer();


        SimpleConsumer consumer = new SimpleConsumer("localhost", KAFKA_BROKER_PORT, 100, 1024);
        long offset = 0l;
        int messageCount = 0;

        for (int i = 0; i < BATCH_SIZE; i++) {
            ByteBufferMessageSet messageSet = consumer.fetch(new FetchRequest(KAFKA_TOPIC, 0, offset, 1024));

            Iterator<MessageAndOffset> iterator = messageSet.iterator();
            MessageAndOffset msgAndOff;
            while (iterator.hasNext()) {
                messageCount++;
                msgAndOff = iterator.next();
                offset = msgAndOff.offset();
                Message message2 = msgAndOff.message();
                BagheeraMessage bmsg = BagheeraMessage.parseFrom(ByteString.copyFrom(message2.payload()));

                String payload = new String(bmsg.getPayload().toByteArray());
//                ByteBuffer payload = message2.payload();
//                byte[] bytes = new byte[payload.remaining()];
//                payload.get(bytes);
//                String payloadString = new String(bytes);
                System.out.println(String.format("Message %d @%d: %s", messageCount, offset, payload));
            }
        }

        consumer.close();

        assertEquals(BATCH_SIZE + 2, messageCount);
    }

    private void produceData() {
        Properties props = getProperties();
        kafka.javaapi.producer.Producer<String,BagheeraMessage> producer = new kafka.javaapi.producer.Producer<String,BagheeraMessage>(new ProducerConfig(props));
        BagheeraMessage msg = getMessage(GOOD_MESSAGE_SIZE);

        assertEquals(GOOD_MESSAGE_SIZE, msg.getPayload().size());
        producer.send(getProducerData(msg));
        producer.send(getProducerData(getMessage(GOOD_MESSAGE_SIZE)));

//        producer.send(getProducerData(getMessage(BAD_MESSAGE_SIZE)));
        for (int i = 0; i < BATCH_SIZE; i++) {
            producer.send(getProducerData(getMessage(GOOD_MESSAGE_SIZE)));
        }
        producer.close();
    }

    private ProducerData<String,BagheeraMessage> getProducerData(BagheeraMessage msg) {
        return new ProducerData<String,BagheeraMessage>(msg.getNamespace(), msg);
    }

    private BagheeraMessage getMessage(int payloadSize) {
        BagheeraMessage.Builder bmsgBuilder = BagheeraMessage.newBuilder();
        bmsgBuilder.setNamespace(KAFKA_TOPIC);
        bmsgBuilder.setId(UUID.randomUUID().toString());
        bmsgBuilder.setIpAddr(ByteString.copyFrom("192.168.1.10".getBytes()));

        StringBuilder content = new StringBuilder(payloadSize);
        for (int i = 0; i < payloadSize; i++) {
            content.append(i % 10);
        }
        bmsgBuilder.setPayload(ByteString.copyFrom(content.toString().getBytes()));
        bmsgBuilder.setTimestamp(System.currentTimeMillis());
        return bmsgBuilder.build();
    }

    private Properties getProperties() {
        Properties props = new Properties();
        props.setProperty("producer.type",    "sync");
        props.setProperty("batch.size",       String.valueOf(BATCH_SIZE));
        props.setProperty("max.message.size", String.valueOf(MAX_MESSAGE_SIZE));
        props.setProperty("broker.list",      KAFKA_BROKER_ID + ":localhost:" + KAFKA_BROKER_PORT);
        props.setProperty("serializer.class", "com.mozilla.bagheera.serializer.BagheeraEncoder");

//broker.list=0:localhost:9092
//serializer.class=com.mozilla.bagheera.serializer.BagheeraEncoder
//producer.type=async
//compression.codec=2
//batch.size=100

        return props;
    }
}
