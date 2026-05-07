(ns app.core
  "Root component and entry point for the Music Visualization app."
  (:require [reagent.core :as r]
            [reagent.dom.client :as rdom-client]
            [app.state :as state]
            [app.theme :as theme]
            [canvas.view :as canvas-view]
            [ui.control-panel :as control-panel]))

;; ============================================================================
;; Root App Component
;; ============================================================================

(def inactivity-timeout-ms 2000)

(def boxy-theme-css
  ".theme-shape-boxy {
     scrollbar-width: thin;
     scrollbar-color: var(--theme-primary) var(--theme-surface-muted);
   }

   .theme-shape-boxy * {
     scrollbar-width: thin;
     scrollbar-color: var(--theme-primary) var(--theme-surface-muted);
   }

   .theme-shape-boxy ::-webkit-scrollbar {
     width: 12px;
     height: 12px;
   }

   .theme-shape-boxy ::-webkit-scrollbar-track {
     background: var(--theme-surface-muted);
     border-radius: 0;
   }

   .theme-shape-boxy ::-webkit-scrollbar-thumb {
     background: var(--theme-primary);
     border: 2px solid var(--theme-surface-muted);
     border-radius: 0;
   }

   .theme-shape-boxy .themed-range {
     appearance: none;
     -webkit-appearance: none;
     height: 18px;
     background: transparent;
   }

   .theme-shape-boxy .themed-range::-webkit-slider-runnable-track {
     height: 8px;
     background: var(--theme-surface-muted);
     border: 1px solid var(--theme-border);
     border-radius: 0;
   }

   .theme-shape-boxy .themed-range::-webkit-slider-thumb {
     appearance: none;
     -webkit-appearance: none;
     width: 16px;
     height: 16px;
     margin-top: -5px;
     background: var(--theme-primary);
     border: 1px solid var(--theme-text);
     border-radius: 0;
   }

   .theme-shape-boxy .themed-range::-moz-range-track {
     height: 8px;
     background: var(--theme-surface-muted);
     border: 1px solid var(--theme-border);
     border-radius: 0;
   }

   .theme-shape-boxy .themed-range::-moz-range-thumb {
     width: 16px;
     height: 16px;
     background: var(--theme-primary);
     border: 1px solid var(--theme-text);
     border-radius: 0;
   }")

(defn app-root []
  (let [timeout-id (atom nil)
        clear-timeout! (fn []
                         (when @timeout-id
                           (js/clearTimeout @timeout-id)
                           (reset! timeout-id nil)))
        schedule-hide! (fn []
                         (clear-timeout!)
                         (reset! timeout-id
                                 (js/setTimeout
                                  (fn []
                                    (when-not (get-in @state/app-state [:ui :show-control-panel])
                                      (state/dispatch :set-interaction-active false)))
                                  inactivity-timeout-ms)))
        mark-active! (fn []
                       (state/dispatch :set-interaction-active true)
                       (schedule-hide!))]
    (r/create-class
     {:display-name "app-root"
      :component-did-mount
      (fn []
        (mark-active!)
        (js/document.addEventListener "mousemove" mark-active!)
        (add-watch state/app-state :ui-inactivity
                   (fn [_ _ old-state new-state]
                     (let [was-open? (get-in old-state [:ui :show-control-panel])
                           open? (get-in new-state [:ui :show-control-panel])]
                       (cond
                         (and was-open? (not open?)) (mark-active!)
                         (and (not was-open?) open?) (state/dispatch :set-interaction-active true))))))
      :component-will-unmount
      (fn []
        (js/document.removeEventListener "mousemove" mark-active!)
        (remove-watch state/app-state :ui-inactivity)
        (clear-timeout!))
      :reagent-render
      (fn []
        (let [theme-state (get-in @state/app-state [:ui :theme])
              colors (theme/colors theme-state)
              boxy? (= (:shape (theme/effective-theme theme-state)) :boxy)]
          [:div {:class (when boxy? "theme-shape-boxy")
                 :style {:display "flex"
                         :flex-direction "column"
                         :height "100vh"
                         :font-family (theme/font-family theme-state)
                         :background (:app-background colors)
                         :color (:text colors)
                         "--theme-primary" (:primary colors)
                         "--theme-surface-muted" (:surface-muted colors)
                         "--theme-border" (:border colors)
                         "--theme-text" (:text colors)}}
           [:style boxy-theme-css]
         [:div {:style {:display "flex" :flex 1 :overflow "hidden"}}
          ;; Main content: Canvas area (full width)
          [:div {:style {:flex 1
                         :overflow "auto"
                         :background (:app-background colors)
                         :position "relative"}}
           [canvas-view/canvas-manager]]]

         ;; Control panel (side panel)
         [control-panel/control-panel]]))})))

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
