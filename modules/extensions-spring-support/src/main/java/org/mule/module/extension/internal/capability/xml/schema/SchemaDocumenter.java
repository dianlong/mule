/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.module.extension.internal.capability.xml.schema;

import static org.mule.module.extension.internal.capability.xml.schema.AnnotationProcessorUtils.getFieldsAnnotatedWith;
import static org.mule.module.extension.internal.capability.xml.schema.AnnotationProcessorUtils.getJavaDocSummary;
import static org.mule.module.extension.internal.capability.xml.schema.AnnotationProcessorUtils.getMethodDocumentation;
import static org.mule.module.extension.internal.capability.xml.schema.AnnotationProcessorUtils.getOperationMethods;
import static org.mule.module.extension.internal.capability.xml.schema.AnnotationProcessorUtils.getTypeElementsAnnotatedWith;
import org.mule.api.MuleRuntimeException;
import org.mule.config.i18n.MessageFactory;
import org.mule.extension.annotations.Configuration;
import org.mule.extension.annotations.Parameter;
import org.mule.extension.annotations.ParameterGroup;
import org.mule.extension.introspection.Extension;
import org.mule.extension.introspection.declaration.fluent.ConfigurationDeclaration;
import org.mule.extension.introspection.declaration.fluent.Declaration;
import org.mule.extension.introspection.declaration.fluent.OperationDeclaration;
import org.mule.extension.introspection.declaration.fluent.ParameterDeclaration;
import org.mule.module.extension.internal.util.IntrospectionUtils;
import org.mule.util.CollectionUtils;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import org.apache.commons.collections.Predicate;

/**
 * Utility class that picks a {@link Declaration}
 * on which a {@link Extension} has already been described
 * and enriches such description with the javadocs extracted from the extension's acting classes.
 * <p/>
 * This is necessary because such documentation is not available on runtime, thus this class
 * uses the annotation processor's AST access to extract it
 *
 * @since 3.7.0
 */
final class SchemaDocumenter
{

    private ProcessingEnvironment processingEnv;

    SchemaDocumenter(ProcessingEnvironment processingEnv)
    {
        this.processingEnv = processingEnv;
    }

    void document(Declaration declaration, TypeElement extensionElement, RoundEnvironment roundEnvironment)
    {
        declaration.setDescription(getJavaDocSummary(processingEnv, extensionElement));
        documentConfigurations(declaration, extensionElement, roundEnvironment);
        documentOperations(declaration, roundEnvironment);
    }



    private void documentOperations(Declaration declaration, RoundEnvironment roundEnvironment)
    {
        final Map<String, ExecutableElement> methods = getOperationMethods(roundEnvironment);

        try
        {
            for (OperationDeclaration operation : declaration.getOperations())
            {
                ExecutableElement method = methods.get(operation.getName());

                if (method == null)
                {
                    continue;
                }

                MethodDocumentation documentation = getMethodDocumentation(processingEnv, method);
                operation.setDescription(documentation.getSummary());
                documentOperationParameters(operation, documentation);
            }
        }
        catch (Exception e)
        {
            throw new MuleRuntimeException(MessageFactory.createStaticMessage("Exception found while trying to document XSD schema"), e);
        }
    }

    private void documentOperationParameters(OperationDeclaration operation, MethodDocumentation documentation)
    {
        for (ParameterDeclaration parameter : operation.getParameters())
        {
            String description = documentation.getParameters().get(parameter.getName());
            if (description != null)
            {
                parameter.setDescription(description);
            }
        }
    }

    private void documentConfigurations(Declaration declaration, TypeElement extensionElement, RoundEnvironment roundEnvironment)
    {
        if (declaration.getConfigurations().size() > 1)
        {
            for (TypeElement configurationElement : getTypeElementsAnnotatedWith(Configuration.class, roundEnvironment))
            {
                ConfigurationDeclaration configurationDeclaration = findMatchingConfiguration(declaration, configurationElement);
                documentConfigurationParameters(configurationDeclaration.getParameters(), configurationElement);
            }
        }
        else
        {
            documentConfigurationParameters(declaration.getConfigurations().get(0).getParameters(), extensionElement);
        }
    }

    private void documentConfigurationParameters(Collection<ParameterDeclaration> parameters, final TypeElement element)
    {
        final Map<String, VariableElement> variableElements = getFieldsAnnotatedWith(element, Parameter.class);
        TypeElement traversingElement = element;
        while (traversingElement != null && !Object.class.getName().equals(traversingElement.getQualifiedName().toString()))
        {
            Class<?> declaringClass = AnnotationProcessorUtils.classFor(traversingElement, processingEnv);
            for (ParameterDeclaration parameter : parameters)
            {
                Field field = IntrospectionUtils.getField(declaringClass, parameter);
                if (field != null && variableElements.containsKey(field.getName()))
                {
                    parameter.setDescription(getJavaDocSummary(processingEnv, variableElements.get(field.getName())));
                }
            }

            traversingElement = (TypeElement) processingEnv.getTypeUtils().asElement(traversingElement.getSuperclass());
        }

        for (VariableElement variableElement : getFieldsAnnotatedWith(element, ParameterGroup.class).values())
        {
            TypeElement typeElement = (TypeElement) processingEnv.getTypeUtils().asElement(variableElement.asType());
            documentConfigurationParameters(parameters, typeElement);
        }
    }

    private ConfigurationDeclaration findMatchingConfiguration(Declaration declaration, final TypeElement configurationElement) {
        return (ConfigurationDeclaration) CollectionUtils.find(declaration.getConfigurations(), new Predicate()
        {
            @Override
            public boolean evaluate(Object object)
            {
                Configuration configuration = configurationElement.getAnnotation(Configuration.class);
                ConfigurationDeclaration configurationDeclaration = (ConfigurationDeclaration) object;
                return configurationDeclaration.getName().equals(configuration.name());
            }
        });
    }
}
