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
   - id: Unique split ID
   - orientation: :h (horizontal split) or :v (vertical split)
   - left: Left/top child node
   - right: Right/bottom child node
   
   Returns: Split node map"
  ([orientation left right]
   (create-split-node nil orientation left right))
  ([id orientation left right]
   {:type :split
    :id id
    :orientation orientation
    :sizes [50 50]
    :left left
    :right right}))

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

(defn- find-canvas-path
  "Find the path to a canvas node by ID.

   Returns a vector of tree keys, such as [:left :right], or nil if not found."
  ([tree id]
   (find-canvas-path tree id []))
  ([tree id path]
   (cond
     (nil? tree) nil
     (and (= (:type tree) :canvas)
          (= (:id tree) id)) path
     (= (:type tree) :split)
     (or (find-canvas-path (:left tree) id (conj path :left))
         (find-canvas-path (:right tree) id (conj path :right)))
     :else nil)))

(defn- node-at-path
  [tree path]
  (if (empty? path)
    tree
    (get-in tree path)))

(defn- replace-at-path
  [tree path replacement]
  (if (empty? path)
    replacement
    (assoc-in tree path replacement)))

(defn find-parent-of-node
  "Find the parent of a node with the given ID.
   
   Returns: [parent-node child-key] where child-key is :left or :right, or nil if not found"
  [tree target-id]
  (when-let [path (find-canvas-path tree target-id)]
    (when (seq path)
      [(node-at-path tree (pop path)) (peek path)])))

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
  (when-let [target-path (find-canvas-path tree canvas-id)]
    (let [target (node-at-path tree target-path)
          new-split (create-split-node (str "split-" canvas-id "-" new-canvas-id)
                                       orientation
                                       target
                                       (create-canvas-node new-canvas-id :waveform {}))]
      (replace-at-path tree target-path new-split))))

(defn update-split-sizes
  "Update a split node's child size weights."
  [tree split-id sizes]
  (cond
    (nil? tree) nil

    (and (= (:type tree) :split)
         (= (:id tree) split-id))
    (assoc tree :sizes sizes)

    (= (:type tree) :split)
    (-> tree
        (update :left update-split-sizes split-id sizes)
        (update :right update-split-sizes split-id sizes))

    :else tree))

(defn remove-canvas
  "Remove a canvas from the tree.
   
   If removing leaves only one canvas, promote the sibling to replace the split.
   
   Args:
   - tree: The layout tree
   - canvas-id: ID of the canvas to remove
   
   Returns: New tree with canvas removed, or nil if canvas-id not found or is the last canvas"
  [tree canvas-id]
  (let [canvas-path (find-canvas-path tree canvas-id)
        all-canvas-ids (get-all-canvas-ids tree)]
    
    (cond
      ;; Canvas not found
      (nil? canvas-path) nil
      
      ;; Can't remove the only canvas
      (= (count all-canvas-ids) 1) nil

      ;; A root canvas can only be the only canvas, but keep the shape explicit.
      (empty? canvas-path) nil
      
      ;; Remove from parent
      :else
      (let [parent-path (pop canvas-path)
            child-key (peek canvas-path)
            sibling-key (if (= child-key :left) :right :left)
            sibling (node-at-path tree (conj parent-path sibling-key))]
        ;; Replace the parent split with the removed canvas's sibling.
        (replace-at-path tree parent-path sibling)))))

(defn update-canvas-settings
  "Update the settings of a specific canvas.
   
   Args:
   - tree: The layout tree
   - canvas-id: ID of the canvas to update
   - settings: New settings map or a function to update existing settings
   
   Returns: New tree with updated settings"
  [tree canvas-id settings]
  (let [canvas-path (find-canvas-path tree canvas-id)]
    (if (nil? canvas-path)
      tree
      (let [canvas (node-at-path tree canvas-path)
            current-settings (or (:settings canvas) {})
            new-settings (if (fn? settings)
                           (settings current-settings)
                           (merge current-settings settings))]
        (assoc-in tree (conj canvas-path :settings) new-settings)))))

(defn change-canvas-visualizer
  "Change the visualizer type of a specific canvas.
   
   Args:
   - tree: The layout tree
   - canvas-id: ID of the canvas to update
   - visualizer-type: New visualizer type keyword
   
   Returns: New tree with updated visualizer"
  [tree canvas-id visualizer-type]
  (let [canvas-path (find-canvas-path tree canvas-id)]
    (if (nil? canvas-path)
      tree
      (assoc-in tree (conj canvas-path :visualizer-type) visualizer-type))))

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
      (str pad "Split(id=" (:id tree) ", orientation=" (:orientation tree) ", sizes=" (:sizes tree) ")\n"
           (tree->string (:left tree) :indent (+ indent 2)) "\n"
           (tree->string (:right tree) :indent (+ indent 2)))
      :else (str pad "Unknown"))))
