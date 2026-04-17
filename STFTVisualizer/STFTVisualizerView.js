/**
 * STFTVisualizerView.js
 * Presentation layer for STFT visualization.
 * Handles canvas rendering, resize handling, and visual state.
 */

import { CanvasResizeManager } from './CanvasResizeManager.js';
import { STFTVisualizerModel } from './STFTVisualizerModel.js';

export class STFTVisualizerView {
  constructor(canvasElement) {
    this.canvas = canvasElement;
    this.ctx = canvasElement.getContext('2d');
    
    // Dimension and resize tracking
    this.resizeManager = new CanvasResizeManager(canvasElement);
    
    // Backup canvas for anti-flicker during resize
    this.backupCanvas = document.createElement('canvas');
    this.backupCtx = this.backupCanvas.getContext('2d');
  }

  /**
   * Set canvas dimensions (called when canvas size changes).
   * @param {number} width
   * @param {number} height
   */
  setCanvasDimensions(width, height) {
    if (this.canvas.width !== width) this.canvas.width = width;
    if (this.canvas.height !== height) this.canvas.height = height;
    
    this.resizeManager.reset();
    this._updateBackupCanvas();
  }

  /**
   * Get current canvas dimensions.
   * @returns {{width: number, height: number}}
   */
  getCanvasDimensions() {
    return this.resizeManager.getDimensions();
  }

  /**
   * Get the canvas element.
   * @returns {HTMLCanvasElement}
   */
  getCanvasElement() {
    return this.canvas;
  }

  /**
   * Check for resize and handle scaling if needed.
   * @returns {boolean} True if resize was detected and handled
   */
  checkCanvasResize() {
    return this.resizeManager.checkResize((newWidth, newHeight, oldWidth, oldHeight) => {
      this._handleResizeScaling(newWidth, newHeight, oldWidth, oldHeight);
    });
  }

  /**
   * Handle scaling of existing content when canvas resizes.
   * @private
   */
  _handleResizeScaling(newWidth, newHeight, oldWidth, oldHeight) {
    console.log(`Canvas resized from ${oldWidth}x${oldHeight} to ${newWidth}x${newHeight}`);
    
    const hasValidBackup = this.backupCanvas.width > 0 && this.backupCanvas.height > 0;
    const hasValidDestination = newWidth > 0 && newHeight > 0;

    if (hasValidBackup && hasValidDestination) {
      try {
        this.ctx.clearRect(0, 0, newWidth, newHeight);
        this.ctx.drawImage(
          this.backupCanvas,
          0, 0, this.backupCanvas.width, this.backupCanvas.height,
          0, 0, newWidth, newHeight
        );
        console.log('Scaled spectrogram to new canvas size');
      } catch (e) {
        console.log('Could not scale canvas content:', e);
      }
    }

    if (newWidth > 0 && newHeight > 0) {
      this._updateBackupCanvas();
    }
  }

  /**
   * Update backup canvas (called after drawing).
   * @private
   */
  _updateBackupCanvas() {
    const { width, height } = this.resizeManager.getDimensions();
    
    if (width <= 0 || height <= 0) {
      console.log('Skipping backup update - invalid canvas dimensions');
      return;
    }

    if (this.backupCanvas.width !== width || this.backupCanvas.height !== height) {
      this.backupCanvas.width = width;
      this.backupCanvas.height = height;
    }

    try {
      this.backupCtx.clearRect(0, 0, width, height);
      this.backupCtx.drawImage(this.canvas, 0, 0);
    } catch (e) {
      console.log('Could not update backup canvas:', e);
    }
  }

  /**
   * Draw a single spectrogram column (latest FFT magnitude frame).
   * @param {Float32Array} magnitudes - Magnitude spectrum
   * @param {number} binCount - Number of frequency bins
   */
  drawSpectrogramColumn(magnitudes, binCount) {
    const { width, height } = this.resizeManager.getDimensions();

    if (width <= 0 || height <= 0) return;

    // Shift existing image left by 1 pixel
    try {
      this.ctx.drawImage(this.canvas, 1, 0);
    } catch (e) {
      console.log('Canvas empty during shift, continuing...');
    }

    // Draw new column on the right
    for (let i = 0; i < binCount; i++) {
      const bin_spacing = Math.ceil(height / binCount);
      const y = height - (i * height / binCount);

      const norm = STFTVisualizerModel.magnitudeToDb(magnitudes[i]);
      const intensity = Math.floor(norm * 255);
      
      this.ctx.fillStyle = `rgb(${intensity},${intensity},${intensity})`;
      this.ctx.fillRect(0, y, 1, bin_spacing);
    }
  }

  /**
   * Clear the canvas.
   */
  clear() {
    const { width, height } = this.resizeManager.getDimensions();
    this.ctx.clearRect(0, 0, width, height);
    this._updateBackupCanvas();
  }

  /**
   * Cleanup (called on visualizer stop).
   */
  cleanup() {
    // Disconnect any event listeners if needed (currently none)
    // Could be extended for ResizeObserver, etc.
  }
}
