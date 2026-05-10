(ns visualizers.analytic
  "Analytic-signal phase-plane visualizer.

   Computes x[n] + j*Hilbert(x[n]) for a mono mix and plots real values on the
   x-axis and imaginary values on the y-axis."
  (:require [app.theme :as theme]
            [visualizers.protocol :as protocol]
            [audio.interop :as interop]
            [audio.sample-puller :as puller]))

;; ============================================================================
;; Settings and sample preparation
;; ============================================================================

(def default-settings
  {:sample-mode :since-last-frame
   :sample-count 2048
   :max-samples 4096
   :continuous? true
   :fade-alpha 0.08
   :line-width 2
   :line-color "#00ffff"
   :background-color "white"
   :grid-color "#d8d8d8"
   :axis-color "#9a9a9a"})

(defn theme-settings
  [theme-state]
  (let [colors (theme/colors theme-state)]
    {:background-color (:background colors)
     :grid-color (theme/mix (:background colors) (:accent-b colors) 0.25)
     :axis-color (theme/mix (:background colors) (:text colors) 0.42)
     :line-color (:accent-c colors)}))

(defn- effective-settings
  [settings]
  (merge default-settings settings))

(defn- clamp-count
  [n min-value max-value]
  (-> n
      (max min-value)
      (min max-value)))

(defn- clamp-value
  [value min-value max-value]
  (-> value
      (max min-value)
      (min max-value)))

(defn- copy-float32-array
  [samples]
  (let [output (js/Float32Array. (.-length samples))]
    (.set output samples 0)
    output))

