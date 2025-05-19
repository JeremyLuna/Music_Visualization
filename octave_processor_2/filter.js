/**
 * filter.js - Audio filter implementations for the Music Frequency Visualizer
 * 
 * This module provides digital filter implementations used for frequency analysis:
 * - BiquadFilter: A single second-order (biquad) filter section
 * - ButterworthFilter: A 4th order Butterworth filter implemented as 2 cascaded biquad sections
 */

/**
 * Single biquad filter section implementing Direct Form II structure
 */
export class BiquadFilter {
    /**
     * Create a new biquad filter with the specified coefficients
     * 
     * @param {number} b0 - Feedforward coefficient
     * @param {number} b1 - Feedforward coefficient
     * @param {number} b2 - Feedforward coefficient
     * @param {number} a1 - Feedback coefficient
     * @param {number} a2 - Feedback coefficient
     */
    constructor(b0, b1, b2, a1, a2) {
        this.b0 = b0;
        this.b1 = b1;
        this.b2 = b2;
        this.a1 = a1;
        this.a2 = a2;
        this.z1 = 0; // First delay element
        this.z2 = 0; // Second delay element
    }
    
    /**
     * Process a single sample through the filter
     * 
     * @param {number} input - Input sample
     * @returns {number} - Filtered output sample
     */
    process(input) {
        // Direct Form II implementation
        const w = input - this.a1 * this.z1 - this.a2 * this.z2;
        const output = this.b0 * w + this.b1 * this.z1 + this.b2 * this.z2;
        
        // Update delay elements
        this.z2 = this.z1;
        this.z1 = w;
        
        return output;
    }
    
    /**
     * Reset the filter state
     */
    reset() {
        this.z1 = 0;
        this.z2 = 0;
    }
}

/**
 * Butterworth filter implementation with configurable type and order
 * Implemented as cascaded biquad sections
 */
export class ButterworthFilter {
    /**
     * Create a new Butterworth filter
     * 
     * @param {string} type - Filter type: 'lowpass', 'highpass', or 'bandpass'
     * @param {number} periodInSamples - Period of the target frequency in samples
     * @param {number} Q - Base Q factor controlling resonance/bandwidth
     * @param {number} [order=4] - Filter order (must be even)
     */
    constructor(type, periodInSamples, Q, order = 4) {
        this.type = type;
        this.periodInSamples = periodInSamples;
        this.Q = Q;
        
        // Ensure order is even
        if (order % 2 !== 0) {
            throw new Error('Filter order must be even');
        }
        
        this.order = order;
        this.stages = [];
        
        this._calculateCoefficients();
    }
    
