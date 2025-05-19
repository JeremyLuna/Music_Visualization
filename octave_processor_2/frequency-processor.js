/**
 * frequency-processor.js - AudioWorklet processor for frequency analysis
 * This module contains the processor and OctaveBufferManager
 */

// Import the required modules
import { CircularBuffer } from './CircularBuffer.js';
import { FilterBank, ButterworthFilter } from './filter.js';

/**
 * OctaveBufferManager - Manages multiple buffers of decreasing sample rates
 * for efficient multi-resolution analysis
 * 
 * NOTE ON TERMINOLOGY:
 * In this implementation, "octave" numbering is based on downsampling factors, NOT musical octaves:
 * - Octave 0: Original sample rate (highest), analyzes highest frequencies
 * - Octave 1: Half sample rate, analyzes mid-range frequencies
 * - Octave 2, 3, etc.: Progressively lower sample rates for lower frequencies
 * 
 * This is opposite to musical convention where higher octave numbers represent higher frequencies.
 * The implementation is chosen for computational efficiency, starting with the base case (full rate)
 * and creating additional downsampled buffers only as needed for lower frequencies.
 */
class OctaveBufferManager {
    /**
     * Create a new OctaveBufferManager
     * 
     * @param {number} sampleRate - Original sample rate
     * @param {number} minSamplesPerPeriod - Minimum samples per period
     * @param {number} maxSamplesPerPeriod - Maximum samples per period
     * @param {number} minPeriodsInBuffer - Minimum number of periods to store in each buffer
     * @param {number} numFilters - Number of filters in the filter bank
     * @param {number} percentOverlap - Percentage of overlap between filters
     * @param {number} filterOrder - Order of the filters to use
     */
    constructor(sampleRate, minSamplesPerPeriod, maxSamplesPerPeriod, minPeriodsInBuffer, 
                numFilters, percentOverlap, filterOrder) {
        this.sampleRate = sampleRate;
        this.minSamplesPerPeriod = minSamplesPerPeriod;
        this.maxSamplesPerPeriod = maxSamplesPerPeriod;
        this.minPeriodsInBuffer = minPeriodsInBuffer;
        this.percentOverlap = percentOverlap;
        
        // Calculate buffer sizes
        this.bufferSize = 2 * minSamplesPerPeriod * minPeriodsInBuffer;
        
        // Determine how many octaves we need
        this.numOctaves = this._calculateNumOctaves();
        
        // Create octave-based buffers and sample rates
        this.octaveBuffers = [];
        this.octaveSampleRates = [];
        
        // Initialize buffers
        this._createBuffers();
        
        // Create a single filter bank that will be used for all octaves
        // The filter bank works with periods, so it can be reused across different sample rates
        this.filterBank = new FilterBank(
            minSamplesPerPeriod,
            maxSamplesPerPeriod,
            numFilters,
            percentOverlap,
            filterOrder
        );
        
        // Create decimation counters for downsampling
        this.decimationCounters = new Array(this.numOctaves).fill(0);
        
        // Create downsampling filters (lowpass at Nyquist/2)
        this.lowpassFilters = this._createLowpassFilters(filterOrder);
        
        // Initialize filter states
        this.filterStates = new Array(this.numOctaves - 1).fill(null)
            .map(() => new Float32Array(4).fill(0)); // For a 4th order filter
    }
    
    /**
     * Calculate the number of octaves needed based on period range
     * 
     * @returns {number} - Number of octaves needed
     * @private
     */
    _calculateNumOctaves() {
        // Each octave halves the sample rate, doubling the period in samples
        // So we need to calculate how many halvings it takes to go from 
        // minSamplesPerPeriod to maxSamplesPerPeriod
        return Math.max(1, Math.ceil(Math.log2(this.maxSamplesPerPeriod / this.minSamplesPerPeriod)) + 1);
    }
    
    /**
     * Create circular buffers for each octave
     * 
     * @private
     */
    _createBuffers() {
        // In our implementation, octaves are numbered by downsampling factor:
        // - Octave 0: Original sample rate (highest resolution, highest frequencies)
        // - Octave 1: Half sample rate 
        // - Octave 2+: Progressively lower sample rates
        // This is opposite to musical convention where higher octaves = higher frequencies
        
        for (let octave = 0; octave < this.numOctaves; octave++) {
            // Calculate the effective sample rate for this octave
            const octaveSampleRate = this.sampleRate / Math.pow(2, octave);
            this.octaveSampleRates.push(octaveSampleRate);
            
            // Create circular buffer for this octave (same size for all octaves)
            this.octaveBuffers.push(new CircularBuffer(this.bufferSize));
        }
    }
    
