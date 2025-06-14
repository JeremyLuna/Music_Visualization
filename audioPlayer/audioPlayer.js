export class AudioSamplePuller {
  constructor(audioContext, workletURL) {
    this.audioContext = audioContext;
    this.workletURL = workletURL;
    this.channelDataBuffer = [];
    this._ready = this._initWorklet();
  }

  async _initWorklet() {
    await this.audioContext.audioWorklet.addModule(this.workletURL);
    this.node = new AudioWorkletNode(this.audioContext, 'sample-processor', {
      numberOfInputs: 1,
      numberOfOutputs: 0, // still zero, since we’re only “tapping” the stream
    });
    this.node.port.onmessage = (e) => {
      const channels = e.data.map(ch => ch.slice());
      this.channelDataBuffer.push(channels);
    };
  }

  async ready() {
    await this._ready;
  }

  pullSamples() {
    if (!this.channelDataBuffer.length) return null;
    const numCh = this.channelDataBuffer[0].length;
    const out = [];
    for (let ch = 0; ch < numCh; ch++) {
      let total = 0;
      for (const block of this.channelDataBuffer) total += block[ch].length;
      const merged = new Float32Array(total);
      let off = 0;
      for (const block of this.channelDataBuffer) {
        merged.set(block[ch], off);
        off += block[ch].length;
      }
      out.push(merged);
    }
    this.channelDataBuffer = [];
    return out;
  }

  connect(sourceNode) {
    // Route source → worklet (to pull samples)
    sourceNode.connect(this.node);
    // AND route source → destination (so you still hear it)
    sourceNode.connect(this.audioContext.destination);
  }
}

export class MusicPlayer {
  /**
   * @param {HTMLDivElement} containerDiv
   *   The DIV where the player UI should be created.
   * @param {string} workletURL
   *   Path/URL to 'sample-processor.js' (e.g. './sample-processor.js').
   */
  constructor(containerDiv, workletURL = 'sample-processor.js') {
    if (!(containerDiv instanceof HTMLDivElement)) {
      throw new Error('MusicPlayer: constructor argument must be a <div>.');
    }
    this.container = containerDiv;
    this.container.classList.add('music-player-container');
    this.workletURL = workletURL;
    this.audioContext = null;
    this.samplePuller = null;
    this.audioElement = null;
    this.mediaSourceNode = null;
    this.gainNode = null;
    this.audioBufferInfo = null;
    this._buildUI();

    // load css
    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = './audioPlayer/styles.css';
    document.head.appendChild(link);
  }

