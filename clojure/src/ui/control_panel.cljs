(ns ui.control-panel
  "Control panel UI component for adjusting audio and visualizer settings."
  (:require [reagent.core :as r]
            [app.state :as state]
            [audio.player :as player]
            [canvas.model :as canvas-model]
            [visualizers.registry :as registry]))

(defn- canvas-ids-from-layout
  [node]
  (cond
    (nil? node) []
    (= (:type node) :canvas) [(:id node)]
    (= (:type node) :split) (concat (canvas-ids-from-layout (:left node))
                                    (canvas-ids-from-layout (:right node)))
    :else []))

(defn- format-time
  "Format a time value in seconds as minutes:seconds."
  [seconds]
  (let [safe-seconds (if (and (number? seconds) (js/isFinite seconds))
                       (max 0 seconds)
                       0)
        total-seconds (js/Math.floor safe-seconds)
        minutes (js/Math.floor (/ total-seconds 60))
        seconds (mod total-seconds 60)]
    (str minutes ":" (when (< seconds 10) "0") seconds)))

;; ============================================================================
;; Audio Player Control Component
;; ============================================================================

(defn audio-player-controls
  "UI controls for audio playback (file upload, playback toggle, seek)."
  []
  (let [audio-player (get-in @state/app-state [:audio :player])
        is-playing? (get-in @state/app-state [:audio :is-playing])
        current-time (get-in @state/app-state [:audio :current-time])
        duration (get-in @state/app-state [:audio :duration])]
    [:div.audio-player {:style {:padding "10px" :border-bottom "1px solid #ddd"}}
     [:h3 {:style {:margin-bottom "10px"}} "Audio Player"]
   
     ;; File upload
     [:div {:style {:margin-bottom "10px"}}
      [:input
       {:type "file"
        :accept "audio/*"
        :style {:font-size "12px"}
        :on-change (fn [e]
                     (let [file (-> e .-target .-files (aget 0))]
                       (when (and audio-player file)
                         (-> (player/load-audio-file audio-player file)
                             (.catch (fn [err]
                                       (.error js/console "Failed to load audio file:" err)))))))}]]
   
     ;; Playback controls
     [:div {:style {:display "flex" :gap "5px" :margin-bottom "10px"}}
      [:button
       {:on-click #(when audio-player (player/toggle-playback audio-player))
        :disabled (nil? audio-player)
        :aria-label (if is-playing? "Pause" "Play")
        :title (if is-playing? "Pause" "Play")
        :style {:width "32px" :height "32px" :cursor "pointer"}}
       (if is-playing? "⏸" "▶")]
      [:button
       {:on-click #(when audio-player (player/stop audio-player))
        :disabled (nil? audio-player)
        :aria-label "Stop"
        :title "Stop"
        :style {:width "32px" :height "32px" :cursor "pointer"}}
       "⏹"]]
   
     ;; Time display and seek
     [:div {:style {:display "flex" :align-items "center" :gap "5px" :font-size "12px"}}
      [:span (str (format-time current-time) " / " (format-time duration))]
      [:input
       {:type "range"
        :min 0
        :max (max duration 0.001)
        :value (min current-time (max duration 0.001))
        :step 0.01
        :disabled (or (nil? audio-player) (<= duration 0))
        :on-change #(when audio-player
                      (player/seek audio-player (js/parseFloat (-> % .-target .-value))))
        :style {:flex 1 :cursor "pointer"}}]]]))

;; ============================================================================
;; Visualizer Settings Component
;; ============================================================================

(defn visualizer-settings
  "Settings for the selected visualizer."
  [canvas-id]
  (let [available-viz (registry/get-available-visualizers)
        canvas-node (canvas-model/find-node (get-in @state/app-state [:layout :root]) canvas-id)
        selected-viz (or (:visualizer-type canvas-node) :waveform)
        settings (or (:settings canvas-node) {})]
    [:div.visualizer-settings {:style {:padding "10px" :border-bottom "1px solid #ddd"}}
     [:h4 {:style {:margin-bottom "10px"}} (str "Canvas " canvas-id " Settings")]
     
     ;; Visualizer type selector
     [:div {:style {:margin-bottom "10px"}}
      [:label {:style {:font-size "12px" :display "block" :margin-bottom "5px"}}
       "Visualizer Type:"]
      [:select
       {:style {:width "100%" :padding "5px" :font-size "12px"}
        :value (name selected-viz)
        :on-change #(state/dispatch :change-visualizer
                                    canvas-id
                                    (keyword (-> % .-target .-value)))}
       (for [{:keys [type] :as viz} available-viz]
         ^{:key type}
         [:option {:value (name type)} (:name viz)])]]
     
     ;; Visualizer-specific settings
     [:div {:style {:background "#f0f0f0" :padding "8px" :border-radius "3px" :font-size "11px"}}
      (case selected-viz
        :waveform
        [:<>
         [:label {:style {:display "block" :margin-bottom "4px"}} "Buffer Size"]
         [:input {:type "number"
                  :min 128 :max 8192 :step 128
                  :value (or (:buffer-size settings) 2048)
                  :on-change #(state/dispatch :update-visualizer-settings
                                              canvas-id
                                              {:buffer-size (js/parseInt (-> % .-target .-value))})}]
         [:label {:style {:display "block" :margin "6px 0 4px"}} "Line Width"]
         [:input {:type "number"
                  :min 1 :max 8 :step 1
                  :value (or (:line-width settings) 1)
                  :on-change #(state/dispatch :update-visualizer-settings
                                              canvas-id
                                              {:line-width (js/parseInt (-> % .-target .-value))})}]
         [:label {:style {:display "block" :margin "6px 0 4px"}} "Line Color"]
         [:input {:type "color"
                  :value (or (:line-color settings) "#00ff00")
                  :on-change #(state/dispatch :update-visualizer-settings
                                              canvas-id
                                              {:line-color (-> % .-target .-value)})}]]

        :stft
        [:<>
         [:label {:style {:display "block" :margin-bottom "4px"}} "FFT Size"]
         [:select {:value (str (or (:fft-size settings) 512))
                   :on-change #(state/dispatch :update-visualizer-settings
                                               canvas-id
                                               {:fft-size (js/parseInt (-> % .-target .-value))})}
          (for [n [256 512 1024 2048 4096]]
            ^{:key n} [:option {:value n} n])]]

        [:p "No settings for this visualizer."])]]))