    /**
     * Create lowpass filters for downsampling
     * 
     * @param {number} filterOrder - Order of the filters to use
     * @returns {Array} - Array of filter coefficient arrays
     * @private
     */
    _createLowpassFilters(filterOrder) {
        const filters = [];
        
        // We need one less filter than the number of octaves (no filter for the first octave)
        for (let octave = 1; octave < this.numOctaves; octave++) {
            // Simple 4th order Butterworth lowpass filter coefficients
            // These are fixed coefficients for a cutoff at ~0.4 * Nyquist
            // This is a simplification - in a complete implementation we'd calculate these dynamically
            // based on the specific octave and sample rate
            
            // b0, b1, b2, a1, a2 (normalized, a0 = 1)
            // The first stage:
            const stage1 = [0.0675, 0.1349, 0.0675, -1.1430, 0.4128];
            
            // The second stage:
            const stage2 = [0.0675, 0.1349, 0.0675, -1.0373, 0.3071];
            
            // Store both stages as a single array for simplicity
            filters.push([...stage1, ...stage2]);
        }
        
        return filters;
    }
    
    /**
     * Process a block of values and store in appropriate octave buffers
     * 
     * @param {Float32Array} inputBlock - Block of values to process
     * @param {boolean} useLowPassFilter - Whether to use lowpass filtering for downsampling
     */
    processBlock(inputBlock, useLowPassFilter = true) {
        // Process the first octave directly (original sample rate)
        for (let i = 0; i < inputBlock.length; i++) {
            this.octaveBuffers[0].write(inputBlock[i]);
        }
        
        // For each additional octave, downsample and apply lowpass filtering
        for (let octave = 1; octave < this.numOctaves; octave++) {
            const filterCoeffs = this.lowpassFilters[octave - 1];
            const filterState = this.filterStates[octave - 1];
            const decimationFactor = Math.pow(2, octave);
            
            // Process each value
            for (let i = 0; i < inputBlock.length; i++) {
                let value = inputBlock[i];
                
                // Apply lowpass filter if enabled
                if (useLowPassFilter) {
                    // First stage filtering
                    const b0 = filterCoeffs[0], b1 = filterCoeffs[1], b2 = filterCoeffs[2];
                    const a1 = filterCoeffs[3], a2 = filterCoeffs[4];
                    
                    // Direct Form II implementation
                    const w1 = value - a1 * filterState[0] - a2 * filterState[1];
                    value = b0 * w1 + b1 * filterState[0] + b2 * filterState[1];
                    
                    filterState[1] = filterState[0];
                    filterState[0] = w1;
                    
                    // Second stage filtering
                    const b0_2 = filterCoeffs[5], b1_2 = filterCoeffs[6], b2_2 = filterCoeffs[7];
                    const a1_2 = filterCoeffs[8], a2_2 = filterCoeffs[9];
                    
                    const w2 = value - a1_2 * filterState[2] - a2_2 * filterState[3];
                    value = b0_2 * w2 + b1_2 * filterState[2] + b2_2 * filterState[3];
                    
                    filterState[3] = filterState[2];
                    filterState[2] = w2;
                }
                
                // Increment decimation counter
                this.decimationCounters[octave]++;
                
                // Keep only every N-th value (where N is the decimation factor)
                if (this.decimationCounters[octave] % decimationFactor === 0) {
                    this.octaveBuffers[octave].write(value);
                }
            }
        }
    }
    
    /**
     * Get the buffer for a specific octave
     * 
     * @param {number} octave - Octave index
     * @returns {CircularBuffer} - The buffer for the specified octave
     */
    getOctaveBuffer(octave) {
        if (octave < 0 || octave >= this.numOctaves) {
            throw new Error('Invalid octave index');
        }
        return this.octaveBuffers[octave];
    }
    
    /**
     * Get the filter bank
     * 
     * @returns {FilterBank} - The filter bank
     */
    getFilterBank() {
        return this.filterBank;
    }
    
