(ns visualizers.multi-analytic
  "Pitch-separated analytic-signal visualizer.

   Uses a logarithmic bank of complex analysis kernels to estimate harmonic
   fundamentals, then draws small analytic traces centered at each detected
   pitch lane."
  (:require [app.state :as state]
            [app.theme :as theme]
            [visualizers.protocol :as protocol]
            [audio.interop :as interop]
            [audio.sample-puller :as puller]))

;; ============================================================================
;; Settings and numeric helpers
;; ============================================================================

(def default-settings
  {:bins-per-octave 24
   :min-frequency 55
   :max-frequency 1760
   :voice-count 6
   :harmonic-count 5
   :threshold 0.18
   :analysis-cycles 6
   :max-kernel-size 4096
   :trace-count 72
   :trace-hop 32
   :line-width 2
   :background-color "white"
   :grid-color "#d8d8d8"
   :axis-color "#9a9a9a"
   :label-color "#444444"})

(defn theme-settings
  [theme-state]
  (let [colors (theme/colors theme-state)]
    {:background-color (:background colors)
     :grid-color (theme/mix (:background colors) (:accent-b colors) 0.2)
     :axis-color (theme/mix (:background colors) (:text colors) 0.34)
     :label-color (:muted-text colors)}))

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

(defn- log2
  [value]
  (/ (js/Math.log value) (js/Math.log 2)))

(defn- effective-settings
  [settings sample-rate]
  (let [{:keys [bins-per-octave min-frequency max-frequency] :as merged}
        (merge default-settings settings)
        sr (if (and (finite-number? sample-rate) (pos? sample-rate))
             sample-rate
             44100)
        nyquist (/ sr 2)
        safe-bins-per-octave (int (clamp (setting-number bins-per-octave 24) 6 96))
        safe-min-frequency (clamp (setting-number min-frequency 55) 20 nyquist)
        safe-max-frequency (clamp (setting-number max-frequency 1760)
                                  safe-min-frequency
                                  nyquist)
        safe-voice-count (int (clamp (setting-number (:voice-count merged) 6) 1 12))
        safe-harmonic-count (int (clamp (setting-number (:harmonic-count merged) 5) 1 10))
        safe-threshold (clamp (setting-number (:threshold merged) 0.18) 0.01 0.95)
        safe-analysis-cycles (clamp (setting-number (:analysis-cycles merged) 6) 2 16)
        safe-max-kernel-size (int (clamp (setting-number (:max-kernel-size merged) 4096) 256 8192))
        safe-trace-count (int (clamp (setting-number (:trace-count merged) 72) 8 180))
        safe-trace-hop (int (clamp (setting-number (:trace-hop merged) 32) 1 512))
        safe-line-width (clamp (setting-number (:line-width merged) 2) 0.5 8)]
    (assoc merged
           :sample-rate sr
           :bins-per-octave safe-bins-per-octave
           :min-frequency safe-min-frequency
           :max-frequency safe-max-frequency
           :voice-count safe-voice-count
           :harmonic-count safe-harmonic-count
           :threshold safe-threshold
           :analysis-cycles safe-analysis-cycles
           :max-kernel-size safe-max-kernel-size
           :trace-count safe-trace-count
           :trace-hop safe-trace-hop
           :line-width safe-line-width)))

;; ============================================================================
;; Log-frequency complex analysis kernels
;; ============================================================================

(defn- frequency-bin-count
  [min-frequency max-frequency bins-per-octave]
  (inc (int (js/Math.floor (* bins-per-octave
                              (log2 (/ max-frequency min-frequency)))))))

(defn- bin-frequency
  [min-frequency bins-per-octave index]
  (* min-frequency (js/Math.pow 2 (/ index bins-per-octave))))

(defn- hann
  [n size]
  (* 0.5 (- 1 (js/Math.cos (/ (* 2 js/Math.PI n)
                              (dec size))))))

