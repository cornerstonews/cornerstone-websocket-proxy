package com.github.cornerstonews.websocket;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.UnresolvedAddressException;
import java.util.Objects;

//import javax.websocket.ClientEndpoint;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
//import javax.websocket.OnClose;
//import javax.websocket.OnError;
//import javax.websocket.OnMessage;
//import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WebSocketProxy {
    private static final Logger log = LogManager.getLogger(WebSocketProxy.class);

    private final URI targetUrl;
    private Session clientSession;
    private SessionHandler sessionHandler;
    private ClientEndpointConfig clientEndpointConfig;
    private boolean wholeTextMessageSupport = false;
    private boolean wholeBinaryMessageSupport = false;
    private int textBufferSize = 8192; // 8 Bytes, default for tomcat
    private int binaryBufferSize = 8192; // 8 Bytes, default for tomcat
    
    public WebSocketProxy(Session session, String targetUrl) throws URISyntaxException, IOException {
        this(session, targetUrl, null);
    }
    
    public WebSocketProxy(Session session, String targetUrl, boolean wholeTextMessageSupport, Integer textBufferSize, boolean wholeBinaryMessageSupport, Integer binaryBufferSize) throws URISyntaxException, IOException {
        this(session, targetUrl, null, wholeTextMessageSupport, textBufferSize, wholeBinaryMessageSupport, binaryBufferSize);
    }
    
    public WebSocketProxy(Session session, String targetUrl, ClientEndpointConfig cec) throws URISyntaxException, IOException {
        this(session, targetUrl, cec, false, null, false, null);
    }
    
    public WebSocketProxy(Session session, String targetUrl, ClientEndpointConfig cec, boolean wholeTextMessageSupport, Integer textBufferSize, boolean wholeBinaryMessageSupport, Integer binaryBufferSize) throws URISyntaxException, IOException {
        this.clientSession = Objects.requireNonNull(session, "Client session must not be null");
        if (!this.clientSession.isOpen()) {
            throw new IOException("Client session must be open");
        }
        this.clientEndpointConfig = cec;
        this.targetUrl = new URI(targetUrl);
        this.wholeTextMessageSupport = wholeTextMessageSupport;
        this.wholeBinaryMessageSupport = wholeBinaryMessageSupport;
        this.textBufferSize = (textBufferSize == null || textBufferSize <= 8192) ? this.textBufferSize : textBufferSize;
        this.binaryBufferSize = (binaryBufferSize == null || binaryBufferSize <= 8192) ? this.binaryBufferSize : binaryBufferSize;
    }
    
    /**
     * This method should be invoked once new connection is established.
     * 
     * After socket has been opened, it creates a connection with target and binds the 
     * session handler with client connection. On successful connection it lets the client 
     * know the handshake was successful by sending 'Connection Established' text message.
     * If connection was not successful then it closes the client connection.
     * 
     * @param session - the session that has just been activated.
     * @param config - the configuration used to configure this endpoint.
     * 
     */
    public void onOpen(Session session, EndpointConfig config) {
        log.info("[Client {}] connection has been opened.", session.getId());
        
        try {
            this.sessionHandler = new SessionHandler();
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            if(this.clientEndpointConfig == null) {
                this.clientEndpointConfig = ClientEndpointConfig.Builder.create().build();
            }
            
            // Connecting targets with ClientEndpointConfig requires instance of a class that extends Endpoint
            // rather than an annotated class with @ClientEndpoint
//            container.connectToServer(sessionHandler, this.targetUrl);
            container.connectToServer(sessionHandler, this.clientEndpointConfig, this.targetUrl);
        } catch (DeploymentException | IOException e) {
            log.error("[Client {}] Error while creating proxy session with target: '{}'", clientSession.getId(), e.getMessage());
            log.trace("Stacktrace: ", e);
            this.closeClientConnection();
        }
    }
    
    /**
     * This method should be invoked when the client closes a WebSocket connection,
     * so it can close related target connection. 
     * 
     * @param session - the session about to be closed.
     * @param reason - the reason the session was closed.
     */
    public void onClose(Session session, CloseReason reason) {
        log.info("[Client {}] session has been closed.", session.getId());
        log.trace("[Client {}] session close reason: {}", session.getId(), reason.toString());

        // Close target side connection
        this.sessionHandler.closeTargetConnection();
    }

    /**
     * This method should be invoked when an error is detected on the client connection,
     * so it can close related target connection. 
     *      
     * @param session - the session in use when the error occurs.
     * @param t - the throwable representing the problem.
     */
    public void onError(Session session, Throwable t) {
        String message = t.getMessage();
        if(t.getCause() instanceof UnresolvedAddressException) {
            message = "Can not connect to target '" + this.targetUrl + "' due to unresolved address.";
        }
        log.debug("[Client {}] Error has been detected", session.getId());
        log.trace("[Client {}] Error: {}, Stacktrace: ", session.getId(), message, t);
        
        // Close target side connection
        this.sessionHandler.closeTargetConnection();
        this.closeClientConnection();
    }

    /**
     * This method should be invoked each time server receives a text WebSocket message.
     * 
     * @param session - the session that is sending the message.
     * @param message - text message.
     * @param isLast  - flag to indicate if this message is the last of the whole message being delivered.
     * 
     */
    public void onMessage(Session session, String message, boolean isLast) {
        log.debug("[Client {}] has received text message: {}", session.getId(), message);
        this.sessionHandler.sendMessageToTarget(message, isLast);
    }

    /**
     * This method should be invoked each time server receives a binary WebSocket message.
     * 
     * @param session - the session that is sending the message.
     * @param message - binary message.
     * @param isLast  - flag to indicate if this message is the last of the whole message being delivered.
     * 
     */
    public void onMessage(Session session, ByteBuffer message, boolean isLast) {
        log.debug("[Client {}] has received a binary message: {}", session.getId(), message);
        this.sessionHandler.sendMessageToTarget(message, isLast);
    }

    // ------------------------------------------------------
    // Client side message handling
    // ------------------------------------------------------
    protected void closeClientConnection() {
        try {
            log.trace("[Client {}] Closing client session.", this.clientSession.getId());
            if (this.clientSession.isOpen()) {
                this.clientSession.close();
            }
            log.info("[Client {}] Client session closed.", this.clientSession.getId());
        } catch (IOException e) {
            log.error("[Client {}] Error while closing a client connection session. Error: {}", this.clientSession.getId(), e.getMessage());
            log.trace("[Client {}] Stacktrace: ", this.clientSession.getId(), e);
        }
    }

    protected void sendMessageToClient(String message, boolean isLast) {
        try {
            log.trace("Sending received text message: '{}' from [Target {}] -> [Client {}]", message, this.sessionHandler.getTargetSessionId(), this.clientSession.getId());
            this.clientSession.getBasicRemote().sendText(message, isLast);
            log.debug("Successfully sent received text message from [Target {}] -> [Client {}]", this.sessionHandler.getTargetSessionId(), this.clientSession.getId());
        } catch (IOException e) {
            log.error("[Client {}] Error while sending text message to client. Error: {}", this.clientSession.getId(), e.getMessage());
            log.trace("[Client {}] Stacktrace: ", this.clientSession.getId(), e);

            this.sessionHandler.closeTargetConnection();
            this.closeClientConnection();
        }
    }

    protected void sendMessageToClient(ByteBuffer message, boolean isLast) {
        try {
            log.trace("Sending received binary message: '{}' from [Target {}] -> [Client {}]", message, this.sessionHandler.getTargetSessionId(), this.clientSession.getId());
            this.clientSession.getBasicRemote().sendBinary(message, isLast);
            log.debug("Successfully sent received binary message from [Target {}] -> [Client {}]", this.sessionHandler.getTargetSessionId(), this.clientSession.getId());
        } catch (IOException e) {
            log.error("[Client {}] Error while sending binary message to client. Error: {}", this.clientSession.getId(), e.getMessage());
            log.trace("[Client {}] Stacktrace: ", this.clientSession.getId(), e);

            this.sessionHandler.closeTargetConnection();
            this.closeClientConnection();
        }
    }
    
