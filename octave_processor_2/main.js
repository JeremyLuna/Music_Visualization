/**
 * main.js - Main thread JavaScript for the Frequency Analyzer
 */

class FrequencyAnalyzer {
    constructor() {
        // Audio context and nodes
        this.audioContext = null;
        this.audioBuffer = null;
        this.source = null;
        this.gainNode = null;
        this.audioWorkletNode = null;
        this.analyzerNode = null;
        this.isPlaying = false;
        this.isInitialized = false;
        this.isProcessorInitialized = false;
        
        // Canvas and rendering
        this.canvas = document.getElementById('visualizer');
        this.ctx = this.canvas.getContext('2d');
        this.lastFrameTime = 0;
        this.frameCount = 0;
        this.fps = 0;
        this.animationId = null;
        
        // Playback state
        this.startTime = 0;
        this.pausedAt = 0;
        this.duration = 0;
        
        // Waveform data
        this.activeFrequencies = [];
        this.waveformHistory = [];
        
        // Initialize UI
        this.initUI();
        
        // Initialize handlers
        this.initEventHandlers();
        
        // Initialize AudioContext and AudioWorklet
        this.initAudio().then(() => {
            console.log('Audio initialized');
            // Hide loading overlay once everything is ready
            document.getElementById('loading-overlay').style.display = 'none';
        }).catch(error => {
            console.error('Failed to initialize audio:', error);
            alert('Failed to initialize audio: ' + error.message);
            document.getElementById('loading-overlay').style.display = 'none';
        });
        
        // Set up window resize handler
        window.addEventListener('resize', this.resizeCanvas.bind(this));
        this.resizeCanvas();
    }
    
    /**
     * Initialize the audio context and worklet
     */
    async initAudio() {
        try {
            // Create AudioContext
            this.audioContext = new (window.AudioContext || window.webkitAudioContext)();
            
            // Create analyzer for visualization
            this.analyzerNode = this.audioContext.createAnalyser();
            this.analyzerNode.fftSize = 4096;
            
            // Create gain node for volume control
            this.gainNode = this.audioContext.createGain();
            this.gainNode.gain.value = document.getElementById('volume').value / 100;
            
            // Connect gain node to destination (speaker output)
            this.gainNode.connect(this.audioContext.destination);
            
            // Load AudioWorklet module
            await this.audioContext.audioWorklet.addModule('frequency-processor.js');
            
            // Wait for processor to initialize
            await new Promise((resolve, reject) => {
                // Create the AudioWorkletNode
                this.audioWorkletNode = new AudioWorkletNode(this.audioContext, 'frequency-analysis-processor', {
                    outputChannelCount: [1],
                    processorOptions: {}
                });
                
                // Set up message handling
                this.audioWorkletNode.port.onmessage = (event) => {
                    this.handleProcessorMessage(event.data);
                };
                
                // Wait for the processor to be ready
                const initTimeout = setTimeout(() => {
                    reject(new Error('Timeout waiting for processor initialization'));
                }, 5000);
                
                // Set up a one-time handler for initialization message
                const messageHandler = (event) => {
                    if (event.data.type === 'processorInitialized') {
                        clearTimeout(initTimeout);
                        this.isProcessorInitialized = true;
                        
                        // Load classes into the processor
                        this.loadClassesIntoProcessor()
                            .then(resolve)
                            .catch(reject);
                        
                        // Remove the one-time handler
                        this.audioWorkletNode.port.removeEventListener('message', messageHandler);
                    }
                };
                
                this.audioWorkletNode.port.addEventListener('message', messageHandler);
                this.audioWorkletNode.port.start();
            });
            
            // Connect worklet to analyzer (for visualization only, not audio output)
            this.audioWorkletNode.connect(this.analyzerNode);
            
            // Initialize parameters
            await this.initializeProcessor();
            
            this.isInitialized = true;
            return true;
        } catch (error) {
            console.error('Error initializing audio:', error);
            throw error;
        }
    }
    