(defn- make-kernel
  [{:keys [sample-rate analysis-cycles max-kernel-size]} frequency]
  (let [raw-size (int (js/Math.ceil (/ (* analysis-cycles sample-rate)
                                       frequency)))
        window-size (int (clamp raw-size 32 max-kernel-size))
        cos-kernel (js/Float32Array. window-size)
        sin-kernel (js/Float32Array. window-size)
        phase-step (/ (* 2 js/Math.PI frequency) sample-rate)
        window-sum (atom 0)]
    (doseq [n (range window-size)]
      (swap! window-sum + (hann n window-size)))
    (doseq [n (range window-size)]
      (let [window-value (/ (hann n window-size) (max @window-sum 1.0e-12))
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
                                    :max-frequency
                                    :analysis-cycles
                                    :max-kernel-size])
     :kernels kernels
     :max-window-size max-window-size}))

(defn- ensure-kernels!
  [kernel-state settings]
  (let [config (select-keys settings [:sample-rate
                                      :bins-per-octave
                                      :min-frequency
                                      :max-frequency
                                      :analysis-cycles
                                      :max-kernel-size])]
    (when (not= (:config @kernel-state) config)
      (reset! kernel-state (make-kernels settings)))
    @kernel-state))

(defn- complex-at
  [sample-buffer end-index {:keys [window-size cos-kernel sin-kernel]}]
  (let [start (- end-index window-size)]
    (if (neg? start)
      [0 0]
      (loop [i 0
             real 0
             imag 0]
        (if (< i window-size)
          (let [sample (or (aget sample-buffer (+ start i)) 0)]
            (recur (inc i)
                   (+ real (* sample (aget cos-kernel i)))
                   (- imag (* sample (aget sin-kernel i)))))
          [real imag])))))

(defn- compute-complex-frame
  [sample-buffer kernels]
  (let [reals (js/Float32Array. (count kernels))
        imags (js/Float32Array. (count kernels))
        mags (js/Float32Array. (count kernels))
        end-index (.-length sample-buffer)]
    (doseq [[bin-index kernel] (map-indexed vector kernels)]
      (let [[real imag] (complex-at sample-buffer end-index kernel)
            mag (js/Math.sqrt (+ (* real real) (* imag imag)))]
        (aset reals bin-index real)
        (aset imags bin-index imag)
        (aset mags bin-index mag)))
    {:reals reals
     :imags imags
     :mags mags}))

;; ============================================================================
;; Fundamental scoring
;; ============================================================================

(defn- harmonic-bin-offset
  [bins-per-octave harmonic]
  (int (js/Math.round (* bins-per-octave (log2 harmonic)))))

(defn- harmonic-salience
  [mags bin-index {:keys [bins-per-octave harmonic-count]}]
  (let [bin-count (.-length mags)]
    (loop [h 1
           score 0]
      (if (<= h harmonic-count)
        (let [harmonic-index (+ bin-index (harmonic-bin-offset bins-per-octave h))
              contribution (if (< harmonic-index bin-count)
                             (/ (aget mags harmonic-index)
                                (js/Math.sqrt h))
                             0)]
          (recur (inc h) (+ score contribution)))
        score))))

(defn- harmonic-related?
  [a b]
  (let [low (min a b)
        high (max a b)
        ratio (/ high low)
        nearest (js/Math.round ratio)]
    (and (>= nearest 2)
         (<= nearest 8)
         (< (js/Math.abs (- (log2 ratio) (log2 nearest)))
            0.045))))

(defn- candidate-too-close?
  [candidate selected bins-per-octave]
  (some (fn [{:keys [bin-index frequency]}]
          (or (< (js/Math.abs (- (:bin-index candidate) bin-index))
                 (max 2 (/ bins-per-octave 8)))
              (and (< frequency (:frequency candidate))
                   (harmonic-related? frequency (:frequency candidate)))))
        selected))