//    @ClientEndpoint
    public class SessionHandler extends Endpoint {
        private Session targetSession;

        private SessionHandler() {
        }
        
        public String getTargetSessionId() {
            return targetSession.getId();
        }
        
        /**
         * This method will be invoked once new connection to target is established. 
         * On successful connection to the target, it lets the client know the handshake
         * was successful by sending 'Connection Established' text message.
         * 
         * @param session - the session that has just been activated.
         * @param config  - the configuration used to configure this endpoint.
         * 
         */
//        @OnOpen
        public void onOpen(Session session, EndpointConfig config) {
            this.targetSession = session;
            log.info("[ProxyHandler {}] connected to endpoint", session.getId());
            log.trace("[ProxyHandler {}] Endpoint: {}", session.getId(), targetUrl);
            log.info("[ProxyHandler {}] is ready to proxy requests for [Client {}]", session.getId(), clientSession.getId());
            
            registerMessageHandlers(this, session);
        }
        
        private void registerMessageHandlers(SessionHandler sessionHandler, Session session) {
            // Register message handlers since non-annotated ClientEndpoint (class that extends Endpoint) 
            // does not call onMessage without it being registered to session. 
            // Annotated (@ClientEndpoint) does not need this.
            
            // Handling incoming text messages (
            if(wholeTextMessageSupport) {
                session.setMaxTextMessageBufferSize(textBufferSize);
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(String wholeMessage) {
                        log.trace("[ProxyHandler {}] Whole<String>: has received text message: {}", session.getId(), wholeMessage);
                        sessionHandler.onMessage(session,  wholeMessage, true);
                    }
                });
            } else {
                session.addMessageHandler(new MessageHandler.Partial<String>() {
                    @Override
                    public void onMessage(String partialMessage, boolean last) {
                        log.trace("[ProxyHandler {}] Partial<String>: has received text message: {}", session.getId(), partialMessage);
                        sessionHandler.onMessage(session,  partialMessage, last);
                    }
                });
            }
            
            // Handling incoming binary messages
            if(wholeBinaryMessageSupport) {
                session.setMaxBinaryMessageBufferSize(binaryBufferSize);
                session.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
                    @Override
                    public void onMessage(ByteBuffer wholeMessage) {
                        sessionHandler.onMessage(session,  wholeMessage, true);
                    }
                });
            } else {
                session.addMessageHandler(new MessageHandler.Partial<ByteBuffer>() {
                    @Override
                    public void onMessage(ByteBuffer partialMessage, boolean last) {
                        sessionHandler.onMessage(session,  partialMessage, last);
                    }
                });
            }
        }

        /**
         * This method will be invoked when the target closes a WebSocket connection. 
         * This method will call close related client connection.
         * 
         * @param session  - the session about to be closed.
         * @param reason   - the reason the session was closed.
         */
