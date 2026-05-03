/**
 * WebSocket Connection Manager
 * Handles:
 * - Robust BASE_URL detection (file://, localhost, remote)
 * - Automatic reconnection with exponential backoff
 * - Heartbeat/ping-pong monitoring
 * - Connection state management
 * - Logging and debugging
 */

class WebSocketManager {
    constructor(options = {}) {
        this.options = {
            fallbackHost: options.fallbackHost || 'localhost',
            fallbackPort: options.fallbackPort || 8085,
            fallbackScheme: options.fallbackScheme || 'http',
            maxReconnectAttempts: options.maxReconnectAttempts || 10,
            initialReconnectDelay: options.initialReconnectDelay || 1000,
            maxReconnectDelay: options.maxReconnectDelay || 30000,
            heartbeatInterval: options.heartbeatInterval || 30000,
            ...options
        };

        this.baseURL = this.determineBaseURL();
        this.wsURL = `${this.baseURL}/ws`;
        this.apiURL = `${this.baseURL}/api/realtime`;

        this.stompClient = null;
        this.isConnected = false;
        this.reconnectAttempts = 0;
        this.reconnectTimeout = null;
        this.heartbeatInterval = null;
        this.lastHeartbeat = Date.now();

        this.subscriptions = new Map();
        this.callbacks = {
            onConnect: options.onConnect || (() => {}),
            onDisconnect: options.onDisconnect || (() => {}),
            onError: options.onError || (() => {}),
            onReconnectAttempt: options.onReconnectAttempt || (() => {})
        };

        this.log('🔌 WebSocketManager initialized', {
            baseURL: this.baseURL,
            wsURL: this.wsURL,
            fallbackHost: this.options.fallbackHost,
            fallbackPort: this.options.fallbackPort
        });
    }

    /**
     * Determine BASE_URL with robust fallback logic
     */
    determineBaseURL() {
        let baseURL = window.location.origin;


        if (!baseURL || baseURL === 'null' || baseURL.startsWith('file://')) {
            const scheme = window.location.protocol === 'https:' ? 'https' : this.options.fallbackScheme;
            baseURL = `${scheme}://${this.options.fallbackHost}:${this.options.fallbackPort}`;

            console.warn(
                `⚠️ Detected file:// or invalid origin. Using fallback BASE_URL: ${baseURL}`,
                `If server runs on different host/port, update WebSocketManager options.`,
                `Current page protocol: ${window.location.protocol}`,
                `Current window.location.origin: ${window.location.origin}`
            );

            this.updateUIWarning(baseURL);
        } else {
            this.log('✅ Using actual BASE_URL from window.location.origin', baseURL);
        }

        return baseURL;
    }

    /**
     * Update UI to show fallback warning
     */
    updateUIWarning(baseURL) {
        try {
            const connectionStatus = document.getElementById('connectionStatus');
            if (connectionStatus) {
                connectionStatus.textContent = `⚠️ Using fallback: ${baseURL}`;
                connectionStatus.className = 'connection-status disconnected';
            }
        } catch (e) {
        }
    }

    /**
     * Connect to WebSocket with automatic retry
     */
    connect() {
        if (this.isConnected && this.stompClient) {
            this.log('ℹ️ Already connected');
            return Promise.resolve();
        }

        return new Promise((resolve, reject) => {
            try {
                this.log('🔄 Connecting to WebSocket...', { url: this.wsURL });

                const socket = new SockJS(this.wsURL);
                this.stompClient = Stomp.over(socket);
                this.stompClient.debug = null;

                this.stompClient.connect(
                    {},
                    (frame) => {
                        this.isConnected = true;
                        this.reconnectAttempts = 0;

                        this.log('✅ WebSocket connected successfully', {
                            version: frame.version,
                            server: frame.server
                        });


                        this.startHeartbeat();


                        this.callbacks.onConnect();

                        resolve();
                    },
                    (error) => {
                        this.handleConnectionError(error);
                        reject(error);
                    }
                );
            } catch (error) {
                this.log('❌ Error creating WebSocket connection', error);
                this.handleConnectionError(error);
                reject(error);
            }
        });
    }

    /**
     * Handle connection errors with automatic retry
     */
    handleConnectionError(error) {
        this.isConnected = false;
        this.log('❌ WebSocket connection error', error);

        this.callbacks.onError(error);


        if (this.reconnectAttempts < this.options.maxReconnectAttempts) {
            this.scheduleReconnect();
        } else {
            this.log(`❌ Max reconnection attempts (${this.options.maxReconnectAttempts}) reached. Stopping.`);
            this.callbacks.onError({
                message: 'Connection failed after max retries',
                maxAttempts: this.options.maxReconnectAttempts
            });
        }
    }

