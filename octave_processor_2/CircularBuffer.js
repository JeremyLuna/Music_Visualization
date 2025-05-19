/**
 * CircularBuffer.js - Efficient fixed-size buffer implementation for data processing
 * 
 * This module provides a circular buffer implementation that efficiently manages
 * a fixed-size buffer where new data overwrites the oldest data when the buffer is full.
 */

/**
 * CircularBuffer - A fixed-size buffer that wraps around when full
 */
export class CircularBuffer {
    /**
     * Create a new circular buffer
     * 
     * @param {number} size - The size of the buffer
     * @param {Float32Array} [initialData] - Optional initial data to populate the buffer
     */
    constructor(size, initialData) {
        if (size <= 0 || !Number.isInteger(size)) {
            throw new Error('Buffer size must be a positive integer');
        }
        
        this.buffer = new Float32Array(size);
        this.size = size;
        this.writeIndex = 0;
        this.filled = false;
        
        // Initialize with data if provided
        if (initialData) {
            this.writeAll(initialData);
        }
    }
    
    /**
     * Write a single value to the buffer
     * 
     * @param {number} value - The value to write
     * @returns {number} - The write index where the value was written
     */
    write(value) {
        const currentIndex = this.writeIndex;
        this.buffer[this.writeIndex] = value;
        this.writeIndex = (this.writeIndex + 1) % this.size;
        
        // Mark as filled once we've wrapped around
        if (this.writeIndex === 0) {
            this.filled = true;
        }
        
        return currentIndex;
    }
    
    /**
     * Write multiple values to the buffer
     * 
     * @param {Float32Array|Array<number>} values - Array of values to write
     * @returns {number} - The index of the last written value
     */
    writeAll(values) {
        let lastIndex = -1;
        for (let i = 0; i < values.length; i++) {
            lastIndex = this.write(values[i]);
        }
        return lastIndex;
    }
    
    /**
     * Read a single value from the buffer
     * 
     * @param {number} index - The index to read from (relative to writeIndex)
     * @returns {number} - The value at the specified index
     */
    read(index) {
        if (index < 0 || index >= this.size) {
            throw new Error('Index out of range');
        }
        
        // Calculate the actual index in the underlying buffer
        const actualIndex = (this.writeIndex - 1 - index + this.size) % this.size;
        return this.buffer[actualIndex];
    }
    
    /**
     * Check if the buffer has been completely filled at least once
     * 
     * @returns {boolean} - True if the buffer has been filled, false otherwise
     */
    isFull() {
        return this.filled;
    }
    
    /**
     * Get the current write index
     * 
     * @returns {number} - The current write index
     */
    getWriteIndex() {
        return this.writeIndex;
    }
    
    /**
     * Get the entire buffer as a Float32Array
     * 
     * @returns {Float32Array} - The underlying buffer array
     */
    getBuffer() {
        return this.buffer;
    }
    
    /**
     * Get the last N values in correct order (most recent value first)
     * 
     * @param {number} n - Number of values to retrieve
     * @returns {Float32Array} - The last N values
     */
    getLastN(n) {
        if (n > this.size) {
            throw new Error('Requested more values than buffer size');
        }
        
        const result = new Float32Array(n);
        const startIndex = (this.writeIndex - n + this.size) % this.size;
        
        for (let i = 0; i < n; i++) {
            result[i] = this.buffer[(startIndex + i) % this.size];
        }
        
        return result;
    }
    
    /**
     * Get the buffer ordered from oldest to newest value
     * 
     * @returns {Float32Array} - The buffer in chronological order
     */
    getOrdered() {
        // If the buffer isn't filled, we just need to return the portion that's filled
        if (!this.filled) {
            return this.buffer.slice(0, this.writeIndex);
        }
        
        // If the buffer is filled, we need to reorder it
        const result = new Float32Array(this.size);
        
        // Copy from write index to end
        for (let i = 0; i < this.size - this.writeIndex; i++) {
            result[i] = this.buffer[this.writeIndex + i];
        }
        
        // Copy from beginning to write index
        for (let i = 0; i < this.writeIndex; i++) {
            result[this.size - this.writeIndex + i] = this.buffer[i];
        }
        
        return result;
    }
    
    /**
     * Get a slice of the buffer
     * 
     * @param {number} start - Start index (relative to oldest value)
     * @param {number} end - End index (relative to oldest value)
     * @returns {Float32Array} - The requested slice
     */
    getSlice(start, end) {
        if (start < 0 || end > this.size || start >= end) {
            throw new Error('Invalid slice parameters');
        }
        
        // Get the ordered buffer first
        const ordered = this.getOrdered();
        
        // Return the slice
        return ordered.slice(start, end);
    }
    
    /**
     * Clear the buffer (reset to zeros)
     */
    clear() {
        this.buffer.fill(0);
        this.writeIndex = 0;
        this.filled = false;
    }
    
    /**
     * Get the size of the buffer
     * 
     * @returns {number} - The size of the buffer
     */
    getSize() {
        return this.size;
    }
    
    /**
     * Get the number of valid values in the buffer
     * 
     * @returns {number} - The number of valid values
     */
    getValidCount() {
        return this.filled ? this.size : this.writeIndex;
    }
    
    /**
     * Apply a function to each value in the buffer (in chronological order)
     * 
     * @param {Function} fn - Function to apply to each value: fn(value, index, buffer)
     */
    forEach(fn) {
        const ordered = this.getOrdered();
        for (let i = 0; i < ordered.length; i++) {
            fn(ordered[i], i, this);
        }
    }
    
    /**
     * Create a new buffer with the results of applying a function to each value
     * 
     * @param {Function} fn - Mapping function: fn(value, index, buffer)
     * @returns {CircularBuffer} - A new circular buffer with the mapped values
     */
    map(fn) {
        const ordered = this.getOrdered();
        const mapped = new Float32Array(ordered.length);
        
        for (let i = 0; i < ordered.length; i++) {
            mapped[i] = fn(ordered[i], i, this);
        }
        
        return new CircularBuffer(this.size, mapped);
    }
    
    /**
     * Find the maximum value in the buffer
     * 
     * @returns {number} - The maximum value
     */
    max() {
        const valueCount = this.getValidCount();
        if (valueCount === 0) return 0;
        
        let max = -Infinity;
        for (let i = 0; i < this.size; i++) {
            if (this.buffer[i] > max) {
                max = this.buffer[i];
            }
        }
        return max;
    }
    
    /**
     * Find the minimum value in the buffer
     * 
     * @returns {number} - The minimum value
     */
    min() {
        const valueCount = this.getValidCount();
        if (valueCount === 0) return 0;
        
        let min = Infinity;
        for (let i = 0; i < this.size; i++) {
            if (this.buffer[i] < min) {
                min = this.buffer[i];
            }
        }
        return min;
    }
    
    /**
     * Calculate the average value of the buffer
     * 
     * @returns {number} - The average value
     */
    average() {
        const valueCount = this.getValidCount();
        if (valueCount === 0) return 0;
        
        let sum = 0;
        for (let i = 0; i < valueCount; i++) {
            sum += this.buffer[i];
        }
        return sum / valueCount;
    }
    
    /**
     * Calculate the RMS (Root Mean Square) of the buffer
     * 
     * @returns {number} - The RMS value
     */
    rms() {
        const valueCount = this.getValidCount();
        if (valueCount === 0) return 0;
        
        let sumSquares = 0;
        for (let i = 0; i < valueCount; i++) {
            sumSquares += this.buffer[i] * this.buffer[i];
        }
        return Math.sqrt(sumSquares / valueCount);
    }
}
