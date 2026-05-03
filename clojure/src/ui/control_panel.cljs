(ns ui.control-panel
  "Control panel UI component for adjusting audio, theme, and visualizer settings."
  (:require [reagent.core :as r]
            [app.state :as state]
            [app.theme :as theme]
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

(defn- current-theme
  []
  (get-in @state/app-state [:ui :theme]))

(defn- current-colors
  []
  (theme/colors (current-theme)))

(defn- section-style
  [theme]
  (let [colors (theme/colors theme)]
    {:padding "10px"
     :border-bottom (str "1px solid " (:border colors))}))

(defn- label-style
  [theme]
  {:font-size "12px"
   :display "block"
   :margin-bottom "5px"
   :color (:muted-text (theme/colors theme))})

(defn- input-style
  [theme]
  (merge (theme/control-style theme)
         {:width "100%"
          :padding "5px"
          :font-size "12px"
          :box-sizing "border-box"}))

(defn- inline-input-style
  [theme]
  (merge (theme/control-style theme)
         {:padding "4px"
          :font-size "12px"}))

(defn- reset-visualizer-setting!
  [canvas-id setting-key]
  (state/dispatch :update-visualizer-settings canvas-id #(dissoc % setting-key)))

(defn- color-setting-row
  [canvas-id settings effective-settings setting-key label]
  (let [theme (current-theme)
        colors (theme/colors theme)
        overridden? (contains? settings setting-key)]
    [:div {:style {:display "grid"
                   :grid-template-columns "1fr 44px 48px"
                   :gap "6px"
                   :align-items "center"
                   :margin-top "6px"}}
     [:label {:style {:font-size "11px" :color (:text colors)}} label]
     [:input {:type "color"
              :value (get effective-settings setting-key)
              :title (if overridden? "Using canvas override" "Using theme color")
              :style {:width "44px"
                      :height "28px"
                      :padding "2px"
                      :border (str "1px solid " (:border colors))
                      :border-radius (theme/radius theme :small)
                      :background (:surface colors)}
              :on-change #(state/dispatch :update-visualizer-settings
                                           canvas-id
                                           {setting-key (-> % .-target .-value)})}]
     [:button {:disabled (not overridden?)
               :title "Use theme color"
               :on-click #(reset-visualizer-setting! canvas-id setting-key)
               :style (merge (theme/button-style theme)
                             {:height "28px"
                              :font-size "11px"
                              :opacity (if overridden? 1 0.45)})}
      "Reset"]]))

;; ============================================================================
;; Audio Player Control Component
;; ============================================================================

(defn audio-player-controls
  "UI controls for audio playback (file upload, playback toggle, seek)."
  []
  (let [audio-player (get-in @state/app-state [:audio :player])
        is-playing? (get-in @state/app-state [:audio :is-playing])
        current-time (get-in @state/app-state [:audio :current-time])
        duration (get-in @state/app-state [:audio :duration])
        theme (current-theme)
        colors (theme/colors theme)]
    [:div.audio-player {:style (section-style theme)}
     [:h3 {:style {:margin "0 0 10px"
                   :font-size "14px"
                   :color (:text colors)}}
      "Audio Player"]

     [:div {:style {:margin-bottom "10px"}}
      [:input
       {:type "file"
        :accept "audio/*"
        :style (merge (input-style theme) {:padding "4px"})
        :on-change (fn [e]
                     (let [file (-> e .-target .-files (aget 0))]
                       (when (and audio-player file)
                         (-> (player/load-audio-file audio-player file)
                             (.catch (fn [err]
                                       (.error js/console "Failed to load audio file:" err)))))))}]]

     [:div {:style {:display "flex" :gap "5px" :margin-bottom "10px"}}
      [:button
       {:on-click #(when audio-player (player/toggle-playback audio-player))
        :disabled (nil? audio-player)
        :aria-label (if is-playing? "Pause" "Play")
        :title (if is-playing? "Pause" "Play")
        :style (merge (theme/button-style theme :primary)
                      {:width "32px" :height "32px"})}
       (if is-playing? "⏸" "▶")]
      [:button
       {:on-click #(when audio-player (player/stop audio-player))
        :disabled (nil? audio-player)
        :aria-label "Stop"
        :title "Stop"
        :style (merge (theme/button-style theme)
                      {:width "32px" :height "32px"})}
       "⏹"]]

     [:div {:style {:display "flex" :align-items "center" :gap "5px" :font-size "12px"}}
      [:span {:style {:color (:muted-text colors)}}
       (str (format-time current-time) " / " (format-time duration))]
      [:input
       {:type "range"
        :min 0
        :max (max duration 0.001)
        :value (min current-time (max duration 0.001))
        :step 0.01
        :disabled (or (nil? audio-player) (<= duration 0))
        :on-change #(when audio-player
                      (player/seek audio-player (js/parseFloat (-> % .-target .-value))))
        :style {:flex 1 :cursor "pointer" :accent-color (:primary colors)}}]]]))

;; ============================================================================
;; Theme Settings Component
;; ============================================================================

(defn theme-settings
  "Theme palette, shape, and custom color controls."
  []
  (let [theme-state (current-theme)
        effective-theme (theme/effective-theme theme-state)
        colors (:colors effective-theme)
        custom? (= (:palette effective-theme) :custom)]
    [:div.theme-settings {:style (section-style effective-theme)}
     [:h3 {:style {:margin "0 0 10px"
                   :font-size "14px"
                   :color (:text colors)}}
      "Theme"]

     [:div {:style {:margin-bottom "10px"}}
      [:label {:style (label-style effective-theme)} "Palette"]
      [:select {:style (input-style effective-theme)
                :value (name (:palette effective-theme))
                :on-change #(state/dispatch :set-theme-palette
                                            (keyword (-> % .-target .-value)))}
       (for [{:keys [id name]} (theme/palette-options)]
         ^{:key id}
         [:option {:value (clojure.core/name id)} name])]]

     [:div {:style {:margin-bottom "10px"}}
      [:label {:style (label-style effective-theme)} "Shape"]
      [:div {:style {:display "grid"
                     :grid-template-columns "1fr 1fr"
                     :gap "6px"}}
       (for [[shape label] [[:boxy "Boxy"] [:rounded "Rounded"]]]
         ^{:key shape}
         [:button {:on-click #(state/dispatch :set-theme-shape shape)
                   :style (merge (theme/button-style effective-theme
                                                     (if (= (:shape effective-theme) shape)
                                                       :primary
                                                       :neutral))
                                 {:height "30px"
                                  :font-size "12px"})}
          label])]]

     (when custom?
       [:div {:style {:background (:surface-muted colors)
                      :padding "8px"
                      :border-radius (theme/radius effective-theme :medium)
                      :font-size "11px"}}
        (for [color-key theme/palette-color-keys]
          ^{:key color-key}
          [:div {:style {:display "grid"
                         :grid-template-columns "1fr 44px"
                         :align-items "center"
                         :gap "6px"
                         :margin-bottom "6px"}}
           [:label {:style {:color (:text colors)}}
            (-> color-key name (.replaceAll "-" " "))]
           [:input {:type "color"
                    :value (get colors color-key)
                    :style {:width "44px"
                            :height "26px"
                            :padding "2px"
                            :border (str "1px solid " (:border colors))
                            :border-radius (theme/radius effective-theme :small)
                            :background (:surface colors)}
                    :on-change #(state/dispatch :set-theme-custom-color
                                                color-key
                                                (-> % .-target .-value))}]])])]))