    /**
     * Get the sample rate for a specific octave
     * 
     * @param {number} octave - Octave index
     * @returns {number} - The sample rate for the specified octave
     */
    getOctaveSampleRate(octave) {
        if (octave < 0 || octave >= this.numOctaves) {
            throw new Error('Invalid octave index');
        }
        return this.octaveSampleRates[octave];
    }
    
    /**
     * Get all octave sample rates
     * 
     * @returns {Array<number>} - Array of sample rates for all octaves
     */
    getAllOctaveSampleRates() {
        return [...this.octaveSampleRates];
    }
    
    /**
     * Get the number of octaves
     * 
     * @returns {number} - The number of octaves
     */
    getNumOctaves() {
        return this.numOctaves;
    }
    
    /**
     * Reset all buffers and filter states
     */
    reset() {
        // Reset all buffers
        for (let octave = 0; octave < this.numOctaves; octave++) {
            this.octaveBuffers[octave].clear();
        }
        
        // Reset all decimation counters
        this.decimationCounters.fill(0);
        
        // Reset all filter states
        for (let i = 0; i < this.filterStates.length; i++) {
            this.filterStates[i].fill(0);
        }
    }
    
    /**
     * Update parameters of the filter bank
     * 
     * @param {number} [numFilters] - New number of filters
     * @param {number} [percentOverlap] - New percentage overlap
     * @param {number} [filterOrder] - New filter order
     */
    updateFilterBankParameters(numFilters, percentOverlap, filterOrder) {
        this.filterBank.updateParameters(
            numFilters,
            percentOverlap,
            filterOrder
        );
    }
    
    /**
     * Get buffer info for debugging
     * 
     * @returns {Object} - Object containing buffer information
     */
    getBufferInfo() {
        const info = [];
        
        // Get filter bank center periods
        const centerPeriods = this.filterBank.getCenterPeriods();
        
        for (let octave = 0; octave < this.numOctaves; octave++) {
            info.push({
                octave: octave,
                sampleRate: this.octaveSampleRates[octave],
                bufferSize: this.bufferSize,
                filled: this.octaveBuffers[octave].isFull(),
                writeIndex: this.octaveBuffers[octave].getWriteIndex(),
                // The same filter bank is used for all octaves, but we scale periods by octave
                minPeriod: centerPeriods[0] / Math.pow(2, octave),
                maxPeriod: centerPeriods[centerPeriods.length - 1] / Math.pow(2, octave)
            });
        }
        
        return {
            octaveInfo: info,
            filterBankInfo: {
                numFilters: this.filterBank.getFilterCount(),
                q: this.filterBank.getQ()
            }
        };
    }
}

/**
 * FrequencyAnalysisProcessor - AudioWorklet processor for frequency analysis
 */
class FrequencyAnalysisProcessor extends AudioWorkletProcessor {
    /**
     * Static getter for processor parameters
     */
    static get parameterDescriptors() {
        return [
            {
                name: 'minSamplesPerPeriod',
                defaultValue: 10,
                minValue: 4,
                maxValue: 100,
                automationRate: 'k-rate'
            },
            {
                name: 'maxSamplesPerPeriod',
                defaultValue: 1000,
                minValue: 20,
                maxValue: 10000,
                automationRate: 'k-rate'
            },
            {
                name: 'minPeriodsInBuffer',
                defaultValue: 10,
                minValue: 2,
                maxValue: 100,
                automationRate: 'k-rate'
            },
            {
                name: 'numFilters',
                defaultValue: 48,
                minValue: 12,
                maxValue: 200,
                automationRate: 'k-rate'
            },
            {
                name: 'percentOverlap',
                defaultValue: 50,
                minValue: 0,
                maxValue: 90,
                automationRate: 'k-rate'
            },
            {
                name: 'filterOrder',
                defaultValue: 4,
                minValue: 2,
                maxValue: 8,
                automationRate: 'k-rate'
            },
            {
                name: 'threshold',
                defaultValue: 85,
                minValue: 0,
                maxValue: 100,
                automationRate: 'k-rate'
            },
            {
                name: 'useLowPassFilter',
                defaultValue: 1,
                minValue: 0,
                maxValue: 1,
                automationRate: 'k-rate'
            },
            {
                name: 'analysisInterval',
                defaultValue: 2,
                minValue: 1,
                maxValue: 10,
                automationRate: 'k-rate'
            }
        ];
    }
    
