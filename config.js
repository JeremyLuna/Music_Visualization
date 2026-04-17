/**
 * config.js
 * Centralized configuration for Music Visualization app.
 */

export const CONFIG = {
  // FFT and STFT settings
  FFT_SIZE: 1024,
  HOP_SIZE: 512, // half of FFT_SIZE
  
  // UI timing
  HIDE_DELAY: 2500, // milliseconds before hiding control panel

  // dB scaling
  DB_MIN: -100, // minimum dB value
  DB_RANGE: 100, // dB range for normalization (max is 0)

  // Color and visualization
  COLORMAP: {
    // Grayscale for STFT (can expand with other colormaps later)
    GRAYSCALE: {
      getColor: (intensity) => `rgb(${intensity},${intensity},${intensity})`
    }
  },

  // FFT size options for UI
  FFT_SIZES: [256, 512, 1024, 2048, 4096]
};
