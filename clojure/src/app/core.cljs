(ns app.core
  "Root component and entry point for the Music Visualization app."
  (:require [reagent.dom.client :as rdom-client]
            [app.state :as state]
            [canvas.view :as canvas-view]
            [ui.control-panel :as control-panel]))

;; ============================================================================
;; Root App Component
;; ============================================================================

(defn app-root []
  [:div {:style {:display "flex" :flex-direction "column" :height "100vh" :font-family "sans-serif"}}
   [:div {:style {:display "flex" :flex 1 :overflow "hidden"}}
    ;; Main content: Canvas area (full width)
    [:div {:style {:flex 1 :overflow "auto" :background "#f5f5f5" :position "relative"}}
     [canvas-view/canvas-manager]]]
   
   ;; Control panel (side panel)
   [control-panel/control-panel]])

;; ============================================================================
;; Initialization
;; ============================================================================

(defonce root (atom nil))

(defn ^:export init []
  "Initialize and mount the app. Called by Shadow-cljs on page load."
  (let [app-container (js/document.getElementById "app")]
    (when (nil? @root)
      (reset! root (rdom-client/create-root app-container)))
    (rdom-client/render @root [app-root])))
