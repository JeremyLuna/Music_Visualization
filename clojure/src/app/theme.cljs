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
   {:name "Studio"
    :shape :rounded
    :colors {:background "#f4f6f8"
             :surface "#ffffff"
             :text "#18212b"
             :primary "#287d74"
             :accent-a "#16a085"
             :accent-b "#1f8f99"
             :accent-c "#f4d35e"}}

   :night-drive
   {:name "Night Drive"
    :shape :boxy
    :colors {:background "#05080d"
             :surface "#17212b"
             :text "#eef5f8"
             :primary "#ffb703"
             :accent-a "#8ecae6"
             :accent-b "#219ebc"
             :accent-c "#fb8500"}}

   :aurora
   {:name "Aurora"
    :shape :rounded
    :colors {:background "#0b1324"
             :surface "#1f2d4a"
             :text "#f8fafc"
             :primary "#66d9a4"
             :accent-a "#66d9a4"
             :accent-b "#7c3aed"
             :accent-c "#f8e16c"}}

   :paper
   {:name "Paper"
    :shape :boxy
    :colors {:background "#f8f7f3"
             :surface "#fffefa"
             :text "#24211c"
             :primary "#386641"
             :accent-a "#6a994e"
             :accent-b "#a7c957"
             :accent-c "#f2e8cf"}}})

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

(defn- relative-lightness
  [hex-color]
  (let [[r g b] (hex->rgb hex-color)]
    (/ (+ (* 0.299 r) (* 0.587 g) (* 0.114 b)) 255)))

(defn- readable-on
  [hex-color]
  (if (> (relative-lightness hex-color) 0.62)
    "#101820"
    "#ffffff"))

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
  (let [{:keys [background surface text primary]} editable
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
            :primary-text (readable-on primary)
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
