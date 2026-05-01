(ns app.core
  "Root component and entry point for the Music Visualization app."
  (:require [reagent.core :as r]
            [app.state :as state]
            [canvas.view :as canvas-view]
            [ui.control-panel :as control-panel]))

;; ============================================================================
;; Root App Component
;; ============================================================================

(defn app-root []
  [:div {:style {:display "flex" :flex-direction "column" :height "100vh" :font-family "sans-serif"}}
   [:header {:style {:background "#333" :color "white" :padding "10px"}}
    [:h1 "Music Visualization (ClojureScript)"]]
   
   [:div {:style {:display "flex" :flex 1 :overflow "hidden"}}
    ;; Main content: Canvas area (full width)
    [:div {:style {:flex 1 :overflow "auto" :background "#f5f5f5" :position "relative"}}
     [canvas-view/canvas-manager]]]
   
   ;; Control panel (side panel)
   [control-panel/control-panel]
   
   [:footer {:style {:background "#333" :color "white" :padding "5px" :text-align "center" :font-size "12px"}}
    "Status: ClojureScript implementation in progress"]])

;; ============================================================================
;; Initialization
;; ============================================================================

(defn ^:export init []
  "Initialize and mount the app. Called by Shadow-cljs on page load."
  (r/render [app-root] (js/document.getElementById "app")))
