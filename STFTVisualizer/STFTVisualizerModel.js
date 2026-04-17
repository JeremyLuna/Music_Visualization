/**
 * STFTVisualizerModel.js
 * Data and processing layer for STFT visualization.
 * Handles FFT processing, spectrogram state, and sample buffering.
 */

import { FFT } from '../FFT/FFT.js';
import { CONFIG } from '../config.js';

export class STFTVisualizerModel {
  constructor(fftSize = CONFIG.FFT_SIZE, hopSize = CONFIG.HOP_SIZE) {
    this.fftSize = fftSize;
    this.hopSize = hopSize;
    
    this.sampleBuffer = [];
    this.window = null;
    this.binCount = fftSize / 2;

    // For storing the latest spectrogram column data
    this.lastMagnitudes = null;
    
    this._makeWindow();
  }

  /**
   * Generate Hann window for FFT.
   * @private
   */
  _makeWindow() {
    this.window = new Float32Array(this.fftSize);
    for (let n = 0; n < this.fftSize; n++) {
      this.window[n] = 1 - Math.cos((2 * Math.PI * n) / (this.fftSize - 1));
    }
  }

  /**
   * Add raw audio samples to the buffer.
   * @param {Float32Array[]} channels - Array of Float32Arrays (one per channel)
   */
  addSamples(channels) {
    if (!channels || channels.length === 0) return;

    // Convert to mono
    const numChannels = channels.length;
    const numSamples = channels[0].length;
    for (let i = 0; i < numSamples; i++) {
      let sum = 0;
      for (let ch = 0; ch < numChannels; ch++) {
        sum += channels[ch][i];
      }
      this.sampleBuffer.push(sum / numChannels);
    }
  }

  /**
   * Process buffered samples into FFT frames.
   * Returns array of magnitude spectra that are ready to visualize.
   * @returns {Float32Array[]} Array of magnitude spectra, or empty if not enough samples
   */
  processSamples() {
    const magnitudeFrames = [];

    while (this.sampleBuffer.length >= this.fftSize) {
      const frame = this.sampleBuffer.slice(0, this.fftSize);
      this.sampleBuffer = this.sampleBuffer.slice(this.hopSize);

      // Apply window
      const re = new Float32Array(this.fftSize);
      const im = new Float32Array(this.fftSize);
      for (let i = 0; i < this.fftSize; i++) {
        re[i] = frame[i] * this.window[i];
        im[i] = 0;
      }

      // Perform FFT
      FFT.fft_in_place(re, im);

      // Compute magnitude spectrum
      const mags = new Float32Array(this.binCount);
      for (let i = 0; i < this.binCount; i++) {
        const real = re[i];
        const imag = im[i];
        mags[i] = Math.sqrt(real * real + imag * imag) / this.binCount;
      }

      magnitudeFrames.push(mags);
      this.lastMagnitudes = mags;
    }

    return magnitudeFrames;
  }

  /**
   * Change FFT size and reset state.
   * @param {number} newSize
   */
  setFftSize(newSize) {
    if (newSize === this.fftSize) return;
    this.fftSize = newSize;
    this.hopSize = newSize / 2;
    this.binCount = newSize / 2;
    this._makeWindow();
    this.sampleBuffer = [];
    this.lastMagnitudes = null;
  }

  /**
   * Get the bin count (FFT_SIZE / 2).
   * @returns {number}
   */
  getBinCount() {
    return this.binCount;
  }

  /**
   * Get current FFT size.
   * @returns {number}
   */
  getFftSize() {
    return this.fftSize;
  }

  /**
   * Clear sample buffer (useful on canvas clear).
   */
  clearBuffer() {
    this.sampleBuffer = [];
    this.lastMagnitudes = null;
  }

  /**
   * Convert magnitude to dB-normalized value [0, 1].
   * @param {number} magnitude
   * @returns {number} Normalized value [0, 1], clipped
   */
  static magnitudeToDb(magnitude) {
    let db = 20 * Math.log10(magnitude);
    let norm = (db - CONFIG.DB_MIN) / CONFIG.DB_RANGE;
    // Clip to [0, 1]
    return Math.max(0, Math.min(1, norm));
  }
}
