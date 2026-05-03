(ns visualizers.stft
  "STFT (Short-Time Fourier Transform) spectrogram visualizer.
   
   Displays frequency content over time using FFT-based spectral analysis."
  (:require [visualizers.protocol :as protocol]
            [audio.interop :as interop]
            [audio.sample-puller :as puller]))

;; ============================================================================
;; FFT and Spectrum Computation
;; ============================================================================

(def default-settings
  {:fft-size 512
   :color-map :hot})

(defn- effective-settings
  [settings]
  (merge default-settings settings))

(defn compute-magnitude-spectrum
  "Compute magnitude spectrum from audio samples using FFT.
   
   Args:
   - samples: Float32Array of audio samples
   - fft-size: Size of FFT (must be power of 2)
   
   Returns: Uint8Array of magnitude values (0-255) normalized"
  [samples fft-size]
  (let [fft-instance (interop/create-fft fft-size)]
    (if (nil? fft-instance)
      nil
      (let [;; Pad or truncate samples to FFT size
            input-real (js/Float32Array. fft-size)
            input-imag (js/Float32Array. fft-size)
            samples-to-copy (min (.-length samples) fft-size)]
        
        ;; Copy samples into FFT input
        (.set input-real (js/Float32Array. samples 0 samples-to-copy) 0)
        
        ;; Compute FFT
        (interop/fft-forward fft-instance input-real input-imag)
        
        ;; Compute magnitude spectrum (sqrt(real^2 + imag^2))
        (let [spectrum (interop/get-fft-spectrum input-real input-imag)
              output (js/Uint8ClampedArray. (/ fft-size 2))]
          
          ;; Normalize to 0-255 range and apply log scale for better visualization
          (doseq [i (range (.-length spectrum))]
            (let [mag (aget spectrum i)
                  ;; Log scale: log(1 + mag) to compress dynamic range
                  log-mag (js/Math.log (+ 1 mag))
                  ;; Normalize to 0-255
                  normalized (js/Math.min 255 (* log-mag 50))]
              (aset output i (int normalized))))
          
          output)))))

;; ============================================================================
;; STFT Visualizer Record and Implementation
;; ============================================================================

(defrecord STFTVisualizer
  [settings                       ;; {:fft-size 512, :color-map :viridis}
   spectrogram-buffer]           ;; Circular buffer of spectra for history
  
  protocol/IVisualizer
  
  (render [this canvas-element sample-puller]
    (let [ctx (interop/get-canvas-context canvas-element)
          canvas-width (interop/get-canvas-width canvas-element)
          canvas-height (interop/get-canvas-height canvas-element)
          {:keys [fft-size]} (effective-settings settings)
          num-bins (/ fft-size 2)]
      
      ;; Get latest audio samples
      (let [samples-data (puller/pull-channel-samples sample-puller 0 :max-samples fft-size)
            spectrum (compute-magnitude-spectrum samples-data fft-size)]
        
        (if (nil? spectrum)
          nil
          (do
            ;; Shift existing spectrogram left and add new spectrum on the right
            ;; For simplicity, we'll just draw the current spectrum
            
            ;; Create image data for spectrogram
            ;; Height = frequency bins, Width = time (or single frame for now)
            (let [pixel-data (js/Uint8ClampedArray. (* canvas-width canvas-height 4))]
              
              ;; Map spectrum to vertical column (frequency axis)
              ;; Simple approach: draw spectrum as vertical stripes
              (doseq [freq-bin (range num-bins)
                      x (range canvas-width)]
                (let [mag (aget spectrum freq-bin)
                      ;; Map frequency bin to vertical position
                      y (int (* (/ freq-bin num-bins) canvas-height))
                      ;; Simple hot color map: black -> red -> yellow -> white
                      [r g b] (cond
                                (<= mag 64) [0 0 0]
                                (<= mag 128) [(- (* mag 2) 128) 0 0]
                                (<= mag 192) [255 (- (* mag 2) 256) 0]
                                :else [255 255 (- (* mag 2) 384)])
                      ;; Put RGB pixel at (x, y)
                      pixel-idx (* (+ (* y canvas-width) x) 4)]
                  (aset pixel-data pixel-idx r)
                  (aset pixel-data (+ pixel-idx 1) g)
                  (aset pixel-data (+ pixel-idx 2) b)
                  (aset pixel-data (+ pixel-idx 3) 255)))
              
              ;; Draw the pixels to canvas
              (interop/draw-pixels canvas-element pixel-data canvas-width canvas-height)))))))
  
  (update-settings [this new-settings]
    (assoc this :settings (merge settings new-settings)))
  
  (get-settings [this]
    settings))

(defn create-stft-visualizer
  "Create a new STFT visualizer.
   
   Args:
   - fft-size: FFT size in samples (must be power of 2, default 512)
   - color-map: Color scheme keyword (default :hot)
   
   Returns: STFTVisualizer instance"
  [& {:keys [fft-size color-map]}]
  (->STFTVisualizer
   (cond-> {}
     (some? fft-size) (assoc :fft-size fft-size)
     (some? color-map) (assoc :color-map color-map))
   nil))  ;; Spectrogram buffer initialized on first render if needed
