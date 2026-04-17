/**
 * BaseVisualizerController.js
 * Abstract base class for all visualizers.
 * Provides standard interface and lifecycle management.
 */

export class BaseVisualizerController {
  /**
   * @param {HTMLCanvasElement} canvasElement
   * @param {HTMLDivElement} settingsDiv
   * @param {EventEmitter} eventEmitter
   * @param {Object} audioSource
   */
  constructor(canvasElement, settingsDiv, eventEmitter, audioSource) {
    this.canvasElement = canvasElement;
    this.settingsDiv = settingsDiv;
    this.eventEmitter = eventEmitter;
    this.audioSource = audioSource;
    this._running = false;
  }

  /**
   * Start the visualizer.
   * Must be implemented by subclasses.
   */
  start() {
    throw new Error('start() must be implemented by subclass');
  }

  /**
   * Stop the visualizer.
   * Must be implemented by subclasses.
   */
  stop() {
    throw new Error('stop() must be implemented by subclass');
  }

  /**
   * Cleanup resources.
   * Called when visualizer is being destroyed.
   */
  destroy() {
    this.stop();
  }

  /**
   * Check if visualizer is running.
   */
  isRunning() {
    return this._running;
  }

  /**
   * Get the canvas element.
   */
  getCanvasElement() {
    return this.canvasElement;
  }

  /**
   * Get the settings container.
   */
  getSettingsDiv() {
    return this.settingsDiv;
  }
}
