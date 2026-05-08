(ns app.theme
  "Theme palettes and helpers for UI and visualizer styling.")

(def palette-color-keys
  [:background
   :surface
   :text
   :primary
   :accent-a
   :accent-b
   :accent-c])

(def palettes
  {:studio
   {:name "Studio" ; modern light web look
    :shape :rounded
    :colors {:background "#F8FAFC"
             :surface    "#FFFFFF"
             :text       "#0F172A"
             :primary    "#4F46E5"
             :accent-a   "#06B6D4"
             :accent-b   "#F59E0B"
             :accent-c   "#10B981"}}
   
   :papyrus
   {:name "Papyrus" ; comfy graphite on beige
    :shape :boxy
    :colors {:background "#E9DFC8"
             :surface "#F4ECD8"
             :text "#3B3833"
             :primary "#5A5248"
             :accent-a "#A67C52"
             :accent-b "#7A8B78"
             :accent-c "#B85C38"}}

   :night-drive
   {:name "Night Drive" ; black, dark purple, hazard orange
    :shape :boxy
    :colors {:background "#05080d"
             :surface "#17212b"
             :text "#eef5f8"
             :primary "#ffb703"
             :accent-a "#8ecae6"
             :accent-b "#219ebc"
             :accent-c "#fb8500"}}
   
   :vaporwave
   {:name "Vaporwave" ; lighter than night drive, pastels
    :shape :rounded
    :colors {:background "#20123a"
             :surface "#35205f"
             :text "#fff4ff"
             :primary "#ff71ce"
             :accent-a "#01cdfe"
             :accent-b "#b967ff"
             :accent-c "#fffb96"}}

   :aurora
   {:name "Aurora" ; nighttime blues to greens
    :shape :rounded
    :colors {:background "#07131F" ; deep night blue
             :surface "#102235"    ; elevated panels/cards
             :text "#E6F7FF"       ; soft icy white
             :primary "#4FD1C5"    ; aurora teal
             :accent-a "#3B82F6"   ; electric blue
             :accent-b "#14B8A6"   ; green-teal
             :accent-c "#8B5CF6"}} ; faint polar violet}

   :high-contrast-dark
   {:name "High Contrast (Dark)"
    :shape :boxy
    :colors {:background "#000000"
             :surface "#000000"
             :text "#ffffff"
             :primary "#ffffff"
             :accent-a "#ffffff"
             :accent-b "#ffffff"
             :accent-c "#ffffff"}}

   :high-contrast-light
   {:name "High Contrast (Light)"
    :shape :boxy
    :colors {:background "#ffffff"
             :surface "#ffffff"
             :text "#000000"
             :primary "#000000"
             :accent-a "#000000"
             :accent-b "#000000"
             :accent-c "#000000"}}

   :white-rose
   {:name "White Rose" ; soft white and blush pink
    :shape :rounded
    :colors {:background "#fffafc"  ; warm white with pink tint
             :surface    "#ffffff"  ; clean card surface
             :text       "#c97a9a"  ; muted plum-gray for readability
             :primary    "#e8a9c1"  ; rose pink
             :accent-a   "#f6d6e2"  ; pale blush
             :accent-b   "#c97a9a"  ; dusty rose
             :accent-c   "#ffe8f0"}} ; very light pink highlight
   
   :black-rose
   {:name "Black Rose" ; deep black and rich purple
    :shape :rounded
    :colors {:background "#121014"  ; near-black
             :surface    "#1c1720"  ; elevated dark surface
             :text       "#8e80ff"  ; soft lavender-white
             :primary    "#8b5cf6"  ; vivid violet
             :accent-a   "#c084fc"  ; orchid purple
             :accent-b   "#4c1d95"  ; deep royal purple
             :accent-c   "#ec4899"}} ; rose-magenta contrast

   :green-phosphor
   {:name "Green Phosphor" ; black and green
    :shape :boxy
    :colors {:background "#000000"
             :surface "#000000"
             :text "#00ff00"
             :primary "#00ff00"
             :accent-a "#00ff00"
             :accent-b "#00ff00"
             :accent-c "#00ff00"}}})

(def default-theme
  {:palette :aurora
   :custom-colors {}})

