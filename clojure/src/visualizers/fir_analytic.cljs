(ns visualizers.fir-analytic
  "Streaming FIR Hilbert analytic-signal visualizer.

   Builds an odd-symmetric, Hamming-windowed Hilbert transformer and plots the
   delayed real branch against the Hilbert branch in the complex plane."
  (:require [app.theme :as theme]
            [visualizers.protocol :as protocol]
            [audio.interop :as interop]
            [audio.sample-puller :as puller]))

;; ============================================================================
;; Settings and FIR kernel
;; ============================================================================

(def default-settings
  {:kernel-size 1023
   :window-size 1023
   :draw-count 511
   :draw-position :center
   :min-scale-magnitude 0.05
   :max-zoom 8
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

(defn- clamp
  [value min-value max-value]
  (-> value
      (max min-value)
      (min max-value)))

(defn- odd-size
  [value]
  (let [n (int (clamp (or value 1023) 3 4095))]
    (if (odd? n) n (dec n))))

(defn- effective-settings
  [settings]
  (let [{:keys [kernel-size window-size draw-count] :as merged}
        (merge default-settings settings)
        kernel-size (odd-size kernel-size)
        window-size (int (clamp (or window-size 1023) 3 8192))
        draw-count (int (clamp (or draw-count 511) 2 window-size))
        min-scale-magnitude (clamp (or (:min-scale-magnitude merged) 0.05)
                                   1.0e-4
                                   1)
        max-zoom (clamp (or (:max-zoom merged) 8) 1 64)]
    (assoc merged
           :kernel-size kernel-size
           :window-size window-size
           :draw-count draw-count
           :min-scale-magnitude min-scale-magnitude
           :max-zoom max-zoom)))

(defn- hamming
  [n size]
  (- 0.54 (* 0.46 (js/Math.cos (/ (* 2 js/Math.PI n)
                                   (dec size))))))

(defn- build-hilbert-kernel
  [kernel-size]
  (let [mid (bit-shift-right kernel-size 1)
        kernel (js/Float32Array. kernel-size)]
    (doseq [i (range kernel-size)]
      (let [offset (- i mid)
            coefficient (if (or (zero? offset) (even? offset))
                          0
                          (* (/ 2 (* js/Math.PI offset))
                             (hamming i kernel-size)))]
        (aset kernel i coefficient)))
    {:kernel kernel
     :mid mid}))

(defn- cached-kernel
  [kernel-cache kernel-size]
  (let [entry @kernel-cache]
    (if (= (:kernel-size entry) kernel-size)
      entry
      (let [next-entry (assoc (build-hilbert-kernel kernel-size)
                              :kernel-size kernel-size)]
        (reset! kernel-cache next-entry)
        next-entry))))

;; ============================================================================
;; Sample preparation
;; ============================================================================

(defn- mono-samples
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

(defn- finite-number?
  [value]
  (and (number? value) (js/isFinite value)))

(defn- convolve-at
  [samples source-index kernel]
  (loop [k 0
         sum 0]
    (if (< k (.-length kernel))
      (recur (inc k)
             (+ sum (* (aget kernel k)
                       (aget samples (- source-index k)))))
      sum)))

(defn- analytic-points
  "Return an interleaved Float32Array: real0, imag0, real1, imag1..."
  [samples {:keys [kernel mid]} {:keys [window-size draw-count draw-position]}]
  (let [kernel-size (.-length kernel)
        sample-count (.-length samples)
        valid-count (max 0 (- sample-count (dec kernel-size)))
        window-count (min window-size valid-count)
        draw-count (min draw-count window-count)
        window-start (- valid-count window-count)
        draw-offset (case draw-position
                      :newest (- window-count draw-count)
                      :center (js/Math.floor (/ (- window-count draw-count) 2))
                      (js/Math.floor (/ (- window-count draw-count) 2)))
        valid-start (+ window-start draw-offset)
        points (js/Float32Array. (* draw-count 2))]
    (doseq [i (range draw-count)]
      (let [source-index (+ (dec kernel-size) valid-start i)
            real (aget samples (- source-index mid))
            imag (convolve-at samples source-index kernel)
            point-index (* i 2)]
        (aset points point-index real)
        (aset points (inc point-index) imag)))
    points))

;; ============================================================================
;; Drawing
;; ============================================================================

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

(defn- max-magnitude
  [points]
  (loop [i 0
         max-value 0]
    (if (< i (.-length points))
      (let [x (aget points i)
            y (aget points (inc i))
            magnitude (if (and (finite-number? x) (finite-number? y))
                        (js/Math.sqrt (+ (* x x) (* y y)))
                        0)]
        (recur (+ i 2) (max max-value magnitude)))
      max-value)))

(defn- draw-points!
  [ctx canvas-width canvas-height points
   {:keys [line-color line-width min-scale-magnitude max-zoom]}
   previous-scale]
  (let [point-count (/ (.-length points) 2)]
    (when (> point-count 1)
      (let [center-x (/ canvas-width 2)
            center-y (/ canvas-height 2)
            radius (* (min center-x center-y) 0.88)
            magnitude (max-magnitude points)
            target-scale (min (/ radius (max magnitude min-scale-magnitude))
                              (* radius max-zoom))
            scale (if previous-scale
                    (+ (* previous-scale 0.86) (* target-scale 0.14))
                    target-scale)]
        (set! (.-strokeStyle ctx) line-color)
        (set! (.-lineWidth ctx) line-width)
        (set! (.-lineCap ctx) "round")
        (set! (.-lineJoin ctx) "round")
        (.beginPath ctx)
        (doseq [i (range point-count)]
          (let [point-index (* i 2)
                real (aget points point-index)
                imag (aget points (inc point-index))
                x (+ center-x (* real scale))
                y (- center-y (* imag scale))]
            (when (and (finite-number? real)
                       (finite-number? imag)
                       (finite-number? x)
                       (finite-number? y))
              (if (zero? i)
                (.moveTo ctx x y)
                (.lineTo ctx x y)))))
        (.stroke ctx)
        scale))))

;; ============================================================================
;; Visualizer record
;; ============================================================================

(defrecord FirAnalyticSignalVisualizer
  [settings
   kernel-cache
   scale-state]

  protocol/IVisualizer

  (render [this canvas-element sample-puller]
    (let [ctx (interop/get-canvas-context canvas-element)
          canvas-width (interop/get-canvas-width canvas-element)
          canvas-height (interop/get-canvas-height canvas-element)
          {:keys [kernel-size window-size background-color grid-color axis-color]
           :as eff-settings} (effective-settings settings)
          kernel-entry (cached-kernel kernel-cache kernel-size)
          pull-count (+ window-size (dec kernel-size))
          samples (mono-samples sample-puller pull-count)
          points (analytic-points samples kernel-entry eff-settings)]
      (set! (.-globalAlpha ctx) 1)
      (set! (.-fillStyle ctx) background-color)
      (.fillRect ctx 0 0 canvas-width canvas-height)
      (draw-grid! ctx canvas-width canvas-height grid-color axis-color)
      (when-let [next-scale (draw-points! ctx
                                          canvas-width
                                          canvas-height
                                          points
                                          eff-settings
                                          @scale-state)]
        (reset! scale-state next-scale))))

  (update-settings [this new-settings]
    (assoc this :settings (merge settings new-settings)))

  (get-settings [this]
    settings))

(defn create-fir-analytic-signal-visualizer
  "Create a finite-Hilbert-transform analytic-signal visualizer."
  [& {:keys [kernel-size window-size draw-count draw-position line-color line-width
             background-color grid-color axis-color]}]
  (->FirAnalyticSignalVisualizer
   (cond-> {}
     (some? kernel-size) (assoc :kernel-size kernel-size)
     (some? window-size) (assoc :window-size window-size)
     (some? draw-count) (assoc :draw-count draw-count)
     (some? draw-position) (assoc :draw-position draw-position)
     (some? line-width) (assoc :line-width line-width)
     (some? line-color) (assoc :line-color line-color)
     (some? background-color) (assoc :background-color background-color)
     (some? grid-color) (assoc :grid-color grid-color)
     (some? axis-color) (assoc :axis-color axis-color))
   (atom nil)
   (atom nil)))
