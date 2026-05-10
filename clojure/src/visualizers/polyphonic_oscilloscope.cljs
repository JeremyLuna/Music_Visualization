(ns visualizers.polyphonic-oscilloscope
  "Polyphonic oscilloscope visualizer.

   Uses constant-Q analysis to find active frequency bands, then draws
   frequency-placed waveform traces reconstructed from selected CQT partials."
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
   :threshold-db -32
   :selection-mode :peaks
   :neighbor-bins 1
   :harmonic-count 5
   :max-traces 12
   :cycles 3
   :amplitude-gain 3.5
   :line-width 2
   :color-mode :theme
   :background-color "#000000"
   :grid-color "#253244"
   :label-color "#d5e7ff"
   :trace-color "#65f0d3"})

(defn theme-settings
  [theme-state]
  (let [colors (theme/colors theme-state)]
    {:background-color (:background colors)
     :grid-color (theme/mix (:background colors) (:accent-a colors) 0.32)
     :label-color (:text colors)
     :trace-color (:accent-c colors)
     :color-mode :theme}))

(def note-names
  ["C" "C#" "D" "D#" "E" "F" "F#" "G" "G#" "A" "A#" "B"])

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
  (let [{:keys [bins-per-octave min-frequency max-frequency harmonic-count max-traces cycles]
         :as merged} (merge default-settings settings)
        sr (if (and (finite-number? sample-rate) (pos? sample-rate))
             sample-rate
             44100)
        nyquist (/ sr 2)
        safe-bins-per-octave (int (clamp (setting-number bins-per-octave 24) 1 96))
        safe-min-frequency (clamp (setting-number min-frequency 55) 1 nyquist)
        safe-max-frequency (clamp (setting-number max-frequency 7040) safe-min-frequency nyquist)]
    (assoc merged
           :sample-rate sr
           :bins-per-octave safe-bins-per-octave
           :min-frequency safe-min-frequency
           :max-frequency safe-max-frequency
           :threshold-db (clamp (setting-number (:threshold-db merged) -52) -120 0)
           :neighbor-bins (int (clamp (setting-number (:neighbor-bins merged) 1) 0 6))
           :harmonic-count (int (clamp (setting-number harmonic-count 5) 1 16))
           :max-traces (int (clamp (setting-number max-traces 28) 1 96))
           :cycles (clamp (setting-number cycles 3) 0.5 12)
           :amplitude-gain (clamp (setting-number (:amplitude-gain merged) 3.5) 0.1 30)
           :line-width (clamp (setting-number (:line-width merged) 2) 1 8)
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
      (swap! window-sum + (make-window-value n window-size)))
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

(defn- component-at
  [sample-buffer max-window-size {:keys [window-size cos-kernel sin-kernel]}]
  (let [sample-count (.-length sample-buffer)
        frame-start (max 0 (- sample-count max-window-size))
        offset (int (js/Math.floor (/ (- max-window-size window-size) 2)))
        start (+ frame-start offset)]
    (loop [i 0
           real 0
           imag 0]
      (if (< i window-size)
        (let [sample (or (aget sample-buffer (+ start i)) 0)]
          (recur (inc i)
                 (+ real (* sample (aget cos-kernel i)))
                 (- imag (* sample (aget sin-kernel i)))))
        [real imag]))))

(defn- compute-cqt-components
  [sample-buffer {:keys [kernels max-window-size]}]
  (let [bin-count (count kernels)
        mags (js/Float32Array. bin-count)
        real-values (js/Float32Array. bin-count)
        imag-values (js/Float32Array. bin-count)]
    (doseq [[bin-index kernel] (map-indexed vector kernels)]
      (let [[real imag] (component-at sample-buffer max-window-size kernel)]
        (aset real-values bin-index real)
        (aset imag-values bin-index imag)
        (aset mags bin-index (js/Math.sqrt (+ (* real real) (* imag imag))))))
    {:mags mags
     :real real-values
     :imag imag-values}))

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
;; Band selection and pitch helpers
;; ============================================================================

(defn- magnitude->db
  [mag]
  (* 20 (js/Math.log10 (max mag 1.0e-12))))

(defn- local-peak?
  [mags index]
  (let [value (aget mags index)
        left (if (pos? index) (aget mags (dec index)) 0)
        right (if (< (inc index) (.-length mags)) (aget mags (inc index)) 0)]
    (and (> value left)
         (>= value right))))

(defn- summed-magnitude
  [mags index radius]
  (let [start (max 0 (- index radius))
        end (min (dec (.-length mags)) (+ index radius))]
    (loop [i start
           sum 0]
      (if (<= i end)
        (recur (inc i) (+ sum (aget mags i)))
        sum))))

(defn- refined-bin-index
  [mags index]
  (if (and (pos? index) (< (inc index) (.-length mags)))
    (let [left (magnitude->db (aget mags (dec index)))
          center (magnitude->db (aget mags index))
          right (magnitude->db (aget mags (inc index)))
          denom (+ left right (- (* 2 center)))]
      (if (< (js/Math.abs denom) 1.0e-9)
        index
        (+ index (clamp (* 0.5 (/ (- left right) denom)) -0.5 0.5))))
    index))

(defn- select-bands
  [mags {:keys [threshold-db selection-mode neighbor-bins max-traces]}]
  (let [bin-count (.-length mags)
        thresholded (for [i (range bin-count)
                          :let [mag (aget mags i)
                                db (magnitude->db mag)]
                          :when (>= db threshold-db)]
                      {:index i
                       :magnitude mag
                       :db db
                       :score (case selection-mode
                                :neighbor-sum (summed-magnitude mags i neighbor-bins)
                                mag)})]
    (->> thresholded
         (filter (fn [{:keys [index]}]
                   (case selection-mode
                     :active true
                     :peaks (local-peak? mags index)
                     :neighbor-sum (local-peak? mags index)
                     (local-peak? mags index))))
         (sort-by :score >)
         (take max-traces)
         (sort-by :index)
         vec)))

(defn- frequency->y
  [frequency {:keys [min-frequency max-frequency]} height]
  (let [span (max 1.0e-9 (/ (js/Math.log (/ max-frequency min-frequency))
                            (js/Math.log 2)))
        octave-pos (/ (/ (js/Math.log (/ frequency min-frequency))
                         (js/Math.log 2))
                      span)]
    (* (- 1 (clamp octave-pos 0 1)) height)))

(defn- nearest-bin-index
  [frequency {:keys [min-frequency bins-per-octave]} bin-count]
  (int (clamp (js/Math.round (* bins-per-octave
                                 (/ (js/Math.log (/ frequency min-frequency))
                                    (js/Math.log 2))))
              0
              (dec bin-count))))

(defn- freq->note-label
  [frequency]
  (if (pos? frequency)
    (let [midi (+ 69 (* 12 (/ (js/Math.log (/ frequency 440))
                              (js/Math.log 2))))
          nearest (js/Math.round midi)
          cents (* 100 (- midi nearest))
          octave (- (js/Math.floor (/ nearest 12)) 1)
          note (get note-names (mod nearest 12))
          cents-text (str (if (neg? cents) "" "+")
                          (.toFixed cents 0)
                          "c")]
      (str note octave " " cents-text))
    ""))

;; ============================================================================
;; Color and drawing helpers
;; ============================================================================

(defn- hsi-css
  [h saturation intensity]
  (let [h (mod h 360)
        s (clamp saturation 0 1)
        i (clamp intensity 0 1)
        sector-h (mod h 120)
        radians (/ (* sector-h js/Math.PI) 180)
        denominator (max 1.0e-9 (js/Math.cos (- (/ js/Math.PI 3) radians)))
        boosted (* i (+ 1 (/ (* s (js/Math.cos radians)) denominator)))
        dimmed (* i (- 1 s))
        third (- (* 3 i) boosted dimmed)
        [r g b] (cond
                  (< h 120) [boosted third dimmed]
                  (< h 240) [dimmed boosted third]
                  :else [third dimmed boosted])]
    (str "rgb(" (int (* 255 (clamp r 0 1))) ","
         (int (* 255 (clamp g 0 1))) ","
         (int (* 255 (clamp b 0 1))) ")")))

(defn- trace-color-for
  [frequency {:keys [color-mode min-frequency trace-color]}]
  (case color-mode
    :hsi-octave
    (let [octave-phase (mod (/ (js/Math.log (/ frequency min-frequency))
                               (js/Math.log 2))
                            1)
          hue (* 360 octave-phase)]
      (hsi-css hue 0.82 0.52))
    trace-color))

(defn- draw-grid!
  [ctx width height {:keys [min-frequency max-frequency grid-color label-color]}]
  (set! (.-strokeStyle ctx) grid-color)
  (set! (.-lineWidth ctx) 1)
  (set! (.-font ctx) "11px ui-monospace, SFMono-Regular, Menlo, monospace")
  (set! (.-fillStyle ctx) label-color)
  (let [start-octave (js/Math.ceil (/ (js/Math.log (/ min-frequency 55))
                                      (js/Math.log 2)))
        end-octave (js/Math.floor (/ (js/Math.log (/ max-frequency 55))
                                     (js/Math.log 2)))]
    (doseq [octave (range start-octave (inc end-octave))]
      (let [frequency (* 55 (js/Math.pow 2 octave))
            y (+ 0.5 (frequency->y frequency
                                    {:min-frequency min-frequency
                                     :max-frequency max-frequency}
                                    height))]
        (.beginPath ctx)
        (.moveTo ctx 0 y)
        (.lineTo ctx width y)
        (.stroke ctx)
        (.fillText ctx (str (.toFixed frequency 0) " Hz") 8 (- y 4))))))

(defn- normalize-partials
  [partials]
  (let [sum (reduce + (map #(js/Math.sqrt (+ (* (:real %) (:real %))
                                             (* (:imag %) (:imag %))))
                           partials))]
    (max sum 1.0e-9)))

