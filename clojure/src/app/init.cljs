(ns app.init
  "Application initialization and setup.
   
   Handles AudioContext creation, player setup, and component wiring."
  (:require [app.state :as state]
            [audio.player :as player]))

;; ============================================================================
;; Global Player State
;; ============================================================================

(defonce audio-player-instance (atom nil))

;; ============================================================================
;; Initialization Functions
;; ============================================================================

(defn ^:async initialize-audio
  "Initialize the audio system.
   
   Creates the AudioContext, AudioPlayer, and sets up the audio pipeline."
  []
  (js/Promise.
   (fn [resolve reject]
     (-> (player/create-audio-player)
         (.then (fn [audio-player]
                  (reset! audio-player-instance audio-player)
                  (.log js/console "✓ Audio player initialized")
                  (resolve audio-player)))
         (.catch (fn [error]
                   (.error js/console "Failed to initialize audio:" error)
                   (reject error)))))))

(defn initialize-app
  "Full app initialization.
   
   Sets up all subsystems and mounts the React tree."
  []
  (-> (initialize-audio)
      (.then (fn [player]
               (.log js/console "✓ App initialization complete")
               player))
      (.catch (fn [error]
                (.error js/console "App initialization failed:" error)))))

;; ============================================================================
;; Event Handlers and Hooks
;; ============================================================================

(defn setup-keyboard-shortcuts
  "Set up keyboard shortcuts for common actions."
  []
  (js/document.addEventListener "keydown"
    (fn [event]
      (let [key (.-key event)]
        (cond
          ;; Space bar: play/pause
          (= key " ")
          (do
            (.preventDefault event)
            (.log js/console "Play/pause toggle (TODO)"))
          
          ;; Escape: hide control panel if open
          (= key "Escape")
          (let [show? (get-in @state/app-state [:ui :show-control-panel])]
            (when show?
              (state/dispatch :toggle-control-panel))))))))

(defn setup-ui-hooks
  "Set up UI event handlers and lifecycle hooks."
  []
  ;; Set up keyboard shortcuts
  (setup-keyboard-shortcuts)
  
  ;; Log app state changes during development
  (add-watch state/app-state :debug
    (fn [_ _ old-state new-state]
      ;; Uncomment for debugging:
      ;; (.log js/console "State updated:" new-state)
      nil)))

(defn mount-app
  "Mount the React app to the DOM."
  []
  (let [app-container (js/document.getElementById "app")]
    (if (nil? app-container)
      (do
        (.error js/console "App container (#app) not found in DOM")
        false)
      (do
        ;; Render the root component
        (require '[app.core])
        (let [reagent (require '[reagent.core])]
          (reagent/render [(require '[app.core])/app-root] app-container)
          true)))))

;; ============================================================================
;; Main Entry Point
;; ============================================================================

(defn ^:export init
  "Initialize and launch the app. Called by Shadow-cljs on page load."
  []
  (.log js/console "🚀 Music Visualization App Loading...")
  
  ;; Set up UI hooks
  (setup-ui-hooks)
  
  ;; Initialize audio system
  (-> (initialize-app)
      (.then (fn [player]
               ;; Mount the React app
               (mount-app)
               (.log js/console "✅ App ready!")))
      (.catch (fn [error]
                (.error js/console "😞 Startup failed:" error)))))
