// dynamicCanvas.js

export class DynamicCanvas {
    constructor(options = {}) {
        this.cssPath = options.cssPath || 'dynamic_canvas/dynamic_canvas.css';
        this.layoutTree = {
            id: 'root',
            type: 'canvas',
            color: this.getRandomColor(),
            // no children yet
        };
        this.dragState = null;
        this.hideUITimer = null;
        this.HIDE_DELAY = 2500;

        this.init();
    }

    init() {
        this.loadStyles();
        this.createDOM();
        this.bindUIAutohide();
        this.render();
    }

    loadStyles() {
        const link = document.createElement('link');
        link.rel = 'stylesheet';
        link.href = this.cssPath;
        document.head.appendChild(link);
    }

    createDOM() {
        // Main container
        this.container = this.createEl('div', 'dc-container');
        // Area where panels (canvases or splits) get rendered
        this.canvasArea = this.createEl('div', 'dc-canvas-area');
        this.container.appendChild(this.canvasArea);
        document.body.appendChild(this.container);
    }

    bindUIAutohide() {
        const showUI = () => {
            document.body.classList.remove('hide-ui');
            clearTimeout(this.hideUITimer);
            this.hideUITimer = setTimeout(() => {
                document.body.classList.add('hide-ui');
            }, this.HIDE_DELAY);
        };
        document.addEventListener('mousemove', showUI);
        document.addEventListener('mousedown', showUI);
        document.addEventListener('keydown', showUI);
        showUI(); // initial kick-off
    }

    render() {
        // Clear previous contents
        this.canvasArea.innerHTML = '';
        // Recursively render starting from root
        this.renderNode(this.layoutTree, this.canvasArea);
    }

