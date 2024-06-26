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

package software.amazon.awssdk.awscore.client.handler;

import static software.amazon.awssdk.utils.CollectionUtils.firstIfPresent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import software.amazon.awssdk.annotations.SdkProtectedApi;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.utils.IoUtils;
import software.amazon.eventstream.HeaderValue;
import software.amazon.eventstream.Message;

@SdkProtectedApi
public final class AwsClientHandlerUtils {

    private AwsClientHandlerUtils() {

    }

    /**
     * Encodes the request into a flow message and then returns bytebuffer from the message.
     *
     * @param request The request to encode
     * @return A bytebuffer representing the given request
     */
    public static ByteBuffer encodeEventStreamRequestToByteBuffer(SdkHttpFullRequest request) {
        Map<String, HeaderValue> headers = new LinkedHashMap<>();
        request.forEachHeader((name, value) -> headers.put(name, HeaderValue.fromString(firstIfPresent(value))));

        byte[] payload;
        if (request.contentStreamProvider().isPresent()) {
            try {
                payload = IoUtils.toByteArray(request.contentStreamProvider().get().newStream());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            payload = new byte[0];
        }

        return new Message(headers, payload).toByteBuffer();
    }

}
