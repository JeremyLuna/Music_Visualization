(ns visualizers.stft
  "STFT (Short-Time Fourier Transform) spectrogram visualizer.

   Displays frequency content over time using windowed, overlapping FFT frames."
  (:require [visualizers.protocol :as protocol]
            [audio.interop :as interop]
            [audio.sample-puller :as puller]))

;; ============================================================================
;; Settings and numeric helpers
;; ============================================================================

(def default-settings
  {:fft-size 1024
   :hop-size nil
   :color-map :theme
   :min-db -100
   :max-db 0
   :spectrogram-background-color "black"
   :spectrogram-low-color "#000000"
   :spectrogram-mid-color "#666666"
   :spectrogram-high-color "#ffffff"})

(defn- effective-settings
  [settings]
  (let [{:keys [fft-size hop-size] :as merged} (merge default-settings settings)]
    (assoc merged
           :fft-size fft-size
           :hop-size (or hop-size (/ fft-size 2)))))

(defn- power-of-two?
  [n]
  (and (pos? n) (zero? (bit-and n (dec n)))))

(defn- clamp
  [value min-value max-value]
  (-> value
      (max min-value)
      (min max-value)))

(defn- make-window
  "Build a Hann-style analysis window matching the vanilla JS visualizer."
  [fft-size]
  (let [window (js/Float32Array. fft-size)]
    (doseq [n (range fft-size)]
      (aset window n (- 1 (js/Math.cos (/ (* 2 js/Math.PI n)
                                           (dec fft-size))))))
    window))

;; ============================================================================
;; FFT and spectrum computation
;; ============================================================================

(defn- fft-in-place!
  "Cooley-Tukey radix-2 FFT ported from the vanilla JS implementation."
  [re im]
  (let [n (.-length re)
        levels (/ (js/Math.log n) (js/Math.log 2))]
    (when (not= (js/Math.floor levels) levels)
      (throw (js/Error. "FFT size must be power of 2")))

    ;; Bit-reversed addressing.
    (loop [i 0]
      (when (< i n)
        (let [j (loop [k 0
                       acc 0]
                  (if (< k levels)
                    (recur (inc k)
                           (bit-or (bit-shift-left acc 1)
                                   (bit-and (bit-shift-right-zero-fill i k) 1)))
                    acc))]
          (when (> j i)
            (let [tmp-re (aget re i)
                  tmp-im (aget im i)]
              (aset re i (aget re j))
              (aset im i (aget im j))
              (aset re j tmp-re)
              (aset im j tmp-im))))
        (recur (inc i))))

    ;; Butterfly stages.
    (loop [size 2]
      (when (<= size n)
        (let [half-size (bit-shift-right size 1)
              table-step (/ n size)
              n-factor (/ (* -2 js/Math.PI) n)]
          (loop [i 0]
            (when (< i n)
              (loop [j 0]
                (when (< j half-size)
                  (let [k (* j table-step)
                        ijh (+ i j half-size)
                        trig-factor (* n-factor k)
                        cos-factor (js/Math.cos trig-factor)
                        sin-factor (js/Math.sin trig-factor)
                        t-re (- (* cos-factor (aget re ijh))
                                (* sin-factor (aget im ijh)))
                        t-im (+ (* sin-factor (aget re ijh))
                                (* cos-factor (aget im ijh)))
                        even-idx (+ i j)
                        even-re (aget re even-idx)
                        even-im (aget im even-idx)]
                    (aset re ijh (- even-re t-re))
                    (aset im ijh (- even-im t-im))
                    (aset re even-idx (+ even-re t-re))
                    (aset im even-idx (+ even-im t-im)))
                  (recur (inc j))))
              (recur (+ i size)))))
        (recur (bit-shift-left size 1))))))

(defn- compute-magnitudes
  [frame window fft-size bin-count]
  (let [re (js/Float32Array. fft-size)
        im (js/Float32Array. fft-size)
        mags (js/Float32Array. bin-count)]
    (doseq [i (range fft-size)]
      (aset re i (* (aget frame i) (aget window i))))
    (fft-in-place! re im)
    (doseq [i (range bin-count)]
      (let [real (aget re i)
            imag (aget im i)
            mag (/ (js/Math.sqrt (+ (* real real) (* imag imag)))
                   bin-count)]
        (aset mags i mag)))
    mags))