  _buildUI() {
    // — File input —
    this.fileInput = document.createElement('input');
    this.fileInput.type = 'file';
    this.fileInput.accept = 'audio/*';
    this.fileInput.classList.add('music-player-file-input');
    this.fileInput.addEventListener('change', e => this._onFileSelected(e));
    this.container.appendChild(this.fileInput);

    // — Play/Pause toggle & Stop buttons —
    this.controlsDiv = document.createElement('div');
    this.controlsDiv.classList.add('music-player-controls');

    this.playPauseBtn = document.createElement('button');
    this.playPauseBtn.textContent = 'Play';
    this.playPauseBtn.disabled = true;
    this.playPauseBtn.classList.add('music-player-btn');
    this.playPauseBtn.addEventListener('click', () => this._togglePlayPause());
    this.controlsDiv.appendChild(this.playPauseBtn);

    this.stopBtn = document.createElement('button');
    this.stopBtn.textContent = 'Stop';
    this.stopBtn.disabled = true;
    this.stopBtn.classList.add('music-player-btn');
    this.stopBtn.addEventListener('click', () => this.stop());
    this.controlsDiv.appendChild(this.stopBtn);

    this.container.appendChild(this.controlsDiv);

    // — Scrub slider —
    this.slider = document.createElement('input');
    this.slider.type = 'range';
    this.slider.min = 0;
    this.slider.max = 0;
    this.slider.value = 0;
    this.slider.step = 0.01;
    this.slider.disabled = true;
    this.slider.classList.add('music-player-slider');
    this.slider.addEventListener('input', () => this._onScrub());
    this.container.appendChild(this.slider);

    // — Time display (MM:SS / MM:SS) —
    this.timeDisplay = document.createElement('div');
    this.timeDisplay.textContent = '00:00 / 00:00';
    this.timeDisplay.classList.add('music-player-time-display');
    this.container.appendChild(this.timeDisplay);

    // — Volume slider —
    this.volumeContainer = document.createElement('div');
    this.volumeContainer.classList.add('music-player-volume-container');

    const volLabel = document.createElement('label');
    volLabel.textContent = 'Volume';
    volLabel.classList.add('music-player-volume-label');
    this.volumeContainer.appendChild(volLabel);

    this.volumeSlider = document.createElement('input');
    this.volumeSlider.type = 'range';
    this.volumeSlider.min = 0;
    this.volumeSlider.max = 1;
    this.volumeSlider.step = 0.01;
    this.volumeSlider.value = 1;
    this.volumeSlider.disabled = true;
    this.volumeSlider.classList.add('music-player-volume-slider');
    this.volumeSlider.addEventListener('input', () => {
      if (this.gainNode) {
        this.gainNode.gain.value = parseFloat(this.volumeSlider.value);
      }
    });
    this.volumeContainer.appendChild(this.volumeSlider);

    this.container.appendChild(this.volumeContainer);

    // — Details panel —
    this.detailsPre = document.createElement('pre');
    this.detailsPre.textContent = 'No track loaded.';
    this.detailsPre.classList.add('music-player-details');
    this.container.appendChild(this.detailsPre);
  }

  async _onFileSelected(event) {
    const file = event.target.files[0];
    if (!file) return;

    this._cleanupAudio();

    // 1) Read file as ArrayBuffer for metadata
    const arrayBuffer = await file.arrayBuffer();

    // 2) (Re)create AudioContext if needed
    if (!this.audioContext) {
      this.audioContext = new (window.AudioContext || window.webkitAudioContext)();
    }

    // 3) Decode for metadata
    const audioBuffer = await this.audioContext.decodeAudioData(arrayBuffer.slice(0));
    this.audioBufferInfo = {
      fileName: file.name,
      duration: audioBuffer.duration,
      sampleRate: audioBuffer.sampleRate,
      numberOfChannels: audioBuffer.numberOfChannels
    };

    // 4) Create blob URL for <audio>
    const blobURL = URL.createObjectURL(new Blob([arrayBuffer], { type: file.type }));
    this.audioElement = new Audio();
    this.audioElement.src = blobURL;
    this.audioElement.crossOrigin = 'anonymous';
    await this.audioElement.load();

    // 5) On metadata loaded, enable controls & initialize display
    this.audioElement.addEventListener('loadedmetadata', () => {
      this.slider.max = this.audioElement.duration;
      this.slider.value = 0;
      this.slider.disabled = false;
      this.playPauseBtn.disabled = false;
      this.stopBtn.disabled = false;
      this.volumeSlider.disabled = false;
      this._updateDetails();

      const totalMMSS = this._formatTime(this.audioElement.duration);
      this.timeDisplay.textContent = `00:00 / ${totalMMSS}`;
    });

    // 6) Update slider & time display on timeupdate
    this.audioElement.addEventListener('timeupdate', () => {
      const curr = this.audioElement.currentTime;
      const total = this.audioElement.duration;
      this.slider.value = curr;
      const currMMSS = this._formatTime(curr);
      const totalMMSS = this._formatTime(total);
      this.timeDisplay.textContent = `${currMMSS} / ${totalMMSS}`;

      if (this.audioElement.ended) {
        this.playPauseBtn.textContent = 'Play';
      }
    });

    // 7) Create MediaElementSourceNode
    this.mediaSourceNode = this.audioContext.createMediaElementSource(this.audioElement);

    // 8) Instantiate AudioSamplePuller and tap raw signal
    this.samplePuller = new AudioSamplePuller(this.audioContext, this.workletURL);
    await this.samplePuller.ready();
    this.mediaSourceNode.connect(this.samplePuller.node);

    // 9) Wire up GainNode for volume control, route to destination
    this.gainNode = this.audioContext.createGain();
    this.gainNode.gain.value = parseFloat(this.volumeSlider.value);
    this.mediaSourceNode.connect(this.gainNode);
    this.gainNode.connect(this.audioContext.destination);
  }

