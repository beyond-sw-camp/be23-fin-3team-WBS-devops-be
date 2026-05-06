package com.beyond.wbs.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(ElasticsearchProperties.class)
@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "true")
public class ElasticsearchConfig {

    @Bean
    public ElasticsearchClient elasticsearchClient(ElasticsearchProperties properties) {
        if (properties.getAuthType() == ElasticsearchProperties.AuthType.IAM) {
            throw new IllegalStateException("Elasticsearch IAM auth is not supported. Use BASIC or NONE for local development.");
        }

        HttpHost host = createHost(properties.getEndpoint());
        RestClient restClient;

        if (properties.getAuthType() == ElasticsearchProperties.AuthType.BASIC) {
            validateBasicAuth(properties);
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    AuthScope.ANY,
                    new UsernamePasswordCredentials(properties.getUsername(), properties.getPassword())
            );

            restClient = RestClient.builder(host)
                    .setHttpClientConfigCallback(httpClientBuilder ->
                            httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider))
                    .build();
        } else {
            restClient = RestClient.builder(host).build();
        }

        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }

    private void validateBasicAuth(ElasticsearchProperties properties) {
        if (!StringUtils.hasText(properties.getUsername()) || !StringUtils.hasText(properties.getPassword())) {
            throw new IllegalStateException("Elasticsearch basic auth requires username and password.");
        }
    }

    private HttpHost createHost(String endpoint) {
        URI uri = URI.create(endpoint);
        return new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
    }
}
