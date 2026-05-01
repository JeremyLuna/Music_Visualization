(ns visualizers.waveform
  "Time-domain waveform visualizer.
   
   Displays raw audio samples as a line drawing on a canvas."
  (:require [visualizers.protocol :as protocol]
            [audio.interop :as interop]
            [audio.sample-puller :as puller]))

;; ============================================================================
;; Waveform Visualizer Record and Implementation
;; ============================================================================

(defrecord WaveformVisualizer
  [settings]  ;; {:buffer-size 2048, :line-color "#00ff00", :line-width 1}
  
  protocol/IVisualizer
  
  (render [this canvas-element sample-puller]
    (let [ctx (interop/get-canvas-context canvas-element)
          canvas-width (interop/get-canvas-width canvas-element)
          canvas-height (interop/get-canvas-height canvas-element)
          buffer-size (:buffer-size settings)
          line-color (:line-color settings "#00ff00")
          line-width (:line-width settings 1)]
      
      ;; Get audio samples from puller
      (let [samples (puller/pull-channel-samples sample-puller 0 :max-samples buffer-size)]
        
        ;; Clear canvas
        (interop/clear-canvas canvas-element)
        
        ;; Draw waveform
        (set! (.-strokeStyle ctx) line-color)
        (set! (.-lineWidth ctx) line-width)
        (set! (.-lineCap ctx) "round")
        
        ;; Draw waveform as connected line
        (.beginPath ctx)
        
        ;; Center vertical position
        (let [center-y (/ canvas-height 2)
              samples-len (.-length samples)
              ;; Scale: map sample values (-1 to 1) to pixel height
              y-scale (/ (- canvas-height 20) 2)]
          
          (doseq [i (range samples-len)]
            (let [sample (aget samples i)
                  ;; Map sample (-1 to 1) to pixel coordinates
                  x (int (* (/ i samples-len) canvas-width))
                  y (int (+ center-y (* sample y-scale)))
                  
                  ;; Clamp y to canvas bounds
                  y (js/Math.max 0 (js/Math.min (- canvas-height 1) y))]
              
              (if (= i 0)
                (.moveTo ctx x y)
                (.lineTo ctx x y)))))
        
        (.stroke ctx))))
  
  (update-settings [this new-settings]
    (->WaveformVisualizer new-settings))
  
  (get-settings [this]
    settings))

(defn create-waveform-visualizer
  "Create a new waveform visualizer.
   
   Args:
   - buffer-size: Number of samples to display (default 2048)
   - line-color: CSS color string (default \"#00ff00\")
   - line-width: Line width in pixels (default 1)
   
   Returns: WaveformVisualizer instance"
  [& {:keys [buffer-size line-color line-width]
      :or {buffer-size 2048 line-color "#00ff00" line-width 1}}]
  (->WaveformVisualizer
   {:buffer-size buffer-size
    :line-color line-color
    :line-width line-width}))
