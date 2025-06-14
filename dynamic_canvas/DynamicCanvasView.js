export class DynamicCanvasView {
    constructor(model, controller, options = {}) {
        this.model = model;
        this.controller = controller;
        this.cssPath = options.cssPath || 'dynamic_canvas/dynamic_canvas.css';
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
        this.viewportArray = this.createEl('div', 'viewport-array');
        this.viewport = this.createEl('div', 'viewport');
        this.viewportArray.appendChild(this.viewport);
        document.body.appendChild(this.viewportArray);
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
        this.viewport.innerHTML = '';
        this.renderNode(this.model.layoutTree, this.viewport);
    }

    renderNode(node, container) {
        if (node.type === 'canvas') {
            const panel = this.createEl('div', 'canvas-panel');
            panel.style.flex = '1';
            panel.dataset.id = node.id;

            const content = this.createEl('div', 'canvas-content');
            content.style.position = 'relative';
            content.style.backgroundColor = node.color;
            content.dataset.id = node.id;

            let canvasEl;
            if (node.canvasEl) {
                canvasEl = node.canvasEl;
            } else {
                canvasEl = this.createEl('canvas', 'embedded-canvas');
                node.canvasEl = canvasEl;
            }
            canvasEl.style.position = 'absolute';
            canvasEl.style.top = '0';
            canvasEl.style.left = '0';
            canvasEl.style.width = '100%';
            canvasEl.style.height = '100%';
            canvasEl.width = content.clientWidth;
            canvasEl.height = content.clientHeight;

            const ctx = canvasEl.getContext('2d');
            if (!canvasEl._initialized) {
                canvasEl._initialized = true;
            }

            content.appendChild(canvasEl);

            content.onmouseenter = (e) => {
                e.stopPropagation();
            };

            const resizeObserver = new ResizeObserver(() => {
                canvasEl.width = content.clientWidth;
                canvasEl.height = content.clientHeight;
            });
            resizeObserver.observe(content);

            panel.appendChild(content);

            const controls = this.createEl('div', 'canvas-controls');
            const splitHBtn = this.createEl('button', 'control-btn');
            splitHBtn.innerHTML = '⇔';
            splitHBtn.title = 'Split horizontally';
            splitHBtn.onclick = (e) => {
                e.stopPropagation();
                this.controller.splitCanvas(node.id, 'horizontal');
            };
            const splitVBtn = this.createEl('button', 'control-btn');
            splitVBtn.innerHTML = '⇕';
            splitVBtn.title = 'Split vertically';
            splitVBtn.onclick = (e) => {
                e.stopPropagation();
                this.controller.splitCanvas(node.id, 'vertical');
            };
            const removeBtn = this.createEl('button', 'control-btn');
            removeBtn.innerHTML = '✕';
            removeBtn.title = 'Remove this canvas';
            removeBtn.onclick = (e) => {
                e.stopPropagation();
                this.controller.removeCanvas(node.id);
            };
            controls.appendChild(splitHBtn);
            controls.appendChild(splitVBtn);
            if (node.id !== this.model.layoutTree.id) {
                controls.appendChild(removeBtn);
            }
            panel.appendChild(controls);

            container.appendChild(panel);
        } else if (node.type === 'split') {
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

            const childA = this.createEl('div', 'split-child');
            childA.style.flex = String(node.sizes[0] / 100);
            this.renderNode(node.children[0], childA);
            const childB = this.createEl('div', 'split-child');
            childB.style.flex = String(node.sizes[1] / 100);
            this.renderNode(node.children[1], childB);

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

            splitter.onmousedown = (e) => {
                e.preventDefault();
                this.dragState = {
                    node,
                    startPos: node.direction === 'horizontal' ? e.clientX : e.clientY,
                    startSizes: [...node.sizes],
                    splitContainer,
                    index: 0
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
        splitContainer.children[0].style.flex = String(newSizeA / 100);
        splitContainer.children[1].style.flex = String(newSizeB / 100);
        const splitter = splitContainer.children[2];
        if (node.direction === 'horizontal') {
            splitter.style.left = `calc(${newSizeA}% - 2.5px)`;
        } else {
            splitter.style.top = `calc(${newSizeA}% - 2.5px)`;
        }
    };

    onDragEnd = () => {
        document.body.classList.remove('dragging', 'dragging-vertical');
        document.removeEventListener('mousemove', this.onDrag);
        document.removeEventListener('mouseup', this.onDragEnd);
        this.dragState = null;
    };

    createEl(tag, className) {
        const el = document.createElement(tag);
        if (className) el.classList.add(className);
        return el;
    }
}
