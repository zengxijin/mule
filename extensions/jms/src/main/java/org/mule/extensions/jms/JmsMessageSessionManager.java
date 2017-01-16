/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.jms;

import static java.lang.String.format;
import static org.slf4j.LoggerFactory.getLogger;
import org.mule.extensions.jms.api.config.AckMode;
import org.mule.extensions.jms.api.exception.JmsAckException;
import org.mule.runtime.api.lifecycle.Disposable;
import org.slf4j.Logger;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import java.util.HashMap;
import java.util.Map;

public class JmsMessageSessionManager implements Disposable {

  private static final Logger LOGGER = getLogger(JmsMessageSessionManager.class);
  private final Map<String, Message> pendingAckSessions = new HashMap<>();

  /**
   * Registers the {@link Message} to the {@link Session} using the {@code ackId} in order to being
   * able later to perform a {@link AckMode#MANUAL} ACK
   *
   * @param ackId   the id associated to the {@link Session} used to create the {@link Message}
   * @param message the {@link Message} to use for executing the {@link Message#acknowledge}
   * @throws IllegalArgumentException if no Session was registered with the given AckId
   */
  public void registerMessageForAck(String ackId, Message message) {
    pendingAckSessions.put(ackId, message);

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(format("Registered Message for Session AckId [%s]", ackId));
    }
  }

  /**
   * Executes the {@link Message#acknowledge} on the latest {@link Message} associated to the {@link Session}
   * identified by the {@code ackId}
   *
   * @param ackId the id associated to the {@link Session} that should be ACKed
   * @throws JMSException if an error occurs during the ack
   */
  public void doAck(String ackId) throws JMSException {

    Message message = pendingAckSessions.get(ackId);
    if (message == null) {
      throw new JmsAckException(format("No pending acknowledgement with ackId [%s] exists in this Connection", ackId));
    }

    message.acknowledge();
  }

  @Override
  public void dispose() {

  }
}
