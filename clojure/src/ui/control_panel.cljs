(ns ui.control-panel
  "Control panel UI component for adjusting audio and visualizer settings."
  (:require [reagent.core :as r]
            [app.state :as state]
            [visualizers.registry :as registry]))

;; ============================================================================
;; Audio Player Control Component
;; ============================================================================

(defn audio-player-controls
  "UI controls for audio playback (file upload, play, pause, seek)."
  []
  [:div.audio-player {:style {:padding "10px" :border-bottom "1px solid #ddd"}}
   [:h3 {:style {:margin-bottom "10px"}} "Audio Player"]
   
   ;; File upload
   [:div {:style {:margin-bottom "10px"}}
    [:input
     {:type "file"
      :accept "audio/*"
      :style {:font-size "12px"}
      :on-change (fn [e]
                   ;; TODO: Load audio file via player
                   (.log js/console "File selected:" (-> e .-target .-files (aget 0))))}]]
   
   ;; Playback controls
   [:div {:style {:display "flex" :gap "5px" :margin-bottom "10px"}}
    [:button
     {:on-click #(.log js/console "Play")
      :style {:padding "5px 10px" :cursor "pointer"}}
     "▶ Play"]
    [:button
     {:on-click #(.log js/console "Pause")
      :style {:padding "5px 10px" :cursor "pointer"}}
     "⏸ Pause"]
    [:button
     {:on-click #(.log js/console "Stop")
      :style {:padding "5px 10px" :cursor "pointer"}}
     "⏹ Stop"]]
   
   ;; Time display and seek
   [:div {:style {:display "flex" :align-items "center" :gap "5px" :font-size "12px"}}
    [:span "0:00 / 0:00"]
    [:input
     {:type "range"
      :min 0
      :max 100
      :default-value 0
      :style {:flex 1 :cursor "pointer"}}]]])

;; ============================================================================
;; Visualizer Settings Component
;; ============================================================================

(defn visualizer-settings
  "Settings for the selected visualizer."
  [canvas-id]
  (let [available-viz (registry/get-available-visualizers)]
    [:div.visualizer-settings {:style {:padding "10px" :border-bottom "1px solid #ddd"}}
     [:h4 {:style {:margin-bottom "10px"}} (str "Canvas " canvas-id " Settings")]
     
     ;; Visualizer type selector
     [:div {:style {:margin-bottom "10px"}}
      [:label {:style {:font-size "12px" :display "block" :margin-bottom "5px"}}
       "Visualizer Type:"]
      [:select
       {:style {:width "100%" :padding "5px" :font-size "12px"}
        :on-change #(.log js/console "Visualizer changed:" (-> % .-target .-value))}
       (for [{:keys [type name]} available-viz]
         ^{:key type}
         [:option {:value (name type)} name])]]
     
     ;; Visualizer-specific settings (placeholder)
     [:div {:style {:background "#f0f0f0" :padding "5px" :border-radius "3px" :font-size "11px"}}
      [:p "Visualizer-specific settings (TODO)"]]]))

;; ============================================================================
;; Volume Control Component
;; ============================================================================

(defn volume-control
  "Volume slider control."
  []
  [:div {:style {:padding "10px" :border-bottom "1px solid #ddd"}}
   [:label {:style {:font-size "12px" :display "block" :margin-bottom "5px"}}
    "Volume:"]
   [:input
    {:type "range"
     :min 0
     :max 100
     :default-value 100
     :style {:width "100%"}
     :on-change #(.log js/console "Volume changed:" (-> % .-target .-value))}]
   [:span {:style {:font-size "11px" :color "#666"}}
    " 100%"]])

;; ============================================================================
;; Main Control Panel Component
;; ============================================================================

(defn control-panel
  "Main control panel component with audio and visualizer controls."
  []
  (let [show? (r/cursor state/app-state [:ui :show-control-panel])]
    [:div.control-panel
     {:style {:position "fixed"
              :right (if @show? "0" "-300px")
              :top 0
              :width "280px"
              :height "100%"
              :background "white"
              :border-left "1px solid #ccc"
              :box-shadow "-2px 0 8px rgba(0,0,0,0.1)"
              :overflow-y "auto"
              :transition "right 0.3s ease"
              :z-index 100}}
     
     ;; Header with toggle button
     [:div {:style {:display "flex"
                    :justify-content "space-between"
                    :align-items "center"
                    :padding "10px"
                    :border-bottom "1px solid #ddd"
                    :background "#f5f5f5"}}
      [:h2 {:style {:margin 0 :font-size "14px"}} "Settings"]
      [:button
       {:on-click #(state/dispatch :toggle-control-panel)
        :style {:padding "5px 10px" :cursor "pointer" :background "none" :border "none"}}
       "✕"]]
     
     ;; Panel content
     [:div {:style {:padding "0"}}
      [audio-player-controls]
      [volume-control]
      
      ;; Show visualizer settings for each canvas
      [:div {:style {:padding "10px" :font-size "12px" :color "#666"}}
       "Canvas-specific settings coming soon"]
      
      [visualizer-settings 0]]
     
     ;; Toggle button outside (for hidden state)
     (when-not @show?
       [:button
        {:on-click #(state/dispatch :toggle-control-panel)
         :style {:position "fixed"
                 :right "10px"
                 :bottom "20px"
                 :width "50px"
                 :height "50px"
                 :border-radius "50%"
                 :background "#4CAF50"
                 :color "white"
                 :border "none"
                 :font-size "24px"
                 :cursor "pointer"
                 :box-shadow "0 2px 8px rgba(0,0,0,0.2)"
                 :z-index 99}}
        "⚙"])]))