    /**
     * Constructor for FrequencyAnalysisProcessor
     */
    constructor(options) {
        super();
        
        // Store options
        this.options = options.processorOptions || {};
        
        // Initialize state
        this.initialized = false;
        this.frameCounter = 0;
        this.bufferManager = null;
        this.lastAnalysisTime = 0;
        
        // Set up message port for communication with main thread
        this.port.onmessage = this.handleMessage.bind(this);
        
        // Set initialization flag once MessagePort is ready
        this.port.postMessage({ type: 'processorInitialized' });
    }
    
    /**
     * Handle messages from the main thread
     * 
     * @param {MessageEvent} event - Message event from main thread
     */
    handleMessage(event) {
        const message = event.data;
        
        switch (message.type) {
            case 'init':
                this.initializeBufferManager(message);
                break;
                
            case 'reset':
                if (this.bufferManager) {
                    this.bufferManager.reset();
                }
                this.frameCounter = 0;
                this.lastAnalysisTime = 0;
                this.port.postMessage({ type: 'resetComplete' });
                break;
                
            case 'updateParameters':
                this.updateParameters(message);
                break;
                
            default:
                console.error('Unknown message type:', message.type);
        }
    }
    
    /**
     * Initialize the buffer manager
     * 
     * @param {Object} message - Initialization message from main thread
     */
    initializeBufferManager(message) {
        try {
            // Create buffer manager
            this.bufferManager = new OctaveBufferManager(
                sampleRate,
                message.minSamplesPerPeriod,
                message.maxSamplesPerPeriod,
                message.minPeriodsInBuffer,
                message.numFilters,
                message.percentOverlap,
                message.filterOrder
            );
            
            this.initialized = true;
            this.frameCounter = 0;
            this.lastAnalysisTime = 0;
            
            // Notify main thread that initialization is complete
            this.port.postMessage({
                type: 'initComplete',
                numOctaves: this.bufferManager.getNumOctaves(),
                sampleRates: this.bufferManager.getAllOctaveSampleRates()
            });
        } catch (error) {
            this.port.postMessage({
                type: 'initError',
                error: error.message
            });
        }
    }
    
    /**
     * Update parameters of the buffer manager
     * 
     * @param {Object} message - Parameter update message from main thread
     */
    updateParameters(message) {
        // Check if buffer manager needs to be re-initialized
        const needsReinit = message.minSamplesPerPeriod !== undefined ||
                           message.maxSamplesPerPeriod !== undefined ||
                           message.minPeriodsInBuffer !== undefined;
        
        if (needsReinit) {
            // Get current values if not specified
            const minSamplesPerPeriod = message.minSamplesPerPeriod !== undefined ? 
                message.minSamplesPerPeriod : this.bufferManager.minSamplesPerPeriod;
            
            const maxSamplesPerPeriod = message.maxSamplesPerPeriod !== undefined ?
                message.maxSamplesPerPeriod : this.bufferManager.maxSamplesPerPeriod;
            
            const minPeriodsInBuffer = message.minPeriodsInBuffer !== undefined ?
                message.minPeriodsInBuffer : this.bufferManager.minPeriodsInBuffer;
            
            // Get filter parameters
            const numFilters = message.numFilters !== undefined ?
                message.numFilters : this.bufferManager.getFilterBank().getFilterCount();
            
            const percentOverlap = message.percentOverlap !== undefined ?
                message.percentOverlap : this.bufferManager.percentOverlap;
            
            const filterOrder = message.filterOrder !== undefined ?
                message.filterOrder : message.filterOrder;
            
            // Re-initialize buffer manager
            this.initializeBufferManager({
                minSamplesPerPeriod,
                maxSamplesPerPeriod,
                minPeriodsInBuffer,
                numFilters,
                percentOverlap,
                filterOrder
            });
        } else {
            // Update filter bank parameters if needed
            if (message.numFilters !== undefined || 
                message.percentOverlap !== undefined ||
                message.filterOrder !== undefined) {
                
                this.bufferManager.updateFilterBankParameters(
                    message.numFilters,
                    message.percentOverlap,
                    message.filterOrder
                );
                
                this.port.postMessage({
                    type: 'parametersUpdated'
                });
            }
        }
    }
    
