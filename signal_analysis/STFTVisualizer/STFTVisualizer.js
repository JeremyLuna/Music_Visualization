// STFTVisualizer.js

export class STFTVisualizer {
    constructor(audioSource, canvasElement, settingsDiv) {
        this.audioSource = audioSource; // must implement pullAllSamples(), returning [[ch1_samples...], [ch2_samples...], …]
        this.canvas = canvasElement;        
        this.ctx = this.canvas.getContext('2d');
        this.settingsDiv = settingsDiv;

        // Default FFT settings
        this.fftSize = 1024; // power of 2
        this.hopSize = this.fftSize / 2;
        this.sampleBuffer = []; // mono buffer

        // Initialize window function (Hann)
        this._makeWindow();

        // Prepare a frequency bin count and canvas dimensions
        this.binCount = this.fftSize / 2;
        this.width = this.canvas.width;
        this.height = this.canvas.height;

        // Create <select> for fftSize options
        this._createSettingsUI();

        // Start rendering loop
        this._running = false;
        this._renderLoop = this._renderLoop.bind(this);
    }

    _createSettingsUI() {
        // Label
        const label = document.createElement('label');
        label.textContent = 'FFT Size: ';
        label.style.marginRight = '8px';

        // <select> with common sizes
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
            this.sampleBuffer = []; // flush buffer
            // Clear canvas when size changes
            this.ctx.clearRect(0, 0, this.width, this.height);
        });

        this.settingsDiv.appendChild(label);
        this.settingsDiv.appendChild(select);
    }

    _makeWindow() {
        // Hann window of length fftSize
        this.window = new Float32Array(this.fftSize);
        for (let n = 0; n < this.fftSize; n++) {
            this.window[n] = 0.5 * (1 - Math.cos((2 * Math.PI * n) / (this.fftSize - 1)));
        }
    }

    _start() {
        if (this._running) return;
        this._running = true;
        requestAnimationFrame(this._renderLoop);
    }

    _stop() {
        this._running = false;
    }

    _renderLoop() {
        if (!this._running) return;
        this.height = this.canvas.height;
        this._processNewSamples();
        requestAnimationFrame(this._renderLoop);
    }

    _processNewSamples() {
        // 1) Pull all available samples
        const channels = this.audioSource.pullAllSamples();
        console.log("pulling: ", channels);
        
        if (!channels || channels.length === 0) return;

        // 2) Convert to mono by averaging channels for each sample
        const numChannels = channels.length;
        const numSamples = channels[0].length;
        for (let i = 0; i < numSamples; i++) {
            let sum = 0;
            for (let ch = 0; ch < numChannels; ch++) {
                sum += channels[ch][i];
            }
            this.sampleBuffer.push(sum / numChannels);
        }

        // 3) While enough samples for one FFT frame
        while (this.sampleBuffer.length >= this.fftSize) {
            const frame = this.sampleBuffer.slice(0, this.fftSize);
            this.sampleBuffer = this.sampleBuffer.slice(this.hopSize);

            // 4) Apply window
            const re = new Float32Array(this.fftSize);
            const im = new Float32Array(this.fftSize);
            for (let i = 0; i < this.fftSize; i++) {
                re[i] = frame[i] * this.window[i];
                im[i] = 0;
            }

            // 5) Perform in-place FFT
            this._fft(re, im);

            // 6) Compute magnitude spectrum (bins 0…binCount-1)
            const mags = new Float32Array(this.binCount);
            for (let i = 0; i < this.binCount; i++) {
                const real = re[i];
                const imag = im[i];
                mags[i] = Math.sqrt(real * real + imag * imag);
            }

            // 7) Draw this spectrum slice to canvas (spectrogram)
            this._drawSpectrogramColumn(mags);
        }
    }

    _drawSpectrogramColumn(mags) {
        // Shift existing image left by 1 pixel
        this.ctx.drawImage(this.canvas, 1, 0);
        // Clear rightmost column
        //this.ctx.clearRect(this.width - 1, 0, 1, this.height);

        // For each frequency bin, map to a y position and draw a pixel
        for (let i = 0; i < this.binCount; i++) {
            // Map bin index to y: low freqs at bottom
            const bin_spacing = Math.floor(i * (this.height / this.binCount));
            const y = this.height - (i * bin_spacing);
            // Convert magnitude to dB: 20*log10(m); clamp
            let db = 20 * Math.log10(mags[i] + 1e-8);
            // Normalize: assume range [-100 dB…0 dB]
            let norm = (db + 100) / 100;
            if (norm < 0) norm = 0;
            if (norm > 1) norm = 1;
            
            const intensity = Math.floor(norm * 255);
            this.ctx.fillStyle = `rgb(${intensity},${intensity},${intensity})`;
            this.ctx.fillRect(0, y, 1, bin_spacing);
        }
    }

    // Iterative in-place radix-2 Cooley–Tuk FFT
    _fft(re, im) {
        const n = re.length;
        const levels = Math.log2(n);
        if (Math.floor(levels) !== levels) {
            throw new Error('FFT size must be power of 2');
        }

        // Bit-reversed addressing permutation
        for (let i = 0; i < n; i++) {
            let j = 0;
            for (let k = 0; k < levels; k++) {
                j = (j << 1) | ((i >>> k) & 1);
            }
            if (j > i) {
                [re[i], re[j]] = [re[j], re[i]];
                [im[i], im[j]] = [im[j], im[i]];
            }
        }

        // Cooley–Tuk
        for (let size = 2; size <= n; size <<= 1) {
            const halfSize = size >> 1;
            const tableStep = n / size;
            for (let i = 0; i < n; i += size) {
                for (let j = 0; j < halfSize; j++) {
                    const k = j * tableStep;
                    const tRe = Math.cos(-2 * Math.PI * k / n) * re[i + j + halfSize]
                        - Math.sin(-2 * Math.PI * k / n) * im[i + j + halfSize];
                    const tIm = Math.sin(-2 * Math.PI * k / n) * re[i + j + halfSize]
                        + Math.cos(-2 * Math.PI * k / n) * im[i + j + halfSize];
                    re[i + j + halfSize] = re[i + j] - tRe;
                    im[i + j + halfSize] = im[i + j] - tIm;
                    re[i + j] += tRe;
                    im[i + j] += tIm;
                }
            }
        }
    }

    // Public API to start visualization
    start() {
        this._start();
    }

    // Public API to stop visualization
    stop() {
        this._stop();
    }
}
