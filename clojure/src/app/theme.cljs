(ns app.theme
  "Theme palettes and helpers for UI and visualizer styling.")

(def palette-color-keys
  [:app-background
   :panel-background
   :panel-header
   :surface
   :surface-muted
   :text
   :muted-text
   :border
   :primary
   :primary-text
   :danger
   :canvas-background
   :splitter
   :waveform-line
   :waveform-baseline
   :spectrogram-low
   :spectrogram-mid
   :spectrogram-high])

(def palettes
  {:studio
   {:name "Studio"
    :colors {:app-background "#f4f6f8"
             :panel-background "#ffffff"
             :panel-header "#eef2f5"
             :surface "#ffffff"
             :surface-muted "#edf1f4"
             :text "#18212b"
             :muted-text "#667280"
             :border "#ccd5de"
             :primary "#287d74"
             :primary-text "#ffffff"
             :danger "#c94643"
             :canvas-background "#ffffff"
             :splitter "#cfd8e1"
             :waveform-line "#16a085"
             :waveform-baseline "#c8d1da"
             :spectrogram-low "#071013"
             :spectrogram-mid "#1f8f99"
             :spectrogram-high "#f4d35e"}}

   :night-drive
   {:name "Night Drive"
    :colors {:app-background "#101820"
             :panel-background "#17212b"
             :panel-header "#202d38"
             :surface "#202d38"
             :surface-muted "#263644"
             :text "#eef5f8"
             :muted-text "#9fb1bc"
             :border "#3a4d5e"
             :primary "#ffb703"
             :primary-text "#101820"
             :danger "#ef476f"
             :canvas-background "#05080d"
             :splitter "#384958"
             :waveform-line "#8ecae6"
             :waveform-baseline "#2e4c5f"
             :spectrogram-low "#05080d"
             :spectrogram-mid "#219ebc"
             :spectrogram-high "#fb8500"}}

   :aurora
   {:name "Aurora"
    :colors {:app-background "#14213d"
             :panel-background "#1f2d4a"
             :panel-header "#263859"
             :surface "#263859"
             :surface-muted "#304769"
             :text "#f8fafc"
             :muted-text "#b8c3d1"
             :border "#49607d"
             :primary "#66d9a4"
             :primary-text "#102019"
             :danger "#ff6b6b"
             :canvas-background "#0b1324"
             :splitter "#435775"
             :waveform-line "#66d9a4"
             :waveform-baseline "#37516a"
             :spectrogram-low "#0b1324"
             :spectrogram-mid "#7c3aed"
             :spectrogram-high "#f8e16c"}}

   :paper
   {:name "Paper"
    :colors {:app-background "#f8f7f3"
             :panel-background "#fffefa"
             :panel-header "#efede6"
             :surface "#fffefa"
             :surface-muted "#ebe8df"
             :text "#24211c"
             :muted-text "#706a5f"
             :border "#d5d0c4"
             :primary "#386641"
             :primary-text "#ffffff"
             :danger "#bc4749"
             :canvas-background "#fffefa"
             :splitter "#d1cabd"
             :waveform-line "#6a994e"
             :waveform-baseline "#d6d1c8"
             :spectrogram-low "#1f1b16"
             :spectrogram-mid "#a7c957"
             :spectrogram-high "#f2e8cf"}}})

(def default-theme
  {:palette :studio
   :shape :rounded
   :custom-colors {}})

(defn palette-options
  []
  (conj
   (mapv (fn [[id {:keys [name]}]]
           {:id id :name name})
         palettes)
   {:id :custom :name "Custom"}))

(defn palette-colors
  [palette-id]
  (get-in palettes [palette-id :colors] (get-in palettes [:studio :colors])))

(defn effective-theme
  [theme]
  (let [theme (merge default-theme (or theme {}))
        palette-id (:palette theme)
        base-colors (if (= palette-id :custom)
                      (palette-colors :studio)
                      (palette-colors palette-id))]
    (assoc theme
           :colors (merge base-colors (:custom-colors theme)))))

(defn colors
  [theme]
  (:colors (effective-theme theme)))

(defn color
  [theme color-key]
  (get (colors theme) color-key))

(defn radius
  [theme size]
  (if (= (:shape (effective-theme theme)) :rounded)
    (case size
      :small "4px"
      :medium "6px"
      :large "8px"
      :pill "999px"
      "6px")
    "0"))

(defn control-style
  [theme]
  (let [c (colors theme)]
    {:background (:surface c)
     :color (:text c)
     :border (str "1px solid " (:border c))
     :border-radius (radius theme :small)}))

(defn button-style
  ([theme] (button-style theme :neutral))
  ([theme variant]
   (let [c (colors theme)
         base {:border (str "1px solid " (:border c))
               :border-radius (radius theme :small)
               :cursor "pointer"}]
     (case variant
       :primary (merge base {:background (:primary c)
                             :border (str "1px solid " (:primary c))
                             :color (:primary-text c)})
       :danger (merge base {:background (:danger c)
                            :border (str "1px solid " (:danger c))
                            :color "#ffffff"})
       :ghost (merge base {:background "transparent"
                           :color (:text c)})
       (merge base {:background (:surface-muted c)
                    :color (:text c)})))))

(defn visualizer-settings
  [theme]
  (let [c (colors theme)]
    {:background-color (:canvas-background c)
     :baseline-color (:waveform-baseline c)
     :line-color (:waveform-line c)
     :spectrogram-background-color (:canvas-background c)
     :spectrogram-low-color (:spectrogram-low c)
     :spectrogram-mid-color (:spectrogram-mid c)
     :spectrogram-high-color (:spectrogram-high c)
     :color-map :theme}))
