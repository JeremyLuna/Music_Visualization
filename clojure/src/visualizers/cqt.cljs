(ns visualizers.cqt
  "Constant-Q spectrogram visualizer.

   Displays logarithmically spaced frequency content over time. The analysis Q
   factor is derived from the number of bins per octave."
  (:require [app.state :as state]
            [app.theme :as theme]
            [visualizers.protocol :as protocol]
            [audio.interop :as interop]
            [audio.sample-puller :as puller]))

;; ============================================================================
;; Settings and numeric helpers
;; ============================================================================

(def default-settings
  {:bins-per-octave 12
   :min-frequency 55
   :max-frequency 7040
   :hop-size 512
   :color-map :theme
   :min-db -100
   :max-db 0
   :spectrogram-low-color "#000000"
   :spectrogram-mid-color "#666666"
   :spectrogram-high-color "#ffffff"})

(defn theme-settings
  [theme-state]
  (let [colors (theme/colors theme-state)]
    {:spectrogram-low-color (:background colors)
     :spectrogram-mid-color (:accent-b colors)
     :spectrogram-high-color (:accent-c colors)
     :color-map :theme}))

(defn- finite-number?
  [value]
  (and (number? value) (js/isFinite value)))

(defn- setting-number
  [value fallback]
  (if (finite-number? value)
    value
    fallback))

(defn- clamp
  [value min-value max-value]
  (-> value
      (max min-value)
      (min max-value)))

(defn- q-factor
  [bins-per-octave]
  (/ 1 (- (js/Math.pow 2 (/ 1 bins-per-octave)) 1)))

(defn- effective-settings
  [settings sample-rate]
  (let [{:keys [bins-per-octave min-frequency max-frequency] :as merged}
        (merge default-settings settings)
        sr (if (and (finite-number? sample-rate) (pos? sample-rate))
             sample-rate
             44100)
        nyquist (/ sr 2)
        safe-bins-per-octave (int (clamp (setting-number bins-per-octave 12) 1 96))
        safe-min-frequency (clamp (setting-number min-frequency 55) 1 nyquist)
        safe-max-frequency (clamp (setting-number max-frequency 7040) safe-min-frequency nyquist)
        safe-hop-size (int (clamp (setting-number (:hop-size merged) 512) 1 8192))]
    (assoc merged
           :sample-rate sr
           :bins-per-octave safe-bins-per-octave
           :min-frequency safe-min-frequency
           :max-frequency safe-max-frequency
           :hop-size safe-hop-size
           :q-factor (q-factor safe-bins-per-octave))))

;; ============================================================================
;; Constant-Q kernel construction and analysis
;; ============================================================================

(defn- make-window-value
  [n window-size]
  (* 0.5 (- 1 (js/Math.cos (/ (* 2 js/Math.PI n)
                              (dec window-size))))))

(defn- frequency-bin-count
  [min-frequency max-frequency bins-per-octave]
  (inc (int (js/Math.floor (* bins-per-octave
                              (/ (js/Math.log (/ max-frequency min-frequency))
                                 (js/Math.log 2)))))))

(defn- bin-frequency
  [min-frequency bins-per-octave index]
  (* min-frequency (js/Math.pow 2 (/ index bins-per-octave))))

(defn- make-kernel
  [{:keys [sample-rate q-factor]} frequency]
  (let [window-size (max 2 (int (js/Math.ceil (/ (* q-factor sample-rate)
                                                 frequency))))
        cos-kernel (js/Float32Array. window-size)
        sin-kernel (js/Float32Array. window-size)
        phase-step (/ (* 2 js/Math.PI frequency) sample-rate)
        window-sum (atom 0)]
    (doseq [n (range window-size)]
      (let [window-value (make-window-value n window-size)]
        (swap! window-sum + window-value)))
    (doseq [n (range window-size)]
      (let [window-value (/ (make-window-value n window-size)
                            (max @window-sum 1.0e-12))
            phase (* phase-step n)]
        (aset cos-kernel n (* window-value (js/Math.cos phase)))
        (aset sin-kernel n (* window-value (js/Math.sin phase)))))
    {:frequency frequency
     :window-size window-size
     :cos-kernel cos-kernel
     :sin-kernel sin-kernel}))

(defn- make-kernels
  [{:keys [min-frequency max-frequency bins-per-octave] :as settings}]
  (let [bin-count (frequency-bin-count min-frequency max-frequency bins-per-octave)
        kernels (mapv #(make-kernel settings
                                    (bin-frequency min-frequency bins-per-octave %))
                      (range bin-count))
        max-window-size (if (seq kernels)
                          (apply max (map :window-size kernels))
                          0)]
    {:config (select-keys settings [:sample-rate
                                    :bins-per-octave
                                    :min-frequency
                                    :max-frequency])
     :kernels kernels
     :max-window-size max-window-size}))

(defn- ensure-kernels!
  [kernel-state settings]
  (let [config (select-keys settings [:sample-rate
                                      :bins-per-octave
                                      :min-frequency
                                      :max-frequency])]
    (when (not= (:config @kernel-state) config)
      (reset! kernel-state (make-kernels settings)))
    @kernel-state))

