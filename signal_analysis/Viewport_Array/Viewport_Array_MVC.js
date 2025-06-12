// dynamicCanvas.js

export class DynamicCanvas {
    constructor(options = {}) {
        this.cssPath = options.cssPath || 'dynamic_canvas/dynamic_canvas.css';
        this.layoutTree = {
            id: 'root',
            type: 'canvas',
            color: this.getRandomColor(),
            canvasEl: null  // Store persistent canvas element
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
        document.addEventListener('keydown', showUI);
    }

    render() {
        // Clear previous contents
        this.canvasArea.innerHTML = '';
        // Recursively render starting from root
        this.renderNode(this.layoutTree, this.canvasArea);
    }

    renderNode(node, container) {
        if (node.type === 'canvas') {
            // CONTENT DIV that will now hold an actual <canvas>
            const content = this.createEl('div', 'canvas-content');
            content.style.position = 'relative';
            content.style.backgroundColor = node.color;
            content.dataset.id = node.id;

            // Get or create persistent HTMLCanvasElement
            let canvasEl;
            if (node.canvasEl) {
                canvasEl = node.canvasEl;
            } else {
                canvasEl = this.createEl('canvas', 'embedded-canvas');
                node.canvasEl = canvasEl;
            }
            // Make it fill the parent
            canvasEl.style.position = 'absolute';
            canvasEl.style.top = '0';
            canvasEl.style.left = '0';
            canvasEl.style.width = '100%';
            canvasEl.style.height = '100%';
            // Update actual pixel size
            canvasEl.width = content.clientWidth;
            canvasEl.height = content.clientHeight;

            // Initialize drawing if first time
            const ctx = canvasEl.getContext('2d');
            if (!canvasEl._initialized) {
                ctx.fillStyle = node.color;
                //ctx.fillRect(0, 0, canvasEl.width, canvasEl.height);
                canvasEl._initialized = true;
            }

            // Append canvas to content
            content.appendChild(canvasEl);

            // Mouse events for content
            content.onmouseenter = (e) => {
                e.stopPropagation();
                // Optionally draw something to indicate canvas is active
                //const ctxHover = canvasEl.getContext('2d');
                //ctxHover.fillStyle = 'rgba(0,0,0,0.05)';
                //ctxHover.fillRect(0, 0, canvasEl.width, canvasEl.height);
            };

            // Observe resizing to update canvas size
            const resizeObserver = new ResizeObserver(() => {
                canvasEl.width = content.clientWidth;
                canvasEl.height = content.clientHeight;
                // Optionally redraw background
                //const ctxResize = canvasEl.getContext('2d');
                //ctxResize.fillStyle = node.color;
                //ctxResize.fillRect(0, 0, canvasEl.width, canvasEl.height);
            });
            resizeObserver.observe(content);

            panel.appendChild(content);

        } else if (node.type === 'split') {
            // Internal node: render two children with a splitter between
            const splitContainer = this.createEl('div', 'split-container');
            splitContainer.dataset.id = node.id;
            splitContainer.style.display = 'flex';
            splitContainer.style.flex = '1';
            splitContainer.style.position = 'relative';
            // Set direction
            if (node.direction === 'horizontal') {
                splitContainer.style.flexDirection = 'row';
            } else {
                splitContainer.style.flexDirection = 'column';
            }

            // Render children
            const childA = this.createEl('div', 'split-child');
            childA.style.flex = String(node.sizes[0] / 100);
            this.renderNode(node.children[0], childA);
            const childB = this.createEl('div', 'split-child');
            childB.style.flex = String(node.sizes[1] / 100);
            this.renderNode(node.children[1], childB);

            // Splitter element
            const splitter = this.createEl('div', 'splitter');
            if (node.direction === 'horizontal') {
                splitter.style.cursor = 'col-resize';
                splitter.style.width = '5px';
                splitter.style.position = 'absolute';
                splitter.style.left = `calc(${node.sizes[0]}% - 2.5px)`;
                splitter.style.top = '0';
                splitter.style.bottom = '0';
            } else {
                splitter.style.cursor = 'row-resize';
                splitter.style.height = '5px';
                splitter.style.position = 'absolute';
                splitter.style.top = `calc(${node.sizes[0]}% - 2.5px)`;
                splitter.style.left = '0';
                splitter.style.right = '0';
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
        if (!this.dragState) return;
        const { node, startPos, startSizes, splitContainer } = this.dragState;
        const delta = (node.direction === 'horizontal' ? e.clientX : e.clientY) - startPos;
        const totalSize = (node.direction === 'horizontal' ? splitContainer.clientWidth : splitContainer.clientHeight);
        let deltaPercent = (delta / totalSize) * 100;
        const newSizeA = startSizes[0] + deltaPercent;
        const newSizeB = startSizes[1] - deltaPercent;
        if (newSizeA < 5 || newSizeB < 5) return;
        node.sizes = [newSizeA, newSizeB];
        // Adjust each side’s flex to match the new percentages:
        splitContainer.children[0].style.flex = String(newSizeA / 100);
        splitContainer.children[1].style.flex = String(newSizeB / 100);
        // Move the splitter bar:
        const splitter = splitContainer.children[2];
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
            color: this.getRandomColor(),
            canvasEl: null
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
            this.layoutTree = newContainer;
        } else {
            const parent = this.findParentById(this.layoutTree, node.id);
            if (!parent) return;
            const idx = parent.children.findIndex(c => c.id === node.id);
            parent.children[idx] = newContainer;
        }
        this.render();
    }

    removeCanvas(canvasId) {
        // Cannot remove root if it's the only canvas
        if (canvasId === this.layoutTree.id && this.layoutTree.type === 'canvas') return;

        // tell plugins
        this.findNodeById(this.layoutTree, canvasId).canvasEl.close();

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