    /**
     * Process each audio frame
     * 
     * @param {Array} inputs - Array of input channels
     * @param {Array} outputs - Array of output channels
     * @param {Object} parameters - AudioParam values
     * @returns {boolean} - Whether to continue processing
     */
    process(inputs, outputs, parameters) {
        // Check if we're initialized
        if (!this.initialized || !this.bufferManager) {
            return true;
        }
        
        // Get input data (mono - first channel of first input)
        const input = inputs[0];
        if (!input || !input.length) {
            return true;
        }
        
        const inputChannel = input[0];
        if (!inputChannel || !inputChannel.length) {
            return true;
        }
        
        // Extract parameters
        const useLowPassFilter = parameters.useLowPassFilter[0] > 0.5;
        const analysisInterval = Math.max(1, Math.round(parameters.analysisInterval[0]));
        const threshold = parameters.threshold[0];
        
        // Process input into octave buffers
        this.bufferManager.processBlock(inputChannel, useLowPassFilter);
        
        // Increment frame counter
        this.frameCounter++;
        
        // Only analyze on the specified interval
        if (this.frameCounter % analysisInterval === 0) {
            // Perform frequency analysis
            this.analyzeFrequencies(threshold);
        }
        
        // Always continue processing
        return true;
    }

    /**
     * Measure frequency precisely using autocorrelation
     * 
     * @param {Float32Array} buffer - Filtered buffer containing the frequency component
     * @param {number} estimatedFreq - Estimated frequency from filter bank
     * @param {number} sampleRate - Sample rate
     * @param {number} filterQ - Q factor of the filter (determines bandwidth)
     * @returns {number} - Precise frequency measurement
     */
    measureFrequencyPrecise(buffer, estimatedFreq, sampleRate, filterQ) {
        // Expected period from estimated frequency (same as peak.period)
        const expectedPeriod = Math.floor(sampleRate / estimatedFreq);
        
        // Safety check - if expectedPeriod is invalid or buffer is too small
        if (expectedPeriod <= 0 || expectedPeriod >= buffer.length / 3) {
            console.warn(`Cannot perform autocorrelation: expectedPeriod=${expectedPeriod}, buffer.length=${buffer.length}`);
            return estimatedFreq; // Return the estimated frequency if we can't improve it
        }
        
        // Instead of complex bandwidth calculations, use a simple percentage range
        // This avoids all the potential issues with Q factors
        const percentRange = Math.min(25, Math.max(5, 100 / filterQ)); // Cap between 5% and 25%
        
        // Calculate min and max lag values directly
        const rangeSamples = Math.ceil(expectedPeriod * percentRange / 100);
        const minLag = Math.max(1, expectedPeriod - rangeSamples);
        const maxLag = Math.min(Math.floor(buffer.length / 2), expectedPeriod + rangeSamples);
        
        // Log the search parameters for debugging
        // console.log(`Search range: ${minLag}-${maxLag}, Expected: ${expectedPeriod}, Range: ${rangeSamples} samples`);
        
        // Validate search range
        if (maxLag <= minLag) {
            console.warn(`Invalid search range: minLag=${minLag}, maxLag=${maxLag}`);
            return estimatedFreq;
        }
        
        // Create correlation array
        const corrRange = maxLag - minLag + 1;
        const corr = new Float32Array(corrRange);
        
        // Calculate autocorrelation for targeted lags
        for (let lagIndex = 0; lagIndex < corrRange; lagIndex++) {
            const lag = minLag + lagIndex;
            let sum = 0;
            
            // Use up to 3 periods for correlation
            const samplesToUse = Math.min(buffer.length - lag, 3 * expectedPeriod);
            
            // Check if we have enough samples
            if (samplesToUse < expectedPeriod / 2) {
                console.warn(`Not enough samples for correlation: ${samplesToUse} < ${expectedPeriod/2}`);
                return estimatedFreq;
            }
            
            for (let i = 0; i < samplesToUse; i++) {
                sum += buffer[i] * buffer[i + lag];
            }
            
            corr[lagIndex] = sum;
        }
        
        // Find peak
        let maxIndex = 0;
        for (let i = 1; i < corrRange; i++) {
            if (corr[i] > corr[maxIndex]) {
                maxIndex = i;
            }
        }
        
        // Actual lag value
        const actualLag = minLag + maxIndex;
        
        // Parabolic interpolation for sub-sample precision
        if (maxIndex > 0 && maxIndex < corrRange - 1) {
            const y1 = corr[maxIndex - 1];
            const y2 = corr[maxIndex];
            const y3 = corr[maxIndex + 1];
            
            // Avoid division by zero or unreliable interpolation
            const denominator = y1 - 2 * y2 + y3;
            if (Math.abs(denominator) > 1e-6 * Math.abs(y2)) {
                const offset = 0.5 * (y1 - y3) / denominator;
                
                // Check if interpolation result is reasonable
                if (Math.abs(offset) < 1.0) {
                    // Final precise frequency
                    return sampleRate / (actualLag + offset);
                }
            }
        }
        
        // Fall back to simple lag if interpolation fails
        return sampleRate / actualLag;
    }
    
