// dynamicCanvas.js
export class DynamicCanvas {
    constructor(options = {}) {
        this.cssPath = options.cssPath || 'dynamic_canvas/dynamic_canvas.css';
        this.layoutTree = {
            id: 'root',
            type: 'canvas',
            color: this.getRandomColor()
        };
        this.dragState = null;
        this.hideUITimer = null;
        this.HIDE_DELAY = 2500;
        this.init();
    }

    init() {
        this.loadStyles();
        this.createDOM();
        this.render();
        this.setupUIAutoHide();
    }

    loadStyles() {
        const link = document.createElement('link');
        link.rel = 'stylesheet';
        link.href = this.cssPath;
        document.head.appendChild(link);
    }

    createDOM() {
        this.container = this.createEl('div', 'container');
        this.canvasArea = this.createEl('div', 'canvas-area', 'canvasArea');
        this.container.appendChild(this.canvasArea);
        document.body.appendChild(this.container);
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

    findNodeById(tree, id) {
        if (tree.id === id) return tree;
        if (tree.children) {
            for (const child of tree.children) {
                const result = this.findNodeById(child, id);
                if (result) return result;
            }
        }
        return null;
    }

    findParentById(tree, id) {
        if (tree.children) {
            for (const child of tree.children) {
                if (child.id === id) return tree;
                const result = this.findParentById(child, id);
                if (result) return result;
            }
        }
        return null;
    }

    splitCanvas(canvasId, direction) {
        const node = this.findNodeById(this.layoutTree, canvasId);
        if (!node || node.type !== 'canvas') return;

        const newCanvas = {
            id: this.generateId(),
            type: 'canvas',
            color: this.getRandomColor()
        };

        const newContainer = {
            id: this.generateId(),
            type: 'split',
            direction,
            children: [
                { ...node, id: this.generateId() },
                newCanvas
            ],
            sizes: [50, 50]
        };

        if (node.id === this.layoutTree.id) {
            this.layoutTree = newContainer;
        } else {
            const parent = this.findParentById(this.layoutTree, canvasId);
            if (!parent) return;
            const index = parent.children.findIndex(child => child.id === canvasId);
            if (index === -1) return;
            parent.children[index] = newContainer;
        }

        this.render();
    }

    splitHorizontal(id) {
        this.splitCanvas(id, 'horizontal');
    }

    splitVertical(id) {
        this.splitCanvas(id, 'vertical');
    }

    removeCanvas(id) {
        if (id === this.layoutTree.id && this.layoutTree.type === 'canvas') return;

        const parent = this.findParentById(this.layoutTree, id);
        if (!parent || !parent.children) return;

        const index = parent.children.findIndex(child => child.id === id);
        if (index === -1) return;

        parent.children.splice(index, 1);

        if (parent.children.length === 1) {
            const remaining = parent.children[0];
            const grandParent = this.findParentById(this.layoutTree, parent.id);

            if (!grandParent) {
                this.layoutTree = remaining;
            } else {
                const parentIndex = grandParent.children.findIndex(child => child.id === parent.id);
                grandParent.children[parentIndex] = remaining;
            }
        }

        this.render();
    }

    render() {
        this.canvasArea.innerHTML = '';
        this.renderNode(this.layoutTree, this.canvasArea);
    }

    renderNode(node, container) {
        if (node.type === 'canvas') {
            const panel = this.createEl('div', 'canvas-panel');
            panel.style.flex = '1';

            const content = this.createEl('div', 'canvas-content');
            content.style.backgroundColor = node.color;
            content.textContent = `Canvas ${node.id.split('_')[1] || 'Root'}`;

            const controls = this.createEl('div', 'canvas-controls');

            const btn = (label, title, handler) => {
                const b = this.createEl('button', 'control-btn');
                b.innerHTML = label;
                b.title = title;
                b.onclick = (e) => {
                    e.stopPropagation();
                    handler();
                };
                return b;
            };

            controls.appendChild(btn('→', 'Split horizontally', () => this.splitHorizontal(node.id)));
            controls.appendChild(btn('↓', 'Split vertically', () => this.splitVertical(node.id)));

            if (!(node.id === this.layoutTree.id && this.layoutTree.type === 'canvas')) {
                const rm = btn('✕', 'Remove canvas', () => this.removeCanvas(node.id));
                rm.classList.add('remove');
                controls.appendChild(rm);
            }

            panel.appendChild(content);
            panel.appendChild(controls);
            container.appendChild(panel);

        } else if (node.type === 'split') {
            const splitContainer = this.createEl('div', `split-container split-${node.direction}`);
            splitContainer.style.flex = '1';

            node.children.forEach((child, i) => {
                const childContainer = this.createEl('div');
                childContainer.style.flex = `${node.sizes[i] || 50}`;
                childContainer.style.display = 'flex';

                this.renderNode(child, childContainer);
                splitContainer.appendChild(childContainer);

                if (i < node.children.length - 1) {
                    const divider = this.createEl('div', `divider ${node.direction}-divider`);
                    this.setupDividerDragging(divider, node, i, splitContainer);
                    splitContainer.appendChild(divider);
                }
            });

            container.appendChild(splitContainer);
        }
    }

    setupDividerDragging(divider, node, i, splitContainer) {
        let startPos, startSizes;
        divider.onmousedown = (e) => {
            e.preventDefault();
            divider.classList.add('dragging');
            document.body.classList.add('dragging');
            if (node.direction === 'vertical') {
                document.body.classList.add('dragging-vertical');
            }

            startPos = node.direction === 'horizontal' ? e.clientX : e.clientY;
            startSizes = [...node.sizes];
            const leftChild = splitContainer.children[i * 2];
            const rightChild = splitContainer.children[(i + 1) * 2];

            const onMouseMove = (e) => {
                requestAnimationFrame(() => {
                    const currentPos = node.direction === 'horizontal' ? e.clientX : e.clientY;
                    const delta = currentPos - startPos;
                    const containerSize = node.direction === 'horizontal'
                        ? splitContainer.offsetWidth : splitContainer.offsetHeight;
                    const deltaPercent = (delta / containerSize) * 100;

                    const newSize1 = Math.max(5, Math.min(95, startSizes[i] + deltaPercent));
                    const newSize2 = Math.max(5, Math.min(95, startSizes[i + 1] - deltaPercent));

                    leftChild.style.flex = newSize1;
                    rightChild.style.flex = newSize2;
                });
            };

            const onMouseUp = (e) => {
                divider.classList.remove('dragging');
                document.body.classList.remove('dragging', 'dragging-vertical');

                const currentPos = node.direction === 'horizontal'
                    ? (e?.clientX || startPos) : (e?.clientY || startPos);
                const delta = currentPos - startPos;
                const containerSize = node.direction === 'horizontal'
                    ? splitContainer.offsetWidth : splitContainer.offsetHeight;
                const deltaPercent = (delta / containerSize) * 100;

                node.sizes[i] = Math.max(5, Math.min(95, startSizes[i] + deltaPercent));
                node.sizes[i + 1] = Math.max(5, Math.min(95, startSizes[i + 1] - deltaPercent));

                document.removeEventListener('mousemove', onMouseMove);
                document.removeEventListener('mouseup', onMouseUp);
            };

            document.addEventListener('mousemove', onMouseMove);
            document.addEventListener('mouseup', onMouseUp);
        };
    }

    createEl(tag, className = '', id = '') {
        const el = document.createElement(tag);
        if (className) el.className = className;
        if (id) el.id = id;
        return el;
    }

    setupUIAutoHide() {
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
}
