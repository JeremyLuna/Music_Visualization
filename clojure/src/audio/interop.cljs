(ns audio.interop
  "JavaScript interop layer for Web Audio API, Canvas 2D, and FFT.
   
   Provides ClojureScript wrappers around browser APIs.")

;; ============================================================================
;; Web Audio API Interop
;; ============================================================================

(defn create-audio-context
  "Create a new AudioContext."
  []
  (if (exists? js/window.AudioContext)
    (js* "new window.AudioContext()")
    (js* "new window.webkitAudioContext()")))

(defn create-media-element-source
  "Create MediaElementAudioSourceNode from an audio HTMLElement."
  [^js audio-context audio-element]
  (.createMediaElementSource audio-context audio-element))

(defn create-gain-node
  "Create a GainNode for volume control."
  [^js audio-context]
  (.createGain audio-context))

(defn connect-nodes
  "Connect an audio node to another node or the destination.
   
   (connect-nodes source dest)
   (connect-nodes source dest audio-context)"
  ([source dest]
   (.connect source dest)
   nil)
  ([source dest destination]
   (.connect source dest destination)
   nil))

(defn create-audio-worklet
  "Load an AudioWorklet from a URL.
   
   Returns a promise that resolves with the WorkletNode once loaded."
  [^js audio-context worklet-url worklet-name]
  (-> (.addModule (.-audioWorklet audio-context) worklet-url)
      (.then (fn []
               (js/AudioWorkletNode.
                audio-context
                worklet-name
                (clj->js {:numberOfInputs 1
                          :numberOfOutputs 0}))))))

(defn resume-audio-context
  "Resume an AudioContext after a user gesture, if the browser suspended it."
  [^js audio-context]
  (if (= "suspended" (.-state audio-context))
    (.resume audio-context)
    (js/Promise.resolve audio-context)))

(defn get-audio-context-destination
  "Get the destination (speakers) of an AudioContext."
  [audio-context]
  (.-destination audio-context))

(defn set-gain
  "Set the gain (volume) of a GainNode."
  [gain-node value]
  (set! (-> gain-node .-gain .-value) value))

(defn play-audio-element
  "Start playing an audio HTMLElement."
  [audio-element]
  (.play audio-element))

(defn pause-audio-element
  "Pause an audio HTMLElement."
  [audio-element]
  (.pause audio-element))

(defn get-audio-element-duration
  "Get the duration (in seconds) of an audio HTMLElement."
  [audio-element]
  (.-duration audio-element))

(defn get-audio-element-current-time
  "Get the current playback time of an audio HTMLElement."
  [audio-element]
  (.-currentTime audio-element))

(defn set-audio-element-current-time
  "Seek to a specific time in an audio HTMLElement."
  [audio-element time]
  (set! (.-currentTime audio-element) time))

(defn get-sample-rate
  "Get the sample rate (Hz) of an AudioContext."
  [audio-context]
  (.-sampleRate audio-context))

;; ============================================================================
;; Canvas 2D Drawing Interop
;; ============================================================================

(defn get-canvas-context
  "Get 2D context from a canvas HTMLElement."
  [canvas-element]
  (.getContext canvas-element "2d"))

(defn clear-canvas
  "Clear a canvas by filling it with white."
  [canvas-element]
  (let [ctx (get-canvas-context canvas-element)
        width (.-width canvas-element)
        height (.-height canvas-element)]
    (set! (.-fillStyle ctx) "white")
    (.fillRect ctx 0 0 width height)))

(defn draw-line
  "Draw a line on a canvas from (x1, y1) to (x2, y2) with a specific color and width.
   
   Options:
   - :stroke-color (default: \"black\")
   - :stroke-width (default: 1)"
  [canvas-element x1 y1 x2 y2 & {:keys [stroke-color stroke-width] :or {stroke-color "black" stroke-width 1}}]
  (let [ctx (get-canvas-context canvas-element)]
    (set! (.-strokeStyle ctx) stroke-color)
    (set! (.-lineWidth ctx) stroke-width)
    (.beginPath ctx)
    (.moveTo ctx x1 y1)
    (.lineTo ctx x2 y2)
    (.stroke ctx)))

(defn draw-pixels
  "Draw pixels (spectrogram) on a canvas using ImageData.
   
   Args:
   - canvas-element: the target canvas
   - pixel-data: Uint8ClampedArray or array of RGBA pixel values
   - width, height: dimensions of the pixel data"
  [canvas-element pixel-data width height]
  (let [ctx (get-canvas-context canvas-element)
        image-data (.createImageData ctx width height)]
    (.set (.-data image-data) pixel-data)
    (.putImageData ctx image-data 0 0)))

(defn get-canvas-width
  "Get the width of a canvas element."
  [canvas-element]
  (.-width canvas-element))

(defn get-canvas-height
  "Get the height of a canvas element."
  [canvas-element]
  (.-height canvas-element))

(defn set-canvas-size
  "Set the width and height of a canvas element."
  [canvas-element width height]
  (set! (.-width canvas-element) width)
  (set! (.-height canvas-element) height))

;; ============================================================================
;; FFT Library Interop (assuming fftjs or similar loaded globally or via npm)
;; ============================================================================

(defn create-fft
  "Create an FFT instance for a given size.
   
   Expects a global FFT library (e.g., fftjs) to be available.
   Returns an FFT object with forward() method."
  [size]
  (if (exists? js/FFT)
    (js/FFT. size)
    (do
      (.error js/console "FFT library not loaded. Install fftjs or similar.")
      nil)))

(defn fft-forward
  "Perform forward FFT on real-valued input (assumes complex output).
   
   Args:
   - fft-instance: FFT object created by create-fft
   - real-input: Float32Array of input values
   - imag-input: Float32Array of imaginary values (usually zeros)
   
   Returns: [real-output imag-output] arrays with frequency bins."
  [fft-instance real-input imag-input]
  (.forward fft-instance real-input imag-input))

(defn get-fft-spectrum
  "Extract magnitude spectrum from FFT output.
   
   Args:
   - real: real part output from FFT
   - imag: imaginary part output from FFT
   
   Returns: Float32Array of magnitude values (0 to 1 or higher)"
  [real imag]
  (let [spectrum (js/Float32Array. (/ (.-length real) 2))]
    (doseq [i (range (.-length spectrum))]
      (let [r (aget real i)
            i-val (aget imag i)
            mag (js/Math.sqrt (+ (* r r) (* i-val i-val)))]
        (aset spectrum i (/ mag (.-length real)))))
    spectrum))

;; ============================================================================
;; Utility functions
;; ============================================================================

(defn request-animation-frame
  "Schedule a callback for the next animation frame."
  [callback]
  (js/requestAnimationFrame callback))

(defn file-reader-read-array-buffer
  "Read a file as an ArrayBuffer asynchronously.
   
   Returns a promise that resolves with the ArrayBuffer."
  [file]
  (js/Promise.
   (fn [resolve reject]
     (let [reader (js/FileReader.)]
       (set! (.-onload reader) #(resolve (.-result reader)))
       (set! (.-onerror reader) #(reject (.-error reader)))
       (.readAsArrayBuffer reader file)))))

(defn decode-audio-data
  "Decode audio data using Web Audio API.
   
   Args:
   - audio-context: AudioContext
   - array-buffer: ArrayBuffer with encoded audio
   
   Returns: a promise that resolves with an AudioBuffer."
  [audio-context array-buffer]
  (.decodeAudioData audio-context array-buffer))