    /**
     * Calculate filter coefficients based on current parameters
     * Creates the necessary biquad sections for the specified filter order
     * @private
     */
    _calculateCoefficients() {
        // Convert period to normalized frequency (0 to 0.5, where 0.5 is Nyquist)
        const normalizedFreq = 1.0 / this.periodInSamples;
        const w0 = 2 * Math.PI * normalizedFreq;
        const cosw0 = Math.cos(w0);
        
        // Clear existing stages
        this.stages = [];
        
        // Number of biquad sections needed (each implements a 2nd order filter)
        const numSections = this.order / 2;
        
        // Calculate Butterworth poles for the specified order
        // For each section, we need to adjust the Q value
        for (let section = 0; section < numSections; section++) {
            // Calculate Q value for this section using Butterworth pole positions
            // For a normalized Butterworth filter, the poles are equally spaced around a circle
            const poleAngle = Math.PI * (2 * section + 1) / (2 * this.order);
            const poleReal = -Math.cos(poleAngle);
            const poleImag = Math.sin(poleAngle);
            
            // Calculate Q from pole position
            // Q = 1 / (2 * cos(pole_angle)) for lowpass/highpass
            // For bandpass, we use the base Q directly as it relates to bandwidth
            let sectionQ;
            if (this.type === 'bandpass') {
                // For bandpass, scale Q based on section
                // This is a simplified approach - more sophisticated filter design
                // would use more complex transformations
                sectionQ = this.Q * (1.0 + 0.1 * section);
            } else {
                // For lowpass/highpass, calculate Q from pole position
                sectionQ = 1.0 / (2.0 * Math.cos(poleAngle));
                
                // Scale by the user-specified Q
                sectionQ *= this.Q / 0.7071; // Normalize to standard Butterworth Q
            }
            
            // Calculate alpha for this section
            const alpha = Math.sin(w0) / (2 * sectionQ);
            
            // Calculate coefficients based on filter type
            let b0, b1, b2, a0, a1, a2;
            
            if (this.type === 'lowpass') {
                b0 = (1 - cosw0) / 2;
                b1 = 1 - cosw0;
                b2 = (1 - cosw0) / 2;
                a0 = 1 + alpha;
                a1 = -2 * cosw0;
                a2 = 1 - alpha;
            } else if (this.type === 'highpass') {
                b0 = (1 + cosw0) / 2;
                b1 = -(1 + cosw0);
                b2 = (1 + cosw0) / 2;
                a0 = 1 + alpha;
                a1 = -2 * cosw0;
                a2 = 1 - alpha;
            } else if (this.type === 'bandpass') {
                b0 = alpha;
                b1 = 0;
                b2 = -alpha;
                a0 = 1 + alpha;
                a1 = -2 * cosw0;
                a2 = 1 - alpha;
            } else {
                throw new Error(`Unsupported filter type: ${this.type}`);
            }
            
            // Normalize by a0
            b0 /= a0;
            b1 /= a0;
            b2 /= a0;
            a1 /= a0;
            a2 /= a0;
            
            // Create and add biquad section
            this.stages.push(new BiquadFilter(b0, b1, b2, a1, a2));
        }
    }
    
    /**
     * Process a single sample through the cascaded filter stages
     * 
     * @param {number} input - Input sample
     * @returns {number} - Filtered output sample
     */
    process(input) {
        let output = input;
        for (const stage of this.stages) {
            output = stage.process(output);
        }
        return output;
    }
    
    /**
     * Update the filter's target frequency by setting a new period
     * 
     * @param {number} periodInSamples - New period in samples
     */
    setPeriod(periodInSamples) {
        this.periodInSamples = periodInSamples;
        this._calculateCoefficients();
    }
    
    /**
     * Update the filter's order
     * 
     * @param {number} order - New filter order (must be even)
     */
    setOrder(order) {
        if (order % 2 !== 0) {
            throw new Error('Filter order must be even');
        }
        this.order = order;
        this._calculateCoefficients();
    }
    
    /**
     * Reset all filter stages to their initial state
     */
    reset() {
        for (const stage of this.stages) {
            stage.reset();
        }
    }
    
    /**
     * Process an entire buffer of samples
     * 
     * @param {Float32Array} inputBuffer - Input audio buffer
     * @returns {Float32Array} - Filtered output buffer
     */
    processBuffer(inputBuffer) {
        const outputBuffer = new Float32Array(inputBuffer.length);
        
        // Reset filter state
        this.reset();
        
        // Process each sample
        for (let i = 0; i < inputBuffer.length; i++) {
            outputBuffer[i] = this.process(inputBuffer[i]);
        }
        
        return outputBuffer;
    }
}

/**
 * A filter bank for frequency analysis using bandpass filters
 * Creates a bank of filters evenly distributed in a logarithmic scale
 */
export class FilterBank {
    /**
     * Create a new filter bank
     * 
     * @param {number} minSamplesPerPeriod - Minimum samples per period (highest frequency)
     * @param {number} maxSamplesPerPeriod - Maximum samples per period (lowest frequency)
     * @param {number} numFilters - Total number of filters to create across the range
     * @param {number} percentOverlap - Percentage of overlap between adjacent filters (0-100)
     * @param {number} [filterOrder=4] - Order of each filter (must be even)
     */
    constructor(minSamplesPerPeriod, maxSamplesPerPeriod, numFilters, percentOverlap, filterOrder = 4) {
        this.minSamplesPerPeriod = minSamplesPerPeriod;
        this.maxSamplesPerPeriod = maxSamplesPerPeriod;
        this.numFilters = numFilters;
        this.percentOverlap = percentOverlap;
        this.filterOrder = filterOrder;
        
        // Verify parameters
        if (minSamplesPerPeriod >= maxSamplesPerPeriod) {
            throw new Error('minSamplesPerPeriod must be less than maxSamplesPerPeriod');
        }
        
        if (numFilters < 2) {
            throw new Error('numFilters must be at least 2');
        }
        
        // Calculate Q from percentage overlap
        this.Q = this._calculateQFromOverlap(percentOverlap);
        
        this.filters = [];
        this.centerPeriods = [];
        
        this._createFilters();
    }
    