    /**
     * Load our JavaScript classes into the AudioWorklet processor
     */
    async loadClassesIntoProcessor() {
        try {
            // Convert frequency values to periods
            const minFrequency = parseInt(document.getElementById('minFrequency').value);
            const maxFrequency = parseInt(document.getElementById('maxFrequency').value);
            
            // Calculate periods based on sample rate
            // period = sampleRate / frequency
            const minSamplesPerPeriod = Math.ceil(this.audioContext.sampleRate / maxFrequency);
            const maxSamplesPerPeriod = Math.floor(this.audioContext.sampleRate / minFrequency);
            
            // Simply send initialization parameters - classes are now included in the processor file
            this.audioWorkletNode.port.postMessage({
                type: 'init',
                minSamplesPerPeriod: parseInt(document.getElementById('minSamplesPerPeriod').value),
                maxSamplesPerPeriod: maxSamplesPerPeriod,
                minPeriodsInBuffer: parseInt(document.getElementById('minPeriodsInBuffer').value),
                numFilters: parseInt(document.getElementById('numFilters').value),
                percentOverlap: parseInt(document.getElementById('percentOverlap').value),
                filterOrder: parseInt(document.getElementById('filterOrder').value)
            });
            
            // Wait for initialization to complete
            await new Promise((resolve, reject) => {
                const messageHandler = (event) => {
                    if (event.data.type === 'initComplete') {
                        // Handle successful initialization
                        // Remove the one-time handler
                        this.audioWorkletNode.port.removeEventListener('message', messageHandler);
                        resolve();
                    } else if (event.data.type === 'initError') {
                        reject(new Error(event.data.error));
                    }
                };
                
                this.audioWorkletNode.port.addEventListener('message', messageHandler);
            });
            
            return true;
        } catch (error) {
            console.error('Error initializing processor:', error);
            throw error;
        }
    }
    
    /**
     * Initialize the AudioWorklet processor with parameters
     */
    async initializeProcessor() {
        // Set up processor parameters
        if (this.audioWorkletNode) {
            // Set all parameters
            const parameters = this.audioWorkletNode.parameters;
            
            parameters.get('minSamplesPerPeriod').value = parseInt(document.getElementById('minSamplesPerPeriod').value);
            parameters.get('maxSamplesPerPeriod').value = parseInt(document.getElementById('maxPeriod').value);
            parameters.get('minPeriodsInBuffer').value = parseInt(document.getElementById('minPeriodsInBuffer').value);
            parameters.get('numFilters').value = parseInt(document.getElementById('numFilters').value);
            parameters.get('percentOverlap').value = parseInt(document.getElementById('percentOverlap').value);
            parameters.get('filterOrder').value = parseInt(document.getElementById('filterOrder').value);
            
            // Set threshold to exact slider value
            this.updateThreshold();
            
            parameters.get('useLowPassFilter').value = document.getElementById('useLowPassFilter').checked ? 1 : 0;
            parameters.get('analysisInterval').value = parseInt(document.getElementById('analysisInterval').value);
        }
    }
    
    /**
     * Update processor parameters when UI controls change
     */
    updateProcessorParameters() {
        if (this.audioWorkletNode) {
            // Update all parameters that can be changed during runtime
            const parameters = this.audioWorkletNode.parameters;
            
            // Update threshold with exact slider value
            this.updateThreshold();
            
            parameters.get('useLowPassFilter').value = document.getElementById('useLowPassFilter').checked ? 1 : 0;
            parameters.get('analysisInterval').value = parseInt(document.getElementById('analysisInterval').value);
            
            // Convert frequency values to periods for minSamples and maxSamples
            const minFrequency = parseInt(document.getElementById('minFrequency').value);
            const maxFrequency = parseInt(document.getElementById('maxFrequency').value);
            
            // Calculate periods based on sample rate
            const minSamplesPerPeriod = Math.ceil(this.audioContext.sampleRate / maxFrequency);
            const maxSamplesPerPeriod = Math.floor(this.audioContext.sampleRate / minFrequency);
            
            // For parameters that require re-initialization, we need to send a message
            this.audioWorkletNode.port.postMessage({
                type: 'updateParameters',
                percentOverlap: parseInt(document.getElementById('percentOverlap').value),
                numFilters: parseInt(document.getElementById('numFilters').value),
                filterOrder: parseInt(document.getElementById('filterOrder').value),
                minSamplesPerPeriod: minSamplesPerPeriod,
                maxSamplesPerPeriod: maxSamplesPerPeriod
            });
        }
    }
    
