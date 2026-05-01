(ns visualizers.protocol
  "Visualizer protocol defining the interface for audio visualizers.")

;; ============================================================================
;; IVisualizer Protocol
;; ============================================================================

(defprotocol IVisualizer
  "Protocol for audio visualization implementations."
  
  (render [this canvas-element sample-puller]
    "Render visualization to a canvas element.
     
     Args:
     - canvas-element: HTMLCanvasElement to draw to
     - sample-puller: SamplePuller instance providing audio samples
     
     Returns: nil (side-effects only)")
  
  (update-settings [this settings]
    "Update visualizer settings and return modified visualizer.
     
     Args:
     - settings: Map of new settings
     
     Returns: New visualizer instance (or self if unchanged)")
  
  (get-settings [this]
    "Get current visualizer settings.
     
     Returns: Settings map"))