(defn- build-partials
  [base-frequency {:keys [kernels]} {:keys [real imag]} settings]
  (let [{:keys [harmonic-count neighbor-bins max-frequency selection-mode]} settings
        bin-count (count kernels)]
    (vec
     (for [harmonic (range 1 (inc harmonic-count))
           :let [target-frequency (* base-frequency harmonic)]
           :while (<= target-frequency max-frequency)
           :let [center-index (nearest-bin-index target-frequency settings bin-count)
                 radius (if (= selection-mode :neighbor-sum) neighbor-bins 0)]
           bin-index (range (max 0 (- center-index radius))
                            (inc (min (dec bin-count) (+ center-index radius))))
           :let [kernel (get kernels bin-index)]]
       {:frequency (:frequency kernel)
        :real (aget real bin-index)
        :imag (aget imag bin-index)
        :weight (/ 1 harmonic)}))))

(defn- partial-value
  [{:keys [frequency real imag weight]} time-offset]
  (let [phase (* 2 js/Math.PI frequency time-offset)]
    (* weight (+ (* real (js/Math.cos phase))
                 (* (- imag) (js/Math.sin phase))))))

(defn- draw-trace!
  [ctx width height band kernels-state components settings]
  (let [{:keys [mags]} components
        {:keys [bins-per-octave min-frequency cycles line-width amplitude-gain label-color]}
        settings
        base-index (:index band)
        refined-index (refined-bin-index mags base-index)
        base-frequency (bin-frequency min-frequency bins-per-octave refined-index)
        y (frequency->y base-frequency settings height)
        label-width 108
        right-pad 14
        trace-width (max 24 (- width label-width right-pad))
        partials (build-partials base-frequency kernels-state components settings)
        norm (normalize-partials partials)
        row-scale (* amplitude-gain 18)
        color (trace-color-for base-frequency settings)]
    (when (seq partials)
      (set! (.-strokeStyle ctx) color)
      (set! (.-lineWidth ctx) line-width)
      (set! (.-lineCap ctx) "round")
      (set! (.-lineJoin ctx) "round")
      (.beginPath ctx)
      (doseq [x (range trace-width)]
        (let [t (/ x (max 1 (dec trace-width)))
              time-offset (/ (* t cycles) base-frequency)
              sample-value (/ (reduce + (map #(partial-value % time-offset) partials))
                              norm)
              px (+ label-width x)
              py (- y (* row-scale sample-value))]
          (if (zero? x)
            (.moveTo ctx px py)
            (.lineTo ctx px py))))
      (.stroke ctx)
      (set! (.-fillStyle ctx) label-color)
      (set! (.-font ctx) "11px ui-monospace, SFMono-Regular, Menlo, monospace")
      (.fillText ctx
                 (str (.toFixed base-frequency 1) " Hz")
                 8
                 (- y 4))
      (.fillText ctx
                 (freq->note-label base-frequency)
                 8
                 (+ y 10)))))

(defn- clear-canvas!
  [canvas-element background-color]
  (let [ctx (interop/get-canvas-context canvas-element)
        width (interop/get-canvas-width canvas-element)
        height (interop/get-canvas-height canvas-element)]
    (set! (.-globalAlpha ctx) 1)
    (set! (.-fillStyle ctx) background-color)
    (.fillRect ctx 0 0 width height)))

(def analysis-setting-keys
  [:bins-per-octave
   :min-frequency
   :max-frequency])

;; ============================================================================
;; Visualizer record
;; ============================================================================

(defrecord PolyphonicOscilloscopeVisualizer
  [settings
   sample-buffer
   last-sample-totals
   kernel-state]

  protocol/IVisualizer

  (render [this canvas-element sample-puller]
    (let [eff-settings (effective-settings settings (state/get-sample-rate))
          ctx (interop/get-canvas-context canvas-element)
          canvas-width (interop/get-canvas-width canvas-element)
          canvas-height (interop/get-canvas-height canvas-element)
          {:keys [max-window-size] :as kernels-state} (ensure-kernels! kernel-state eff-settings)
          display-samples (int (js/Math.ceil (* (:sample-rate eff-settings)
                                                (/ (:cycles eff-settings)
                                                   (:min-frequency eff-settings)))))
          keep-size (+ max-window-size display-samples)]
      (when (and (pos? canvas-width) (pos? canvas-height) (pos? max-window-size))
        (let [current-totals (current-sample-totals sample-puller)]
          (if (or (nil? @last-sample-totals)
                  (some true? (map < current-totals @last-sample-totals)))
            (reset! last-sample-totals current-totals)
            (let [new-samples (pull-new-mono-samples sample-puller @last-sample-totals)]
              (reset! last-sample-totals current-totals)
              (append-samples! @sample-buffer new-samples)
              (trim-sample-buffer! @sample-buffer keep-size)))))
      (clear-canvas! canvas-element (:background-color eff-settings))
      (draw-grid! ctx canvas-width canvas-height eff-settings)
      (when (>= (.-length @sample-buffer) max-window-size)
        (let [components (compute-cqt-components @sample-buffer kernels-state)
              bands (select-bands (:mags components) eff-settings)]
          (doseq [band bands]
            (draw-trace! ctx
                         canvas-width
                         canvas-height
                         band
                         kernels-state
                         components
                         eff-settings))))))

  (update-settings [this new-settings]
    (let [old-settings (effective-settings settings (state/get-sample-rate))
          next-settings (merge settings new-settings)
          next-effective (effective-settings next-settings (state/get-sample-rate))]
      (when (not= (select-keys old-settings analysis-setting-keys)
                  (select-keys next-effective analysis-setting-keys))
        (set! (.-length @sample-buffer) 0)
        (reset! kernel-state nil)
        (reset! last-sample-totals nil))
      (assoc this :settings next-settings)))

  (get-settings [this]
    settings))

(defn create-polyphonic-oscilloscope-visualizer
  "Create a new Polyphonic Oscilloscope visualizer."
  [& {:keys [bins-per-octave min-frequency max-frequency threshold-db selection-mode
             neighbor-bins harmonic-count max-traces cycles amplitude-gain line-width
             color-mode background-color grid-color label-color trace-color]}]
  (->PolyphonicOscilloscopeVisualizer
   (cond-> {}
     (some? bins-per-octave) (assoc :bins-per-octave bins-per-octave)
     (some? min-frequency) (assoc :min-frequency min-frequency)
     (some? max-frequency) (assoc :max-frequency max-frequency)
     (some? threshold-db) (assoc :threshold-db threshold-db)
     (some? selection-mode) (assoc :selection-mode selection-mode)
     (some? neighbor-bins) (assoc :neighbor-bins neighbor-bins)
     (some? harmonic-count) (assoc :harmonic-count harmonic-count)
     (some? max-traces) (assoc :max-traces max-traces)
     (some? cycles) (assoc :cycles cycles)
     (some? amplitude-gain) (assoc :amplitude-gain amplitude-gain)
     (some? line-width) (assoc :line-width line-width)
     (some? color-mode) (assoc :color-mode color-mode)
     (some? background-color) (assoc :background-color background-color)
     (some? grid-color) (assoc :grid-color grid-color)
     (some? label-color) (assoc :label-color label-color)
     (some? trace-color) (assoc :trace-color trace-color))
   (atom (array))
   (atom nil)
   (atom nil)))
