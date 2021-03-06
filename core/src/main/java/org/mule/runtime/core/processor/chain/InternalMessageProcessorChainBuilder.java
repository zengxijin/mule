/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.processor.chain;

import org.mule.runtime.core.api.processor.MessageProcessorChain;
import org.mule.runtime.core.api.processor.MessageProcessorPathElement;
import org.mule.runtime.core.api.processor.Processor;
import org.mule.runtime.core.util.NotificationUtils;

import java.util.List;

/**
 * Constructs a custom chain for subflows using the subflow name as the chain name.
 */
public class InternalMessageProcessorChainBuilder extends DefaultMessageProcessorChainBuilder {

  protected MessageProcessorChain createInterceptingChain(Processor head, List<Processor> processors,
                                                          List<Processor> processorForLifecycle) {
    return new InternalMessageProcessorChain(name, head, processors, processorForLifecycle);
  }

  /**
   * Generates message processor identfiers specific for subflows.
   */
  public static class InternalMessageProcessorChain extends DefaultMessageProcessorChain {

    protected InternalMessageProcessorChain(String name, Processor head, List<Processor> processors,
                                            List<Processor> processorsForLifecycle) {
      super(name, head, processors, processorsForLifecycle);
    }

    @Override
    public void addMessageProcessorPathElements(MessageProcessorPathElement pathElement) {
      NotificationUtils.addMessageProcessorPathElements(processors, pathElement);
    }

  }
}
