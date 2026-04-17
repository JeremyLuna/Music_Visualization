/**
 * VisualizerRegistry.js
 * Central registry for managing visualizers.
 * Enables plugin-like registration and instantiation of visualizers.
 */

export class VisualizerRegistry {
  constructor() {
    this.visualizers = {}; // { name: ControllerClass }
  }

  /**
   * Register a visualizer controller class.
   * @param {string} name - Unique name for the visualizer (e.g., 'stft', 'waveform')
   * @param {class} ControllerClass - The visualizer controller class
   * @throws {Error} if name already registered or ControllerClass is invalid
   */
  register(name, ControllerClass) {
    if (!name || typeof name !== 'string') {
      throw new Error('Visualizer name must be a non-empty string');
    }
    if (this.visualizers[name]) {
      throw new Error(`Visualizer '${name}' is already registered`);
    }
    if (typeof ControllerClass !== 'function') {
      throw new Error(`ControllerClass for '${name}' must be a constructor function`);
    }

    this.visualizers[name] = ControllerClass;
    console.log(`Registered visualizer: '${name}'`);
  }

  /**
   * Create an instance of a registered visualizer.
   * @param {string} name - The visualizer name
   * @param {HTMLCanvasElement} canvasElement
   * @param {HTMLDivElement} settingsDiv
   * @param {EventEmitter} eventEmitter
   * @param {Object} audioSource
   * @returns {BaseVisualizerController} New visualizer instance
   * @throws {Error} if visualizer not found
   */
  create(name, canvasElement, settingsDiv, eventEmitter, audioSource) {
    if (!this.visualizers[name]) {
      throw new Error(`Visualizer '${name}' not found. Registered: ${this.list().join(', ')}`);
    }

    const ControllerClass = this.visualizers[name];
    const instance = new ControllerClass(canvasElement, settingsDiv, eventEmitter, audioSource);
    return instance;
  }

  /**
   * Get list of registered visualizer names.
   * @returns {string[]}
   */
  list() {
    return Object.keys(this.visualizers);
  }

  /**
   * Check if a visualizer is registered.
   * @param {string} name
   * @returns {boolean}
   */
  has(name) {
    return name in this.visualizers;
  }

  /**
   * Unregister a visualizer (useful for cleanup/testing).
   * @param {string} name
   * @returns {boolean} true if unregistered, false if not found
   */
  unregister(name) {
    if (this.visualizers[name]) {
      delete this.visualizers[name];
      console.log(`Unregistered visualizer: '${name}'`);
      return true;
    }
    return false;
  }

  /**
   * Clear all registered visualizers.
   */
  clear() {
    this.visualizers = {};
  }
}

// Export a global singleton for convenience
export const registryInstance = new VisualizerRegistry();
