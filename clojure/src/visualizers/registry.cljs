(ns visualizers.registry
  "Visualizer registry and factory functions.
   
   Maintains a registry of available visualizers and provides factory functions
   to create instances on demand."
  (:require [visualizers.stft :as stft]
            [visualizers.waveform :as waveform]))

;; ============================================================================
;; Visualizer Registry
;; ============================================================================

(def visualizer-registry
  "Registry of available visualizers.
   
   Maps visualizer type keywords to factory functions."
  {:stft     {:name "STFT Spectrogram"
              :factory #(stft/create-stft-visualizer %)}
   :waveform {:name "Waveform"
              :factory #(waveform/create-waveform-visualizer %)}})

;; ============================================================================
;; Factory Functions
;; ============================================================================

(defn create-visualizer
  "Create a visualizer instance of a specific type.
   
   Args:
   - visualizer-type: Keyword identifying the visualizer (:stft, :waveform)
   - options: Map of options to pass to the visualizer constructor
   
   Returns: Visualizer instance, or nil if type not found"
  [visualizer-type & {:as options}]
  (if-let [entry (get visualizer-registry visualizer-type)]
    (let [factory (:factory entry)]
      (factory options))
    (do
      (.warn js/console "Unknown visualizer type:" visualizer-type)
      nil)))

(defn get-available-visualizers
  "Get list of available visualizer types and names.
   
   Returns: List of {:type keyword :name string} maps"
  []
  (mapv (fn [[type entry]]
          {:type type
           :name (:name entry)})
        visualizer-registry))

(defn get-visualizer-name
  "Get the friendly name for a visualizer type.
   
   Args:
   - visualizer-type: Keyword identifying the visualizer
   
   Returns: Name string or nil if not found"
  [visualizer-type]
  (:name (get visualizer-registry visualizer-type)))