(defn- concat-float32-arrays
  [& arrays]
  (let [segments (filter #(and (some? %) (pos? (.-length %))) arrays)
        sample-count (reduce + 0 (map #(.-length %) segments))
        output (js/Float32Array. sample-count)]
    (loop [remaining segments
           offset 0]
      (when (seq remaining)
        (let [segment (first remaining)]
          (.set output segment offset)
          (recur (rest remaining) (+ offset (.-length segment))))))
    output))

(defn- current-sample-totals
  [sample-puller]
  (mapv #(puller/get-channel-samples-written sample-puller %)
        (range (puller/get-channel-count sample-puller))))

(defn- mono-samples
  [sample-puller pull-channel-fn]
  (let [channel-count (puller/get-channel-count sample-puller)
        channels (mapv pull-channel-fn (range channel-count))
        sample-count (if (seq channels)
                       (apply min (map #(.-length %) channels))
                       0)
        output (js/Float32Array. sample-count)]
    (when (and (pos? channel-count) (pos? sample-count))
      (doseq [i (range sample-count)]
        (let [sum (reduce + (map #(aget % i) channels))]
          (aset output i (/ sum channel-count)))))
    output))

(defn- pull-fixed-mono-samples
  [sample-puller sample-count]
  (mono-samples sample-puller
                #(puller/pull-channel-samples sample-puller %
                                              :max-samples sample-count)))

(defn- pull-new-mono-samples
  [sample-puller previous-totals max-samples]
  (let [samples (mono-samples sample-puller
                              #(puller/pull-channel-samples-since
                                sample-puller
                                %
                                (get previous-totals % 0)))
        sample-count (.-length samples)]
    (if (> sample-count max-samples)
      (.subarray samples (- sample-count max-samples) sample-count)
      samples)))

;; ============================================================================
;; FFT and analytic signal computation
;; ============================================================================

(defn- next-power-of-two
  [n]
  (loop [size 1]
    (if (>= size n)
      size
      (recur (* size 2)))))

(defn- fft-in-place!
  "Cooley-Tukey radix-2 FFT. Set inverse? to true for a normalized inverse FFT."
  [re im inverse?]
  (let [n (.-length re)
        levels (/ (js/Math.log n) (js/Math.log 2))]
    (when (not= (js/Math.floor levels) levels)
      (throw (js/Error. "FFT size must be power of 2")))

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

    (loop [size 2]
      (when (<= size n)
        (let [half-size (bit-shift-right size 1)
              table-step (/ n size)
              direction (if inverse? 2 -2)
              n-factor (/ (* direction js/Math.PI) n)]
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
        (recur (bit-shift-left size 1))))

    (when inverse?
      (doseq [i (range n)]
        (aset re i (/ (aget re i) n))
        (aset im i (/ (aget im i) n))))))

(defn- analytic-signal
  [samples]
  (let [sample-count (.-length samples)
        fft-size (next-power-of-two (max 2 sample-count))
        re (js/Float32Array. fft-size)
        im (js/Float32Array. fft-size)]
    (doseq [i (range sample-count)]
      (aset re i (aget samples i)))
    (fft-in-place! re im false)
    (let [nyquist (bit-shift-right fft-size 1)]
      (doseq [i (range fft-size)]
        (cond
          (zero? i) nil
          (= i nyquist) nil
          (< i nyquist) (do
                          (aset re i (* 2 (aget re i)))
                          (aset im i (* 2 (aget im i))))
          :else (do
                  (aset re i 0)
                  (aset im i 0)))))
    (fft-in-place! re im true)
    {:real re
     :imag im
     :length sample-count}))

;; ============================================================================
;; Drawing
;; ============================================================================

(def ^:private two-pi (* 2 js/Math.PI))

(defn- phase-near
  [phase reference]
  (if (some? reference)
    (loop [p phase]
      (cond
        (> (- p reference) js/Math.PI) (recur (- p two-pi))
        (< (- p reference) (- js/Math.PI)) (recur (+ p two-pi))
        :else p))
    phase))

(defn- phase-delta
  [from-phase to-phase]
  (let [to-near (phase-near to-phase from-phase)]
    (- to-near from-phase)))

(defn- dominant-orientation
  [real imag start-index end-index]
  (loop [i (inc start-index)
         previous-phase (when (< start-index end-index)
                          (js/Math.atan2 (aget imag start-index)
                                         (aget real start-index)))
         total 0]
    (if (and previous-phase (< i end-index))
      (let [phase (js/Math.atan2 (aget imag i) (aget real i))
            delta (phase-delta previous-phase phase)]
        (recur (inc i) (phase-near phase previous-phase) (+ total delta)))
      (if (neg? total) -1 1))))

(defn- stable-analytic-points
  [real imag start-index end-index previous-phase previous-orientation]
  (let [orientation (or previous-orientation
                        (dominant-orientation real imag start-index end-index))]
    (loop [i start-index
           phase previous-phase
           points []]
      (if (< i end-index)
        (let [x (aget real i)
              y (* orientation (aget imag i))
              magnitude (js/Math.sqrt (+ (* x x) (* y y)))
              raw-phase (if (> magnitude 1.0e-8)
                          (js/Math.atan2 y x)
                          (or phase 0))
              unwrapped-phase (phase-near raw-phase phase)
              stable-phase (if (and phase (< unwrapped-phase phase))
                             phase
                             unwrapped-phase)]
          (recur (inc i)
                 stable-phase
                 (conj points [(* magnitude (js/Math.cos stable-phase))
                               (* magnitude (js/Math.sin stable-phase))])))
        {:points points
         :phase phase
         :orientation orientation}))))

(defn- draw-grid!
  [ctx canvas-width canvas-height grid-color axis-color]
  (let [columns 8
        rows 8
        center-x (/ canvas-width 2)
        center-y (/ canvas-height 2)]
    (set! (.-strokeStyle ctx) grid-color)
    (set! (.-lineWidth ctx) 1)
    (.beginPath ctx)
    (doseq [i (range (inc columns))]
      (let [x (+ 0.5 (* (/ i columns) canvas-width))]
        (.moveTo ctx x 0)
        (.lineTo ctx x canvas-height)))
    (doseq [i (range (inc rows))]
      (let [y (+ 0.5 (* (/ i rows) canvas-height))]
        (.moveTo ctx 0 y)
        (.lineTo ctx canvas-width y)))
    (.stroke ctx)

    (set! (.-strokeStyle ctx) axis-color)
    (set! (.-lineWidth ctx) 1)
    (.beginPath ctx)
    (.moveTo ctx center-x 0)
    (.lineTo ctx center-x canvas-height)
    (.moveTo ctx 0 center-y)
    (.lineTo ctx canvas-width center-y)
    (.stroke ctx)))

(defn- clear-canvas!
  [ctx canvas-width canvas-height background-color grid-color axis-color]
  (set! (.-globalAlpha ctx) 1)
  (set! (.-fillStyle ctx) background-color)
  (.fillRect ctx 0 0 canvas-width canvas-height)
  (draw-grid! ctx canvas-width canvas-height grid-color axis-color))

(defn- fade-canvas!
  [ctx canvas-width canvas-height background-color grid-color axis-color fade-alpha]
  (set! (.-globalAlpha ctx) (clamp-value fade-alpha 0 1))
  (set! (.-fillStyle ctx) background-color)
  (.fillRect ctx 0 0 canvas-width canvas-height)
  (set! (.-globalAlpha ctx) 1)
  (draw-grid! ctx canvas-width canvas-height grid-color axis-color))

(defn- max-magnitude
  [points]
  (loop [remaining points
         max-value 0]
    (if (seq remaining)
      (let [[x y] (first remaining)]
        (recur (rest remaining)
               (max max-value
                    (js/Math.sqrt (+ (* x x) (* y y))))))
      max-value)))

(defn- draw-analytic-path!
  [ctx canvas-width canvas-height points
   {:keys [line-color line-width]}
   previous-point
   previous-scale]
  (when (seq points)
    (let [center-x (/ canvas-width 2)
          center-y (/ canvas-height 2)
          radius (* (min center-x center-y) 0.88)
          magnitude (max (max-magnitude points) 1.0e-6)
          target-scale (/ radius magnitude)
          scale (if previous-scale
                  (+ (* previous-scale 0.88) (* target-scale 0.12))
                  target-scale)]
      (set! (.-strokeStyle ctx) line-color)
      (set! (.-lineWidth ctx) line-width)
      (set! (.-lineCap ctx) "round")
      (set! (.-lineJoin ctx) "round")
      (.beginPath ctx)
      (when previous-point
        (.moveTo ctx (first previous-point) (second previous-point)))
      (doseq [[i [real imag]] (map-indexed vector points)]
        (let [x (+ center-x (* real scale))
              y (- center-y (* imag scale))]
          (if (and (zero? i) (nil? previous-point))
            (.moveTo ctx x y)
            (.lineTo ctx x y))))
      (.stroke ctx)
      (let [[real imag] (last points)]
        {:point [(+ center-x (* real scale))
                 (- center-y (* imag scale))]
         :scale scale}))))

(defn- draw-analytic-range!
  [ctx canvas-width canvas-height {:keys [real imag length]}
   settings previous-point previous-scale previous-phase previous-orientation
   start-index end-index]
  (let [safe-start (clamp-count start-index 0 length)
        safe-end (clamp-count end-index safe-start length)
        {:keys [points phase orientation]} (stable-analytic-points real
                                                                   imag
                                                                   safe-start
                                                                   safe-end
                                                                   previous-phase
                                                                   previous-orientation)]
    (when-let [draw-state (draw-analytic-path! ctx
                                               canvas-width
                                               canvas-height
                                               points
                                               settings
                                               previous-point
                                               previous-scale)]
      (assoc draw-state
             :phase phase
             :orientation orientation))))

;; ============================================================================
;; Analytic Signal Visualizer Record and Implementation
;; ============================================================================

(defrecord AnalyticSignalVisualizer
  [settings
   last-sample-totals
   previous-samples
   pending-samples
   last-point
   scale-state
   phase-state
   last-canvas-state]

  protocol/IVisualizer

  (render [this canvas-element sample-puller]
    (let [ctx (interop/get-canvas-context canvas-element)
          canvas-width (interop/get-canvas-width canvas-element)
          canvas-height (interop/get-canvas-height canvas-element)
          {:keys [sample-mode sample-count max-samples continuous? fade-alpha
                  background-color grid-color axis-color]
           :as eff-settings} (effective-settings settings)
          sample-count (clamp-count sample-count 2 8192)
          max-samples (clamp-count max-samples 2 8192)
          current-totals (current-sample-totals sample-puller)
          reset-stream? (or (nil? @last-sample-totals)
                            (some true? (map < current-totals @last-sample-totals)))
          samples (case sample-mode
                    :fixed-window
                    (pull-fixed-mono-samples sample-puller sample-count)

                    :since-last-frame
                    (if reset-stream?
                      (pull-fixed-mono-samples sample-puller sample-count)
                      (pull-new-mono-samples sample-puller
                                             @last-sample-totals
                                             max-samples))

                    (pull-new-mono-samples sample-puller
                                           @last-sample-totals
                                           max-samples))
          continuous-frame? (and continuous? (= sample-mode :since-last-frame))
          canvas-state [canvas-width canvas-height background-color grid-color axis-color]
          canvas-reset? (or (not= @last-canvas-state canvas-state)
                            reset-stream?
                            (not continuous-frame?))]
      (reset! last-sample-totals current-totals)
      (when canvas-reset?
        (reset! previous-samples nil)
        (reset! pending-samples nil)
        (reset! last-point nil)
        (reset! scale-state nil)
        (reset! phase-state nil)
        (reset! last-canvas-state canvas-state))
      (if canvas-reset?
        (clear-canvas! ctx canvas-width canvas-height background-color grid-color axis-color)
        (fade-canvas! ctx canvas-width canvas-height background-color grid-color axis-color fade-alpha))
      (if continuous-frame?
        (let [pending @pending-samples
              previous @previous-samples
              pending-length (if pending (.-length pending) 0)
              current-length (.-length samples)]
          (when (and (> pending-length 0) (> current-length 0))
            (let [previous-length (if previous (.-length previous) 0)
                  combined (concat-float32-arrays previous pending samples)
                  start-index (+ previous-length (if @last-point 1 0))
                  end-index (+ previous-length pending-length 1)
                  {:keys [phase orientation]} @phase-state
                  draw-state (draw-analytic-range! ctx
                                                   canvas-width
                                                   canvas-height
                                                   (analytic-signal combined)
                                                   eff-settings
                                                   @last-point
                                                   @scale-state
                                                   phase
                                                   orientation
                                                   start-index
                                                   end-index)]
              (when draw-state
                (reset! last-point (:point draw-state))
                (reset! scale-state (:scale draw-state))
                (reset! phase-state (select-keys draw-state [:phase :orientation])))))
          (when (> current-length 0)
            (reset! previous-samples pending)
            (reset! pending-samples (copy-float32-array samples))))
        (do
          (reset! previous-samples nil)
          (reset! pending-samples nil)
          (when (> (.-length samples) 1)
            (let [draw-state (draw-analytic-range! ctx
                                                   canvas-width
                                                   canvas-height
                                                   (analytic-signal samples)
                                                   eff-settings
                                                   nil
                                                   nil
                                                   nil
                                                   nil
                                                   0
                                                   (.-length samples))]
              (when draw-state
                (reset! last-point (:point draw-state))
                (reset! scale-state (:scale draw-state))
                (reset! phase-state (select-keys draw-state [:phase :orientation])))))))))

  (update-settings [this new-settings]
    (let [old-mode (:sample-mode (effective-settings settings))
          old-continuous? (:continuous? (effective-settings settings))
          next-settings (merge settings new-settings)
          next-effective (effective-settings next-settings)
          next-mode (:sample-mode next-effective)
          next-continuous? (:continuous? next-effective)]
      (when (or (not= old-mode next-mode)
                (not= old-continuous? next-continuous?))
        (reset! last-sample-totals nil)
        (reset! previous-samples nil)
        (reset! pending-samples nil)
        (reset! last-point nil)
        (reset! scale-state nil)
        (reset! phase-state nil)
        (reset! last-canvas-state nil))
      (assoc this :settings next-settings)))

  (get-settings [this]
    settings))

(defn create-analytic-signal-visualizer
  "Create a new analytic-signal visualizer."
  [& {:keys [sample-mode sample-count max-samples continuous? fade-alpha line-color line-width
             background-color grid-color axis-color]}]
  (->AnalyticSignalVisualizer
   (cond-> {}
     (some? sample-mode) (assoc :sample-mode sample-mode)
     (some? sample-count) (assoc :sample-count sample-count)
     (some? max-samples) (assoc :max-samples max-samples)
     (some? continuous?) (assoc :continuous? continuous?)
     (some? fade-alpha) (assoc :fade-alpha fade-alpha)
     (some? line-width) (assoc :line-width line-width)
     (some? line-color) (assoc :line-color line-color)
     (some? background-color) (assoc :background-color background-color)
     (some? grid-color) (assoc :grid-color grid-color)
     (some? axis-color) (assoc :axis-color axis-color))
   (atom nil)
   (atom nil)
   (atom nil)
   (atom nil)
   (atom nil)
   (atom nil)
   (atom nil)))