(defn- detect-fundamentals
  [mags kernels {:keys [voice-count threshold bins-per-octave] :as settings}]
  (let [bin-count (.-length mags)
        scores (js/Float32Array. bin-count)
        peak-score (atom 0)]
    (doseq [i (range bin-count)]
      (let [score (harmonic-salience mags i settings)]
        (aset scores i score)
        (when (> score @peak-score)
          (reset! peak-score score))))
    (if (<= @peak-score 1.0e-12)
      []
      (let [score-floor (* @peak-score threshold)
            candidates (->> (range bin-count)
                            (keep (fn [i]
                                    (let [score (aget scores i)
                                          prev-score (if (pos? i) (aget scores (dec i)) 0)
                                          next-score (if (< i (dec bin-count)) (aget scores (inc i)) 0)]
                                      (when (and (>= score score-floor)
                                                 (>= score prev-score)
                                                 (>= score next-score))
                                        {:bin-index i
                                         :frequency (:frequency (nth kernels i))
                                         :magnitude (aget mags i)
                                         :score score}))))
                            (sort-by :score >))]
        (loop [remaining candidates
               selected []]
          (if (or (empty? remaining) (>= (count selected) voice-count))
            (sort-by :frequency selected)
            (let [candidate (first remaining)]
              (recur (rest remaining)
                     (if (candidate-too-close? candidate selected bins-per-octave)
                       selected
                       (conj selected candidate))))))))))

;; ============================================================================
;; Sample buffering
;; ============================================================================

(defn- current-sample-totals
  [sample-puller]
  (mapv #(puller/get-channel-samples-written sample-puller %)
        (range (puller/get-channel-count sample-puller))))