    renderNode(node, container) {
        if (node.type === 'canvas') {
            // Leaf: render a single canvas panel
            const panel = this.createEl('div', 'canvas-panel');
            panel.style.flex = '1';
            panel.dataset.id = node.id;

            // CONTENT DIV that will now hold an actual <canvas>
            const content = this.createEl('div', 'canvas-content');
            content.style.position = 'relative';
            content.style.backgroundColor = node.color;
            content.dataset.id = node.id;

            // Create actual HTMLCanvasElement inside content
            const canvasEl = this.createEl('canvas', 'embedded-canvas');
            // Make it fill the parent
            canvasEl.style.position = 'absolute';
            canvasEl.style.top = '0';
            canvasEl.style.left = '0';
            canvasEl.style.width = '100%';
            canvasEl.style.height = '100%';
            canvasEl.width = content.clientWidth;
            canvasEl.height = content.clientHeight;

            content.appendChild(canvasEl);
            panel.appendChild(content);

            // CONTROLS (split/remove buttons)
            const controls = this.createEl('div', 'canvas-controls');
            // Split horizontally
            const splitHBtn = this.createEl('button', 'control-btn');
            splitHBtn.innerHTML = '⇔';
            splitHBtn.title = 'Split horizontally';
            splitHBtn.onclick = (e) => {
                e.stopPropagation();
                this.splitCanvas(node.id, 'horizontal');
            };
            // Split vertically
            const splitVBtn = this.createEl('button', 'control-btn');
            splitVBtn.innerHTML = '⇕';
            splitVBtn.title = 'Split vertically';
            splitVBtn.onclick = (e) => {
                e.stopPropagation();
                this.splitCanvas(node.id, 'vertical');
            };
            // Remove canvas (only if not root)
            const removeBtn = this.createEl('button', 'control-btn');
            removeBtn.innerHTML = '✕';
            removeBtn.title = 'Remove this canvas';
            removeBtn.onclick = (e) => {
                e.stopPropagation();
                this.removeCanvas(node.id);
            };

            controls.appendChild(splitHBtn);
            controls.appendChild(splitVBtn);
            if (node.id !== this.layoutTree.id) {
                controls.appendChild(removeBtn);
            }
            panel.appendChild(controls);

            // Attach a ResizeObserver to keep canvas size in sync
            const resizeObserver = new ResizeObserver(entries => {
                for (let entry of entries) {
                    const cr = entry.contentRect;
                    canvasEl.width = Math.floor(cr.width);
                    canvasEl.height = Math.floor(cr.height);
                    // Redraw if desired; here we'll clear to background
                    const ctx = canvasEl.getContext('2d');
                    ctx.clearRect(0, 0, canvasEl.width, canvasEl.height);
                    // (Optionally draw something to indicate canvas is active)
                    ctx.fillStyle = 'rgba(0,0,0,0.05)';
                    ctx.fillRect(0, 0, canvasEl.width, canvasEl.height);
                }
            });
            resizeObserver.observe(content);

            container.appendChild(panel);
        } else if (node.type === 'split') {
            // Internal node: render two children with a splitter between
            const splitContainer = this.createEl('div', 'split-container');
            splitContainer.dataset.id = node.id;
            splitContainer.style.display = 'flex';
            splitContainer.style.flex = '1';
            splitContainer.style.position = 'relative';

            if (node.direction === 'horizontal') {
                splitContainer.style.flexDirection = 'row';
            } else {
                splitContainer.style.flexDirection = 'column';
            }

            // First child panel/container
            const childA = this.createEl('div', 'split-child');
            childA.style.flex = node.sizes[0] / 100;
            this.renderNode(node.children[0], childA);

            // Second child panel/container
            const childB = this.createEl('div', 'split-child');
            childB.style.flex = node.sizes[1] / 100;
            this.renderNode(node.children[1], childB);

            // Splitter bar
            const splitter = this.createEl('div', 'splitter');
            splitter.style.position = 'absolute';
            splitter.style.background = 'rgba(0,0,0,0.2)';
            splitter.style.zIndex = '10';
            if (node.direction === 'horizontal') {
                splitter.style.cursor = 'col-resize';
                splitter.style.width = '5px';
                splitter.style.top = '0';
                splitter.style.bottom = '0';
                splitter.style.left = `calc(${node.sizes[0]}% - 2.5px)`;
            } else {
                splitter.style.cursor = 'row-resize';
                splitter.style.height = '5px';
                splitter.style.left = '0';
                splitter.style.right = '0';
                splitter.style.top = `calc(${node.sizes[0]}% - 2.5px)`;
            }

            // Mouse events for dragging
            splitter.onmousedown = (e) => {
                e.preventDefault();
                this.dragState = {
                    node,
                    startPos: node.direction === 'horizontal' ? e.clientX : e.clientY,
                    startSizes: [...node.sizes],
                    splitContainer,
                    index: 0 // always split between child 0 and 1
                };
                document.body.classList.add('dragging');
                if (node.direction === 'vertical') {
                    document.body.classList.add('dragging-vertical');
                }
                document.addEventListener('mousemove', this.onDrag);
                document.addEventListener('mouseup', this.onDragEnd);
            };

            splitContainer.appendChild(childA);
            splitContainer.appendChild(childB);
            splitContainer.appendChild(splitter);
            container.appendChild(splitContainer);
        }
    }

    onDrag = (e) => {
        const { node, startPos, startSizes, splitContainer } = this.dragState;
        // Determine current mouse position along the split axis:
        const currentPos = node.direction === 'horizontal' ? e.clientX : e.clientY;
        const delta = currentPos - startPos;

        // Measure the container’s current pixel size:
        const totalSize = node.direction === 'horizontal'
            ? splitContainer.clientWidth
            : splitContainer.clientHeight;

        // Compute how many percentage‐points we’ve moved:
        const deltaPercent = (delta / totalSize) * 100;

        // Clamp between 5% and 95% to avoid collapsing:
        const newSizeA = Math.max(5, Math.min(95, startSizes[0] + deltaPercent));
        const newSizeB = 100 - newSizeA; // keep sum = 100

        // Update model state so future splits/removals are correct:
        node.sizes[0] = newSizeA;
        node.sizes[1] = newSizeB;

        // Now update the DOM in place:
        // Recall: in renderNode we did:
        //   splitContainer.appendChild(childA);
        //   splitContainer.appendChild(childB);
        //   splitContainer.appendChild(splitter);
        //
        // So at indices 0 and 1 are the two “.split-child” wrappers,
        // and at index 2 is the splitter itself.
        const childA = splitContainer.children[0];
        const childB = splitContainer.children[1];
        const splitter = splitContainer.children[2];

        // Adjust each side’s flex to match the new percentages:
        childA.style.flex = String(newSizeA / 100);
        childB.style.flex = String(newSizeB / 100);

        // Move the splitter bar:
        if (node.direction === 'horizontal') {
            splitter.style.left = `calc(${newSizeA}% - 2.5px)`;
        } else {
            splitter.style.top = `calc(${newSizeA}% - 2.5px)`;
        }
    };

