/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.metadata;

import org.mule.runtime.api.meta.model.ComponentModel;
import org.mule.runtime.api.metadata.MetadataAttributes;
import org.mule.runtime.api.metadata.descriptor.ComponentMetadataDescriptor;
import org.mule.runtime.api.metadata.descriptor.InputMetadataDescriptor;
import org.mule.runtime.api.metadata.descriptor.OutputMetadataDescriptor;

@FunctionalInterface
public interface TypeConverterDelegate<T extends ComponentModel> {

  ComponentMetadataDescriptor<T> getMetadataDescriptor(T component,
                                                       InputMetadataDescriptor inputMetadataDescriptor,
                                                       OutputMetadataDescriptor outputMetadataDescriptor,
                                                       MetadataAttributes attributes);
}
