/**
 * Copyright 2018 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.smallrye.openapi.runtime.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.microprofile.openapi.models.Components;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.Paths;
import org.eclipse.microprofile.openapi.models.media.Content;
import org.eclipse.microprofile.openapi.models.media.MediaType;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.eclipse.microprofile.openapi.models.parameters.Parameter;
import org.eclipse.microprofile.openapi.models.parameters.RequestBody;
import org.eclipse.microprofile.openapi.models.responses.APIResponses;
import org.eclipse.microprofile.openapi.models.tags.Tag;

import io.smallrye.openapi.api.OpenApiConstants;
import io.smallrye.openapi.api.models.ComponentsImpl;
import io.smallrye.openapi.api.models.OpenAPIImpl;
import io.smallrye.openapi.api.models.PathsImpl;
import io.smallrye.openapi.api.models.media.ContentImpl;
import io.smallrye.openapi.api.models.media.MediaTypeImpl;
import io.smallrye.openapi.api.models.responses.APIResponsesImpl;
import io.smallrye.openapi.api.util.MergeUtil;

/**
 * Class with some convenience methods useful for working with the OAI data model.
 * 
 * @author eric.wittmann@gmail.com
 */
public class ModelUtil {

    /**
     * Constructor.
     */
    private ModelUtil() {
    }

    /**
     * Adds a {@link Tag} to the {@link OpenAPI} model. If a tag having the same
     * name already exists in the model, the tags' attributes are merged, with the
     * new tag's attributes overriding the value of any attributes specified on
     * both.
     * 
     * @param openApi the OpenAPI model
     * @param tag a new {@link Tag} to add
     */
    public static void addTag(OpenAPI openApi, Tag tag) {
        List<Tag> tags = openApi.getTags();

        if (tags == null || tags.isEmpty()) {
            openApi.addTag(tag);
            return;
        }

        Tag current = tags.stream().filter(t -> t.getName().equals(tag.getName())).findFirst().orElse(null);
        int currentIndex = tags.indexOf(current);

        if (current != null) {
            Tag replacement = MergeUtil.mergeObjects(current, tag);
            tags.set(currentIndex, replacement);
        } else {
            openApi.addTag(tag);
        }
    }