    onDragEnd = (e) => {
        document.body.classList.remove('dragging', 'dragging-vertical');
        document.removeEventListener('mousemove', this.onDrag);
        document.removeEventListener('mouseup', this.onDragEnd);
        this.dragState = null;
    };

    splitCanvas(canvasId, direction) {
        // Find the node to split
        const node = this.findNodeById(this.layoutTree, canvasId);
        if (!node || node.type !== 'canvas') return;

        // Create a new leaf
        const newLeaf = {
            id: this.generateId(),
            type: 'canvas',
            color: this.getRandomColor()
        };
        // Replace the canvas node with a split node
        const newContainer = {
            id: this.generateId(),
            type: 'split',
            direction,
            sizes: [50, 50],
            children: [
                { ...node, id: node.id }, // keep same id for first child
                newLeaf
            ]
        };

        if (node.id === this.layoutTree.id) {
            // Replacing root
            this.layoutTree = newContainer;
        } else {
            const parent = this.findParentById(this.layoutTree, canvasId);
            if (!parent) return;
            const idx = parent.children.findIndex(c => c.id === canvasId);
            parent.children[idx] = newContainer;
        }
        this.render();
    }

    removeCanvas(canvasId) {
        // Cannot remove root if it's the only canvas
        if (canvasId === this.layoutTree.id && this.layoutTree.type === 'canvas') return;

        const parent = this.findParentById(this.layoutTree, canvasId);
        if (!parent) return;
        const idx = parent.children.findIndex(c => c.id === canvasId);
        if (idx === -1) return;

        // The sibling becomes the replacement for the parent split
        const sibling = parent.children[1 - idx];
        if (parent.id === this.layoutTree.id) {
            this.layoutTree = sibling;
        } else {
            const grandParent = this.findParentById(this.layoutTree, parent.id);
            if (!grandParent) return;
            const parentIdx = grandParent.children.findIndex(c => c.id === parent.id);
            grandParent.children[parentIdx] = sibling;
        }
        this.render();
    }

    findNodeById(tree, id) {
        if (tree.id === id) return tree;
        if (tree.type === 'split') {
            for (let child of tree.children) {
                const found = this.findNodeById(child, id);
                if (found) return found;
            }
        }
        return null;
    }

    findParentById(tree, id, parent = null) {
        if (tree.id === id) return parent;
        if (tree.type === 'split') {
            for (let child of tree.children) {
                const found = this.findParentById(child, id, tree);
                if (found) return found;
            }
        }
        return null;
    }

    getRandomColor() {
        const colors = [
            '#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4', '#FFEAA7',
            '#DDA0DD', '#98D8C8', '#F7DC6F', '#BB8FCE', '#85C1E9',
            '#F8C471', '#82E0AA', '#F1948A', '#85C1E9', '#D2B4DE'
        ];
        return colors[Math.floor(Math.random() * colors.length)];
    }

    generateId() {
        return 'canvas_' + Math.random().toString(36).substr(2, 9);
    }

    createEl(tag, className) {
        const el = document.createElement(tag);
        if (className) el.classList.add(className);
        return el;
    }
}
