(ns canvas.view
  "Reagent components for rendering the dynamic canvas layout.
   
   Renders the layout tree as DOM elements with flexbox, canvas elements,
   and interactive controls (split, remove, resize)."
  (:require [reagent.core :as r]
            [app.state :as state]
            [canvas.model :as model]))

;; ============================================================================
;; Helper functions
;; ============================================================================

(defn canvas-element
  "Create a canvas DOM element with sizing."
  [canvas-id]
  (let [el-ref (r/atom nil)
        registered-el (atom nil)
        resize-observer (atom nil)
        register! (fn [id]
                    (when-let [el @el-ref]
                      (reset! registered-el el)
                      (state/dispatch :register-canvas-element id el)))
        unregister! (fn [id]
                      (when-let [el @registered-el]
                        (state/dispatch :unregister-canvas-element id el)
                        (reset! registered-el nil)))]
    (r/create-class
     {:component-did-mount
      (fn [this]
        (let [[_ mounted-canvas-id] (r/argv this)]
          (register! mounted-canvas-id))
        (when (exists? js/ResizeObserver)
          (let [observer (js/ResizeObserver.
                         (fn [entries]
                           ;; Update canvas internal resolution to match CSS size
                           (doseq [entry entries]
                             (let [rect (.-contentRect entry)
                                   width (.-width rect)
                                   height (.-height rect)
                                   canvas (.-target entry)]
                               (set! (.-width canvas) (int width))
                               (set! (.-height canvas) (int height))))))]
            (reset! resize-observer observer)
            (.observe observer @el-ref))))

      :component-did-update
      (fn [this old-argv]
        (let [[_ old-canvas-id] old-argv
              [_ new-canvas-id] (r/argv this)]
          (when (not= old-canvas-id new-canvas-id)
            (unregister! old-canvas-id)
            (register! new-canvas-id))))

      :component-will-unmount
      (fn [this]
        (let [[_ unmounted-canvas-id] (r/argv this)]
          (when-let [observer @resize-observer]
            (.disconnect observer)
            (reset! resize-observer nil))
          (unregister! unmounted-canvas-id)))
      
      :reagent-render
      (fn [_canvas-id]
        [:canvas
         {:style {:width "100%"
                  :height "100%"
                  :background "white"}
          :ref (fn [el] (reset! el-ref el))}])})))

;; ============================================================================
;; Tree Rendering Components
;; ============================================================================

(defn canvas-node-view
  "Render a single canvas node with controls."
  [canvas-id canvas-node on-split on-remove can-remove?]
  [:div.canvas-node
   {:style {:display "flex"
            :flex-direction "column"
            :height "100%"
            :width "100%"
            :position "relative"}}
   
   ;; Canvas toolbar
   [:div.canvas-toolbar
    {:style {:background "#f0f0f0"
             :padding "5px"
             :border-bottom "1px solid #ccc"
             :display "flex"
             :align-items "center"
             :gap "5px"
             :font-size "12px"
             :position "relative"}}
    
    [:span
     {:style {:position "absolute"
              :left "50%"
              :transform "translateX(-50%)"}}
     (str "Canvas " canvas-id)]
    
    [:div
     {:style {:margin-left "auto"
              :display "flex"
              :gap "5px"}}
     [:button
      {:on-click #(on-split canvas-id :h)
       :style {:padding "3px 8px" :font-size "11px" :cursor "pointer"}}
      "↔"]
     
     [:button
      {:on-click #(on-split canvas-id :v)
       :style {:padding "3px 8px" :font-size "11px" :cursor "pointer"}}
      "↕"]
     
     (when can-remove?
       [:button
        {:on-click #(on-remove canvas-id)
         :style {:padding "3px 8px" :font-size "11px" :cursor "pointer"
                 :background "#ff6b6b" :color "white"}}
        "x"])]]
   
   ;; Canvas element
   [:div
    {:style {:flex 1 :overflow "hidden"}}
    [canvas-element canvas-id]]])

(declare layout-tree-view)

(defn- clamp
  [value min-value max-value]
  (-> value
      (max min-value)
      (min max-value)))

(defn- set-body-drag-style!
  [cursor]
  (let [body-style (.. js/document -body -style)]
    (set! (.-cursor body-style) cursor)
    (set! (.-userSelect body-style) "none")))

(defn- clear-body-drag-style! []
  (let [body-style (.. js/document -body -style)]
    (set! (.-cursor body-style) "")
    (set! (.-userSelect body-style) "")))

(defn- resize-split-from-event!
  [split-id is-horizontal? container-el event]
  (when container-el
    (let [rect (.getBoundingClientRect container-el)
          total-size (if is-horizontal? (.-width rect) (.-height rect))
          pointer-pos (if is-horizontal?
                        (- (.-clientX event) (.-left rect))
                        (- (.-clientY event) (.-top rect)))]
      (when (pos? total-size)
        (let [min-percent (min 45 (* 100 (/ 100 total-size)))
              left-size (clamp (* 100 (/ pointer-pos total-size))
                               min-percent
                               (- 100 min-percent))]
          (state/dispatch :resize-split split-id [left-size (- 100 left-size)]))))))

