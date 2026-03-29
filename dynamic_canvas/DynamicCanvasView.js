const STYLE_MAP = {
    'viewport-array': {
        display: 'flex',
        height: '100vh',
        width: '100vw'
    },
    'viewport': {
        flex: '1',
        display: 'flex',
        position: 'relative'
    },
    'split-container': {
        display: 'flex',
        width: '100%',
        height: '100%',
        position: 'relative'
    },
    'split-child': {
        display: 'flex',
        flex: '1',
        flexDirection: 'column'
    },
    'canvas-panel': {
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        position: 'relative',
        border: '2px solid #333',
        minWidth: '50px',
        minHeight: '50px'
    },
    'canvas-controls': {
        position: 'absolute',
        top: '8px',
        right: '8px',
        display: 'flex',
        gap: '4px',
        opacity: '0',
        zIndex: '100',
        pointerEvents: 'none'
    },
    'control-btn': {
        width: '24px',
        height: '24px',
        background: 'rgba(0, 122, 204, 0.9)',
        color: 'white',
        border: 'none',
        borderRadius: '4px',
        cursor: 'pointer',
        fontSize: '12px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        backdropFilter: 'blur(10px)'
    },
    'canvas-content': {
        position: 'relative',
        width: '100%',
        height: '100%',
        overflow: 'hidden'
    },
    'embedded-canvas': {
        position: 'absolute',
        top: '0',
        left: '0',
        width: '100%',
        height: '100%',
        display: 'block'
    }
};

export class DynamicCanvasView {
    constructor(model, controller, options = {}) {
        this.model = model;
        this.controller = controller;
        this.dragState = null;
        this.hideUITimer = null;
        this.HIDE_DELAY = 2500;
        this.init();
    }

    init() {
        this.createDOM();
        this.bindUIAutohide();
        this.render();
    }

    createDOM() {
        this.viewportArray = this.createEl('div', 'viewport-array');
        this.viewport = this.createEl('div', 'viewport');
        this.viewportArray.appendChild(this.viewport);
        document.body.appendChild(this.viewportArray);
    }

    bindUIAutohide() {
        const showUI = () => {
            document.body.style.cursor = '';
            document.querySelectorAll('.canvas-controls').forEach(c => {
                c.style.opacity = '1';
                c.style.pointerEvents = 'auto';
            });
            clearTimeout(this.hideUITimer);
            this.hideUITimer = setTimeout(() => {
                document.body.style.cursor = 'none';
                document.querySelectorAll('.canvas-controls').forEach(c => {
                    c.style.opacity = '0';
                    c.style.pointerEvents = 'none';
                });
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

            panel.addEventListener('mouseenter', () => {
                controls.style.opacity = '1';
                controls.style.pointerEvents = 'auto';
                panel.style.borderColor = '#555';
            });
            panel.addEventListener('mouseleave', () => {
                controls.style.opacity = '0';
                controls.style.pointerEvents = 'none';
                panel.style.borderColor = '#333';
            });

            [splitHBtn, splitVBtn, removeBtn].forEach(btn => {
                btn.addEventListener('mouseenter', () => {
                    btn.style.background = btn === removeBtn
                        ? 'rgba(220,53,69,1)'
                        : 'rgba(0,122,204,1)';
                    btn.style.transform = 'scale(1.1)';
                });
                btn.addEventListener('mouseleave', () => {
                    btn.style.background = btn === removeBtn
                        ? 'rgba(220,53,69,0.9)'
                        : 'rgba(0,122,204,0.9)';
                    btn.style.transform = 'scale(1)';
                });
            });

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
                document.body.style.userSelect = 'none';
                document.body.style.cursor = node.direction === 'horizontal' ? 'col-resize' : 'row-resize';
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
        document.body.style.userSelect = '';
        document.body.style.cursor = '';
        document.removeEventListener('mousemove', this.onDrag);
        document.removeEventListener('mouseup', this.onDragEnd);
        this.dragState = null;
    };

    createEl(tag, className) {
        const el = document.createElement(tag);
        if (className && STYLE_MAP[className]) {
            Object.assign(el.style, STYLE_MAP[className]);
        }
        return el;
    }
}