    /**
     * Update threshold with the slider value, but apply a power of 3.3 internally
     * This gives better control at the lower end of the threshold range
     */
    updateThreshold() {
        // Get the slider element and its raw value (0-1)
        const slider = document.getElementById('threshold');
        const sliderValue = parseFloat(slider.value);
        
        // Update the UI display with the direct slider value
        document.getElementById('thresholdValue').textContent = sliderValue.toFixed(2);
        
        // Apply power of 3.3 for better control at lower values
        // This makes the threshold curve more sensitive at the low end
        const processedThreshold = Math.pow(sliderValue, 3.3);
        
        // Set the processed threshold value on the processor
        if (this.audioWorkletNode && this.audioWorkletNode.parameters) {
            this.audioWorkletNode.parameters.get('threshold').value = processedThreshold;
        }
    }
    
    /**
     * Handle messages from the AudioWorklet processor
     */
    handleProcessorMessage(message) {
        switch (message.type) {
            case 'analysisResults':
                // Update active frequencies
                this.activeFrequencies = message.peaks;
                
                // Update display
                document.getElementById('activeFreqs').textContent = 
                    `Active frequencies: ${this.activeFrequencies.length}`;
                break;
                
            case 'resetComplete':
                console.log('Processor reset complete');
                break;
                
            case 'parametersUpdated':
                // Parameters have been updated
                break;
                
            default:
                console.log('Unknown message from processor:', message);
        }
    }
    
    /**
     * Initialize UI event handlers
     */
    initEventHandlers() {
        // Audio file input
        document.getElementById('audioFile').addEventListener('change', this.loadAudioFile.bind(this));
        
        // Playback controls
        document.getElementById('playPauseButton').addEventListener('click', this.togglePlayPause.bind(this));
        document.getElementById('stopButton').addEventListener('click', this.stopAudio.bind(this));
        
        // FPS display toggle
        document.getElementById('showFps').addEventListener('change', () => {
            document.getElementById('fpsCounter').style.display = 
                document.getElementById('showFps').checked ? 'block' : 'none';
        });
        
        // Volume control
        document.getElementById('volume').addEventListener('input', this.updateVolume.bind(this));
        
        // Scrubbing
        document.getElementById('progress-container').addEventListener('click', this.handleScrub.bind(this));
        
        // Toggle controls visibility
        document.getElementById('toggleButton').addEventListener('click', () => {
            document.getElementById('controls').classList.toggle('hidden');
            document.getElementById('toggleButton').classList.toggle('visible');
        });
        
        // Hide controls when clicking on canvas
        this.canvas.addEventListener('click', () => {
            if (!document.getElementById('controls').classList.contains('hidden')) {
                document.getElementById('controls').classList.add('hidden');
                document.getElementById('toggleButton').classList.add('visible');
            }
        });
        
        // Connect all parameter change handlers
        this.initParameterHandlers();
    }
    
