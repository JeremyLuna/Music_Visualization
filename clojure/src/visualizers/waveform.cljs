(ns visualizers.waveform
  "Time-domain waveform visualizer.
   
   Displays raw audio samples as a line drawing on a canvas."
  (:require [visualizers.protocol :as protocol]
            [audio.interop :as interop]
            [audio.sample-puller :as puller]))

;; ============================================================================
;; Settings and sample preparation
;; ============================================================================

(def default-settings
  {:buffer-size 2048
   :line-color "#00ff00"
   :line-width 2
   :background-color "white"
   :baseline-color "#d8d8d8"})

(defn- effective-settings
  [settings]
  (merge default-settings settings))

(defn- mono-samples
  [sample-puller buffer-size]
  (let [channel-count (puller/get-channel-count sample-puller)
        channels (mapv #(puller/pull-channel-samples sample-puller % :max-samples buffer-size)
                       (range channel-count))
        sample-count (if (seq channels)
                       (apply min (map #(.-length %) channels))
                       0)
        output (js/Float32Array. sample-count)]
    (when (pos? sample-count)
      (doseq [i (range sample-count)]
        (let [sum (reduce + (map #(aget % i) channels))]
          (aset output i (/ sum channel-count)))))
    output))

;; ============================================================================
;; Waveform Visualizer Record and Implementation
;; ============================================================================

(defrecord WaveformVisualizer
  [settings]  ;; User-provided settings; defaults are applied while rendering.
  
  protocol/IVisualizer
  
  (render [this canvas-element sample-puller]
    (let [ctx (interop/get-canvas-context canvas-element)
          canvas-width (interop/get-canvas-width canvas-element)
          canvas-height (interop/get-canvas-height canvas-element)
          {:keys [buffer-size line-color line-width background-color baseline-color]} (effective-settings settings)
          samples (mono-samples sample-puller buffer-size)
          samples-len (.-length samples)
          center-y (/ canvas-height 2)
          y-scale (* (/ canvas-height 2) 0.85)]
      (set! (.-fillStyle ctx) background-color)
      (.fillRect ctx 0 0 canvas-width canvas-height)

      ;; Draw a center line so silence still gives visual feedback.
      (set! (.-strokeStyle ctx) baseline-color)
      (set! (.-lineWidth ctx) 1)
      (.beginPath ctx)
      (.moveTo ctx 0 center-y)
      (.lineTo ctx canvas-width center-y)
      (.stroke ctx)

      (when (pos? samples-len)
        (set! (.-strokeStyle ctx) line-color)
        (set! (.-lineWidth ctx) line-width)
        (set! (.-lineCap ctx) "round")
        (set! (.-lineJoin ctx) "round")
        (.beginPath ctx)
        (doseq [i (range samples-len)]
          (let [sample (aget samples i)
                x (if (= samples-len 1)
                    0
                    (* (/ i (dec samples-len)) canvas-width))
                y (-> (- center-y (* sample y-scale))
                      (js/Math.max 0)
                      (js/Math.min (dec canvas-height)))]
            (if (zero? i)
              (.moveTo ctx x y)
              (.lineTo ctx x y))))
        (.stroke ctx))))
  
  (update-settings [this new-settings]
    (assoc this :settings (merge settings new-settings)))
  
  (get-settings [this]
    settings))

(defn create-waveform-visualizer
  "Create a new waveform visualizer.
   
   Args:
   - buffer-size: Number of samples to display (default 2048)
   - line-color: CSS color string (default \"#00ff00\")
   - line-width: Line width in pixels (default 2)
   
   Returns: WaveformVisualizer instance"
  [& {:keys [buffer-size line-color line-width]
      :as options}]
  (->WaveformVisualizer
   (cond-> {}
     (some? buffer-size) (assoc :buffer-size buffer-size)
     (some? line-color) (assoc :line-color line-color)
     (some? line-width) (assoc :line-width line-width)
     (:background-color options) (assoc :background-color (:background-color options))
     (:baseline-color options) (assoc :baseline-color (:baseline-color options)))))
