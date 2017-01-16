/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.jms.test;

import org.mule.runtime.api.message.Message;
import org.mule.runtime.core.api.MuleEventContext;
import org.mule.runtime.core.api.lifecycle.Callable;

import java.util.LinkedList;
import java.util.Queue;

public class SomeClass implements Callable {

  public static Queue<Object> payloads = new LinkedList<>();
  public static Queue<Message> rawMessages = new LinkedList<>();

  @Override
  public Object onCall(MuleEventContext eventContext) throws Exception {
    payloads.add(eventContext.getMessage().getPayload().getValue());
    rawMessages.add(eventContext.getMessage());
    return null;
  }
}
