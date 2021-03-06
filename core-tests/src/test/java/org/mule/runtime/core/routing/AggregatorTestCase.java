/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.routing;

import static java.util.Optional.of;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.mule.runtime.core.DefaultEventContext;
import org.mule.runtime.core.api.EventContext;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.Event;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.core.api.message.InternalMessage;
import org.mule.runtime.core.api.MuleSession;
import org.mule.runtime.core.api.store.ObjectStoreException;
import org.mule.runtime.core.construct.Flow;
import org.mule.runtime.core.routing.correlation.EventCorrelatorCallback;
import org.mule.runtime.core.session.DefaultMuleSession;
import org.mule.tck.junit4.AbstractMuleContextTestCase;
import org.mule.tck.testmodels.fruit.Apple;

import java.util.Iterator;

import org.junit.Test;


public class AggregatorTestCase extends AbstractMuleContextTestCase {

  public AggregatorTestCase() {
    setStartContext(true);
  }

  @Test
  public void testMessageAggregator() throws Exception {
    Flow flow = getTestFlowWithComponent("test", Apple.class);
    MuleSession session = new DefaultMuleSession();

    TestEventAggregator router = new TestEventAggregator(3);
    router.setMuleContext(muleContext);
    router.setFlowConstruct(flow);
    router.initialise();

    EventContext context = DefaultEventContext.create(flow, TEST_CONNECTOR, "foo");

    InternalMessage message1 = InternalMessage.builder().payload("test event A").build();
    InternalMessage message2 = InternalMessage.builder().payload("test event B").build();
    InternalMessage message3 = InternalMessage.builder().payload("test event C").build();

    Event event1 = Event.builder(context).message(message1).flow(flow).session(session).build();
    Event event2 = Event.builder(context).message(message2).flow(flow).session(session).build();
    Event event3 = Event.builder(context).message(message3).flow(flow).session(session).build();

    assertNull(router.process(event1));
    assertNull(router.process(event2));

    Event result = router.process(event3);
    assertNotNull(result);
    assertTrue(result.getMessageAsString(muleContext).contains("test event A"));
    assertTrue(result.getMessageAsString(muleContext).contains("test event B"));
    assertTrue(result.getMessageAsString(muleContext).contains("test event C"));
    assertTrue(result.getMessageAsString(muleContext).matches("test event [A,B,C] test event [A,B,C] test event [A,B,C] "));
  }

  public static class TestEventAggregator extends AbstractAggregator {

    protected final int eventThreshold;
    protected int eventCount = 0;

    public TestEventAggregator(int eventThreshold) {
      this.eventThreshold = eventThreshold;
    }

    @Override
    protected EventCorrelatorCallback getCorrelatorCallback(final MuleContext muleContext) {
      return new EventCorrelatorCallback() {

        @Override
        public boolean shouldAggregateEvents(EventGroup events) {
          eventCount++;
          if (eventCount == eventThreshold) {
            eventCount = 0;
            return true;
          }
          return false;
        }

        @Override
        public EventGroup createEventGroup(Event event, Object groupId) {
          return new EventGroup(groupId, muleContext, of(eventThreshold), storePrefix);
        }

        @Override
        public Event aggregateEvents(EventGroup events) throws AggregationException {
          if (events.size() != eventThreshold) {
            throw new IllegalStateException("eventThreshold not yet reached?");
          }

          StringBuilder newPayload = new StringBuilder(80);

          try {
            for (Iterator iterator = events.iterator(false); iterator.hasNext();) {
              Event event = (Event) iterator.next();
              try {
                newPayload.append(event.getMessageAsString(muleContext)).append(" ");
              } catch (MuleException e) {
                throw new AggregationException(events, next, e);
              }
            }
          } catch (ObjectStoreException e) {
            throw new AggregationException(events, next, e);
          }

          return Event.builder(events.getMessageCollectionEvent())
              .message(InternalMessage.builder().payload(newPayload.toString()).build()).build();
        }
      };
    }
  }
}
