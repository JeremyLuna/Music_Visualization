(ns visualizers.engine
  "Visualizer runtime loop that renders active visualizers per canvas."
  (:require [app.state :as state]
            [visualizers.registry :as registry]
            [visualizers.protocol :as protocol]))

(defonce running? (atom false))
(defonce raf-id (atom nil))
(defonce runtime-instances (atom {}))

(defn- layout-canvas-nodes
  [node]
  (cond
    (nil? node) []
    (= (:type node) :canvas) [node]
    (= (:type node) :split) (concat (layout-canvas-nodes (:left node))
                                    (layout-canvas-nodes (:right node)))
    :else []))

(defn- ensure-visualizer-instance!
  [canvas-id visualizer-type settings]
  (let [existing-entry (get @runtime-instances canvas-id)
        existing (:instance existing-entry)
        existing-type (:type existing-entry)]
    (when (or (nil? existing) (not= existing-type visualizer-type))
      (if-let [instance (apply registry/create-visualizer visualizer-type (mapcat identity settings))]
        (swap! runtime-instances assoc canvas-id
               {:type visualizer-type
                :settings settings
                :instance instance})
        (.warn js/console "Failed to create visualizer for" canvas-id visualizer-type)))))

(defn- sync-visualizer-settings!
  [canvas-id settings]
  (when-let [viz (get-in @runtime-instances [canvas-id :instance])]
    (let [current-settings (get-in @runtime-instances [canvas-id :settings])]
      (when (not= current-settings settings)
        (let [updated (protocol/update-settings viz settings)]
          (swap! runtime-instances assoc canvas-id
                 {:type (get-in @runtime-instances [canvas-id :type])
                  :settings settings
                  :instance updated}))))))

(defn- prune-orphan-instances!
  [active-ids]
  (let [known-ids (set (keys @runtime-instances))]
    (when (not= known-ids active-ids)
      (swap! runtime-instances
             (fn [instances]
               (into {}
                     (filter (fn [[id _]] (contains? active-ids id)) instances)))))))

(defn- render-step!
  []
  (let [s @state/app-state
        layout-root (get-in s [:layout :root])
        sample-puller (get-in s [:audio :sample-puller])
        canvas-elements (:canvas-elements s)
        nodes (layout-canvas-nodes layout-root)
        theme-state (get-in s [:ui :theme])
        active-ids (set (map :id nodes))]

    ;; Remove orphan visualizer instances
    (prune-orphan-instances! active-ids)

    ;; Render all active canvas visualizers
    (doseq [node nodes]
      (let [canvas-id (:id node)
            canvas-el (get canvas-elements canvas-id)
            visualizer-type (or (:visualizer-type node) :waveform)
            settings (merge (registry/theme-settings visualizer-type theme-state)
                            (or (:settings node) {}))]
        (when (and canvas-el sample-puller)
          (ensure-visualizer-instance! canvas-id visualizer-type settings)
          (sync-visualizer-settings! canvas-id settings)
          (when-let [viz (get-in @runtime-instances [canvas-id :instance])]
            (protocol/render viz canvas-el sample-puller)))))))

(defn- tick!
  []
  (when @running?
    (render-step!)
    (reset! raf-id (js/requestAnimationFrame tick!))))

(defn start!
  []
  (when-not @running?
    (reset! running? true)
    (reset! raf-id (js/requestAnimationFrame tick!))
    (.log js/console "Visualizer engine started")))

(defn stop!
  []
  (when @running?
    (reset! running? false)
    (when-let [id @raf-id]
      (js/cancelAnimationFrame id))
    (reset! raf-id nil)
    (reset! runtime-instances {})
    (.log js/console "Visualizer engine stopped")))
