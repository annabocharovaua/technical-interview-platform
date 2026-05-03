/**
 * Audio Manager
 * Handles:
 * - Microphone access and permissions
 * - Audio processing (noise suppression, gain control)
 * - Audio constraints optimization
 * - Device selection
 * - Audio quality monitoring
 */
class AudioManager {
    constructor(options = {}) {
        this.options = {
            audioConstraints: options.audioConstraints || {
                audio: {
                    echoCancellation: true,
                    noiseSuppression: true,
                    autoGainControl: true,
                    channelCount: 1,
                    sampleRate: 16000,
                    sampleSize: 16,
                    ...options.audioConstraints?.audio
                }
            },
            volumeThreshold: options.volumeThreshold || 0.1,
            silenceThreshold: options.silenceThreshold || 0.02,
            ...options
        };
        this.mediaStream = null;
        this.audioContext = null;
        this.analyser = null;
        this.mediaRecorder = null;
        this.isRecording = false;
        this.isMicrophoneActive = false;
        this.currentVolume = 0;
        this.selectedDeviceId = null;
        this.callbacks = {
            onMicrophoneGranted: options.onMicrophoneGranted || (() => {}),
            onMicrophoneDenied: options.onMicrophoneDenied || (() => {}),
            onVolumeChange: options.onVolumeChange || (() => {}),
            onError: options.onError || (() => {})
        };
        this.log('🎤 AudioManager initialized');
    }
    /**
     * Request microphone access with fallback audio constraints
     */
    async requestMicrophoneAccess() {
        try {
            this.log('🎤 Requesting microphone access...');
            const constraints = {
                audio: {
                    echoCancellation: { ideal: true },
                    noiseSuppression: { ideal: true },
                    autoGainControl: { ideal: true },
                    channelCount: { ideal: 1 },
                    sampleRate: { ideal: 16000 }
                }
            };
            try {
                this.mediaStream = await navigator.mediaDevices.getUserMedia(constraints);
                this.log('✅ Microphone access granted with ideal constraints');
            } catch (error) {
                this.log('⚠️ Ideal constraints failed, trying fallback...', error.message);
                this.mediaStream = await navigator.mediaDevices.getUserMedia({
                    audio: true
                });
                this.log('✅ Microphone access granted with fallback constraints');
            }
            this.isMicrophoneActive = true;
            this.setupAudioProcessing();
            this.callbacks.onMicrophoneGranted();
            return this.mediaStream;
        } catch (error) {
            this.log('❌ Microphone access denied or error', error);
            this.isMicrophoneActive = false;
            let errorMessage = 'Unknown error';
            if (error.name === 'NotAllowedError') {
                errorMessage = 'Microphone permission denied';
            } else if (error.name === 'NotFoundError') {
                errorMessage = 'No microphone found';
            } else if (error.name === 'NotReadableError') {
                errorMessage = 'Microphone is in use by another application';
            } else if (error.name === 'SecurityError') {
                errorMessage = 'Microphone access requires HTTPS';
            }
            this.callbacks.onMicrophoneDenied({
                errorName: error.name,
                errorMessage,
                originalError: error
            });
            throw error;
        }
    }
    /**
     * Setup audio processing (analyser for volume monitoring)
     */
    setupAudioProcessing() {
        if (!this.mediaStream) {
            this.log('⚠️ No media stream available');
            return;
        }
        try {
            if (!this.audioContext) {
                this.audioContext = new (window.AudioContext || window.webkitAudioContext)();
                this.log(`✅ AudioContext created (sampleRate: ${this.audioContext.sampleRate}Hz)`);
            }
            const source = this.audioContext.createMediaStreamSource(this.mediaStream);
            this.analyser = this.audioContext.createAnalyser();
            this.analyser.fftSize = 256;
            source.connect(this.analyser);
            this.analyser.connect(this.audioContext.destination);
            this.monitorVolume();
            this.log('✅ Audio processing setup complete');
        } catch (error) {
            this.log('❌ Error setting up audio processing', error);
            this.callbacks.onError({
                type: 'audio_processing_error',
                error
            });
        }
    }
    /**
     * Monitor microphone volume in real-time
     */
    monitorVolume() {
        if (!this.analyser) return;
        const monitoringInterval = setInterval(() => {
            if (!this.isMicrophoneActive) {
                clearInterval(monitoringInterval);
                return;
            }
            const dataArray = new Uint8Array(this.analyser.frequencyBinCount);
            this.analyser.getByteFrequencyData(dataArray);
            let sum = 0;
            for (let i = 0; i < dataArray.length; i++) {
                sum += dataArray[i] * dataArray[i];
            }
            const rms = Math.sqrt(sum / dataArray.length);
            this.currentVolume = rms / 255;
            this.callbacks.onVolumeChange({
                volume: this.currentVolume,
                isSpeaking: this.currentVolume > this.options.volumeThreshold,
                isSilent: this.currentVolume < this.options.silenceThreshold
            });
        }, 100);
    }
    /**
     * Get available audio input devices
     */
    async getAudioDevices() {
        try {
            const devices = await navigator.mediaDevices.enumerateDevices();
            const audioDevices = devices
                .filter(device => device.kind === 'audioinput')
                .map(device => ({
                    id: device.deviceId,
                    label: device.label || `Microphone ${device.deviceId.substring(0, 5)}...`,
                    kind: device.kind
                }));
            this.log('✅ Available audio devices', audioDevices);
            return audioDevices;
        } catch (error) {
            this.log('❌ Error enumerating audio devices', error);
            throw error;
        }
    }
    /**
     * Switch to different audio input device
     */
    async switchAudioDevice(deviceId) {
        try {
            this.log(`🔄 Switching to audio device: ${deviceId}`);
            if (this.mediaStream) {
                this.mediaStream.getTracks().forEach(track => track.stop());
            }
            const constraints = {
                audio: {
                    deviceId: { exact: deviceId },
                    ...this.options.audioConstraints.audio
                }
            };
            this.mediaStream = await navigator.mediaDevices.getUserMedia(constraints);
            this.selectedDeviceId = deviceId;
            this.setupAudioProcessing();
            this.log(`✅ Switched to audio device: ${deviceId}`);
            return this.mediaStream;
        } catch (error) {
            this.log('❌ Error switching audio device', error);
            this.callbacks.onError({
                type: 'device_switch_error',
                deviceId,
                error
            });
            throw error;
        }
    }
    /**
     * Get current volume level (0-1)
     */
    getVolume() {
        return this.currentVolume;
    }
    /**
     * Get audio stream
     */
    getMediaStream() {
        return this.mediaStream;
    }
    /**
     * Get audio context
     */
    getAudioContext() {
        return this.audioContext;
    }
    /**
     * Stop recording and release microphone
     */
    stopRecording() {
        if (this.mediaStream) {
            this.mediaStream.getTracks().forEach(track => {
                track.stop();
            });
            this.mediaStream = null;
        }
        if (this.audioContext && this.audioContext.state !== 'closed') {
            this.audioContext.close();
            this.audioContext = null;
        }
        this.isMicrophoneActive = false;
        this.analyser = null;
        this.log('✅ Audio recording stopped');
    }
    /**
     * Get audio quality metrics
     */
    getAudioMetrics() {
        return {
            isMicrophoneActive: this.isMicrophoneActive,
            currentVolume: this.currentVolume,
            audioContextState: this.audioContext?.state || 'not_initialized',
            sampleRate: this.audioContext?.sampleRate || null,
            selectedDevice: this.selectedDeviceId,
            constraints: this.options.audioConstraints
        };
    }
    /**
     * Internal logging
     */
    log(message, data) {
        const timestamp = new Date().toLocaleTimeString();
        if (data) {
            console.log(`[${timestamp}] 🎤 ${message}`, data);
        } else {
            console.log(`[${timestamp}] 🎤 ${message}`);
        }
    }
}
if (typeof module !== 'undefined' && module.exports) {
    module.exports = AudioManager;
}