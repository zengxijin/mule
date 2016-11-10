/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.http.api.policy;

import org.mule.extension.http.api.HttpResponseAttributes;
import org.mule.extension.http.api.listener.builder.HttpListenerResponseBuilder;
import org.mule.extension.http.api.listener.builder.HttpListenerSuccessResponseBuilder;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.core.api.policy.PolicyOperationParametersTransformer;
import org.mule.runtime.core.model.ParameterMap;
import org.mule.runtime.dsl.api.component.ComponentIdentifier;

import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;

public class HttpPolicyListenerParametersTransformer implements PolicyOperationParametersTransformer {

  @Override
  public boolean supports(ComponentIdentifier componentIdentifier) {
    //TODO add support for namespace
    return componentIdentifier.getName().equals("listener");
  }

  @Override
  public Message fromParametersToMessage(Map<String, Object> parameters) {
    HttpListenerResponseBuilder responseBuilder = (HttpListenerResponseBuilder) parameters.get("responseBuilder");
    ParameterMap headers = new ParameterMap();
    //TODO fix usage of ParameterMap since this for each is necessary because it does not adhere to the Map interface.
    for (String headerName : responseBuilder.getHeaders().keySet())
    {
      headers.put(headerName, responseBuilder.getHeaders().get(headerName));
    }
    return Message.builder().payload(responseBuilder.getBody())
        .attributes(new HttpResponseAttributes(responseBuilder.getStatusCode(), responseBuilder.getReasonPhrase(), headers))
        .build();
  }

  //TODO this method is not required for sources, we need two types of PolicyOperationParametersTransformer
  @Override
  public Map<String, Object> fromMessageToParameters(Message message) {
    HttpResponseAttributes httpResponseAttributes = (HttpResponseAttributes) message.getAttributes();
    HttpListenerSuccessResponseBuilder httpListenerSuccessResponseBuilder = new HttpListenerSuccessResponseBuilder();
    httpListenerSuccessResponseBuilder.setBody(message.getPayload().getValue());
    //TODO change this code to work with collection
    for (String headerName : httpResponseAttributes.getHeaders().keySet())
    {
      httpListenerSuccessResponseBuilder.getHeaders().put(headerName, httpResponseAttributes.getHeaders().get(headerName));
    }

    //TODO see media type
    httpListenerSuccessResponseBuilder.setStatusCode(httpResponseAttributes.getStatusCode());
    httpListenerSuccessResponseBuilder.setReasonPhrase(httpResponseAttributes.getReasonPhrase());
    return ImmutableMap.<String, Object>builder().put("responseBuilder", httpListenerSuccessResponseBuilder).build();
  }
}