(defn- clamp-channel
  [value]
  (-> value
      (max 0)
      (min 255)
      (js/Math.round)
      int))

(defn- hex->rgb
  [hex-color]
  (let [value (if (and (string? hex-color) (= (first hex-color) "#"))
                (subs hex-color 1)
                hex-color)
        full-value (if (= (count value) 3)
                     (apply str (mapcat #(repeat 2 %) value))
                     value)
        n (js/parseInt full-value 16)]
    (if (js/isNaN n)
      [0 0 0]
      [(bit-and (bit-shift-right n 16) 255)
       (bit-and (bit-shift-right n 8) 255)
       (bit-and n 255)])))

(defn- channel->hex
  [value]
  (let [hex (.toString (clamp-channel value) 16)]
    (if (= (count hex) 1)
      (str "0" hex)
      hex)))

(defn- rgb->hex
  [[r g b]]
  (str "#" (channel->hex r) (channel->hex g) (channel->hex b)))

(defn- mix-channel
  [a b amount]
  (+ a (* (- b a) amount)))

(defn- mix-rgb
  [[r1 g1 b1] [r2 g2 b2] amount]
  [(mix-channel r1 r2 amount)
   (mix-channel g1 g2 amount)
   (mix-channel b1 b2 amount)])

(defn mix
  [from-color to-color amount]
  (rgb->hex (mix-rgb (hex->rgb from-color) (hex->rgb to-color) amount)))

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

(defn palette-shape
  [palette-id]
  (get-in palettes [palette-id :shape] :rounded))

(defn- normalize-custom-colors
  [custom-colors]
  (cond-> (or custom-colors {})
    (:visualizer-a custom-colors) (assoc :accent-a (:visualizer-a custom-colors))
    (:visualizer-b custom-colors) (assoc :accent-b (:visualizer-b custom-colors))
    (:visualizer-c custom-colors) (assoc :accent-c (:visualizer-c custom-colors))))

(defn editable-colors
  [theme]
  (let [theme (merge default-theme (or theme {}))
        palette-id (:palette theme)
        custom? (= palette-id :custom)
        base-colors (if custom?
                      (palette-colors :studio)
                      (palette-colors palette-id))]
    (select-keys (cond-> base-colors
                   custom? (merge (normalize-custom-colors (:custom-colors theme))))
                 palette-color-keys)))

(defn- expanded-colors
  [editable]
  (let [{:keys [background surface text]} editable
        muted-text (mix text background 0.42)
        border (mix text background 0.78)
        surface-muted (mix surface text 0.08)]
    (merge editable
           {:app-background background
            :panel-background surface
            :panel-header (mix surface background 0.45)
            :surface-muted surface-muted
            :muted-text muted-text
            :border border
            :primary-text background
            :canvas-background background
            :splitter (mix text background 0.72)})))

(defn effective-theme
  [theme]
  (let [theme (merge default-theme (or theme {}))
        palette-id (:palette theme)
        shape (if (= palette-id :custom)
                (or (:shape theme) :rounded)
                (palette-shape palette-id))
        editable (editable-colors theme)]
    (assoc theme
           :shape shape
           :custom-colors (select-keys (normalize-custom-colors (:custom-colors theme))
                                       palette-color-keys)
           :editable-colors editable
           :colors (expanded-colors editable))))

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

(defn font-family
  [theme]
  (if (= (:shape (effective-theme theme)) :boxy)
    "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace"
    "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif"))

(defn control-style
  [theme]
  (let [c (colors theme)]
    {:background (:surface c)
     :color (:text c)
     :border (str "1px solid " (:border c))
     :border-radius (radius theme :small)
     :font-family (font-family theme)}))

(defn button-style
  ([theme] (button-style theme :neutral))
  ([theme variant]
   (let [c (colors theme)
         base {:border (str "1px solid " (:border c))
               :border-radius (radius theme :small)
               :cursor "pointer"
               :font-family (font-family theme)}]
     (case variant
       :primary (merge base {:background (:primary c)
                             :border (str "1px solid " (:primary c))
                             :color (:primary-text c)})
       :ghost (merge base {:background "transparent"
                           :color (:text c)})
       (merge base {:background (:surface-muted c)
                    :color (:text c)})))))
