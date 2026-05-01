export class WaveformVisualizer {
    constructor(audioSource, canvasElement, settingsDiv) {
        this.audioSource = audioSource;
        this.canvas = canvasElement;
        this.ctx = this.canvas.getContext('2d');
        this.settingsDiv = settingsDiv;
        this.isRunning = false;
        this.bufferSize = 1024; // Number of samples to display in the waveform
        this.sampleBuffer = new Float32Array(this.bufferSize);
        this.bufferIndex = 0;
        this.setupUI();
    }

    setupUI() {
        // Basic settings: buffer size selector
        const label = document.createElement('label');
        label.textContent = 'Buffer Size: ';
        const select = document.createElement('select');
        [512, 1024, 2048, 4096].forEach(size => {
            const option = document.createElement('option');
            option.value = size;
            option.textContent = size;
            if (size === this.bufferSize) option.selected = true;
            select.appendChild(option);
        });
        select.addEventListener('change', (e) => {
            this.bufferSize = parseInt(e.target.value);
            this.sampleBuffer = new Float32Array(this.bufferSize);
            this.bufferIndex = 0;
        });
        label.appendChild(select);
        this.settingsDiv.appendChild(label);
    }

    _start() {
        if (this.isRunning) return;
        this.isRunning = true;
        this.renderLoop();
    }

    _stop() {
        this.isRunning = false;
    }

    renderLoop() {
        if (!this.isRunning) return;
        this.processSamples();
        this.draw();
        requestAnimationFrame(() => this.renderLoop());
    }

    processSamples() {
        const samples = this.audioSource.pullAllSamples();
        if (!samples || samples.length === 0) return;

        // Convert to mono by averaging channels
        const monoSamples = new Float32Array(samples[0].length);
        for (let i = 0; i < samples[0].length; i++) {
            let sum = 0;
            for (let ch = 0; ch < samples.length; ch++) {
                sum += samples[ch][i];
            }
            monoSamples[i] = sum / samples.length;
        }

        // Add new samples to circular buffer
        for (let i = 0; i < monoSamples.length; i++) {
            this.sampleBuffer[this.bufferIndex] = monoSamples[i];
            this.bufferIndex = (this.bufferIndex + 1) % this.bufferSize;
        }
    }

    draw() {
        this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
        this.ctx.strokeStyle = 'white';
        this.ctx.lineWidth = 2;
        this.ctx.beginPath();

        const centerY = this.canvas.height / 2;
        const scaleY = this.canvas.height / 2 * 0.8; // 80% of half-height for margin
        const stepX = this.canvas.width / (this.bufferSize - 1);

        for (let i = 0; i < this.bufferSize; i++) {
            const sampleIndex = (this.bufferIndex - this.bufferSize + i + this.bufferSize) % this.bufferSize;
            const sample = this.sampleBuffer[sampleIndex];
            const x = i * stepX;
            const y = centerY - sample * scaleY;
            if (i === 0) {
                this.ctx.moveTo(x, y);
            } else {
                this.ctx.lineTo(x, y);
            }
        }
        this.ctx.stroke();
    }
}