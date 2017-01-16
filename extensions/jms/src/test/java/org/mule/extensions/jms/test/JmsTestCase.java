/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.jms.test;

import org.junit.Rule;
import org.junit.Test;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.core.api.Event;
import org.mule.runtime.core.construct.Flow;
import org.mule.tck.junit4.rule.SystemProperty;
import org.mule.tck.probe.JUnitLambdaProbe;
import org.mule.tck.probe.PollingProber;

import java.util.Collections;
import java.util.Queue;

public class JmsTestCase extends JmsAbstractTestCase {

  @Rule
  public SystemProperty destination = new SystemProperty("destination", newDestination("topicListenerDestination"));

  @Override
  protected String[] getConfigFiles() {
    return new String[] {"source/jms-redelivery.xml", "config/activemq/activemq-default-with-redelivery.xml"};
  }

  //    @Test
  //    public void testRedelivery() throws Exception {
  //        Flow listener = (Flow) getFlowConstruct("listener");
  //        listener.start();
  //
  //        publish("this is a message", "topicListenerDestination", Collections.singletonMap("maxRedelivery", "2"));
  //            PollingProber prober = new PollingProber(300300, 6000);
  //    prober.check(new JUnitLambdaProbe(() -> receivedMessages.size() > 600));
  //        System.out.println(receivedMessages);
  //    }

  @Test
  public void test() throws Exception {
    ((Flow) getFlowConstruct("listener2")).start();
    Flow listener = (Flow) getFlowConstruct("listener");
    listener.start();
    new PollingProber(5000, 1000).check(new JUnitLambdaProbe(listener::isStarted));


    sendMessage();
    sendMessage();
    sendMessage();
    sendMessage();
    sendMessage();
    sendMessage();
    sendMessage();
    sendMessage();
    sendMessage();
    sendMessage();
    sendMessage();
    sendMessage();
    sendMessage();
    sendMessage();
    sendMessage();
    sendMessage();
    sendMessage();
    sendMessage();
    sendMessage();
    sendMessage();


    PollingProber prober = new PollingProber(300300, 1000);
    prober.check(new JUnitLambdaProbe(() -> !SomeClass.rawMessages.isEmpty()));

    Queue<Message> rawMessages = SomeClass.rawMessages;

    prober.check(new JUnitLambdaProbe(() -> SomeClass.rawMessages.size() > 1));

    rawMessages = SomeClass.rawMessages;
  }

  private Event sendMessage() throws Exception {
    return flowRunner("publisher")
        .withVariable("destination", "topicListenerDestination")
        .withPayload("this is the message").run();
  }
}
