(ns canvas.controller
  "Controller for canvas layout state transitions.
   
   Orchestrates interactions between the canvas model and view,
   handling dispatch of layout changes to the app state."
  (:require [canvas.model :as model]
            [app.state :as state]))

;; ============================================================================
;; Canvas State Transitions (via state/dispatch)
;; ============================================================================

(defn split-canvas!
  "Dispatch a canvas split action.
   
   Args:
   - canvas-id: Canvas to split
   - orientation: :h or :v"
  [canvas-id orientation]
  (state/dispatch :split-canvas canvas-id orientation))

(defn remove-canvas!
  "Dispatch a canvas removal action.
   
   Args:
   - canvas-id: Canvas to remove"
  [canvas-id]
  (state/dispatch :remove-canvas canvas-id))

(defn change-visualizer!
  "Dispatch a visualizer change action.
   
   Args:
   - canvas-id: Canvas to update
   - visualizer-type: New visualizer keyword"
  [canvas-id visualizer-type]
  (state/dispatch :change-visualizer canvas-id visualizer-type))

(defn update-visualizer-settings!
  "Dispatch a visualizer settings update action.
   
   Args:
   - canvas-id: Canvas to update
   - settings-map: Map of new settings"
  [canvas-id settings-map]
  (state/dispatch :update-visualizer-settings canvas-id settings-map))

;; ============================================================================
;; Expose state/dispatch handlers for canvas actions
;; ============================================================================

;; Extend app.state/dispatch to handle canvas-specific actions

(defn register-canvas-dispatch-handlers
  "Register canvas-specific dispatch handlers in the state system.
   
   This function extends the app.state/dispatch multimethod to handle
   canvas layout actions."
  []
  
  ;; Note: In the actual state module, we should extend the dispatch
  ;; multimethod to handle these. For now, this is a namespace to organize
  ;; the logic. The actual dispatch calls happen in canvas.view.
  
  nil)
