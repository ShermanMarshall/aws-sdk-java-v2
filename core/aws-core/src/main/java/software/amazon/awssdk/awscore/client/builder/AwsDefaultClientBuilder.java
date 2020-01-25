/*
 * Copyright 2010-2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package software.amazon.awssdk.awscore.client.builder;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import software.amazon.awssdk.annotations.SdkProtectedApi;
import software.amazon.awssdk.annotations.SdkTestInternalApi;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.awscore.client.config.AwsAdvancedClientOption;
import software.amazon.awssdk.awscore.client.config.AwsClientOption;
import software.amazon.awssdk.awscore.interceptor.HelpfulUnknownHostExceptionInterceptor;
import software.amazon.awssdk.awscore.internal.EndpointUtils;
import software.amazon.awssdk.awscore.retry.AwsRetryPolicy;
import software.amazon.awssdk.core.client.builder.SdkDefaultClientBuilder;
import software.amazon.awssdk.core.client.config.SdkAdvancedClientOption;
import software.amazon.awssdk.core.client.config.SdkClientConfiguration;
import software.amazon.awssdk.core.client.config.SdkClientOption;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.ServiceMetadata;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.regions.providers.LazyAwsRegionProvider;
import software.amazon.awssdk.utils.AttributeMap;
import software.amazon.awssdk.utils.CollectionUtils;

/**
 * An SDK-internal implementation of the methods in {@link AwsClientBuilder}, {@link AwsAsyncClientBuilder} and
 * {@link AwsSyncClientBuilder}. This implements all methods required by those interfaces, allowing service-specific builders to
 * just
 * implement the configuration they wish to add.
 *
 * <p>By implementing both the sync and async interface's methods, service-specific builders can share code between their sync
 * and
 * async variants without needing one to extend the other. Note: This only defines the methods in the sync and async builder
 * interfaces. It does not implement the interfaces themselves. This is because the sync and async client builder interfaces both
 * require a type-constrained parameter for use in fluent chaining, and a generic type parameter conflict is introduced into the
 * class hierarchy by this interface extending the builder interfaces themselves.</p>
 *
 * <p>Like all {@link AwsClientBuilder}s, this class is not thread safe.</p>
 *
 * @param <BuilderT> The type of builder, for chaining.
 * @param <ClientT> The type of client generated by this builder.
 */