;; ============================================================================
;; Volume Control Component
;; ============================================================================

(defn volume-control
  "Volume slider control."
  []
  (let [audio-player (get-in @state/app-state [:audio :player])
        volume (get-in @state/app-state [:audio :volume])]
    [:div {:style {:padding "10px" :border-bottom "1px solid #ddd"}}
   [:label {:style {:font-size "12px" :display "block" :margin-bottom "5px"}}
    "Volume:"]
   [:input
    {:type "range"
     :min 0
     :max 100
     :value (* 100 volume)
     :style {:width "100%"}
     :on-change #(let [v (/ (js/parseFloat (-> % .-target .-value)) 100)]
                   (state/dispatch :set-volume v)
                   (when audio-player
                     (player/set-volume audio-player v)))}]
   [:span {:style {:font-size "11px" :color "#666"}}
    (str " " (int (* 100 volume)) "%")]]))

;; ============================================================================
;; Main Control Panel Component
;; ============================================================================

(defn control-panel
  "Main control panel component with audio and visualizer controls."
  []
  (let [panel-el (atom nil)
        handle-document-click
        (fn [event]
          (let [show? (get-in @state/app-state [:ui :show-control-panel])
                panel @panel-el
                target (.-target event)]
            (when (and show?
                       panel
                       (not (.contains panel target)))
              (state/dispatch :hide-control-panel))))]
    (r/create-class
     {:display-name "control-panel"
      :component-did-mount
      (fn []
        (js/document.addEventListener "click" handle-document-click))
      :component-will-unmount
      (fn []
        (js/document.removeEventListener "click" handle-document-click))
      :reagent-render
      (fn []
        (let [show? (r/cursor state/app-state [:ui :show-control-panel])
              interaction-active? (r/cursor state/app-state [:ui :interaction-active])
              toggle-visible? (or @show? @interaction-active?)
              layout-root (r/cursor state/app-state [:layout :root])]
          [:div.control-panel
           {:ref #(reset! panel-el %)
            :style {:position "fixed"
                    :left (if @show? "0" "-300px")
                    :top 0
                    :width "280px"
                    :height "100%"
                    :background "white"
                    :border-right "1px solid #ccc"
                    :box-shadow "2px 0 8px rgba(0,0,0,0.1)"
                    :overflow-y "auto"
                    :transition "left 0.3s ease"
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
             {:on-click #(state/dispatch :hide-control-panel)
              :style {:padding "5px 10px" :cursor "pointer" :background "none" :border "none"}}
             "✕"]]
           
           ;; Panel content
           [:div {:style {:padding "0"}}
            [audio-player-controls]
            [volume-control]
            
            ;; Show visualizer settings for each canvas
            (for [canvas-id (canvas-ids-from-layout @layout-root)]
              ^{:key canvas-id}
              [visualizer-settings canvas-id])]
           
           ;; Toggle button outside (for hidden state)
           (when-not @show?
             [:button
              {:on-click #(state/dispatch :toggle-control-panel)
               :style {:position "fixed"
                       :left "10px"
                       :top "10px"
                       :width "50px"
                       :height "50px"
                       :border-radius "50%"
                       :background "#4CAF50"
                       :color "white"
                       :border "none"
                       :font-size "24px"
                       :cursor "pointer"
                       :box-shadow "0 2px 8px rgba(0,0,0,0.2)"
                       :z-index 99
                       :opacity (if toggle-visible? 1 0)
                       :pointer-events (if toggle-visible? "auto" "none")
                       :transition "opacity 0.2s ease"}}
              "⚙"])]))})))
