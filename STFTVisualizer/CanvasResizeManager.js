/**
 * CanvasResizeManager.js
 * Centralized canvas resize detection and dimension tracking.
 * Can be reused for any component that needs canvas size management.
 */

export class CanvasResizeManager {
  constructor(canvasElement) {
    this.canvas = canvasElement;
    this.lastWidth = canvasElement.width;
    this.lastHeight = canvasElement.height;
    this.width = canvasElement.width;
    this.height = canvasElement.height;
    this.resizeCallback = null;
  }

  /**
   * Check if canvas size has changed.
   * @param {function} onResize - Callback(newWidth, newHeight, oldWidth, oldHeight) when resize detected
   * @returns {boolean} True if canvas was resized
   */
  checkResize(onResize = null) {
    const currentWidth = this.canvas.width;
    const currentHeight = this.canvas.height;

    const resized = currentWidth !== this.lastWidth || currentHeight !== this.lastHeight;

    if (resized) {
      const oldWidth = this.lastWidth;
      const oldHeight = this.lastHeight;

      this.lastWidth = currentWidth;
      this.lastHeight = currentHeight;
      this.width = currentWidth;
      this.height = currentHeight;

      if (onResize) {
        onResize(currentWidth, currentHeight, oldWidth, oldHeight);
      }
    }

    return resized;
  }

  /**
   * Get current canvas dimensions.
   * @returns {{width: number, height: number}}
   */
  getDimensions() {
    return {
      width: this.width,
      height: this.height
    };
  }

  /**
   * Check if dimensions are valid (> 0).
   * @returns {boolean}
   */
  hasValidDimensions() {
    return this.width > 0 && this.height > 0;
  }

  /**
   * Reset dimension tracking (useful on canvas clear).
   */
  reset() {
    this.lastWidth = this.canvas.width;
    this.lastHeight = this.canvas.height;
    this.width = this.canvas.width;
    this.height = this.canvas.height;
  }
}