(defn split-node-view
  "Render a split node with two sub-trees and a splitter."
  [split-node on-split on-remove can-remove?]
  (r/with-let [container-ref (r/atom nil)
               dragging? (r/atom false)]
    (let [orientation (:orientation split-node)
          split-id (:id split-node)
          sizes (or (:sizes split-node) [50 50])
          is-horizontal? (= orientation :h)
          cursor (if is-horizontal? "col-resize" "row-resize")
          flex-direction (if is-horizontal? "row" "column")
          end-drag! (fn [event]
                      (when @dragging?
                        (reset! dragging? false)
                        (clear-body-drag-style!)
                        (try
                          (.releasePointerCapture (.-currentTarget event) (.-pointerId event))
                          (catch js/Error _ nil))))]

      [:div
       {:style {:display "flex"
                :flex-direction flex-direction
                :height "100%"
                :width "100%"
                :overflow "visible"
                :position "relative"}
        :ref (fn [el] (reset! container-ref el))}

       ;; Left/Top child
       [:div
        {:style {:flex (first sizes)
                 :overflow "hidden"
                 :min-width "100px"
                 :min-height "100px"}}
        ^{:key (str "split-left-" (:id (:left split-node)))}
        [layout-tree-view (:left split-node) on-split on-remove can-remove?]]

       ;; Splitter divider. The visible rule is 2px; the child hit area is wider.
       [:div
        {:style {:background "#ddd"
                 :cursor cursor
                 :width (if is-horizontal? "2px" "100%")
                 :height (if is-horizontal? "100%" "2px")
                 :flex-shrink 0
                 :position "relative"
                 :overflow "visible"
                 :user-select "none"
                 :z-index 5}}
        [:div
         {:style (merge
                  {:position "absolute"
                   :cursor cursor
                   :touch-action "none"
                   :z-index 6}
                  (if is-horizontal?
                    {:top 0 :bottom 0 :left "-6px" :width "14px"}
                    {:left 0 :right 0 :top "-6px" :height "14px"}))
          :on-pointer-down (fn [event]
                             (.preventDefault event)
                             (reset! dragging? true)
                             (set-body-drag-style! cursor)
                             (.setPointerCapture (.-currentTarget event) (.-pointerId event))
                             (resize-split-from-event! split-id is-horizontal? @container-ref event))
          :on-pointer-move (fn [event]
                             (when @dragging?
                               (.preventDefault event)
                               (resize-split-from-event! split-id is-horizontal? @container-ref event)))
          :on-pointer-up end-drag!
          :on-pointer-cancel end-drag!}]]

       ;; Right/Bottom child
       [:div
        {:style {:flex (second sizes)
                 :overflow "hidden"
                 :min-width "100px"
                 :min-height "100px"}}
        ^{:key (str "split-right-" (:id (:right split-node)))}
        [layout-tree-view (:right split-node) on-split on-remove can-remove?]]])
    (finally
      (clear-body-drag-style!))))

(defn layout-tree-view
  "Recursively render the layout tree."
  [node on-split on-remove can-remove?]
  (cond
    (nil? node) 
    [:div {:style {:flex 1 :background "#f0f0f0"}} "Empty layout"]
    
    (= (:type node) :canvas)
    ^{:key (str "canvas-" (:id node))}
    [canvas-node-view (:id node) node on-split on-remove can-remove?]
    
    (= (:type node) :split)
    ^{:key (str "split-" (:id node))}
    [split-node-view node on-split on-remove can-remove?]
    
    :else
    [:div {:style {:flex 1 :background "#f0f0f0"}} "Unknown node type"]))

;; ============================================================================
;; Main Canvas Manager Component
;; ============================================================================

(defn canvas-manager
  "Main component managing the dynamic canvas layout."
  []
  (let [layout-atom (r/atom (state/get-layout-root))]
    
    ;; Listen for layout changes in app-state
    (r/create-class
     {:component-did-mount
      (fn []
        ;; Subscribe to app-state changes
        (add-watch state/app-state :canvas-layout
                   (fn [_ _ old-state new-state]
                     (when (not= (:layout old-state) (:layout new-state))
                       (reset! layout-atom (get-in new-state [:layout :root]))))))
      
      :component-will-unmount
      (fn []
        (remove-watch state/app-state :canvas-layout))
      
      :reagent-render
      (fn []
        (let [on-split (fn [canvas-id orientation]
                         (state/dispatch :split-canvas canvas-id orientation))
              on-remove (fn [canvas-id]
                          (state/dispatch :remove-canvas canvas-id))
              can-remove? (> (model/count-canvases @layout-atom) 1)]
          
          [:div.canvas-manager
           {:style {:display "flex"
                    :flex 1
                    :overflow "hidden"
                    :height "100%"
                    :width "100%"}}
           [layout-tree-view @layout-atom on-split on-remove can-remove?]]))})))