    /**
     * Calculate the Q factor based on the desired percentage overlap
     * 
     * @param {number} percentOverlap - Percentage of overlap between adjacent filters (0-100)
     * @returns {number} - Q value
     * @private
     */
    _calculateQFromOverlap(percentOverlap) {
        // Convert percentage to a fraction (0-1)
        const overlapFraction = Math.max(0, Math.min(99, percentOverlap)) / 100;
        
        // Calculate the ratio between adjacent filter center periods
        // For logarithmic spacing, this is a constant ratio
        const ratio = Math.pow(this.maxSamplesPerPeriod / this.minSamplesPerPeriod, 1 / (this.numFilters - 1));
        
        // Calculate the bandwidth factor based on the overlap
        // For a given overlap percentage, the -3dB width should be such that
        // it provides the specified overlap with adjacent filters
        const overlapScaleFactor = 1 + overlapFraction;
        const bwFactor = (ratio - 1) * overlapScaleFactor;
        
        // Q = center_period / bandwidth_in_samples
        // Q = 1 / bwFactor
        const Q = 1 / bwFactor;
        
        return Q;
    }
    
    /**
     * Create the filter bank with logarithmically spaced bandpass filters
     * @private
     */
    _createFilters() {
        // Clear existing filters
        this.filters = [];
        this.centerPeriods = [];
        
        // Calculate the step ratio for logarithmic spacing
        const ratio = Math.pow(this.maxSamplesPerPeriod / this.minSamplesPerPeriod, 1 / (this.numFilters - 1));
        
        // Create the filters
        for (let i = 0; i < this.numFilters; i++) {
            // Calculate period in samples (logarithmic spacing)
            const periodInSamples = this.minSamplesPerPeriod * Math.pow(ratio, i);
            
            // Create bandpass filter
            this.filters.push(new ButterworthFilter('bandpass', periodInSamples, this.Q, this.filterOrder));
            this.centerPeriods.push(periodInSamples);
        }
    }
    
    /**
     * Process a buffer through all filters in the bank and calculate energy values
     * 
     * @param {Float32Array} buffer - Audio buffer to analyze
     * @returns {Float32Array} - Energy values for each filter
     */
    processBuffer(buffer) {
        const energies = new Float32Array(this.filters.length);
        
        // Reset all filters first
        for (const filter of this.filters) {
            filter.reset();
        }
        
        // Process buffer through each filter and calculate energy
        for (let i = 0; i < this.filters.length; i++) {
            // Apply filter to buffer
            const filtered = this.filters[i].processBuffer(buffer);
            
            // Calculate energy (sum of squared samples)
            let energy = 0;
            for (let j = 0; j < filtered.length; j++) {
                energy += filtered[j] * filtered[j];
            }
            
            // Normalize by buffer length
            energies[i] = energy / buffer.length;
        }
        
        return energies;
    }
    
