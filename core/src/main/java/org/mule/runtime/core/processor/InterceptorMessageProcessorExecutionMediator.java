/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.core.processor;

import static java.lang.String.valueOf;
import static org.mule.runtime.api.dsl.config.ComponentConfiguration.ANNOTATION_PARAMETERS;
import static org.mule.runtime.api.dsl.config.ComponentIdentifier.ANNOTATION_NAME;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.core.api.rx.Exceptions.checkedConsumer;
import static org.mule.runtime.core.api.rx.Exceptions.checkedFunction;
import static org.mule.runtime.core.internal.util.rx.Operators.nullSafeMap;
import static reactor.core.publisher.Flux.from;
import static reactor.core.publisher.Flux.just;
import org.mule.runtime.api.dsl.config.ComponentIdentifier;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.meta.AnnotatedObject;
import org.mule.runtime.core.api.Event;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.construct.FlowConstruct;
import org.mule.runtime.core.api.construct.FlowConstructAware;
import org.mule.runtime.core.api.context.MuleContextAware;
import org.mule.runtime.core.api.interception.InterceptableMessageProcessor;
import org.mule.runtime.core.api.interception.MessageProcessorInterceptorCallback;
import org.mule.runtime.core.api.interception.MessageProcessorInterceptorManager;
import org.mule.runtime.core.api.interception.ProcessorParameterResolver;
import org.mule.runtime.core.api.message.InternalMessage;
import org.mule.runtime.core.api.processor.Processor;
import org.mule.runtime.core.exception.MessagingException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Execution mediator for {@link Processor} that intercepts the processor execution with an {@link org.mule.runtime.core.api.interception.MessageProcessorInterceptorCallback interceptor callback}.
 *
 * @since 4.0
 */
public class InterceptorMessageProcessorExecutionMediator implements MessageProcessorExecutionMediator, MuleContextAware,
    FlowConstructAware {

  private transient Logger logger = LoggerFactory.getLogger(InterceptorMessageProcessorExecutionMediator.class);

  private MuleContext muleContext;
  private FlowConstruct flowConstruct;

  @Override
  public void setMuleContext(MuleContext muleContext) {
    this.muleContext = muleContext;
  }

  @Override
  public void setFlowConstruct(FlowConstruct flowConstruct) {
    this.flowConstruct = flowConstruct;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Publisher<Event> apply(Publisher<Event> publisher, Processor processor) {
    if (shouldIntercept(processor)) {
      logger.debug("Applying interceptor for Processor: '{}'", processor.getClass());

      AnnotatedObject annotatedObject = (AnnotatedObject) processor;
      MessageProcessorInterceptorManager interceptorManager = muleContext.getMessageProcessorInterceptorManager();
      MessageProcessorInterceptorCallback interceptorCallback = interceptorManager.retrieveInterceptorCallback();

      Map<String, String> componentParameters = (Map<String, String>) annotatedObject.getAnnotation(ANNOTATION_PARAMETERS);

      //TODO resolve parameters! (delegate to each processor)
      return intercept(publisher, interceptorCallback, componentParameters, processor);
    }

    return processor.apply(publisher);
  }

  private Boolean shouldIntercept(Processor processor) {
    if (processor instanceof AnnotatedObject) {
      AnnotatedObject annotatedObject = (AnnotatedObject) processor;
      ComponentIdentifier componentIdentifier = (ComponentIdentifier) annotatedObject.getAnnotation(ANNOTATION_NAME);
      if (componentIdentifier != null) {
        MessageProcessorInterceptorManager interceptorManager = muleContext.getMessageProcessorInterceptorManager();
        MessageProcessorInterceptorCallback interceptorCallback = interceptorManager.retrieveInterceptorCallback();
        if (null != interceptorCallback) {
          return true;
        } else {
          logger.debug("Processor '{}' does not have a Interceptor Callback", processor.getClass());
        }
      } else {
        logger.warn("Processor '{}' is an '{}' but doesn't have a componentIdentifier", processor.getClass(),
                    AnnotatedObject.class);

      }
    } else {
      logger.debug("Processor '{}' is not an '{}'", processor.getClass(), AnnotatedObject.class);
    }
    return false;
  }


  /**
   * {@inheritDoc}
   */
  private Publisher<Event> intercept(Publisher<Event> publisher, MessageProcessorInterceptorCallback interceptorCallback,
                                     Map<String, String> parameters, Processor processor) {
    AtomicReference<Map<String, Object>> parametersHolder = new AtomicReference<>();
    return from(publisher)
        .concatMap(request -> just(request)
            .map(checkedFunction(event -> {
              parametersHolder.set(resolveParameters(event, processor, parameters));
              return Event.builder(event)
                  .message(InternalMessage
                               .builder(interceptorCallback.before(event.getMessage(), parametersHolder.get()))
                               .build())
                  .build();}))
            .transform(s -> doTransform(s, interceptorCallback, parameters, processor))
            .map(checkedFunction(result -> Event.builder(result).message(InternalMessage
                                                                             .builder(interceptorCallback.after(result.getMessage(), parametersHolder.get(), null))
                                                                             .build()).build()))
            .doOnError(MessagingException.class,
                       checkedConsumer(exception -> interceptorCallback.after(exception.getEvent().getMessage(),
                                                                              parametersHolder.get(),
                                                                              exception))));
  }

  protected Publisher<Event> doTransform(Publisher<Event> publisher, MessageProcessorInterceptorCallback interceptorCallback,
                                         Map<String, String> parameters, Processor processor) {
    return from(publisher).concatMap(checkedFunction(event -> {
      if (interceptorCallback.shouldExecuteProcessor(event.getMessage(), resolveParameters(event, processor, parameters))) {
        return processor.apply(publisher);
      } else {
        Publisher<Event> next = from(publisher).handle(nullSafeMap(checkedFunction(request -> Event.builder(event)
            .message(InternalMessage
                .builder(interceptorCallback
                    .getResult(request.getMessage(), resolveParameters(event, processor, parameters)))
                .build())
            .build())));
        if (processor instanceof InterceptableMessageProcessor) {
          try {
            InterceptableMessageProcessor interceptableMessageProcessor = (InterceptableMessageProcessor) processor;
            Processor nextProcessor = interceptableMessageProcessor.getNext();
            if (nextProcessor != null) {
              next = nextProcessor.apply(next);
            }
          } catch (Exception e) {
            throw new MuleRuntimeException(createStaticMessage("Error while getting next processor from interceptor"),
                                           e);
          }
        }
        return next;
      }
    }));
  }

  private Map<String, Object> resolveParameters(Event event, Processor processor, Map<String, String> parameters)
      throws MuleException {

    if (processor instanceof ProcessorParameterResolver) {
      return ((ProcessorParameterResolver) processor).resolve(event);
    }

    Map<String, Object> resolvedParameters = new HashMap<>();
    for (Map.Entry<String, String> entry : parameters.entrySet()) {
      Object value;
      String paramValue = entry.getValue();
      if (muleContext.getExpressionManager().isExpression(paramValue)) {
        value = muleContext.getExpressionManager().evaluate(paramValue, event, flowConstruct).getValue();
      } else {
        value = valueOf(paramValue);
      }
      resolvedParameters.put(entry.getKey(), value);
    }
    return resolvedParameters;
  }

}
