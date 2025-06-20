// STFTVisualizer.js - Modified for canvas scaling during resize

import { FFT } from '../FFT/FFT.js';

export class STFTVisualizer {
    constructor(audioSource, canvasElement, settingsDiv) {
        this.audioSource = audioSource;
        this.canvas = canvasElement;        
        this.ctx = this.canvas.getContext('2d');
        this.settingsDiv = settingsDiv;

        this.canvas.close = () => {
            this._stop();
        };

        // Default FFT settings
        this.fftSize = 1024;
        this.hopSize = this.fftSize / 2;
        this.sampleBuffer = [];

        // Track canvas dimensions for resize detection
        this.lastWidth = this.canvas.width;
        this.lastHeight = this.canvas.height;
        this.width = this.canvas.width;
        this.height = this.canvas.height;

        // Create backup canvas for resize scaling
        this.backupCanvas = document.createElement('canvas');
        this.backupCtx = this.backupCanvas.getContext('2d');

        // Initialize window function (Hann)
        this._makeWindow();
        this.binCount = this.fftSize / 2;

        this._createSettingsUI();
        this._running = false;
        this._renderLoop = this._renderLoop.bind(this);
    }

    _createSettingsUI() {
        const label = document.createElement('label');
        label.textContent = 'FFT Size: ';
        label.style.marginRight = '8px';

        const select = document.createElement('select');
        [256, 512, 1024, 2048, 4096].forEach((size) => {
            const opt = document.createElement('option');
            opt.value = size;
            opt.textContent = size;
            if (size === this.fftSize) opt.selected = true;
            select.appendChild(opt);
        });
        select.addEventListener('change', () => {
            const newSize = parseInt(select.value, 10);
            if (newSize === this.fftSize) return;
            this.fftSize = newSize;
            this.hopSize = this.fftSize / 2;
            this.binCount = this.fftSize / 2;
            this._makeWindow();
            this.sampleBuffer = [];
            this.ctx.clearRect(0, 0, this.width, this.height);
        });

        this.settingsDiv.appendChild(label);
        this.settingsDiv.appendChild(select);
    }

    _makeWindow() {
        this.window = new Float32Array(this.fftSize);
        for (let n = 0; n < this.fftSize; n++) {
            this.window[n] = 1 - Math.cos((2 * Math.PI * n) / (this.fftSize - 1));
        }
    }

    _start() {
        if (this._running) return;
        this._running = true;
        requestAnimationFrame(this._renderLoop);
    }

    _stop() {
        this._running = false;
        console.log("visualizer has stopped.");
        
    }

    _renderLoop() {
        if (!this._running) return;
        
        // Check for canvas resize and handle scaling
        this._handleCanvasResize();
        
        this._processNewSamples();
        requestAnimationFrame(this._renderLoop);
    }

    _handleCanvasResize() {
        const currentWidth = this.canvas.width;
        const currentHeight = this.canvas.height;
        
        // Check if canvas size changed
        if (currentWidth !== this.lastWidth || currentHeight !== this.lastHeight) {
            console.log(`Canvas resized from ${this.lastWidth}x${this.lastHeight} to ${currentWidth}x${currentHeight}`);
            
            // Only try to scale if:
            // 1. We have valid backup content (width > 0 AND height > 0)
            // 2. The new canvas has valid dimensions (width > 0 AND height > 0)
            // 3. We're not going from 0x0 to something (initial setup)
            const hasValidBackup = this.backupCanvas.width > 0 && this.backupCanvas.height > 0;
            const hasValidDestination = currentWidth > 0 && currentHeight > 0;
            
            if (hasValidBackup && hasValidDestination) {
                try {
                    // Clear the main canvas and scale the backup to new size
                    this.ctx.clearRect(0, 0, currentWidth, currentHeight);
                    
                    // Scale and draw the backup canvas to the new size
                    this.ctx.drawImage(
                        this.backupCanvas,
                        0, 0, this.backupCanvas.width, this.backupCanvas.height,  // source
                        0, 0, currentWidth, currentHeight                         // destination
                    );
                    
                    console.log('Scaled spectrogram to new canvas size');
                } catch (e) {
                    console.log('Could not scale canvas content:', e);
                }
            } else {
                console.log('Skipping scale - invalid dimensions');
            }
            
            // Update stored dimensions
            this.lastWidth = currentWidth;
            this.lastHeight = currentHeight;
            this.width = currentWidth;
            this.height = currentHeight;
            
            // Update backup canvas size to match (only if destination is valid)
            if (currentWidth > 0 && currentHeight > 0) {
                this._updateBackupCanvas();
            }
        }
    }

    _updateBackupCanvas() {
        // Only update backup if we have valid dimensions
        if (this.width <= 0 || this.height <= 0) {
            console.log('Skipping backup update - invalid canvas dimensions');
            return;
        }
        
        // Keep backup canvas in sync with main canvas
        if (this.backupCanvas.width !== this.width || this.backupCanvas.height !== this.height) {
            this.backupCanvas.width = this.width;
            this.backupCanvas.height = this.height;
        }
        
        // Copy current main canvas to backup
        try {
            this.backupCtx.clearRect(0, 0, this.width, this.height);
            this.backupCtx.drawImage(this.canvas, 0, 0);
        } catch (e) {
            console.log('Could not update backup canvas:', e);
        }
    }

    _processNewSamples() {
        const channels = this.audioSource.pullAllSamples();
        
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

        // Process FFT frames
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

            // Draw spectrogram column
            this._drawSpectrogramColumn(mags);
        }
        
        // Update backup canvas after drawing
        this._updateBackupCanvas();
    }

    _drawSpectrogramColumn(mags) {
        // Shift existing image left by 1 pixel
        try {
            this.ctx.drawImage(this.canvas, 1, 0);
        } catch (e) {
            // Canvas might be empty - continue without shifting
            console.log('Canvas empty during shift, continuing...');
        }

        // Draw new column on the right
        for (let i = 0; i < this.binCount; i++) {
            const bin_spacing = Math.ceil(this.height / this.binCount);
            const y = this.height - (i * this.height / this.binCount);
            
            let db = 20 * Math.log10(mags[i]);
            let norm = (db + 100) / 100;
            // 0 magnitude has -inf db so we clip
            if (norm < 0){
                norm = 0;
            } else if (norm > 1) {
                console.log("clipped:", norm);
                norm = 1;
            }
            
            const intensity = Math.floor(norm * 255);
            this.ctx.fillStyle = `rgb(${intensity},${intensity},${intensity})`;
            
            this.ctx.fillRect(0, y, 1, bin_spacing);
            
        }
    }

    start() {
        this._start();
    }

    stop() {
        this._stop();
    }
}