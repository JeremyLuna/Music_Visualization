/**
 * STFTVisualizerController.js
 * Orchestration layer for STFT visualization.
 * Manages Model + View lifecycle, event handling, and rendering.
 */

import { STFTVisualizerModel } from './STFTVisualizerModel.js';
import { STFTVisualizerView } from './STFTVisualizerView.js';
import { CONFIG } from '../config.js';

export class STFTVisualizerController {
  /**
   * @param {HTMLCanvasElement} canvasElement
   * @param {HTMLDivElement} settingsDiv - Container for settings UI
   * @param {EventEmitter} eventEmitter - For listening to 'samplesAvailable' events
   * @param {Object} audioSource - AudioPlayer or similar with pullAllSamples() method
   */
  constructor(canvasElement, settingsDiv, eventEmitter, audioSource) {
    this.canvasElement = canvasElement;
    this.settingsDiv = settingsDiv;
    this.eventEmitter = eventEmitter;
    this.audioSource = audioSource;

    // Create Model and View
    this.model = new STFTVisualizerModel(CONFIG.FFT_SIZE, CONFIG.HOP_SIZE);
    this.view = new STFTVisualizerView(canvasElement);

    // State
    this._running = false;
    this._renderLoopId = null;

    // Bind methods
    this._renderLoop = this._renderLoop.bind(this);
    this._onSamplesAvailable = this._onSamplesAvailable.bind(this);

    // Create settings UI
    this._createSettingsUI();

    // Add close method to canvas (backward compatibility)
    this.canvasElement.close = () => this.stop();
  }

  /**
   * Start the visualizer.
   */
  start() {
    if (this._running) return;
    this._running = true;

    // Subscribe to samples available event if eventEmitter is provided
    if (this.eventEmitter) {
      this.eventEmitter.on('samplesAvailable', this._onSamplesAvailable);
    }

    this._renderLoop();
  }

  /**
   * Stop the visualizer.
   */
  stop() {
    this._running = false;

    // Unsubscribe from samples available event if eventEmitter is provided
    if (this.eventEmitter) {
      this.eventEmitter.off('samplesAvailable', this._onSamplesAvailable);
    }

    // Cancel render loop if pending
    if (this._renderLoopId !== null) {
      cancelAnimationFrame(this._renderLoopId);
      this._renderLoopId = null;
    }

    this.view.cleanup();
    console.log('STFT Visualizer stopped.');
  }

  /**
   * Cleanup and disconnect (called on destroy).
   */
  destroy() {
    this.stop();
  }

  /**
   * Handle incoming samples from event emitter.
   * @private
   */
  _onSamplesAvailable(data) {
    const channels = data.channels;
    this.model.addSamples(channels);
    
    // Process samples immediately when they arrive
    const magnitudeFrames = this.model.processSamples();
    
    // Draw each new magnitude frame
    for (const mags of magnitudeFrames) {
      this.view.drawSpectrogramColumn(mags, this.model.getBinCount());
    }

    // Update backup canvas
    this.view._updateBackupCanvas();
  }

  /**
   * Main render loop.
   * @private
   */
  _renderLoop() {
    if (!this._running) return;

    // Check for canvas resize
    if (this.view.checkCanvasResize()) {
      // Canvas was resized; scaling handled by view
    }

    // Pull samples via old method if eventEmitter is not available
    if (!this.eventEmitter && this.audioSource) {
      const channels = this.audioSource.pullAllSamples();
      if (channels) {
        this.model.addSamples(channels);
        const magnitudeFrames = this.model.processSamples();
        for (const mags of magnitudeFrames) {
          this.view.drawSpectrogramColumn(mags, this.model.getBinCount());
        }
        this.view._updateBackupCanvas();
      }
    }

    this._renderLoopId = requestAnimationFrame(this._renderLoop);
  }

  /**
   * Create FFT size selector UI.
   * @private
   */
  _createSettingsUI() {
    const label = document.createElement('label');
    label.textContent = 'FFT Size: ';
    label.style.marginRight = '8px';

    const select = document.createElement('select');
    CONFIG.FFT_SIZES.forEach((size) => {
      const opt = document.createElement('option');
      opt.value = size;
      opt.textContent = size;
      if (size === this.model.getFftSize()) opt.selected = true;
      select.appendChild(opt);
    });

    select.addEventListener('change', () => {
      const newSize = parseInt(select.value, 10);
      if (newSize === this.model.getFftSize()) return;
      
      this.model.setFftSize(newSize);
      this.view.clear();
    });

    this.settingsDiv.appendChild(label);
    this.settingsDiv.appendChild(select);
  }

  /**
   * Get the Model (for advanced usage).
   */
  getModel() {
    return this.model;
  }

  /**
   * Get the View (for advanced usage).
   */
  getView() {
    return this.view;
  }
}
