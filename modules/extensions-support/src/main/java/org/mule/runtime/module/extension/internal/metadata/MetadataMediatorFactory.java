/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.metadata;

import static org.mule.runtime.api.metadata.descriptor.builder.MetadataDescriptorBuilder.operationMetadataDescriptor;
import static org.mule.runtime.api.metadata.descriptor.builder.MetadataDescriptorBuilder.sourceMetadataDescriptor;
import org.mule.runtime.api.meta.model.OutputModel;
import org.mule.runtime.api.meta.model.operation.OperationModel;
import org.mule.runtime.api.meta.model.parameter.ParameterGroupModel;
import org.mule.runtime.api.meta.model.parameter.ParameterModel;
import org.mule.runtime.api.meta.model.source.SourceCallbackModel;
import org.mule.runtime.api.meta.model.source.SourceModel;
import org.mule.runtime.api.metadata.descriptor.ParameterMetadataDescriptor;
import org.mule.runtime.api.metadata.descriptor.TypeMetadataDescriptor;
import org.mule.runtime.extension.api.model.ImmutableOutputModel;
import org.mule.runtime.extension.api.model.operation.ImmutableOperationModel;
import org.mule.runtime.extension.api.model.parameter.ImmutableParameterGroupModel;
import org.mule.runtime.extension.api.model.parameter.ImmutableParameterModel;
import org.mule.runtime.extension.api.model.source.ImmutableSourceCallbackModel;
import org.mule.runtime.extension.api.model.source.ImmutableSourceModel;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @since 4.0
 */
public class MetadataMediatorFactory {

  public static MetadataMediator<OperationModel> getOperationMetadataMediator(OperationModel model) {
    return new MetadataMediator<>(model, (component, inputMetadataDescriptor, outputMetadataDescriptor, attributes) -> {
      OutputModel typedOutputModel = typeOutputModel(component.getOutput(), outputMetadataDescriptor.getPayloadMetadata());
      OutputModel typedAttributesModel =
          typeOutputModel(component.getOutputAttributes(), outputMetadataDescriptor.getAttributesMetadata());

      OperationModel typedModel = new ImmutableOperationModel(component.getName(), component.getDescription(),
                                                              typeParameterGroupModel(component.getParameterGroupModels(),
                                                                                      inputMetadataDescriptor
                                                                                          .getAllParameters()),
                                                              typedOutputModel, typedAttributesModel, component.isBlocking(),
                                                              component.getExecutionType(), component.requiresConnection(),
                                                              component.isTransactional(),
                                                              component.getDisplayModel().orElse(null),
                                                              component.getErrorModels(),
                                                              component.getModelProperties());

      return operationMetadataDescriptor(typedModel).withAttributes(attributes).build();
    });
  }

  public static MetadataMediator<SourceModel> getSourceMetadataMediator(SourceModel model) {
    return new MetadataMediator<>(model, (component, inputMetadataDescriptor, outputMetadataDescriptor, attributes) -> {
      OutputModel typedOutputModel = typeOutputModel(component.getOutput(), outputMetadataDescriptor.getPayloadMetadata());
      OutputModel typedAttributesModel =
          typeOutputModel(component.getOutputAttributes(), outputMetadataDescriptor.getAttributesMetadata());

      SourceModel typedModel = new ImmutableSourceModel(component.getName(), component.getDescription(), component.hasResponse(),
                                                        typeParameterGroupModel(component.getParameterGroupModels(),
                                                                                inputMetadataDescriptor.getAllParameters()),
                                                        typedOutputModel, typedAttributesModel,
                                                        typeSourceCallback(component.getSuccessCallback(),
                                                                           inputMetadataDescriptor.getAllParameters()),
                                                        typeSourceCallback(component.getErrorCallback(),
                                                                           inputMetadataDescriptor.getAllParameters()),
                                                        component.requiresConnection(),
                                                        component.isTransactional(),
                                                        component.getDisplayModel().orElse(null),
                                                        component.getModelProperties());

      return sourceMetadataDescriptor(typedModel).withAttributes(attributes).build();
    });
  }


  private static OutputModel typeOutputModel(OutputModel untypedModel, TypeMetadataDescriptor typeMetadataDescriptor) {
    return new ImmutableOutputModel(untypedModel.getDescription(), typeMetadataDescriptor.getType(),
                                    typeMetadataDescriptor.isDynamic(), untypedModel.getModelProperties());
  }

  private static List<ParameterGroupModel> typeParameterGroupModel(List<ParameterGroupModel> untypedParameterGroups,
                                                                   Map<String, ParameterMetadataDescriptor> inputTypeDescriptors) {
    List<ParameterGroupModel> parameterGroups = new LinkedList<>();
    untypedParameterGroups.stream().forEach(parameterGroup -> {
      List<ParameterModel> parameters = new LinkedList<>();
      parameterGroup.getParameterModels().forEach(parameterModel -> {
        ParameterMetadataDescriptor parameterMetadataDescriptor = inputTypeDescriptors.get(parameterModel.getName());
        ParameterModel typedParameterModel =
            new ImmutableParameterModel(parameterModel.getName(), parameterModel.getDescription(),
                                        parameterMetadataDescriptor.getType(),
                                        parameterMetadataDescriptor.isDynamic(), parameterModel.isRequired(),
                                        parameterModel.getExpressionSupport(),
                                        parameterModel.getDefaultValue(), parameterModel.getRole(),
                                        parameterModel.getDslConfiguration(), parameterModel.getDisplayModel().orElse(null),
                                        parameterModel.getLayoutModel().orElse(null), parameterModel.getModelProperties());
        parameters.add(typedParameterModel);
      });

      parameterGroups
          .add(new ImmutableParameterGroupModel(parameterGroup.getName(), parameterGroup.getDescription(), parameters,
                                                parameterGroup.getExclusiveParametersModels(),
                                                parameterGroup.isShowInDsl(), parameterGroup.getDisplayModel().orElse(null),
                                                parameterGroup.getLayoutModel().orElse(null),
                                                parameterGroup.getModelProperties()));
    });
    return parameterGroups;
  }

  private static Optional<SourceCallbackModel> typeSourceCallback(Optional<SourceCallbackModel> sourceCallbackModel,
                                                                  Map<String, ParameterMetadataDescriptor> inputTypeDescriptors) {
    return sourceCallbackModel.map(cb -> new ImmutableSourceCallbackModel(cb.getName(), cb.getDescription(),
                                                                          typeParameterGroupModel(cb.getParameterGroupModels(),
                                                                                                  inputTypeDescriptors),
                                                                          cb.getDisplayModel().orElse(null),
                                                                          cb.getModelProperties()));
  }
}
