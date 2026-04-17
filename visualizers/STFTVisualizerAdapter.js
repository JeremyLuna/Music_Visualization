/**
 * STFTVisualizerAdapter.js
 * Adapter to make STFTVisualizerController work as a plugin visualizer.
 * This allows STFT to be registered and instantiated via VisualizerRegistry.
 */

import { STFTVisualizerController } from '../STFTVisualizer/STFTVisualizerController.js';
import { BaseVisualizerController } from './BaseVisualizerController.js';

export class STFTVisualizerAdapter extends BaseVisualizerController {
  constructor(canvasElement, settingsDiv, eventEmitter, audioSource) {
    super(canvasElement, settingsDiv, eventEmitter, audioSource);
    
    // Wrap the STFTVisualizerController
    this.controller = new STFTVisualizerController(
      canvasElement,
      settingsDiv,
      eventEmitter,
      audioSource
    );
  }

  /**
   * Start the visualizer.
   */
  start() {
    this._running = true;
    this.controller.start();
  }

  /**
   * Stop the visualizer.
   */
  stop() {
    this._running = false;
    this.controller.stop();
  }

  /**
   * Cleanup and destroy.
   */
  destroy() {
    this.controller.destroy();
    this._running = false;
  }

  /**
   * Get the underlying controller (for advanced usage).
   */
  getController() {
    return this.controller;
  }

  /**
   * Get the model (forwarded).
   */
  getModel() {
    return this.controller.getModel();
  }

  /**
   * Get the view (forwarded).
   */
  getView() {
    return this.controller.getView();
  }
}