;; ============================================================================
;; Visualizer Settings Component
;; ============================================================================

(defn visualizer-settings
  "Settings for the selected visualizer."
  [canvas-id]
  (let [available-viz (registry/get-available-visualizers)
        canvas-node (canvas-model/find-node (get-in @state/app-state [:layout :root]) canvas-id)
        selected-viz (or (:visualizer-type canvas-node) :waveform)
        settings (or (:settings canvas-node) {})
        theme-state (current-theme)
        colors (theme/colors theme-state)
        theme-viz-settings (theme/visualizer-settings theme-state)
        effective-settings (merge theme-viz-settings settings)]
    [:div.visualizer-settings {:style (section-style theme-state)}
     [:h4 {:style {:margin "0 0 10px"
                   :font-size "13px"
                   :color (:text colors)}}
      (str "Canvas " canvas-id " Settings")]

     [:div {:style {:margin-bottom "10px"}}
      [:label {:style (label-style theme-state)}
       "Visualizer Type"]
      [:select
       {:style (input-style theme-state)
        :value (name selected-viz)
        :on-change #(state/dispatch :change-visualizer
                                    canvas-id
                                    (keyword (-> % .-target .-value)))}
       (for [{:keys [type] :as viz} available-viz]
         ^{:key type}
         [:option {:value (name type)} (:name viz)])]]

     [:div {:style {:background (:surface-muted colors)
                    :padding "8px"
                    :border-radius (theme/radius theme-state :medium)
                    :font-size "11px"
                    :color (:text colors)}}
      (case selected-viz
        :waveform
        [:<>
         [:label {:style {:display "block" :margin-bottom "4px"}} "Buffer Size"]
         [:input {:type "number"
                  :min 128 :max 8192 :step 128
                  :style (inline-input-style theme-state)
                  :value (or (:buffer-size settings) 2048)
                  :on-change #(state/dispatch :update-visualizer-settings
                                              canvas-id
                                              {:buffer-size (js/parseInt (-> % .-target .-value))})}]
         [:label {:style {:display "block" :margin "6px 0 4px"}} "Line Width"]
         [:input {:type "number"
                  :min 1 :max 8 :step 1
                  :style (inline-input-style theme-state)
                  :value (or (:line-width settings) 2)
                  :on-change #(state/dispatch :update-visualizer-settings
                                              canvas-id
                                              {:line-width (js/parseInt (-> % .-target .-value))})}]
         [color-setting-row canvas-id settings effective-settings :line-color "Line"]
         [color-setting-row canvas-id settings effective-settings :background-color "Background"]
         [color-setting-row canvas-id settings effective-settings :baseline-color "Baseline"]]

        :stft
        [:<>
         [:label {:style {:display "block" :margin-bottom "4px"}} "FFT Size"]
         [:select {:value (str (or (:fft-size settings) 1024))
                   :style (inline-input-style theme-state)
                   :on-change #(state/dispatch :update-visualizer-settings
                                               canvas-id
                                               {:fft-size (js/parseInt (-> % .-target .-value))})}
          (for [n [256 512 1024 2048 4096]]
            ^{:key n} [:option {:value n} n])]
         [:label {:style {:display "block" :margin "6px 0 4px"}} "Color Map"]
         [:select {:value (name (or (:color-map settings) :theme))
                   :style (inline-input-style theme-state)
                   :on-change #(let [next-map (keyword (-> % .-target .-value))]
                                 (if (= next-map :theme)
                                   (reset-visualizer-setting! canvas-id :color-map)
                                   (state/dispatch :update-visualizer-settings
                                                   canvas-id
                                                   {:color-map next-map})))}
          [:option {:value "theme"} "Theme Gradient"]
          [:option {:value "gray"} "Gray"]
          [:option {:value "hot"} "Hot"]]
         [color-setting-row canvas-id settings effective-settings :spectrogram-background-color "Background"]
         [color-setting-row canvas-id settings effective-settings :spectrogram-low-color "Low"]
         [color-setting-row canvas-id settings effective-settings :spectrogram-mid-color "Mid"]
         [color-setting-row canvas-id settings effective-settings :spectrogram-high-color "High"]]

        [:p {:style {:margin 0 :color (:muted-text colors)}} "No settings for this visualizer."])]]))