@SdkProtectedApi
public abstract class AwsDefaultClientBuilder<BuilderT extends AwsClientBuilder<BuilderT, ClientT>, ClientT>
    extends SdkDefaultClientBuilder<BuilderT, ClientT>
    implements AwsClientBuilder<BuilderT, ClientT> {
    private static final String DEFAULT_ENDPOINT_PROTOCOL = "https";
    private static final AwsRegionProvider DEFAULT_REGION_PROVIDER =
            new LazyAwsRegionProvider(DefaultAwsRegionProviderChain::new);

    protected AwsDefaultClientBuilder() {
        super();
    }

    @SdkTestInternalApi
    AwsDefaultClientBuilder(SdkHttpClient.Builder defaultHttpClientBuilder,
                            SdkAsyncHttpClient.Builder defaultAsyncHttpClientFactory) {
        super(defaultHttpClientBuilder, defaultAsyncHttpClientFactory);
    }

    /**
     * Implemented by child classes to define the endpoint prefix used when communicating with AWS. This constitutes the first
     * part of the URL in the DNS name for the service. Eg. in the endpoint "dynamodb.amazonaws.com", this is the "dynamodb".
     *
     * <p>For standard services, this should match the "endpointPrefix" field in the AWS model.</p>
     */
    protected abstract String serviceEndpointPrefix();

    /**
     * Implemented by child classes to define the signing-name that should be used when signing requests when communicating with
     * AWS.
     */
    protected abstract String signingName();

    /**
     * Implemented by child classes to define the service name used to identify the request in things like metrics.
     */
    protected abstract String serviceName();

    @Override
    protected final AttributeMap childHttpConfig() {
        return serviceHttpConfig();
    }

    /**
     * Optionally overridden by child classes to define service-specific HTTP configuration defaults.
     */
    protected AttributeMap serviceHttpConfig() {
        return AttributeMap.empty();
    }

    @Override
    protected final SdkClientConfiguration mergeChildDefaults(SdkClientConfiguration configuration) {
        SdkClientConfiguration config = mergeServiceDefaults(configuration);
        return config.merge(c -> c.option(AwsClientOption.AWS_REGION, resolveRegion(config))
                                  .option(AwsAdvancedClientOption.ENABLE_DEFAULT_REGION_DETECTION, true)
                                  .option(AwsClientOption.CREDENTIALS_PROVIDER, DefaultCredentialsProvider.create())
                                  .option(SdkClientOption.RETRY_POLICY, AwsRetryPolicy.defaultRetryPolicy())
                                  .option(SdkAdvancedClientOption.DISABLE_HOST_PREFIX_INJECTION, false)
                                  .option(AwsClientOption.SERVICE_SIGNING_NAME, signingName())
                                  .option(SdkClientOption.SERVICE_NAME, serviceName())
                                  .option(AwsClientOption.ENDPOINT_PREFIX, serviceEndpointPrefix()));
    }

    /**
     * Optionally overridden by child classes to define service-specific default configuration.
     */
    protected SdkClientConfiguration mergeServiceDefaults(SdkClientConfiguration configuration) {
        return configuration;
    }

    @Override
    protected final SdkClientConfiguration finalizeChildConfiguration(SdkClientConfiguration configuration) {
        SdkClientConfiguration config =
            configuration.toBuilder()
                         .option(SdkClientOption.ENDPOINT, resolveEndpoint(configuration))
                         .option(SdkClientOption.EXECUTION_INTERCEPTORS, addAwsInterceptors(configuration))
                         .option(AwsClientOption.SIGNING_REGION, resolveSigningRegion(configuration))
                         .build();
        return finalizeServiceConfiguration(config);
    }

    /**
     * Optionally overridden by child classes to derive service-specific configuration from the default-applied configuration.
     */
    protected SdkClientConfiguration finalizeServiceConfiguration(SdkClientConfiguration configuration) {
        return configuration;
    }

    /**
     * Resolve the signing region from the default-applied configuration.
     */
    private Region resolveSigningRegion(SdkClientConfiguration config) {
        return ServiceMetadata.of(serviceEndpointPrefix())
                              .signingRegion(config.option(AwsClientOption.AWS_REGION));
    }

    /**
     * Resolve the endpoint from the default-applied configuration.
     */
    private URI resolveEndpoint(SdkClientConfiguration config) {
        return Optional.ofNullable(config.option(SdkClientOption.ENDPOINT))
                       .orElseGet(() -> EndpointUtils.buildEndpoint(DEFAULT_ENDPOINT_PROTOCOL,
                                                                    serviceEndpointPrefix(),
                                                                    config.option(AwsClientOption.AWS_REGION)));
    }

    /**
     * Resolve the region that should be used based on the customer's configuration.
     */
    private Region resolveRegion(SdkClientConfiguration config) {
        return config.option(AwsClientOption.AWS_REGION) != null
               ? config.option(AwsClientOption.AWS_REGION)
               : regionFromDefaultProvider(config);
    }

    /**
     * Load the region from the default region provider if enabled.
     */
    private Region regionFromDefaultProvider(SdkClientConfiguration config) {
        Boolean defaultRegionDetectionEnabled = config.option(AwsAdvancedClientOption.ENABLE_DEFAULT_REGION_DETECTION);
        if (defaultRegionDetectionEnabled != null && !defaultRegionDetectionEnabled) {
            throw new IllegalStateException("No region was configured, and use-region-provider-chain was disabled.");
        }
        return DEFAULT_REGION_PROVIDER.getRegion();
    }

    @Override
    public final BuilderT region(Region region) {
        clientConfiguration.option(AwsClientOption.AWS_REGION, region);
        return thisBuilder();
    }

    public final void setRegion(Region region) {
        region(region);
    }

    @Override
    public final BuilderT credentialsProvider(AwsCredentialsProvider credentialsProvider) {
        clientConfiguration.option(AwsClientOption.CREDENTIALS_PROVIDER, credentialsProvider);
        return thisBuilder();
    }

    public final void setCredentialsProvider(AwsCredentialsProvider credentialsProvider) {
        credentialsProvider(credentialsProvider);
    }

    private List<ExecutionInterceptor> addAwsInterceptors(SdkClientConfiguration config) {
        List<ExecutionInterceptor> interceptors = awsInterceptors();
        interceptors = CollectionUtils.mergeLists(interceptors, config.option(SdkClientOption.EXECUTION_INTERCEPTORS));
        return interceptors;
    }

    private List<ExecutionInterceptor> awsInterceptors() {
        return Collections.singletonList(new HelpfulUnknownHostExceptionInterceptor());
    }
}
