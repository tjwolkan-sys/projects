package com.powerdialer.util;

import org.apache.http.*;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.ssl.SSLContexts;
import org.springframework.http.client.*;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

public class ProxyUtil {

    /**
     * Applies SSL bypass for RestTemplate using a proxy.
     *
     * @return RestTemplate configured with SSL bypass and proxy settings.
     */
    public RestTemplate applySSLBypass() {
        HttpComponentsClientHttpRequestFactory factory = null;
        try {

            HttpHost proxy = new HttpHost("10.12.50.52", 8080);

            BasicCredentialsProvider creds = new BasicCredentialsProvider();
            creds.setCredentials(
                    new AuthScope(proxy),
                    new UsernamePasswordCredentials("Turktraktorapp", "CC10Bb55E".toCharArray().toString())
            );

            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

            SSLContext sslContext = SSLContexts.custom()
                    .loadTrustMaterial((chain, authType) -> true)
                    .build();

            SSLConnectionSocketFactory sslSocketFactory =
                    new SSLConnectionSocketFactory(sslContext, (hostname, session) -> true);

            HttpRoutePlanner routePlanner = new DefaultProxyRoutePlanner(proxy);
            CloseableHttpClient httpClient = HttpClientBuilder.create()
                    .setDefaultCredentialsProvider(creds)
                    .setRoutePlanner(routePlanner)
                    .setSSLSocketFactory(sslSocketFactory)
                    .build();

            factory = new HttpComponentsClientHttpRequestFactory();
            factory.setHttpClient(httpClient);
            factory.setConnectTimeout(10_000);
            factory.setReadTimeout(10_000);

            return new RestTemplate(factory);


        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            System.err.println("SSL bypass uygulaması sırasında hata: " + e.getMessage());
        } catch (KeyStoreException e) {
            throw new RuntimeException(e);
        }
        return new RestTemplate(factory);
    }

}