    /**
     * Analyze frequencies in the buffers
     * 
     * @param {number} threshold - Absolute threshold for peak detection
     */
    analyzeFrequencies(threshold) {
        const now = currentTime;
        
        // Calculate time since last analysis
        const timeSinceLastAnalysis = now - this.lastAnalysisTime;
        this.lastAnalysisTime = now;
        
        // Results array
        const results = [];
        
        // Get the filter bank
        const filterBank = this.bufferManager.getFilterBank();
        
        // Process each octave
        // NOTE: In our implementation, higher octave indices correspond to lower frequencies,
        // which is the opposite of musical convention. Octave 0 has the highest sample rate
        // and is used for the highest frequencies.
        for (let octave = 0; octave < this.bufferManager.getNumOctaves(); octave++) {
            const buffer = this.bufferManager.getOctaveBuffer(octave);
            
            // Skip if buffer isn't full yet
            if (!buffer.isFull()) continue;
            
            const sampleRate = this.bufferManager.getOctaveSampleRate(octave);
            
            // Get buffer data
            const bufferData = buffer.getBuffer();
            
            // Process through filter bank
            const energies = filterBank.processBuffer(bufferData);
            
            // Find peaks - we need to adjust the periods by the octave factor
            // since we're using the same filter bank for different sample rates
            // Note: Passing threshold as an absolute value
            const peaks = filterBank.findPeaks(energies, threshold);
            
            // Process each peak to include harmonics
            for (const peak of peaks) {
                // Get the filter that detected this peak to access its Q value
                const filterIndex = peak.index;
                const filter = filterBank.getFilter(filterIndex);
                const filterQ = filter.Q;
                
                // Create a bandpass filter to isolate this frequency component
                const bandpassFilter = new ButterworthFilter(
                    'bandpass',
                    peak.period,
                    filterQ
                );
                
                // Apply the filter to isolate this frequency component
                const filteredSignal = new Float32Array(bufferData.length);
                bandpassFilter.reset();
                
                for (let i = 0; i < bufferData.length; i++) {
                    filteredSignal[i] = bandpassFilter.process(bufferData[i]);
                }
                
                // Estimate frequency from period
                const estimatedFreq = sampleRate / peak.period;
                
                // Measure precise frequency using autocorrelation
                const measuredFreq = this.measureFrequencyPrecise(
                    filteredSignal,
                    estimatedFreq,
                    sampleRate,
                    filterQ
                );
                
                // The actual period at the original sample rate is 
                // the filter bank's period multiplied by 2^octave
                // This is because each octave has a sample rate of originalRate/2^octave
                const scaledPeriod = sampleRate / measuredFreq * Math.pow(2, octave);
                
                // Process harmonics using the precise frequency
                const harmonicCount = Math.min(5, Math.floor((sampleRate / 2) / measuredFreq));
                const waveform = this.processHarmonics(bufferData, measuredFreq, sampleRate, harmonicCount);
                
                // Add peak and its harmonics to results
                results.push({
                    ...peak,
                    octave,
                    period: scaledPeriod,
                    frequency: measuredFreq,  // Use the precise measurement
                    energy: peak.energy,
                    waveform: waveform
                });
            }
        }
        
        // Sort results by frequency (ascending)
        results.sort((a, b) => a.frequency - b.frequency);
        
        // Send results to main thread
        this.port.postMessage({
            type: 'analysisResults',
            peaks: results,
            frameTime: timeSinceLastAnalysis
        });
    }
    