//        @OnClose
        public void OnClose(Session session, CloseReason reason) {
            log.info("[ProxyHandler {}] session has been closed.", session.getId());
            log.trace("[ProxyHandler {}] session close reason: {}", session.getId(), reason.toString());
            closeClientConnection();
        }

        /**
         * This method will be invoked when an error is detected on the target connection. 
         * This method will call close related client connection.
         * 
         * @param session - the session in use when the error occurs.
         * @param t       - the throwable representing the problem.
         */
//        @OnError
        public void onError(Session session, Throwable t) {
            log.debug("[ProxyHandler {}] Error has been detected. Error: {} ", session.getId(), t.getMessage());
            log.trace("[ProxyHandler {}] Stacktrace: ", session.getId(), t);
            closeClientConnection();
            this.closeTargetConnection();
        }

        /**
         * This method should be invoked each time server receives a text WebSocket message.
         * 
         * @param session - the session that is sending the message.
         * @param message - text message.
         * @param isLast  - flag to indicate if this message is the last of the whole message being delivered.
         * 
         */
//        @OnMessage
        public void onMessage(Session session, String message, boolean isLast) {
            log.debug("[ProxyHandler {}] has received text message: {}", session.getId(), message);
            sendMessageToClient(message, isLast);
        }
        
        /**
         * This method should be invoked each time server receives a binary WebSocket message.
         * 
         * @param session - the session that is sending the message.
         * @param message - binary message.
         * @param isLast  - flag to indicate if this message is the last of the whole message being delivered.
         * 
         */
//        @OnMessage
        public void onMessage(Session session, ByteBuffer message, boolean isLast) {
            log.debug("[ProxyHandler {}] has received binary message", session.getId());
            sendMessageToClient(message, isLast);
        }
        
        // ------------------------------------------------------
        // Target side message handling
        // ------------------------------------------------------
        public void closeTargetConnection() {
            try {
                if (this.targetSession != null && this.targetSession.isOpen()) {
                    log.trace("[Target {}] Closing target session.", this.targetSession.getId());
                    this.targetSession.close();
                    log.info("[Target {}] Target session closed.", this.targetSession.getId());
                }
            } catch (IOException e) {
                log.error("[Target {}] Error while closing target connection session. Error: {}", this.targetSession.getId(), e.getMessage());
                log.trace("[Target {}] Stacktrace: ", this.targetSession.getId(), e);
            }
        }

        public void sendMessageToTarget(String message, boolean isLast) {
            try {
                log.trace("Sending received text message: '{}' from [Client {}] -> [Target {}]", message, clientSession.getId(), this.targetSession.getId());
                this.targetSession.getBasicRemote().sendText(message, isLast);
                log.debug("Successfully sent received text message from [Client {}] -> [Target {}]", clientSession.getId(), this.targetSession.getId());
            } catch (IOException e) {
                log.error("[Target {}] Error while sending text message to target. Error: {}", this.targetSession.getId(), e.getMessage());
                log.trace("[Target {}] Stacktrace: ", this.targetSession.getId(), e);

                this.closeTargetConnection();
                closeClientConnection();
            }
        }

        public void sendMessageToTarget(ByteBuffer message, boolean isLast) {
            try {
                log.trace("Sending received binary message: '{}' from [Client {}] -> [Target {}]", message, clientSession.getId(), this.targetSession.getId());
                this.targetSession.getBasicRemote().sendBinary(message, isLast);
                log.debug("Successfully sent received binary message from [Client {}] -> [Target {}]", clientSession.getId(), this.targetSession.getId());
            } catch (IOException e) {
                log.error("[Target {}] Error while sending binary message to target. Error: {}", this.targetSession.getId(), e.getMessage());
                log.trace("[Target {}] Stacktrace: ", this.targetSession.getId(), e);

                this.closeTargetConnection();
                closeClientConnection();
            }
        }
    }
}