    /**
     * Initialize parameter handlers
     */
    initParameterHandlers() {
        // Connect slider UI elements to their value displays
        const sliders = [
            'minSamplesPerPeriod', 'minPeriodsInBuffer', 'minFrequency', 'maxFrequency',
            'numFilters', 'percentOverlap', 'filterOrder', 'analysisInterval',
            'lineWidth', 'fadeFrames', 'amplitudeScale'
        ];
        
        sliders.forEach(sliderId => {
            const slider = document.getElementById(sliderId);
            const valueDisplay = document.getElementById(`${sliderId}Value`);
            
            slider.addEventListener('input', () => {
                // Update value display
                let displayValue = slider.value;
                
                // Add units to some values
                if (sliderId === 'percentOverlap') {
                    displayValue += '%';
                } else if (sliderId === 'minFrequency' || sliderId === 'maxFrequency') {
                    displayValue += ' Hz';
                }
                
                valueDisplay.textContent = displayValue;
                
                // Special handling for minSamplesPerPeriod - affects maxFrequency limit
                if (sliderId === 'minSamplesPerPeriod' && this.audioContext) {
                    const minSamplesPerPeriod = parseInt(slider.value);
                    const theoreticalMaxFreq = Math.floor(this.audioContext.sampleRate / minSamplesPerPeriod);
                    
                    // Update max value of the frequency slider
                    const maxFrequencySlider = document.getElementById('maxFrequency');
                    maxFrequencySlider.max = Math.min(theoreticalMaxFreq, 20000); // Cap at 20kHz
                    
                    // If current value exceeds new max, adjust it
                    if (parseInt(maxFrequencySlider.value) > maxFrequencySlider.max) {
                        maxFrequencySlider.value = maxFrequencySlider.max;
                        document.getElementById('maxFrequencyValue').textContent = maxFrequencySlider.value + ' Hz';
                    }
                    
                    // Update min freq slider's max to match max freq slider's value
                    const minFrequencySlider = document.getElementById('minFrequency');
                    minFrequencySlider.max = maxFrequencySlider.value;
                }
                
                // Update slider ranges to keep them in sync
                if (sliderId === 'minFrequency') {
                    const minFreq = parseInt(slider.value);
                    const maxFrequencySlider = document.getElementById('maxFrequency');
                    
                    // Set min value of max slider to current min frequency
                    maxFrequencySlider.min = minFreq;
                    
                    // If max frequency is now less than min frequency, update it
                    if (parseInt(maxFrequencySlider.value) < minFreq) {
                        maxFrequencySlider.value = minFreq;
                        document.getElementById('maxFrequencyValue').textContent = minFreq + ' Hz';
                    }
                }
                
                if (sliderId === 'maxFrequency') {
                    const maxFreq = parseInt(slider.value);
                    const minFrequencySlider = document.getElementById('minFrequency');
                    
                    // Set max value of min slider to current max frequency
                    minFrequencySlider.max = maxFreq;
                    
                    // If min frequency is now greater than max frequency, update it
                    if (parseInt(minFrequencySlider.value) > maxFreq) {
                        minFrequencySlider.value = maxFreq;
                        document.getElementById('minFrequencyValue').textContent = maxFreq + ' Hz';
                    }
                }
                
                // Update processor parameters if applicable
                if (['numFilters', 'percentOverlap', 
                     'filterOrder', 'useLowPassFilter', 'analysisInterval',
                     'minFrequency', 'maxFrequency', 'minSamplesPerPeriod'].includes(sliderId)) {
                    this.updateProcessorParameters();
                }
            });
        });
        
        // Special handling for threshold slider
        const thresholdSlider = document.getElementById('threshold');
        thresholdSlider.addEventListener('input', () => {
            this.updateThreshold();
        });
        
        // Connect checkboxes
        const checkboxes = ['showLabels', 'fadeEffect', 'showFps', 'useLowPassFilter'];
        
        checkboxes.forEach(checkboxId => {
            document.getElementById(checkboxId).addEventListener('change', () => {
                if (checkboxId === 'useLowPassFilter') {
                    this.updateProcessorParameters();
                }
            });
        });
    }
    
    /**
     * Initialize the UI
     */
    initUI() {
        // Set up initial values for all controls
        document.getElementById('minSamplesPerPeriodValue').textContent = 
            document.getElementById('minSamplesPerPeriod').value;
            
        document.getElementById('minPeriodsInBufferValue').textContent = 
            document.getElementById('minPeriodsInBuffer').value;
            
        document.getElementById('minFrequencyValue').textContent = 
            document.getElementById('minFrequency').value + ' Hz';
            
        document.getElementById('maxFrequencyValue').textContent = 
            document.getElementById('maxFrequency').value + ' Hz';
            
        document.getElementById('numFiltersValue').textContent = 
            document.getElementById('numFilters').value;
            
        document.getElementById('percentOverlapValue').textContent = 
            document.getElementById('percentOverlap').value + '%';
            
        document.getElementById('filterOrderValue').textContent = 
            document.getElementById('filterOrder').value;
            
        document.getElementById('analysisIntervalValue').textContent = 
            document.getElementById('analysisInterval').value;
            
        document.getElementById('lineWidthValue').textContent = 
            document.getElementById('lineWidth').value;
            
        document.getElementById('fadeFramesValue').textContent = 
            document.getElementById('fadeFrames').value;
            
        document.getElementById('amplitudeScaleValue').textContent = 
            document.getElementById('amplitudeScale').value;
            
        // Initialize threshold display
        const threshold = parseFloat(document.getElementById('threshold').value);
        document.getElementById('thresholdValue').textContent = threshold.toFixed(2);
    }
    
    /**
     * Resize the canvas to fill the window
     */
    resizeCanvas() {
        this.canvas.width = window.innerWidth;
        this.canvas.height = window.innerHeight;
    }
    
