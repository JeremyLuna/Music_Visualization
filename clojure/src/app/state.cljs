(ns app.state
  "Central state management for the music visualization app.
   
   App state structure:
   {:audio {:context nil        ;; AudioContext instance
            :player nil         ;; audio HTMLElement
            :sample-puller nil  ;; SamplePuller instance
            :sample-rate 0
            :duration 0
            :is-playing false}
    :layout {:root {:type :canvas    ;; or :split
                    :id 0
                    :orientation nil  ;; :h or :v for splits
                    :left nil
                    :right nil}
             :canvas-counter 1}
    :ui {:show-control-panel false
         :settings {}}
    :visualizers {:instances {}  ;; {canvas-id -> visualizer instance}}
    :samples {:channels {}}})"
  (:require [reagent.core :as r]))

;; Central application state atom
(defonce app-state
  (r/atom
   {:audio {:context nil
            :player nil
            :sample-puller nil
            :sample-rate 0
            :duration 0
            :is-playing false}
    :layout {:root {:type :canvas
                    :id 0
                    :orientation nil
                    :left nil
                    :right nil}
             :canvas-counter 1}
    :ui {:show-control-panel false
         :settings {}}
    :visualizers {:instances {}}
    :samples {:channels {}}}))

;; Dispatch functions for state updates
(defn dispatch
  "Apply an action to update app state."
  [action & args]
  (case action
    :init-audio
    (let [[audio-context] args]
      (swap! app-state assoc-in [:audio :context] audio-context))
    
    :set-sample-rate
    (let [[rate] args]
      (swap! app-state assoc-in [:audio :sample-rate] rate))
    
    :set-duration
    (let [[duration] args]
      (swap! app-state assoc-in [:audio :duration] duration))
    
    :set-playing
    (let [[playing] args]
      (swap! app-state assoc-in [:audio :is-playing] playing))
    
    :toggle-control-panel
    (swap! app-state update-in [:ui :show-control-panel] not)
    
    :split-canvas
    (let [[canvas-id orientation] args]
      ;; Note: Canvas split logic is handled in canvas.view
      ;; (this is a placeholder for state updates if needed)
      )
    
    :remove-canvas
    (let [[canvas-id] args]
      ;; Note: Canvas removal logic is handled in canvas.view
      ;; (this is a placeholder for state updates if needed)
      )
    
    :change-visualizer
    (let [[canvas-id visualizer-type] args]
      ;; Note: Handled in canvas.view
      )
    
    :update-visualizer-settings
    (let [[canvas-id settings] args]
      ;; Note: Handled in canvas.view
      )
    
    (do
      (.warn js/console "Unknown action:" action))))

;; Selector functions for convenient access
(defn get-audio-context []
  (:context (:audio @app-state)))

(defn get-sample-rate []
  (:sample-rate (:audio @app-state)))

(defn get-is-playing []
  (:is-playing (:audio @app-state)))

(defn get-layout-root []
  (:root (:layout @app-state)))

(defn get-next-canvas-id []
  (let [current (:canvas-counter (:layout @app-state))]
    (swap! app-state update-in [:layout :canvas-counter] inc)
    current))