    /**
     * Update the filter bank parameters and recreate filters
     * 
     * @param {number} [numFilters] - New number of filters
     * @param {number} [percentOverlap] - New percentage overlap between filters
     * @param {number} [filterOrder] - New filter order (must be even)
     * @param {number} [minSamplesPerPeriod] - New minimum samples per period
     * @param {number} [maxSamplesPerPeriod] - New maximum samples per period
     */
    updateParameters(numFilters, percentOverlap, filterOrder, minSamplesPerPeriod, maxSamplesPerPeriod) {
        let needsUpdate = false;
        
        if (numFilters !== undefined && numFilters !== this.numFilters) {
            if (numFilters < 2) {
                throw new Error('numFilters must be at least 2');
            }
            this.numFilters = numFilters;
            needsUpdate = true;
        }
        
        if (percentOverlap !== undefined && percentOverlap !== this.percentOverlap) {
            this.percentOverlap = percentOverlap;
            this.Q = this._calculateQFromOverlap(percentOverlap);
            needsUpdate = true;
        }
        
        if (filterOrder !== undefined && filterOrder !== this.filterOrder) {
            this.filterOrder = filterOrder;
            needsUpdate = true;
        }
        
        if (minSamplesPerPeriod !== undefined && minSamplesPerPeriod !== this.minSamplesPerPeriod) {
            this.minSamplesPerPeriod = minSamplesPerPeriod;
            needsUpdate = true;
        }
        
        if (maxSamplesPerPeriod !== undefined && maxSamplesPerPeriod !== this.maxSamplesPerPeriod) {
            this.maxSamplesPerPeriod = maxSamplesPerPeriod;
            needsUpdate = true;
        }
        
        // Verify parameters after all updates
        if (this.minSamplesPerPeriod >= this.maxSamplesPerPeriod) {
            throw new Error('minSamplesPerPeriod must be less than maxSamplesPerPeriod');
        }
        
        if (needsUpdate) {
            this._createFilters();
        }
    }
    
    /**
     * Find peaks in the energy spectrum
     * 
     * @param {Float32Array} energies - Energy values from processBuffer
     * @param {number} threshold - Absolute threshold value for peak detection (not a percentage)
     * @param {number} [sampleRate] - Optional sample rate to convert periods to frequencies in results
     * @returns {Array} - Array of peak objects with index, period, and energy (and frequency if sampleRate provided)
     */
    findPeaks(energies, threshold, sampleRate) {
        const peaks = [];
        
        // Find the maximum energy for informational purposes
        const maxEnergy = Math.max(...energies);
        
        // Use threshold directly as the minimum energy required for a peak
        const absoluteThreshold = threshold;
        
        // Optional: Log the threshold and max energy for debugging
        // console.log(`Threshold: ${absoluteThreshold}, Max Energy: ${maxEnergy}, Ratio: ${absoluteThreshold/maxEnergy}`);
        
        // Find peaks (higher than both neighbors and above threshold)
        for (let i = 1; i < energies.length - 1; i++) {
            if (energies[i] > absoluteThreshold &&
                energies[i] > energies[i - 1] &&
                energies[i] > energies[i + 1]) {
                
                const period = this.centerPeriods[i];
                const peak = {
                    index: i,
                    period: period,
                    energy: energies[i]
                };
                
                // Add frequency only if sample rate is provided
                if (sampleRate !== undefined) {
                    peak.frequency = sampleRate / period;
                }
                
                peaks.push(peak);
            }
        }
        
        return peaks;
    }
    
    /**
     * Get the filter at a specific index
     * 
     * @param {number} index - Filter index
     * @returns {ButterworthFilter} - The filter at the specified index
     */
    getFilter(index) {
        if (index < 0 || index >= this.filters.length) {
            throw new Error('Filter index out of range');
        }
        return this.filters[index];
    }
    
    /**
     * Get the number of filters in the bank
     * 
     * @returns {number} - Number of filters
     */
    getFilterCount() {
        return this.filters.length;
    }
    
    /**
     * Get the current Q value
     * 
     * @returns {number} - Q value
     */
    getQ() {
        return this.Q;
    }
    
    /**
     * Get the center periods of all filters
     * 
     * @returns {Array<number>} - Array of center periods in samples
     */
    getCenterPeriods() {
        return [...this.centerPeriods];
    }
    
    /**
     * Convert center periods to frequencies
     * 
     * @param {number} sampleRate - Sample rate in Hz
     * @returns {Array<number>} - Array of center frequencies in Hz
     */
    getCenterFrequencies(sampleRate) {
        return this.centerPeriods.map(period => sampleRate / period);
    }
    
    /**
     * Get the bandwidth of filters at a specific period
     * 
     * @param {number} period - Period in samples
     * @returns {number} - Bandwidth in samples at the specified period
     */
    getBandwidth(period) {
        return period / this.Q;
    }
}