    /**
     * Load an audio file
     */
    async loadAudioFile(event) {
        const file = event.target.files[0];
        if (!file) return;
        
        try {
            // Update file name display
            document.getElementById('fileName').textContent = file.name;
            
            // Ensure audio context is initialized
            if (!this.audioContext) {
                await this.initAudio();
            }
            
            // Load and decode the file
            const arrayBuffer = await this.readFileAsArrayBuffer(file);
            this.audioBuffer = await this.audioContext.decodeAudioData(arrayBuffer);
            
            // Set duration and display total time
            this.duration = this.audioBuffer.duration;
            document.getElementById('total-time').textContent = this.formatTime(this.duration);
            
            // Enable play button
            document.getElementById('playPauseButton').disabled = false;
            document.getElementById('stopButton').disabled = false;
            
            // Reset playback position
            this.pausedAt = 0;
            document.getElementById('progress-bar').style.width = '0%';
            document.getElementById('current-time').textContent = '0:00';
            
            console.log('Audio file loaded:', file.name);
        } catch (error) {
            console.error('Error loading audio file:', error);
            alert('Error loading audio file: ' + error.message);
        }
    }
    
    /**
     * Read a file as ArrayBuffer
     */
    readFileAsArrayBuffer(file) {
        return new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = (e) => resolve(e.target.result);
            reader.onerror = (e) => reject(e.target.error);
            reader.readAsArrayBuffer(file);
        });
    }
    
    /**
     * Toggle play/pause
     */
    togglePlayPause() {
        if (this.isPlaying) {
            this.pauseAudio();
        } else {
            this.playAudio();
        }
    }
    
    /**
     * Play audio
     */
    playAudio() {
        if (!this.audioBuffer || this.isPlaying) return;
        
        try {
            // Create a new source node
            this.source = this.audioContext.createBufferSource();
            this.source.buffer = this.audioBuffer;
            
            // Connect the source to both the processor and the gain node directly
            // This ensures the audio is both analyzed and heard
            
            // Route 1: For analysis
            this.source.connect(this.audioWorkletNode);
            
            // Route 2: For audio output - connect directly to gain node
            this.source.connect(this.gainNode);
            
            // The gain node is already connected to the destination
            
            // Handle start position
            if (this.pausedAt < this.duration) {
                this.source.start(0, this.pausedAt);
                this.startTime = this.audioContext.currentTime;
            } else {
                // Start from beginning if at end
                this.source.start(0);
                this.startTime = this.audioContext.currentTime;
                this.pausedAt = 0;
            }
            
            // Handle playback end
            this.source.onended = () => {
                if (this.pausedAt >= this.duration) {
                    this.isPlaying = false;
                    document.getElementById('playPauseButton').textContent = 'Play';
                    this.pausedAt = 0;
                    this.stopAudio();
                }
            };
            
            // Update UI
            this.isPlaying = true;
            document.getElementById('playPauseButton').textContent = 'Pause';
            
            // Start visualization and progress updates
            this.waveformHistory = [];
            this.startAnimation();
            this.updateProgress();
        } catch (error) {
            console.error('Error playing audio:', error);
            alert('Error playing audio: ' + error.message);
        }
    }
    
    /**
     * Pause audio
     */
    pauseAudio() {
        if (!this.isPlaying || !this.source) return;
        
        try {
            this.source.stop();
            this.pausedAt += this.audioContext.currentTime - this.startTime;
            this.source = null;
            
            // Update UI
            this.isPlaying = false;
            document.getElementById('playPauseButton').textContent = 'Play';
            
            // Stop animation
            cancelAnimationFrame(this.animationId);
        } catch (error) {
            console.error('Error pausing audio:', error);
        }
    }
    
    /**
     * Stop audio playback
     */
    stopAudio() {
        if (this.source) {
            try {
                this.source.stop();
                this.source = null;
            } catch (error) {
                console.error('Error stopping source:', error);
            }
        }
        
        // Reset state
        this.isPlaying = false;
        document.getElementById('playPauseButton').textContent = 'Play';
        cancelAnimationFrame(this.animationId);
        
        // Reset time and progress
        this.pausedAt = 0;
        document.getElementById('progress-bar').style.width = '0%';
        document.getElementById('current-time').textContent = '0:00';
        
        // Reset processor
        if (this.audioWorkletNode) {
            this.audioWorkletNode.port.postMessage({ type: 'reset' });
        }
        
        // Clear canvas
        this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
        this.waveformHistory = [];
        this.activeFrequencies = [];
        document.getElementById('activeFreqs').textContent = 'Active frequencies: 0';
    }
    
    /**
     * Handle scrubbing through the audio
     */
    handleScrub(e) {
        if (!this.audioBuffer) return;
        
        const rect = document.getElementById('progress-container').getBoundingClientRect();
        const position = (e.clientX - rect.left) / rect.width;
        const newTime = position * this.duration;
        
        // Update time display and progress
        this.pausedAt = newTime;
        document.getElementById('progress-bar').style.width = (position * 100) + '%';
        document.getElementById('current-time').textContent = this.formatTime(newTime);
        
        // If playing, restart from new position
        if (this.isPlaying) {
            if (this.source) {
                this.source.stop();
                this.source = null;
            }
            this.playAudio();
        }
    }
    
    /**
     * Update volume based on slider
     */
    updateVolume() {
        const volumeValue = document.getElementById('volume').value;
        
        if (this.gainNode) {
            this.gainNode.gain.value = volumeValue / 100;
        }
        
        // Update volume icon
        const volumeIcon = document.getElementById('volume-icon');
        if (volumeValue === '0') {
            volumeIcon.textContent = '🔇';
        } else if (volumeValue < 50) {
            volumeIcon.textContent = '🔉';
        } else {
            volumeIcon.textContent = '🔊';
        }
    }
    
    /**
     * Update progress bar and time display
     */
    updateProgress() {
        if (!this.isPlaying || !this.audioBuffer) return;
        
        const currentTime = this.audioContext.currentTime - this.startTime + this.pausedAt;
        
        if (currentTime <= this.duration) {
            document.getElementById('progress-bar').style.width = (currentTime / this.duration * 100) + '%';
            document.getElementById('current-time').textContent = this.formatTime(currentTime);
            requestAnimationFrame(this.updateProgress.bind(this));
        } else {
            // Reached the end of audio
            this.stopAudio();
        }
    }
    
    /**
     * Format time in minutes:seconds
     */
    formatTime(seconds) {
        const mins = Math.floor(seconds / 60);
        const secs = Math.floor(seconds % 60);
        return `${mins}:${secs.toString().padStart(2, '0')}`;
    }
    
    /**
     * Start animation loop
     */
    startAnimation() {
        const animate = (now) => {
            const elapsed = now - this.lastFrameTime;
            
            // Update FPS every 500ms
            if (elapsed > 500) {
                this.fps = Math.round(this.frameCount / (elapsed / 1000));
                this.frameCount = 0;
                this.lastFrameTime = now;
                
                if (document.getElementById('showFps').checked) {
                    document.getElementById('fpsCounter').textContent = `FPS: ${this.fps}`;
                }
            }
            
            // Clear canvas
            if (document.getElementById('fadeEffect').checked) {
                // Semi-transparent overlay for fade effect
                this.ctx.fillStyle = 'rgba(0, 0, 0, 0.2)';
                this.ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);
            } else {
                // Complete clear
                this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
            }
            
            // Store waveforms if using fade effect
            if (document.getElementById('fadeEffect').checked) {
                // Create a deep copy of the frequencies including their waveforms
                const frequenciesCopy = this.activeFrequencies.map(freq => {
                    // Create a proper deep copy including the waveform if present
                    const freqCopy = { ...freq };
                    
                    // Deep copy the waveform array if it exists
                    if (freq.waveform && freq.waveform.length) {
                        freqCopy.waveform = new Float32Array(freq.waveform);
                    }
                    
                    return freqCopy;
                });
                
                this.waveformHistory.unshift({
                    frequencies: frequenciesCopy
                });
                
                // Limit history based on fade frames setting
                const maxFrames = parseInt(document.getElementById('fadeFrames').value);
                if (this.waveformHistory.length > maxFrames) {
                    this.waveformHistory = this.waveformHistory.slice(0, maxFrames);
                }
            }
            
            // Draw waveforms
            if (document.getElementById('fadeEffect').checked) {
                // Draw history with fade effect
                for (let i = 0; i < this.waveformHistory.length; i++) {
                    const opacity = 1 - (i / this.waveformHistory.length);
                    this.drawFrequencies(this.waveformHistory[i].frequencies, opacity);
                }
            } else {
                // Draw current frame only
                this.drawFrequencies(this.activeFrequencies, 1.0);
            }
            
            this.frameCount++;
            this.animationId = requestAnimationFrame(animate);
        };
        
        // Start the animation loop
        this.animationId = requestAnimationFrame(animate);
    }
    
    /**
     * Draw detected frequencies on the canvas
     */
    drawFrequencies(frequencies, opacity) {
        if (!frequencies || frequencies.length === 0) return;
        
        const lineWidth = parseFloat(document.getElementById('lineWidth').value);
        const amplitudeScale = parseFloat(document.getElementById('amplitudeScale').value);
        const showLabels = document.getElementById('showLabels').checked;
        
        // Get the current frequency range from UI sliders
        const minFreq = parseInt(document.getElementById('minFrequency').value);
        const maxFreq = parseInt(document.getElementById('maxFrequency').value);
        
        // Set global alpha for this frame
        this.ctx.globalAlpha = opacity;
        
        // Sort frequencies from lowest to highest
        const sortedFreqs = [...frequencies].sort((a, b) => a.frequency - b.frequency);
        
        // Filter frequencies that are outside our display range
        const visibleFreqs = sortedFreqs.filter(freq => 
            freq.frequency >= minFreq && freq.frequency <= maxFreq);
        
        // Draw each frequency
        for (const freq of visibleFreqs) {
            // Convert to note
            const note = this.frequencyToNote(freq.frequency);
            
            // Calculate vertical position (logarithmic scale)
            const minLog = Math.log2(minFreq);
            const maxLog = Math.log2(maxFreq);
            const freqLog = Math.log2(freq.frequency);
            
            // Position as percentage of height (bottom to top)
            const position = (freqLog - minLog) / (maxLog - minLog);
            const y = this.canvas.height - (position * (this.canvas.height - 100)) - 50;
            
            // Get color based on note
            this.ctx.strokeStyle = this.getNoteColor(note.noteIndex, 60);
            this.ctx.lineWidth = lineWidth;
            
            // Draw waveform if available
            if (freq.waveform && freq.waveform.length > 0) {
                // Scale the waveform
                const xScale = Math.min(this.canvas.width * 0.8 / freq.waveform.length, 5);
                const startX = (this.canvas.width - freq.waveform.length * xScale) / 2;
                
                this.ctx.beginPath();
                
                for (let i = 0; i < freq.waveform.length; i++) {
                    const x = startX + i * xScale;
                    const amplitude = freq.waveform[i] * amplitudeScale;
                    const sampleY = y + amplitude;
                    
                    if (i === 0) {
                        this.ctx.moveTo(x, sampleY);
                    } else {
                        this.ctx.lineTo(x, sampleY);
                    }
                }
                
                this.ctx.stroke();
            } else {
                // Fallback to simple line if no waveform
                const lineLength = amplitudeScale * Math.sqrt(freq.energy);
                const x = this.canvas.width / 2;
                
                this.ctx.beginPath();
                this.ctx.moveTo(x - lineLength / 2, y);
                this.ctx.lineTo(x + lineLength / 2, y);
                this.ctx.stroke();
            }
            
            // Draw labels if enabled
            if (showLabels) {
                this.ctx.fillStyle = this.ctx.strokeStyle;
                this.ctx.font = '14px Arial';
                
                // Format cents display
                let centsText = '';
                if (Math.abs(note.cents) > 5) {
                    centsText = ` ${note.cents > 0 ? '+' : ''}${note.cents}¢`;
                }
                
                const label = `${freq.frequency.toFixed(1)} Hz (${note.name}${note.octave}${centsText})`;
                this.ctx.fillText(label, 10, y + 5);
            }
        }
        
        // Reset alpha
        this.ctx.globalAlpha = 1.0;
    }
    
    /**
     * Convert frequency to musical note
     */
    frequencyToNote(frequency) {
        const A4 = 440; // Hz
        const semitones = 12 * Math.log2(frequency / A4);
        const roundedSemitones = Math.round(semitones);
        const cents = Math.round((semitones - roundedSemitones) * 100);
        
        const noteIndex = ((roundedSemitones % 12) + 12) % 12;
        const octave = Math.floor(roundedSemitones / 12) + 4;
        
        const noteNames = ['A', 'A#', 'B', 'C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#'];
        
        return {
            name: noteNames[noteIndex],
            octave: octave,
            cents: cents,
            noteIndex: noteIndex
        };
    }
    
    /**
     * Get color for musical note
     */
    getNoteColor(noteIndex, brightness = 60) {
        const hue = (noteIndex * 30) % 360;
        return `hsl(${hue}, 80%, ${brightness}%)`;
    }
}

// Initialize the application when the page loads
window.addEventListener('DOMContentLoaded', () => {
    const analyzer = new FrequencyAnalyzer();
    
    // Store the analyzer instance on the window for debugging
    window.analyzer = analyzer;
});