    /**
     * Process harmonics for a fundamental frequency
     * 
     * @param {Float32Array} buffer - Audio buffer
     * @param {number} frequency - Fundamental frequency
     * @param {number} sampleRate - Sample rate
     * @param {number} harmonicCount - Number of harmonics to include
     * @returns {Float32Array} - Waveform containing harmonics
     */
    processHarmonics(buffer, frequency, sampleRate, harmonicCount) {
        // Calculate period for the fundamental
        const fundamentalPeriod = Math.floor(sampleRate / frequency);
        
        // Generate buffer to hold the waveform
        const waveform = new Float32Array(fundamentalPeriod);
        
        // First, extract the phase-aligned fundamental frequency
        const fundamentalBandpass = new ButterworthFilter(
            'bandpass',
            fundamentalPeriod,
            8.7, // Q factor
            4     // Order
        );
        
        // Apply filter to get the fundamental component
        const filteredFundamental = new Float32Array(buffer.length);
        fundamentalBandpass.reset();
        
        for (let i = 0; i < buffer.length; i++) {
            filteredFundamental[i] = fundamentalBandpass.process(buffer[i]);
        }
        
        // Get phase-aligned window for the fundamental
        const { waveform: fundamentalWaveform, startIndex } = this.extractPhaseLockedWaveform(
            filteredFundamental,
            frequency,
            sampleRate
        );
        
        // Copy the fundamental into the result waveform
        for (let i = 0; i < fundamentalWaveform.length; i++) {
            waveform[i] = fundamentalWaveform[i];
        }
        
        // Process harmonics using the SAME TIME WINDOW as the fundamental
        for (let h = 1; h < harmonicCount; h++) { // Start from 1 since we already did the fundamental
            const harmonicFreq = frequency * (h + 1);
            
            // Skip if harmonic exceeds Nyquist frequency
            if (harmonicFreq > sampleRate / 2) continue;
            
            // Band-pass filter at harmonic frequency
            const harmonicPeriod = Math.floor(sampleRate / harmonicFreq);
            const bandpassFilter = new ButterworthFilter(
                'bandpass',
                harmonicPeriod,
                8.7, // Q factor
                4     // Order
            );
            
            // Apply filter
            const filteredHarmonic = new Float32Array(buffer.length);
            bandpassFilter.reset();
            
            for (let i = 0; i < buffer.length; i++) {
                filteredHarmonic[i] = bandpassFilter.process(buffer[i]);
            }
            
            // Extract the harmonic from the SAME time window as the fundamental
            // No phase-locking - just take the exact same time range
            for (let i = 0; i < fundamentalPeriod && (startIndex + i) < buffer.length; i++) {
                waveform[i] += filteredHarmonic[startIndex + i];
            }
        }
        
        return waveform;
    }
    
    /**
     * Extract a phase-locked waveform from a buffer
     * 
     * @param {Float32Array} buffer - Buffer to extract waveform from
     * @param {number} frequency - Target frequency
     * @param {number} sampleRate - Sample rate
     * @returns {Object} - Phase-locked waveform and its start index
     */
    extractPhaseLockedWaveform(buffer, frequency, sampleRate) {
        // Generate reference sine wave
        const period = Math.floor(sampleRate / frequency);
        const refSine = new Float32Array(period);
        
        for (let i = 0; i < period; i++) {
            refSine[i] = Math.sin(2 * Math.PI * i / period);
        }
        
        // Calculate cross-correlation to find best alignment
        let bestCorr = -Infinity;
        let bestOffset = 0;
        
        // Only search in last few periods
        const searchLength = Math.min(buffer.length, period * 5);
        const startIndex = buffer.length - searchLength;
        
        for (let offset = 0; offset < period; offset++) {
            let corr = 0;
            for (let i = 0; i < period && (startIndex + offset + i) < buffer.length; i++) {
                corr += buffer[startIndex + offset + i] * refSine[i];
            }
            
            if (corr > bestCorr) {
                bestCorr = corr;
                bestOffset = offset;
            }
        }
        
        // Extract aligned waveform
        const waveform = new Float32Array(period);
        const extractStart = startIndex + bestOffset;
        
        for (let i = 0; i < period && (extractStart + i) < buffer.length; i++) {
            waveform[i] = buffer[extractStart + i];
        }
        
        // Return both the waveform and the start index so we can extract harmonics from the same window
        return { waveform, startIndex: extractStart };
    }
}

// Register the processor
registerProcessor('frequency-analysis-processor', FrequencyAnalysisProcessor);