(defn- compute-cqt-magnitudes
  [sample-buffer {:keys [kernels max-window-size]}]
  (let [mags (js/Float32Array. (count kernels))]
    (doseq [[bin-index {:keys [window-size cos-kernel sin-kernel]}]
            (map-indexed vector kernels)]
      (let [offset (int (js/Math.floor (/ (- max-window-size window-size) 2)))
            sums (loop [i 0
                        real 0
                        imag 0]
                   (if (< i window-size)
                     (let [sample (or (aget sample-buffer (+ offset i)) 0)]
                       (recur (inc i)
                              (+ real (* sample (aget cos-kernel i)))
                              (- imag (* sample (aget sin-kernel i)))))
                     [real imag]))
            real (nth sums 0)
            imag (nth sums 1)]
        (aset mags bin-index (js/Math.sqrt (+ (* real real) (* imag imag))))))
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

(defn- rgb-css
  [[r g b]]
  (str "rgb(" r "," g "," b ")"))

(defn- background-color-for
  [settings]
  (rgb-css (color-for 0 settings)))

(defn- draw-spectrogram-column!
  [canvas-element mags {:keys [min-db max-db] :as settings}]
  (let [ctx (interop/get-canvas-context canvas-element)
        width (interop/get-canvas-width canvas-element)
        height (interop/get-canvas-height canvas-element)
        bin-count (.-length mags)]
    (when (and (pos? width) (pos? height) (pos? bin-count))
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
            (set! (.-fillStyle ctx) (rgb-css [r g b]))
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
   :spectrogram-low-color
   :spectrogram-mid-color
   :spectrogram-high-color])

(def analysis-setting-keys
  [:bins-per-octave
   :min-frequency
   :max-frequency
   :hop-size])

;; ============================================================================
;; CQT Visualizer Record and Implementation
;; ============================================================================

(defrecord CQTVisualizer
  [settings
   sample-buffer
   last-sample-totals
   kernel-state
   last-canvas-size]

  protocol/IVisualizer

  (render [this canvas-element sample-puller]
    (let [eff-settings (effective-settings settings (state/get-sample-rate))
          background-color (background-color-for eff-settings)
          canvas-width (interop/get-canvas-width canvas-element)
          canvas-height (interop/get-canvas-height canvas-element)
          canvas-size [canvas-width canvas-height]
          {:keys [max-window-size] :as kernels-state} (ensure-kernels! kernel-state eff-settings)
          hop-size (:hop-size eff-settings)]
      (when (and (pos? canvas-width) (pos? canvas-height) (pos? max-window-size))
        (when (not= @last-canvas-size canvas-size)
          (reset! last-canvas-size canvas-size)
          (clear-canvas! canvas-element background-color))
        (let [current-totals (current-sample-totals sample-puller)]
          (if (or (nil? @last-sample-totals)
                  (some true? (map < current-totals @last-sample-totals)))
            (reset! last-sample-totals current-totals)
            (let [new-samples (pull-new-mono-samples sample-puller @last-sample-totals)]
              (reset! last-sample-totals current-totals)
              (append-samples! @sample-buffer new-samples)
              (trim-sample-buffer! @sample-buffer (+ max-window-size (* hop-size 8)))
              (loop []
                (when (>= (.-length @sample-buffer) max-window-size)
                  (let [mags (compute-cqt-magnitudes @sample-buffer kernels-state)]
                    (draw-spectrogram-column! canvas-element mags eff-settings)
                    (.splice @sample-buffer 0 hop-size)
                    (recur))))))))))

  (update-settings [this new-settings]
    (let [old-settings (effective-settings settings (state/get-sample-rate))
          next-settings (merge settings new-settings)
          next-effective (effective-settings next-settings (state/get-sample-rate))]
      (when (not= (select-keys old-settings analysis-setting-keys)
                  (select-keys next-effective analysis-setting-keys))
        (set! (.-length @sample-buffer) 0)
        (reset! kernel-state nil)
        (reset! last-sample-totals nil)
        (reset! last-canvas-size nil))
      (when (not= (select-keys old-settings color-setting-keys)
                  (select-keys next-effective color-setting-keys))
        (reset! last-canvas-size nil))
      (assoc this :settings next-settings)))

  (get-settings [this]
    settings))

(defn create-cqt-visualizer
  "Create a new Constant-Q visualizer.

   Args:
   - bins-per-octave: Number of logarithmic bins per octave; Q is derived from this
   - min-frequency: Lowest included frequency in Hz
   - max-frequency: Highest included frequency in Hz

   Returns: CQTVisualizer instance"
  [& {:keys [bins-per-octave min-frequency max-frequency hop-size color-map min-db max-db
             spectrogram-low-color
             spectrogram-mid-color
             spectrogram-high-color]}]
  (->CQTVisualizer
   (cond-> {}
     (some? bins-per-octave) (assoc :bins-per-octave bins-per-octave)
     (some? min-frequency) (assoc :min-frequency min-frequency)
     (some? max-frequency) (assoc :max-frequency max-frequency)
     (some? hop-size) (assoc :hop-size hop-size)
     (some? color-map) (assoc :color-map color-map)
     (some? min-db) (assoc :min-db min-db)
     (some? max-db) (assoc :max-db max-db)
     (some? spectrogram-low-color) (assoc :spectrogram-low-color spectrogram-low-color)
     (some? spectrogram-mid-color) (assoc :spectrogram-mid-color spectrogram-mid-color)
     (some? spectrogram-high-color) (assoc :spectrogram-high-color spectrogram-high-color))
   (atom (array))
   (atom nil)
   (atom nil)
   (atom nil)))
