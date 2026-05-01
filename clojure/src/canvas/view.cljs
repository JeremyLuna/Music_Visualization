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
  []
  (let [el (r/atom nil)]
    (r/create-class
     {:component-did-mount
      (fn [this]
        ;; Store the DOM node so we can use ResizeObserver
        (reset! el (r/dom-node this))
        
        ;; Set up ResizeObserver to track canvas size changes
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
            (.observe observer @el))))
      
      :reagent-render
      (fn []
        [:canvas
         {:style {:width "100%"
                  :height "100%"
                  :background "white"
                  :border "1px solid #ccc"}
          :ref (fn [el] (reset! el el))}])})))

;; ============================================================================
;; Tree Rendering Components
;; ============================================================================

(defn canvas-node-view
  "Render a single canvas node with controls."
  [canvas-id canvas-node on-split on-remove]
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
             :gap "5px"
             :font-size "12px"}}
    
    [:span (str "Canvas " canvas-id)]
    
    [:button
     {:on-click #(on-split canvas-id :h)
      :style {:padding "3px 8px" :font-size "11px" :cursor "pointer"}}
     "Split H"]
    
    [:button
     {:on-click #(on-split canvas-id :v)
      :style {:padding "3px 8px" :font-size "11px" :cursor "pointer"}}
     "Split V"]
    
    [:button
     {:on-click #(on-remove canvas-id)
      :style {:padding "3px 8px" :font-size "11px" :cursor "pointer"
              :margin-left "auto" :background "#ff6b6b" :color "white"}}
     "Remove"]]
   
   ;; Canvas element
   [:div
    {:style {:flex 1 :overflow "hidden"}}
    [canvas-element]]])

(declare layout-tree-view)

(defn split-node-view
  "Render a split node with two sub-trees and a splitter."
  [split-node on-split on-remove]
  (let [orientation (:orientation split-node)
        is-horizontal? (= orientation :h)
        flex-direction (if is-horizontal? "row" "column")]
    
    [:div
     {:style {:display "flex"
              :flex-direction flex-direction
              :height "100%"
              :width "100%"}}
     
     ;; Left/Top child
     [:div
      {:style {:flex 1
               :overflow "hidden"
               :min-width "100px"
               :min-height "100px"}}
      [layout-tree-view (:left split-node) on-split on-remove]]
     
     ;; Splitter divider
     [:div
      {:style {:background "#ddd"
               :cursor (if is-horizontal? "col-resize" "row-resize")
               :width (if is-horizontal? "8px" "100%")
               :height (if is-horizontal? "100%" "8px")
               :flex-shrink 0
               :user-select "none"}}]
     
     ;; Right/Bottom child
     [:div
      {:style {:flex 1
               :overflow "hidden"
               :min-width "100px"
               :min-height "100px"}}
      [layout-tree-view (:right split-node) on-split on-remove]]]))

(defn layout-tree-view
  "Recursively render the layout tree."
  [node on-split on-remove]
  (cond
    (nil? node) 
    [:div {:style {:flex 1 :background "#f0f0f0"}} "Empty layout"]
    
    (= (:type node) :canvas)
    [canvas-node-view (:id node) node on-split on-remove]
    
    (= (:type node) :split)
    [split-node-view node on-split on-remove]
    
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
                         ;; Get next available ID
                         (let [next-id (state/get-next-canvas-id)]
                           ;; Update app state with new layout
                           (swap! state/app-state
                                  (fn [app-state]
                                    (let [current-layout (get-in app-state [:layout :root])
                                          new-layout (model/split-canvas current-layout canvas-id orientation next-id)]
                                      (assoc-in app-state [:layout :root] new-layout))))))
              
              on-remove (fn [canvas-id]
                          ;; Update app state by removing canvas
                          (swap! state/app-state
                                 (fn [app-state]
                                   (let [current-layout (get-in app-state [:layout :root])
                                         new-layout (model/remove-canvas current-layout canvas-id)]
                                     (if new-layout
                                       (assoc-in app-state [:layout :root] new-layout)
                                       app-state)))))]
          
          [:div.canvas-manager
           {:style {:display "flex"
                    :flex 1
                    :overflow "hidden"
                    :height "100%"
                    :width "100%"}}
           [layout-tree-view @layout-atom on-split on-remove]]))})))
