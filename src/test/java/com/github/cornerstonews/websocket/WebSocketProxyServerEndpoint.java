/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.cornerstonews.websocket;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.ClientEndpointConfig.Configurator;
import javax.websocket.server.ServerEndpoint;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@ServerEndpoint(value = "/proxy-example")
public class WebSocketProxyServerEndpoint { 
    private static final Logger log = LogManager.getLogger(WebSocketProxyServerEndpoint.class);
    
    private WebSocketProxy proxy;
    
    public WebSocketProxyServerEndpoint() throws URISyntaxException {
        log.info("WebSocketProxyServerEndpoint constructor called.");
    }
    
    private void initializeProxy(Session session) throws URISyntaxException, IOException  {
        if(this.proxy == null) {
            ClientEndpointConfig clientEndpointConfig = ClientEndpointConfig.Builder.create().configurator(new Configurator() {
                @Override
                public void beforeRequest(Map<String, List<String>> headers) {
//                    headers.put("Authorization", Collections.singletonList("jfasdfkjasdkfjsf"));
                }
            }).build();
//            String SSL_CONTEXT_PROPERTY = "org.apache.tomcat.websocket.SSL_CONTEXT";  // This is implementation specific.
//            clientEndpointConfig.getUserProperties().put(SSL_CONTEXT_PROPERTY, createSSLContext());
            
            this.proxy = new WebSocketProxy(session, "ws://localhost:9000/ws", clientEndpointConfig);
        }
    }
    
//    private SSLContext createSSLContext() throws Exception {
//        SSLContext sslContext = SSLContext.getInstance("TLS");
//        TrustManager tm = new X509TrustManager() {
//            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
//            }
//
//            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
//            }
//
//            public X509Certificate[] getAcceptedIssuers() {
//                return null;
//            }
//        };
//        sslContext.init(null, new TrustManager[] { tm }, null);
//        return sslContext;
//    }
    
    /**
     * This method is invoked once new connection is established.
     * After socket has been opened, it allows us to intercept the creation of a new session.
     * The session class allows us to send data to the user. In the method onOpen, 
     * we'll let the client know that the handshake was successful.
     * 
     * @param session
     * @param EndpointConfig config
     */
    @OnOpen
    public void onOpen(Session session, EndpointConfig config) {
        try {
            this.initializeProxy(session);
            this.proxy.onOpen(session, config);
        } catch (URISyntaxException | IOException e) {
            log.error("Could not open proxy instance. Cause: ", e.getMessage());
            log.trace("Stacktrace: ", e);
        }
        
    }
    
    /**
     * This method is invoked when the client closes a WebSocket connection.
     * 
     * @param session
     * @return
     */
    @OnClose
    public void onClose(Session session, CloseReason reason) {
        this.proxy.onClose(session, reason);
    }
    
    /**
     * This method is invoked when an error is detected on the connection.
     * 
     * @param session
     * @param t
     * @return
     */
    @OnError
    public void onError(Session session, Throwable t) {
        this.proxy.onError(session, t);
    }
    
    /**
     * This method is invoked each time that the server receives a text WebSocket message.
     * 
     * @param session
     * @param message
     * @return
     * @throws IOException
     */
    @OnMessage
    public void onMessage(Session session, String message, boolean isLast) {
        this.proxy.onMessage(session, message, isLast);
    }
    
    /**
     * This method is invoked each time that the server receives a binary WebSocket message.
     * 
     * @param session
     * @param message
     * @return
     * @throws IOException
     */
    @OnMessage
    public void onMessage(Session session, ByteBuffer message, boolean isLast) {
        this.proxy.onMessage(session, message, isLast);
    }
    
}

