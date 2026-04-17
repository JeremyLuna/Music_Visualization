/**
 * STFTVisualizer.js
 * Public API for STFT visualization.
 * Now uses MVC architecture (Model, View, Controller) internally.
 * Maintains backward compatibility with the old API.
 */

import { STFTVisualizerController } from './STFTVisualizerController.js';

export class STFTVisualizer {
  /**
   * @param {AudioPlayer|MusicPlayer} audioSource - Source with pullAllSamples() method
   * @param {HTMLCanvasElement} canvasElement - Canvas to draw on
   * @param {HTMLDivElement} settingsDiv - Container for settings UI
   * @param {EventEmitter} eventEmitter - Optional EventEmitter for 'samplesAvailable' events
   */
  constructor(audioSource, canvasElement, settingsDiv, eventEmitter = null) {
    this.audioSource = audioSource;
    this.canvasElement = canvasElement;
    this.settingsDiv = settingsDiv;
    this.eventEmitter = eventEmitter;

    // Create the MVC controller internally
    this.controller = new STFTVisualizerController(
      canvasElement,
      settingsDiv,
      eventEmitter,
      audioSource
    );

    // Add close method to canvas (backward compatibility)
    this.canvasElement.close = () => this.stop();
  }

  /**
   * Start visualization.
   * Public method (replaces old _start / public start).
   */
  start() {
    this.controller.start();
  }

  /**
   * Stop visualization.
   * Public method (replaces old _stop / public stop).
   */
  stop() {
    this.controller.stop();
  }

  /**
   * Destroy the visualizer.
   */
  destroy() {
    this.controller.destroy();
  }

  /**
   * Get the underlying controller (for advanced usage).
   */
  getController() {
    return this.controller;
  }

  /**
   * Get the underlying model (for advanced usage).
   */
  getModel() {
    return this.controller.getModel();
  }

  /**
   * Get the underlying view (for advanced usage).
   */
  getView() {
    return this.controller.getView();
  }
}