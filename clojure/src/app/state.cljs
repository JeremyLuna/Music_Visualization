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
         :interaction-active true
         :settings {}}
    :visualizers {:instances {}  ;; {canvas-id -> visualizer instance}}
    :samples {:channels {}}})"
  (:require [reagent.core :as r]
            [canvas.model :as model]))

;; Central application state atom
(defonce app-state
  (r/atom
   {:audio {:context nil
            :player nil
            :sample-puller nil
            :sample-rate 0
            :duration 0
            :current-time 0
            :volume 1.0
            :is-playing false}
    :layout {:root {:type :canvas
                    :id 0
                    :orientation nil
                    :left nil
                    :right nil}
             :canvas-counter 1}
    :ui {:show-control-panel false
         :interaction-active true
         :settings {}}
    :visualizers {:instances {}}
    :canvas-elements {}
    :samples {:channels {}}}))

(defn- canvas-ids
  [layout-root]
  (vec (model/get-all-canvas-ids layout-root)))

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

    :set-audio-player
    (let [[player] args]
      (swap! app-state assoc-in [:audio :player] player))

    :set-sample-puller
    (let [[sample-puller] args]
      (swap! app-state assoc-in [:audio :sample-puller] sample-puller))
    
    :set-duration
    (let [[duration] args]
      (swap! app-state assoc-in [:audio :duration] duration))

    :set-current-time
    (let [[current-time] args]
      (swap! app-state assoc-in [:audio :current-time] current-time))

    :set-volume
    (let [[volume] args]
      (swap! app-state assoc-in [:audio :volume] volume))
    
    :set-playing
    (let [[playing] args]
      (swap! app-state assoc-in [:audio :is-playing] playing))
    
    :toggle-control-panel
    (swap! app-state update-in [:ui :show-control-panel] not)

    :hide-control-panel
    (swap! app-state assoc-in [:ui :show-control-panel] false)

    :set-interaction-active
    (let [[active?] args]
      (swap! app-state assoc-in [:ui :interaction-active] active?))

    :register-canvas-element
    (let [[canvas-id canvas-el] args]
      (when canvas-el
        (swap! app-state assoc-in [:canvas-elements canvas-id] canvas-el)))

    :unregister-canvas-element
    (let [[canvas-id canvas-el] args]
      (swap! app-state
             (fn [s]
               (let [registered-el (get-in s [:canvas-elements canvas-id])]
                 (if (or (nil? canvas-el)
                         (identical? registered-el canvas-el))
                   (update s :canvas-elements dissoc canvas-id)
                   s)))))
    
    :split-canvas
    (let [[canvas-id orientation] args]
      (swap! app-state
             (fn [s]
               (let [next-id (get-in s [:layout :canvas-counter])
                     current-layout (get-in s [:layout :root])
                     new-layout (model/split-canvas current-layout canvas-id orientation next-id)]
                 (if (nil? new-layout)
                   s
                   (-> s
                       (assoc-in [:layout :root] new-layout)
                       (update-in [:layout :canvas-counter] inc)))))))
    
    :remove-canvas
    (let [[canvas-id] args]
      (swap! app-state
             (fn [s]
               (let [current-layout (get-in s [:layout :root])
                     new-layout (model/remove-canvas current-layout canvas-id)]
                 (if (nil? new-layout)
                   s
                   (let [remaining-ids (set (canvas-ids new-layout))]
                     (-> s
                         (assoc-in [:layout :root] new-layout)
                         (update :canvas-elements
                                 (fn [m]
                                   (into {}
                                         (filter (fn [[id _]] (contains? remaining-ids id)) m))))
                         (update-in [:visualizers :instances]
                                    (fn [m]
                                      (into {}
                                            (filter (fn [[id _]] (contains? remaining-ids id)) m)))))))))))

    :resize-split
    (let [[split-id sizes] args]
      (swap! app-state update-in [:layout :root] model/update-split-sizes split-id sizes))
    
    :change-visualizer
    (let [[canvas-id visualizer-type] args]
      (swap! app-state
             (fn [s]
               (-> s
                   (update-in [:layout :root] model/change-canvas-visualizer canvas-id visualizer-type)
                   (assoc-in [:visualizers :instances canvas-id]
                             {:type visualizer-type
                              :settings (get-in s [:visualizers :instances canvas-id :settings] {})})))))
    
    :update-visualizer-settings
    (let [[canvas-id settings] args]
      (swap! app-state
             (fn [s]
               (-> s
                   (update-in [:layout :root] model/update-canvas-settings canvas-id settings)
                   (update-in [:visualizers :instances canvas-id :settings]
                              (fn [existing]
                                (if (fn? settings)
                                  (settings (or existing {}))
                                  (merge (or existing {}) settings))))))))
    
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