;; ============================================================================
;; Volume Control Component
;; ============================================================================

(defn volume-control
  "Volume slider control."
  []
  (let [audio-player (get-in @state/app-state [:audio :player])
        volume (get-in @state/app-state [:audio :volume])
        theme (current-theme)
        colors (theme/colors theme)]
    [:div {:style (section-style theme)}
     [:label {:style (label-style theme)}
      "Volume"]
     [:input
      {:type "range"
       :min 0
       :max 100
       :value (* 100 volume)
       :style {:width "100%" :accent-color (:primary colors)}
       :on-change #(let [v (/ (js/parseFloat (-> % .-target .-value)) 100)]
                     (state/dispatch :set-volume v)
                     (when audio-player
                       (player/set-volume audio-player v)))}]
     [:span {:style {:font-size "11px" :color (:muted-text colors)}}
      (str " " (int (* 100 volume)) "%")]]))

;; ============================================================================
;; Main Control Panel Component
;; ============================================================================

(defn control-panel
  "Main control panel component with audio, theme, and visualizer controls."
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
              layout-root (r/cursor state/app-state [:layout :root])
              theme-state (current-theme)
              colors (theme/colors theme-state)]
          [:div.control-panel
           {:ref #(reset! panel-el %)
            :style {:position "fixed"
                    :left (if @show? "0" "-300px")
                    :top 0
                    :width "280px"
                    :height "100%"
                    :background (:panel-background colors)
                    :color (:text colors)
                    :border-right (str "1px solid " (:border colors))
                    :box-shadow "2px 0 8px rgba(0,0,0,0.18)"
                    :overflow-y "auto"
                    :transition "left 0.3s ease"
                    :z-index 100}}

           [:div {:style {:display "flex"
                          :justify-content "space-between"
                          :align-items "center"
                          :padding "10px"
                          :border-bottom (str "1px solid " (:border colors))
                          :background (:panel-header colors)}}
            [:h2 {:style {:margin 0 :font-size "14px" :color (:text colors)}} "Settings"]
            [:button
             {:on-click #(state/dispatch :hide-control-panel)
              :aria-label "Close settings"
              :title "Close settings"
              :style (merge (theme/button-style theme-state :ghost)
                            {:padding "5px 10px"})}
             "✕"]]

           [:div {:style {:padding "0"}}
            [audio-player-controls]
            [volume-control]
            [theme-settings]

            (for [canvas-id (canvas-ids-from-layout @layout-root)]
              ^{:key canvas-id}
              [visualizer-settings canvas-id])]

           (when-not @show?
             [:button
              {:on-click #(state/dispatch :toggle-control-panel)
               :aria-label "Open settings"
               :title "Open settings"
               :style (merge (theme/button-style theme-state :primary)
                             {:position "fixed"
                              :left "10px"
                              :top "10px"
                              :width "50px"
                              :height "50px"
                              :border-radius (theme/radius theme-state :pill)
                              :font-size "24px"
                              :box-shadow "0 2px 8px rgba(0,0,0,0.2)"
                              :z-index 99
                              :opacity (if toggle-visible? 1 0)
                              :pointer-events (if toggle-visible? "auto" "none")
                              :transition "opacity 0.2s ease"})}
              "⚙"])]))})))