;; ============================================================================
;; Sample buffering
;; ============================================================================

(defn- current-sample-totals
  [sample-puller]
  (mapv #(puller/get-channel-samples-written sample-puller %)
        (range (puller/get-channel-count sample-puller))))

(defn- pull-new-mono-samples
  [sample-puller previous-totals]
  (let [channel-count (puller/get-channel-count sample-puller)
        channels (mapv #(puller/pull-channel-samples-since
                         sample-puller
                         %
                         (get previous-totals % 0))
                       (range channel-count))
        sample-count (if (seq channels)
                       (apply min (map #(.-length %) channels))
                       0)
        output (js/Float32Array. sample-count)]
    (when (and (pos? channel-count) (pos? sample-count))
      (doseq [i (range sample-count)]
        (let [sum (reduce + (map #(aget % i) channels))]
          (aset output i (/ sum channel-count)))))
    output))

(defn- append-samples!
  [sample-buffer samples]
  (doseq [i (range (.-length samples))]
    (.push sample-buffer (aget samples i))))

(defn- trim-sample-buffer!
  [sample-buffer max-size]
  (let [extra (- (.-length sample-buffer) max-size)]
    (when (pos? extra)
      (.splice sample-buffer 0 extra))))

;; ============================================================================
;; Drawing
;; ============================================================================

(defn- hex->rgb
  [hex-color]
  (let [value (if (and (string? hex-color) (= (first hex-color) "#"))
                (subs hex-color 1)
                hex-color)
        full-value (if (= (count value) 3)
                     (apply str (mapcat #(repeat 2 %) value))
                     value)
        n (js/parseInt full-value 16)]
    (if (js/isNaN n)
      [0 0 0]
      [(bit-and (bit-shift-right n 16) 255)
       (bit-and (bit-shift-right n 8) 255)
       (bit-and n 255)])))

(defn- mix-channel
  [a b t]
  (int (+ a (* (- b a) t))))

(defn- mix-rgb
  [[r1 g1 b1] [r2 g2 b2] t]
  [(mix-channel r1 r2 t)
   (mix-channel g1 g2 t)
   (mix-channel b1 b2 t)])

(defn- theme-color-for
  [intensity low-color mid-color high-color]
  (let [t (/ intensity 255)
        low (hex->rgb low-color)
        mid (hex->rgb mid-color)
        high (hex->rgb high-color)]
    (if (< t 0.5)
      (mix-rgb low mid (* t 2))
      (mix-rgb mid high (* (- t 0.5) 2)))))

(defn- color-for
  [intensity {:keys [color-map spectrogram-low-color spectrogram-mid-color spectrogram-high-color]}]
  (case color-map
    :hot (cond
           (< intensity 85) [(* intensity 3) 0 0]
           (< intensity 170) [255 (* (- intensity 85) 3) 0]
           :else [255 255 (* (- intensity 170) 3)])
    :gray [intensity intensity intensity]
    :theme (theme-color-for intensity
                            spectrogram-low-color
                            spectrogram-mid-color
                            spectrogram-high-color)
    [intensity intensity intensity]))

(defn- draw-spectrogram-column!
  [canvas-element mags {:keys [min-db max-db] :as settings}]
  (let [ctx (interop/get-canvas-context canvas-element)
        width (interop/get-canvas-width canvas-element)
        height (interop/get-canvas-height canvas-element)
        bin-count (.-length mags)]
    (when (and (pos? width) (pos? height) (pos? bin-count))
      ;; Scroll history left by one pixel, then draw the newest spectrum at right.
      (.drawImage ctx canvas-element -1 0)
      (let [column-x (dec width)
            bin-height (/ height bin-count)
            db-range (- max-db min-db)]
        (doseq [i (range bin-count)]
          (let [mag (aget mags i)
                db (* 20 (js/Math.log10 (max mag 1.0e-12)))
                norm (clamp (/ (- db min-db) db-range) 0 1)
                intensity (int (* norm 255))
                [r g b] (color-for intensity settings)
                y (* (- bin-count 1 i) bin-height)]
            (set! (.-fillStyle ctx) (str "rgb(" r "," g "," b ")"))
            (.fillRect ctx column-x y 1 (max 1 bin-height))))))))

(defn- clear-canvas!
  [canvas-element background-color]
  (let [ctx (interop/get-canvas-context canvas-element)
        width (interop/get-canvas-width canvas-element)
        height (interop/get-canvas-height canvas-element)]
    (set! (.-fillStyle ctx) background-color)
    (.fillRect ctx 0 0 width height)))

(def color-setting-keys
  [:color-map
   :spectrogram-background-color
   :spectrogram-low-color
   :spectrogram-mid-color
   :spectrogram-high-color])

;; ============================================================================
;; STFT Visualizer Record and Implementation
;; ============================================================================

(defrecord STFTVisualizer
  [settings
   sample-buffer
   last-sample-totals
   window-state
   last-canvas-size]

  protocol/IVisualizer

  (render [this canvas-element sample-puller]
    (let [{:keys [fft-size hop-size spectrogram-background-color] :as eff-settings} (effective-settings settings)
          canvas-width (interop/get-canvas-width canvas-element)
          canvas-height (interop/get-canvas-height canvas-element)
          canvas-size [canvas-width canvas-height]]
      (when (and (power-of-two? fft-size) (pos? canvas-width) (pos? canvas-height))
        (when (not= @last-canvas-size canvas-size)
          (reset! last-canvas-size canvas-size)
          (clear-canvas! canvas-element spectrogram-background-color))
        (when (not= (:fft-size @window-state) fft-size)
          (reset! window-state {:fft-size fft-size
                                :window (make-window fft-size)})
          (set! (.-length @sample-buffer) 0)
          (clear-canvas! canvas-element spectrogram-background-color))
        (let [current-totals (current-sample-totals sample-puller)]
          (if (or (nil? @last-sample-totals)
                  (some true? (map < current-totals @last-sample-totals)))
            (reset! last-sample-totals current-totals)
            (let [new-samples (pull-new-mono-samples sample-puller @last-sample-totals)
                  bin-count (/ fft-size 2)
                  window (:window @window-state)]
              (reset! last-sample-totals current-totals)
              (append-samples! @sample-buffer new-samples)
              (trim-sample-buffer! @sample-buffer (* fft-size 8))
              (loop []
                (when (>= (.-length @sample-buffer) fft-size)
                  (let [frame (js/Float32Array. (.slice @sample-buffer 0 fft-size))
                        mags (compute-magnitudes frame window fft-size bin-count)]
                    (draw-spectrogram-column! canvas-element mags eff-settings)
                    (.splice @sample-buffer 0 hop-size)
                    (recur))))))))))

  (update-settings [this new-settings]
    (let [old-settings (effective-settings settings)
          next-settings (merge settings new-settings)
          next-effective (effective-settings next-settings)]
      (when (not= (:fft-size old-settings) (:fft-size next-effective))
        (set! (.-length @sample-buffer) 0)
        (reset! window-state nil)
        (reset! last-sample-totals nil)
        (reset! last-canvas-size nil))
      (when (not= (select-keys old-settings color-setting-keys)
                  (select-keys next-effective color-setting-keys))
        (reset! last-canvas-size nil))
      (assoc this :settings next-settings)))

  (get-settings [this]
    settings))

(defn create-stft-visualizer
  "Create a new STFT visualizer.

   Args:
   - fft-size: FFT size in samples (must be power of 2, default 1024)
   - color-map: Color scheme keyword (default :gray)

   Returns: STFTVisualizer instance"
  [& {:keys [fft-size hop-size color-map min-db max-db
             spectrogram-background-color
             spectrogram-low-color
             spectrogram-mid-color
             spectrogram-high-color]}]
  (->STFTVisualizer
   (cond-> {}
     (some? fft-size) (assoc :fft-size fft-size)
     (some? hop-size) (assoc :hop-size hop-size)
     (some? color-map) (assoc :color-map color-map)
     (some? min-db) (assoc :min-db min-db)
     (some? max-db) (assoc :max-db max-db)
     (some? spectrogram-background-color) (assoc :spectrogram-background-color spectrogram-background-color)
     (some? spectrogram-low-color) (assoc :spectrogram-low-color spectrogram-low-color)
     (some? spectrogram-mid-color) (assoc :spectrogram-mid-color spectrogram-mid-color)
     (some? spectrogram-high-color) (assoc :spectrogram-high-color spectrogram-high-color))
   (atom (array))
   (atom nil)
   (atom nil)
   (atom nil)))
