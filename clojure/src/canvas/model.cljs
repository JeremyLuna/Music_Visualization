(ns canvas.model
  "Canvas layout tree model - pure functions for layout operations.
   
   The layout is represented as a recursive tree structure where nodes are either:
   - :canvas nodes (leaf) - represent individual canvas elements
   - :split nodes (container) - represent a split into left/right or top/bottom")

;; ============================================================================
;; Tree Node Constructors
;; ============================================================================

(defn create-canvas-node
  "Create a canvas leaf node.
   
   Args:
   - id: Unique canvas ID
   - visualizer-type: Keyword identifying the visualizer (:waveform, :stft, etc.)
   - settings: Map of visualizer settings
   
   Returns: Canvas node map"
  [id visualizer-type settings]
  {:type :canvas
   :id id
   :visualizer-type visualizer-type
   :settings settings})

(defn create-split-node
  "Create a split container node.
   
   Args:
   - orientation: :h (horizontal split) or :v (vertical split)
   - left: Left/top child node
   - right: Right/bottom child node
   
   Returns: Split node map"
  [orientation left right]
  {:type :split
   :orientation orientation
   :left left
   :right right})

;; ============================================================================
;; Tree Navigation & Query
;; ============================================================================

(defn find-node
  "Find a node by ID in the tree.
   
   Returns: The node if found, nil otherwise"
  [tree id]
  (cond
    (nil? tree) nil
    (= (:id tree) id) tree
    (= (:type tree) :split)
    (or (find-node (:left tree) id)
        (find-node (:right tree) id))
    :else nil))

(defn find-parent-of-node
  "Find the parent of a node with the given ID.
   
   Returns: [parent-node child-key] where child-key is :left or :right, or nil if not found"
  [tree target-id]
  (cond
    (nil? tree) nil
    (= (:type tree) :split)
    (if (= (get-in tree [:left :id]) target-id)
      [tree :left]
      (if (= (get-in tree [:right :id]) target-id)
        [tree :right]
        (or (find-parent-of-node (:left tree) target-id)
            (find-parent-of-node (:right tree) target-id))))
    :else nil))

(defn get-all-canvas-ids
  "Get a list of all canvas IDs in the tree."
  [tree]
  (cond
    (nil? tree) []
    (= (:type tree) :canvas)
    [(:id tree)]
    (= (:type tree) :split)
    (concat (get-all-canvas-ids (:left tree))
            (get-all-canvas-ids (:right tree)))
    :else []))

(defn count-canvases
  "Count the number of canvas nodes in the tree."
  [tree]
  (count (get-all-canvas-ids tree)))

(defn is-leaf?
  "Check if a node is a leaf (canvas) node."
  [node]
  (= (:type node) :canvas))

(defn is-split?
  "Check if a node is a split node."
  [node]
  (= (:type node) :split))

;; ============================================================================
;; Tree Mutations (Pure - return new tree)
;; ============================================================================

(defn split-canvas
  "Split an existing canvas node into two sub-canvases.
   
   Args:
   - tree: The layout tree
   - canvas-id: ID of the canvas to split
   - orientation: :h for horizontal, :v for vertical
   - new-canvas-id: ID for the new canvas being created
   
   Returns: New tree with the canvas split, or nil if canvas-id not found"
  [tree canvas-id orientation new-canvas-id]
  (let [target (find-node tree canvas-id)]
    (if (nil? target)
      nil
      (let [parent-result (find-parent-of-node tree canvas-id)]
        (if (nil? parent-result)
          ;; Target is the root - replace it with a split containing old and new
          (create-split-node orientation
                             target
                             (create-canvas-node new-canvas-id :waveform {}))
          ;; Target is not root - replace it in the parent
          (let [[parent child-key] parent-result
                new-split (create-split-node orientation
                                              target
                                              (create-canvas-node new-canvas-id :waveform {}))
                new-tree (assoc-in tree [child-key] new-split)]
            new-tree))))))

(defn remove-canvas
  "Remove a canvas from the tree.
   
   If removing leaves only one canvas, promote the sibling to replace the split.
   
   Args:
   - tree: The layout tree
   - canvas-id: ID of the canvas to remove
   
   Returns: New tree with canvas removed, or nil if canvas-id not found or is the last canvas"
  [tree canvas-id]
  (let [parent-result (find-parent-of-node tree canvas-id)
        all-canvas-ids (get-all-canvas-ids tree)]
    
    (cond
      ;; Canvas not found
      (nil? parent-result) nil
      
      ;; Can't remove the only canvas
      (= (count all-canvas-ids) 1) nil
      
      ;; Remove from parent
      :else
      (let [[parent child-key] parent-result
            sibling-key (if (= child-key :left) :right :left)
            sibling (get parent sibling-key)]
        
        ;; Replace the split with its sibling
        (let [grandparent-result (find-parent-of-node tree (:id parent))]
          (if (nil? grandparent-result)
            ;; Parent is root - sibling becomes new root
            sibling
            ;; Parent is not root - replace parent with sibling in grandparent
            (let [[grandparent parent-key] grandparent-result]
              (assoc-in tree [parent-key] sibling))))))))

(defn update-canvas-settings
  "Update the settings of a specific canvas.
   
   Args:
   - tree: The layout tree
   - canvas-id: ID of the canvas to update
   - settings: New settings map or a function to update existing settings
   
   Returns: New tree with updated settings"
  [tree canvas-id settings]
  (let [canvas (find-node tree canvas-id)]
    (if (nil? canvas)
      tree
      (let [current-settings (or (:settings canvas) {})
            new-settings (if (fn? settings)
                           (settings current-settings)
                           (merge current-settings settings))
            parent-result (find-parent-of-node tree canvas-id)]
        (if (nil? parent-result)
          ;; Canvas is root
          (assoc tree :settings new-settings)
          ;; Canvas is not root
          (let [[parent child-key] parent-result]
            (assoc-in tree [child-key :settings] new-settings)))))))

(defn change-canvas-visualizer
  "Change the visualizer type of a specific canvas.
   
   Args:
   - tree: The layout tree
   - canvas-id: ID of the canvas to update
   - visualizer-type: New visualizer type keyword
   
   Returns: New tree with updated visualizer"
  [tree canvas-id visualizer-type]
  (let [canvas (find-node tree canvas-id)]
    (if (nil? canvas)
      tree
      (let [parent-result (find-parent-of-node tree canvas-id)]
        (if (nil? parent-result)
          ;; Canvas is root
          (assoc tree :visualizer-type visualizer-type)
          ;; Canvas is not root
          (let [[parent child-key] parent-result]
            (assoc-in tree [child-key :visualizer-type] visualizer-type)))))))

;; ============================================================================
;; Tree Serialization (for debugging/logging)
;; ============================================================================

(defn tree->string
  "Convert a tree to a readable string representation."
  [tree & {:keys [indent] :or {indent 0}}]
  (let [pad (apply str (repeat indent " "))]
    (cond
      (nil? tree) ""
      (= (:type tree) :canvas)
      (str pad "Canvas(id=" (:id tree) ", viz=" (:visualizer-type tree) ")")
      (= (:type tree) :split)
      (str pad "Split(" (:orientation tree) ")\n"
           (tree->string (:left tree) :indent (+ indent 2)) "\n"
           (tree->string (:right tree) :indent (+ indent 2)))
      :else (str pad "Unknown"))))
