/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.enhanced.dynamodb.internal.mapper;

import java.util.Arrays;

import software.amazon.awssdk.annotations.SdkInternalApi;
import software.amazon.awssdk.enhanced.dynamodb.mapper.AttributeTag;
import software.amazon.awssdk.enhanced.dynamodb.mapper.AttributeTags;
import software.amazon.awssdk.enhanced.dynamodb.mapper.BeanTableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.BeanTableSchemaAttributeTag;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * Static provider class for core {@link BeanTableSchema} attribute tags. Each of the implemented annotations has a
 * corresponding reference to this class in a
 * {@link BeanTableSchemaAttributeTag}
 * meta-annotation.
 */
@SdkInternalApi
public final class BeanTableSchemaAttributeTags {
    private BeanTableSchemaAttributeTags() {
    }

    public static AttributeTag attributeTagFor(DynamoDbPartitionKey annotation) {
        return AttributeTags.primaryPartitionKey();
    }

    public static AttributeTag attributeTagFor(DynamoDbSortKey annotation) {
        return AttributeTags.primarySortKey();
    }

    public static AttributeTag attributeTagFor(DynamoDbSecondaryPartitionKey annotation) {
        return AttributeTags.secondaryPartitionKey(Arrays.asList(annotation.indexNames()));
    }

    public static AttributeTag attributeTagFor(DynamoDbSecondarySortKey annotation) {
        return AttributeTags.secondarySortKey(Arrays.asList(annotation.indexNames()));
    }
}