    /**
     * Schedule reconnection with exponential backoff
     */
    scheduleReconnect() {
        if (this.reconnectTimeout) {
            clearTimeout(this.reconnectTimeout);
        }

        const delay = Math.min(
            this.options.initialReconnectDelay * Math.pow(2, this.reconnectAttempts),
            this.options.maxReconnectDelay
        );

        this.reconnectAttempts++;

        this.log(`🔄 Scheduling reconnect attempt ${this.reconnectAttempts}/${this.options.maxReconnectAttempts} in ${delay}ms`, {
            delay,
            exponentialBackoff: true
        });

        this.callbacks.onReconnectAttempt({
            attempt: this.reconnectAttempts,
            maxAttempts: this.options.maxReconnectAttempts,
            delayMs: delay
        });

        this.reconnectTimeout = setTimeout(() => {
            this.log(`🔄 Attempting reconnection #${this.reconnectAttempts}...`);
            this.connect().catch(() => {

            });
        }, delay);
    }

    /**
     * Start heartbeat monitoring
     */
    startHeartbeat() {
        this.stopHeartbeat();

        this.heartbeatInterval = setInterval(() => {
            const timeSinceLastMessage = Date.now() - this.lastHeartbeat;

            if (timeSinceLastMessage > this.options.heartbeatInterval * 1.5) {
                this.log('⚠️ No heartbeat received. Connection may be stale.');
                this.disconnect();
                this.scheduleReconnect();
            }
        }, this.options.heartbeatInterval);
    }

    /**
     * Stop heartbeat monitoring
     */
    stopHeartbeat() {
        if (this.heartbeatInterval) {
            clearInterval(this.heartbeatInterval);
            this.heartbeatInterval = null;
        }
    }

    /**
     * Subscribe to a WebSocket topic
     */
    subscribe(destination, callback) {
        if (!this.isConnected || !this.stompClient) {
            this.log(`⚠️ Cannot subscribe - not connected. Queuing subscription for ${destination}`);

            this.subscriptions.set(destination, callback);
            return;
        }

        try {
            const subscription = this.stompClient.subscribe(destination, (message) => {
                this.lastHeartbeat = Date.now();
                callback(message);
            });

            this.subscriptions.set(destination, callback);
            this.log(`✅ Subscribed to ${destination}`);

            return subscription;
        } catch (error) {
            this.log(`❌ Error subscribing to ${destination}`, error);
            throw error;
        }
    }

    /**
     * Send a message to a WebSocket destination
     */
    send(destination, headers, body) {
        if (!this.isConnected || !this.stompClient) {
            this.log(`⚠️ Cannot send - not connected. Destination: ${destination}`);
            throw new Error('WebSocket not connected');
        }

        try {
            this.stompClient.send(destination, headers || {}, body || '');
            this.log(`✅ Message sent to ${destination}`);
        } catch (error) {
            this.log(`❌ Error sending message to ${destination}`, error);
            throw error;
        }
    }

    /**
     * Disconnect from WebSocket
     */
    disconnect() {
        this.stopHeartbeat();

        if (this.reconnectTimeout) {
            clearTimeout(this.reconnectTimeout);
            this.reconnectTimeout = null;
        }

        if (this.stompClient && this.isConnected) {
            try {
                this.stompClient.disconnect(() => {
                    this.isConnected = false;
                    this.log('✅ WebSocket disconnected');
                    this.callbacks.onDisconnect();
                });
            } catch (error) {
                this.log('⚠️ Error during disconnect', error);
                this.isConnected = false;
            }
        }
    }

    /**
     * Check if connected
     */
    isWebSocketConnected() {
        return this.isConnected;
    }

    /**
     * Get connection stats
     */
    getConnectionStats() {
        return {
            isConnected: this.isConnected,
            reconnectAttempts: this.reconnectAttempts,
            maxReconnectAttempts: this.options.maxReconnectAttempts,
            baseURL: this.baseURL,
            wsURL: this.wsURL,
            lastHeartbeat: new Date(this.lastHeartbeat).toISOString(),
            subscriptionCount: this.subscriptions.size
        };
    }

    /**
     * Internal logging
     */
    log(message, data) {
        const timestamp = new Date().toLocaleTimeString();
        if (data) {
            console.log(`[${timestamp}] ${message}`, data);
        } else {
            console.log(`[${timestamp}] ${message}`);
        }
    }
}


if (typeof module !== 'undefined' && module.exports) {
    module.exports = WebSocketManager;
}