  _updateDetails() {
    if (!this.audioBufferInfo) return;
    const { fileName, duration, sampleRate, numberOfChannels } = this.audioBufferInfo;
    this.detailsPre.textContent =
      `File: ${fileName}\n` +
      `Duration: ${duration.toFixed(2)} seconds\n` +
      `Sample Rate: ${sampleRate} Hz\n` +
      `Channels: ${numberOfChannels}`;
  }

  _onScrub() {
    if (this.audioElement) {
      this.audioElement.currentTime = parseFloat(this.slider.value);
      const currMMSS = this._formatTime(this.audioElement.currentTime);
      const totalMMSS = this._formatTime(this.audioElement.duration);
      this.timeDisplay.textContent = `${currMMSS} / ${totalMMSS}`;
    }
  }

  _togglePlayPause() {
    if (!this.audioElement) return;
    if (this.audioElement.paused || this.audioElement.ended) {
      this.audioElement.play();
      this.playPauseBtn.textContent = 'Pause';
    } else {
      this.audioElement.pause();
      this.playPauseBtn.textContent = 'Play';
    }
  }

  stop() {
    if (!this.audioElement) return;
    this.audioElement.pause();
    this.audioElement.currentTime = 0;
    this.slider.value = 0;
    const totalMMSS = this._formatTime(this.audioElement.duration);
    this.timeDisplay.textContent = `00:00 / ${totalMMSS}`;
    this.playPauseBtn.textContent = 'Play';
  }

  /**
   * pullAllSamples()
   * Returns all PCM frames (all channels) since the last call,
   * as [Float32Array_channel0, Float32Array_channel1, …].
   * If none, returns null.
   */
  pullAllSamples() {
    if (!this.samplePuller) {
      console.warn('MusicPlayer: samplePuller not initialized yet.');
      return null;
    }
    return this.samplePuller.pullSamples();
  }

  _cleanupAudio() {
    if (this.audioElement) {
      this.audioElement.pause();
      this.audioElement.src = '';
      this.audioElement = null;
    }
    if (this.mediaSourceNode) {
      this.mediaSourceNode.disconnect();
      this.mediaSourceNode = null;
    }
    if (this.samplePuller && this.audioContext) {
      this.samplePuller.node.disconnect();
      this.samplePuller = null;
    }
    if (this.gainNode) {
      this.gainNode.disconnect();
      this.gainNode = null;
    }
    this.audioBufferInfo = null;
    this.playPauseBtn.disabled = true;
    this.playPauseBtn.textContent = 'Play';
    this.stopBtn.disabled = true;
    this.slider.disabled = true;
    this.slider.value = 0;
    this.slider.max = 0;
    this.volumeSlider.disabled = true;
    this.volumeSlider.value = 1;
    this.timeDisplay.textContent = '00:00 / 00:00';
    this.detailsPre.textContent = 'No track loaded.';
  }

  _formatTime(sec) {
    const m = Math.floor(sec / 60);
    const s = Math.floor(sec % 60);
    const mm = m.toString().padStart(2, '0');
    const ss = s.toString().padStart(2, '0');
    return `${mm}:${ss}`;
  }
}

// --- USAGE EXAMPLE ---
// 1) Include both files in your HTML:
//
//    <link rel="stylesheet" href="styles.css" />
//    <script src="music-player.js"></script>
//
// 2) Add a container:
//
//    <div id="player-container"></div>
//
// 3) After DOM loads:
//
//    const container = document.getElementById('player-container');
//    const player = new MusicPlayer(container, './sample-processor.js');
//
// No inline styles remain; all appearance is driven by `styles.css`.