    /**
     * Gets the {@link Components} from the OAI model. If it doesn't exist, creates it.
     * 
     * @param openApi OpenAPI
     * @return Components
     */
    public static Components components(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new ComponentsImpl());
        }
        return openApi.getComponents();
    }

    /**
     * Gets the {@link Paths} from the OAI model. If it doesn't exist, creates it.
     * 
     * @param openApi OpenAPI
     * @return Paths
     */
    public static Paths paths(OpenAPIImpl openApi) {
        if (openApi.getPaths() == null) {
            openApi.setPaths(new PathsImpl());
        }
        return openApi.getPaths();
    }

    /**
     * Gets the {@link APIResponses} child model from the given operation. If it's null
     * then it will be created and returned.
     * 
     * @param operation Operation
     * @return APIResponses
     */
    public static APIResponses responses(Operation operation) {
        if (operation.getResponses() == null) {
            operation.setResponses(new APIResponsesImpl());
        }
        return operation.getResponses();
    }

    /**
     * Returns true only if the given {@link Parameter} has a schema defined
     * for it. A schema can be defined either via the parameter's "schema"
     * property, or any "content.*.schema" property.
     * 
     * @param parameter Parameter
     * @return Whether the parameter has a schema
     */
    public static boolean parameterHasSchema(Parameter parameter) {
        if (parameter.getSchema() != null) {
            return true;
        }
        if (parameter.getContent() != null && !parameter.getContent().isEmpty()) {
            Collection<MediaType> mediaTypes = parameter.getContent().values();
            for (MediaType mediaType : mediaTypes) {
                if (mediaType.getSchema() != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the list of {@link Schema}s defined for the given {@link Parameter}.
     * A schema can be defined either via the parameter's "schema" property, or any
     * "content.*.schema" property.
     *
     * @param parameter Parameter
     * @return list of schemas, never null
     */
    public static List<Schema> getParameterSchemas(Parameter parameter) {
        if (parameter.getSchema() != null) {
            return Arrays.asList(parameter.getSchema());
        }
        if (parameter.getContent() != null && !parameter.getContent().isEmpty()) {
            Collection<MediaType> mediaTypes = parameter.getContent().values();
            List<Schema> schemas = new ArrayList<>(mediaTypes.size());

            for (MediaType mediaType : mediaTypes) {
                if (mediaType.getSchema() != null) {
                    schemas.add(mediaType.getSchema());
                }
            }
        }
        return Collections.emptyList();
    }

    /**
     * Sets the given {@link Schema} on the given {@link Parameter}. This is tricky
     * because the paramater may EITHER have a schema property or it may have a
     * {@link Content} child which itself has zero or more {@link MediaType} children
     * which will contain the {@link Schema}.
     *
     * The OpenAPI specification requires that a parameter have *either* a schema
     * or a content, but not both.
     * 
     * @param parameter Parameter
     * @param schema Schema
     */
    public static void setParameterSchema(Parameter parameter, Schema schema) {
        if (schema == null) {
            return;
        }
        if (parameter.getContent() == null) {
            parameter.schema(schema);
            return;
        }
        Content content = parameter.getContent();
        if (content.isEmpty()) {
            String[] defMediaTypes = OpenApiConstants.DEFAULT_MEDIA_TYPES.get();
            for (String mediaTypeName : defMediaTypes) {
                MediaType mediaType = new MediaTypeImpl();
                mediaType.setSchema(schema);
                content.addMediaType(mediaTypeName, mediaType);
            }
            return;
        }
        for (String mediaTypeName : content.keySet()) {
            MediaType mediaType = content.getMediaType(mediaTypeName);
            mediaType.setSchema(schema);
        }
    }

    /**
     * Returns true only if the given {@link RequestBody} has a schema defined
     * for it. A schema would be found within the request body's Content/MediaType
     * children.
     * 
     * @param requestBody RequestBody
     * @return Whether RequestBody has a schema
     */
    public static boolean requestBodyHasSchema(RequestBody requestBody) {
        if (requestBody.getContent() != null && !requestBody.getContent().isEmpty()) {
            Collection<MediaType> mediaTypes = requestBody.getContent().values();
            for (MediaType mediaType : mediaTypes) {
                if (mediaType.getSchema() != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Sets the given {@link Schema} on the given {@link RequestBody}.
     * 
     * @param requestBody RequestBody
     * @param schema Schema
     * @param mediaTypes String array
     */
    public static void setRequestBodySchema(RequestBody requestBody, Schema schema, String[] mediaTypes) {
        Content content = requestBody.getContent();
        if (content == null) {
            content = new ContentImpl();
            requestBody.setContent(content);
        }
        if (content.isEmpty()) {
            String[] requestBodyTypes;
            if (mediaTypes != null && mediaTypes.length > 0) {
                requestBodyTypes = mediaTypes;
            } else {
                requestBodyTypes = OpenApiConstants.DEFAULT_MEDIA_TYPES.get();
            }
            for (String mediaTypeName : requestBodyTypes) {
                MediaType mediaType = new MediaTypeImpl();
                mediaType.setSchema(schema);
                content.addMediaType(mediaTypeName, mediaType);
            }
            return;
        }
        for (String mediaTypeName : content.keySet()) {
            MediaType mediaType = content.getMediaType(mediaTypeName);
            mediaType.setSchema(schema);
        }
    }

    /**
     * Returns true if the given operation has a parameter with the given name.
     * 
     * @param operation Operation
     * @param name String
     * @return Whether the operation has a parameter
     */
    public static boolean operationHasParameter(Operation operation, String name) {
        List<Parameter> parameters = operation.getParameters();
        if (parameters == null) {
            return false;
        }
        for (Parameter parameter : parameters) {
            if (parameter.getName() != null && parameter.getName().equals(name)) {
                return true;
            }
            if (parameter.getRef() != null && ModelUtil.nameFromRef(parameter.getRef()).equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the name component of the ref.
     * 
     * @param ref String
     * @return Name
     */
    public static String nameFromRef(String ref) {
        String[] split = ref.split("/");
        return split[split.length - 1];
    }
}