(defn- latest-mono-samples
  [sample-puller sample-count]
  (let [channel-count (puller/get-channel-count sample-puller)
        channels (mapv #(puller/pull-channel-samples sample-puller %
                                                     :max-samples sample-count)
                       (range channel-count))
        count (if (seq channels)
                (apply min (map #(.-length %) channels))
                0)
        output (js/Float32Array. count)]
    (when (and (pos? channel-count) (pos? count))
      (doseq [i (range count)]
        (let [sum (reduce + (map #(aget % i) channels))]
          (aset output i (/ sum channel-count)))))
    output))

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

(defn- format-frequency
  [frequency]
  (if (< frequency 1000)
    (str (js/Math.round frequency) " Hz")
    (str (/ (js/Math.round (/ frequency 100)) 10) " kHz")))

(defn- pitch-y
  [frequency canvas-height {:keys [min-frequency max-frequency]}]
  (let [t (clamp (/ (log2 (/ frequency min-frequency))
                    (max (log2 (/ max-frequency min-frequency)) 1.0e-9))
                 0
                 1)]
    (+ 18 (* (- 1 t) (- canvas-height 36)))))

(defn- voice-color
  [frequency {:keys [min-frequency max-frequency]}]
  (let [t (clamp (/ (log2 (/ frequency min-frequency))
                    (max (log2 (/ max-frequency min-frequency)) 1.0e-9))
                 0
                 1)
        hue (int (+ 205 (* 145 t)))]
    (str "hsl(" (mod hue 360) ", 84%, 56%)")))

(defn- draw-pitch-grid!
  [ctx canvas-width canvas-height {:keys [min-frequency max-frequency grid-color axis-color label-color] :as settings}]
  (set! (.-strokeStyle ctx) grid-color)
  (set! (.-lineWidth ctx) 1)
  (.beginPath ctx)
  (loop [frequency min-frequency]
    (when (<= frequency max-frequency)
      (let [y (+ 0.5 (pitch-y frequency canvas-height settings))]
        (.moveTo ctx 0 y)
        (.lineTo ctx canvas-width y))
      (recur (* frequency 2))))
  (.stroke ctx)

  (set! (.-strokeStyle ctx) axis-color)
  (set! (.-lineWidth ctx) 1)
  (.beginPath ctx)
  (.moveTo ctx (* canvas-width 0.5) 0)
  (.lineTo ctx (* canvas-width 0.5) canvas-height)
  (.stroke ctx)

  (set! (.-fillStyle ctx) label-color)
  (set! (.-font ctx) "11px sans-serif")
  (set! (.-textBaseline ctx) "middle")
  (loop [frequency min-frequency]
    (when (<= frequency max-frequency)
      (.fillText ctx (format-frequency frequency) 8 (pitch-y frequency canvas-height settings))
      (recur (* frequency 2)))))

(defn- nearest-y-distance
  [voice voices canvas-height settings]
  (let [y (pitch-y (:frequency voice) canvas-height settings)
        distances (keep (fn [other]
                          (when (not= (:bin-index voice) (:bin-index other))
                            (js/Math.abs (- y (pitch-y (:frequency other)
                                                       canvas-height
                                                       settings)))))
                        voices)]
    (if (seq distances)
      (apply min distances)
      canvas-height)))

(defn- trace-points
  [sample-buffer kernel {:keys [trace-count trace-hop]}]
  (let [points (js/Float32Array. (* trace-count 2))
        newest-end (.-length sample-buffer)]
    (doseq [i (range trace-count)]
      (let [end-index (- newest-end (* (- trace-count 1 i) trace-hop))
            [real imag] (complex-at sample-buffer end-index kernel)
            point-index (* i 2)]
        (aset points point-index real)
        (aset points (inc point-index) imag)))
    points))

(defn- max-trace-magnitude
  [points]
  (loop [i 0
         max-value 0]
    (if (< i (.-length points))
      (let [real (aget points i)
            imag (aget points (inc i))
            magnitude (js/Math.sqrt (+ (* real real) (* imag imag)))]
        (recur (+ i 2) (max max-value magnitude)))
      max-value)))

(defn- draw-voice!
  [ctx sample-buffer kernels voice voices peak-score canvas-width canvas-height
   {:keys [line-width axis-color label-color] :as settings}]
  (let [frequency (:frequency voice)
        center-x (* canvas-width 0.56)
        center-y (pitch-y frequency canvas-height settings)
        nearest-distance (nearest-y-distance voice voices canvas-height settings)
        radius (-> (min (* canvas-width 0.16)
                        (* canvas-height 0.16)
                        (* nearest-distance 0.38)
                        84)
                   (max 10))
        kernel (nth kernels (:bin-index voice))
        points (trace-points sample-buffer kernel settings)
        trace-max (max-trace-magnitude points)]
    (when (> trace-max 1.0e-12)
      (let [scale (/ radius trace-max)
            alpha (+ 0.32 (* 0.68 (js/Math.sqrt (clamp (/ (:score voice)
                                                          (max peak-score 1.0e-12))
                                                       0
                                                       1))))
            color (voice-color frequency settings)]
        (set! (.-globalAlpha ctx) 0.42)
        (set! (.-strokeStyle ctx) axis-color)
        (set! (.-lineWidth ctx) 1)
        (.beginPath ctx)
        (.moveTo ctx (- center-x radius) center-y)
        (.lineTo ctx (+ center-x radius) center-y)
        (.moveTo ctx center-x (- center-y radius))
        (.lineTo ctx center-x (+ center-y radius))
        (.stroke ctx)

        (set! (.-globalAlpha ctx) alpha)
        (set! (.-strokeStyle ctx) color)
        (set! (.-lineWidth ctx) line-width)
        (set! (.-lineCap ctx) "round")
        (set! (.-lineJoin ctx) "round")
        (.beginPath ctx)
        (doseq [i (range (/ (.-length points) 2))]
          (let [point-index (* i 2)
                real (aget points point-index)
                imag (aget points (inc point-index))
                x (+ center-x (* real scale))
                y (- center-y (* imag scale))]
            (if (zero? i)
              (.moveTo ctx x y)
              (.lineTo ctx x y))))
        (.stroke ctx)

        (set! (.-globalAlpha ctx) 1)
        (set! (.-fillStyle ctx) label-color)
        (set! (.-font ctx) "12px sans-serif")
        (set! (.-textBaseline ctx) "middle")
        (.fillText ctx (format-frequency frequency)
                   (+ center-x radius 8)
                   center-y)))))

(defn- clear-canvas!
  [ctx canvas-width canvas-height background-color]
  (set! (.-globalAlpha ctx) 1)
  (set! (.-fillStyle ctx) background-color)
  (.fillRect ctx 0 0 canvas-width canvas-height))

;; ============================================================================
;; Visualizer record
;; ============================================================================

(def analysis-setting-keys
  [:bins-per-octave
   :min-frequency
   :max-frequency
   :analysis-cycles
   :max-kernel-size])

(def buffer-setting-keys
  [:trace-count
   :trace-hop])

(defrecord MultiAnalyticVisualizer
  [settings
   sample-buffer
   last-sample-totals
   kernel-state]

  protocol/IVisualizer

  (render [this canvas-element sample-puller]
    (let [ctx (interop/get-canvas-context canvas-element)
          canvas-width (interop/get-canvas-width canvas-element)
          canvas-height (interop/get-canvas-height canvas-element)
          eff-settings (effective-settings settings (state/get-sample-rate))
          {:keys [background-color trace-count trace-hop]
           :as draw-settings} eff-settings
          {:keys [kernels max-window-size] :as kernels-state} (ensure-kernels! kernel-state eff-settings)
          max-buffer-size (+ max-window-size (* trace-count trace-hop) 512)]
      (when (and (pos? canvas-width) (pos? canvas-height) (pos? max-window-size))
        (let [current-totals (current-sample-totals sample-puller)]
          (if (or (nil? @last-sample-totals)
                  (some true? (map < current-totals @last-sample-totals)))
            (do
              (reset! last-sample-totals current-totals)
              (set! (.-length @sample-buffer) 0)
              (append-samples! @sample-buffer
                               (latest-mono-samples sample-puller max-buffer-size)))
            (let [new-samples (pull-new-mono-samples sample-puller @last-sample-totals)]
              (reset! last-sample-totals current-totals)
              (append-samples! @sample-buffer new-samples))))
        (trim-sample-buffer! @sample-buffer max-buffer-size)
        (clear-canvas! ctx canvas-width canvas-height background-color)
        (draw-pitch-grid! ctx canvas-width canvas-height draw-settings)
        (when (>= (.-length @sample-buffer) max-window-size)
          (let [{:keys [mags]} (compute-complex-frame @sample-buffer kernels)
                voices (detect-fundamentals mags kernels eff-settings)
                peak-score (if (seq voices)
                             (apply max (map :score voices))
                             0)]
            (doseq [voice voices]
              (draw-voice! ctx
                           @sample-buffer
                           kernels
                           voice
                           voices
                           peak-score
                           canvas-width
                           canvas-height
                           draw-settings)))))))

  (update-settings [this new-settings]
    (let [old-settings (effective-settings settings (state/get-sample-rate))
          next-settings (merge settings new-settings)
          next-effective (effective-settings next-settings (state/get-sample-rate))]
      (when (not= (select-keys old-settings analysis-setting-keys)
                  (select-keys next-effective analysis-setting-keys))
        (set! (.-length @sample-buffer) 0)
        (reset! kernel-state nil)
        (reset! last-sample-totals nil))
      (when (not= (select-keys old-settings buffer-setting-keys)
                  (select-keys next-effective buffer-setting-keys))
        (set! (.-length @sample-buffer) 0)
        (reset! last-sample-totals nil))
      (assoc this :settings next-settings)))

  (get-settings [this]
    settings))

(defn create-multi-analytic-visualizer
  "Create a pitch-separated analytic-signal visualizer."
  [& {:keys [bins-per-octave min-frequency max-frequency voice-count harmonic-count
             threshold analysis-cycles max-kernel-size trace-count trace-hop line-width
             background-color grid-color axis-color label-color]}]
  (->MultiAnalyticVisualizer
   (cond-> {}
     (some? bins-per-octave) (assoc :bins-per-octave bins-per-octave)
     (some? min-frequency) (assoc :min-frequency min-frequency)
     (some? max-frequency) (assoc :max-frequency max-frequency)
     (some? voice-count) (assoc :voice-count voice-count)
     (some? harmonic-count) (assoc :harmonic-count harmonic-count)
     (some? threshold) (assoc :threshold threshold)
     (some? analysis-cycles) (assoc :analysis-cycles analysis-cycles)
     (some? max-kernel-size) (assoc :max-kernel-size max-kernel-size)
     (some? trace-count) (assoc :trace-count trace-count)
     (some? trace-hop) (assoc :trace-hop trace-hop)
     (some? line-width) (assoc :line-width line-width)
     (some? background-color) (assoc :background-color background-color)
     (some? grid-color) (assoc :grid-color grid-color)
     (some? axis-color) (assoc :axis-color axis-color)
     (some? label-color) (assoc :label-color label-color))
   (atom (array))
   (atom nil)
   (atom nil)))
