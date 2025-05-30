// load related css
const canvas_css_link = document.createElement('link');
canvas_css_link.rel = 'stylesheet';
canvas_css_link.href = 'dynamic_canvas/dynamic_canvas.css';  // URL of your stylesheet
document.head.appendChild(canvas_css_link);

// create and attach html elements
const container = document.createElement('div');
container.className = 'container';

const canvasArea = document.createElement('div');
canvasArea.className = 'canvas-area';
canvasArea.id = 'canvasArea';

container.appendChild(canvasArea);
document.body.appendChild(container);

let layoutTree = {
    id: 'root',
    type: 'canvas',
    color: getRandomColor()
};

let dragState = null;

function getRandomColor() {
    const colors = [
        '#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4', '#FFEAA7',
        '#DDA0DD', '#98D8C8', '#F7DC6F', '#BB8FCE', '#85C1E9',
        '#F8C471', '#82E0AA', '#F1948A', '#85C1E9', '#D2B4DE'
    ];
    return colors[Math.floor(Math.random() * colors.length)];
}

function generateId() {
    return 'canvas_' + Math.random().toString(36).substr(2, 9);
}

function findNodeById(tree, id) {
    if (tree.id === id) return tree;
    if (tree.children) {
        for (let child of tree.children) {
            const result = findNodeById(child, id);
            if (result) return result;
        }
    }
    return null;
}

function findParentById(tree, id) {
    if (tree.children) {
        for (let child of tree.children) {
            if (child.id === id) return tree;
            const result = findParentById(child, id);
            if (result) return result;
        }
    }
    return null;
}

function splitCanvas(canvasId, direction) {
    const node = findNodeById(layoutTree, canvasId);
    if (!node || node.type !== 'canvas') return;

    const newCanvas = {
        id: generateId(),
        type: 'canvas',
        color: getRandomColor()
    };

    const newContainer = {
        id: generateId(),
        type: 'split',
        direction: direction,
        children: [
            { ...node, id: generateId() },
            newCanvas
        ],
        sizes: [50, 50]
    };

    // Check if this canvas is the root of the entire tree
    if (node.id === layoutTree.id) {
        layoutTree = newContainer;
    } else {
        const parent = findParentById(layoutTree, canvasId);
        if (!parent) {
            console.error('Could not find parent for canvas:', canvasId);
            return;
        }
        const index = parent.children.findIndex(child => child.id === canvasId);
        if (index === -1) {
            console.error('Could not find canvas in parent children:', canvasId);
            return;
        }
        parent.children[index] = newContainer;
    }

    render();
}

function splitHorizontal(canvasId) {
    splitCanvas(canvasId, 'horizontal');
}

function splitVertical(canvasId) {
    splitCanvas(canvasId, 'vertical');
}

function removeCanvas(canvasId) {
    // Don't allow removing the root canvas if it's the only one
    if (canvasId === layoutTree.id && layoutTree.type === 'canvas') return;

    const parent = findParentById(layoutTree, canvasId);
    if (!parent || !parent.children) {
        console.error('Could not find parent for canvas:', canvasId);
        return;
    }

    const index = parent.children.findIndex(child => child.id === canvasId);
    if (index === -1) {
        console.error('Could not find canvas in parent children:', canvasId);
        return;
    }

    // Remove the selected canvas
    parent.children.splice(index, 1);

    // If parent now has only one child, replace parent with that child
    if (parent.children.length === 1) {
        const remaining = parent.children[0];
        const grandParent = findParentById(layoutTree, parent.id);
        
        if (!grandParent) {
            // Parent is root
            layoutTree = remaining;
        } else {
            const parentIndex = grandParent.children.findIndex(child => child.id === parent.id);
            grandParent.children[parentIndex] = remaining;
        }
    }

    render();
}



function renderNode(node, container) {
    if (node.type === 'canvas') {
        const panel = document.createElement('div');
        panel.className = 'canvas-panel';
        panel.style.flex = '1';
        
        const content = document.createElement('div');
        content.className = 'canvas-content';
        content.style.backgroundColor = node.color;
        content.textContent = `Canvas ${node.id.split('_')[1] || 'Root'}`;
        
        // Add hover controls
        const controls = document.createElement('div');
        controls.className = 'canvas-controls';
        
        // Split horizontal button
        const splitHBtn = document.createElement('button');
        splitHBtn.className = 'control-btn';
        splitHBtn.innerHTML = '→';
        splitHBtn.title = 'Split horizontally';
        splitHBtn.onclick = (e) => {
            e.stopPropagation();
            splitHorizontal(node.id);
        };
        
        // Split vertical button
        const splitVBtn = document.createElement('button');
        splitVBtn.className = 'control-btn';
        splitVBtn.innerHTML = '↓';
        splitVBtn.title = 'Split vertically';
        splitVBtn.onclick = (e) => {
            e.stopPropagation();
            splitVertical(node.id);
        };
        
        controls.appendChild(splitHBtn);
        controls.appendChild(splitVBtn);

        // Remove button (only show if not the only root canvas)
        if (!(node.id === layoutTree.id && layoutTree.type === 'canvas')) {
            const removeBtn = document.createElement('button');
            removeBtn.className = 'control-btn remove';
            removeBtn.innerHTML = '✕';
            removeBtn.title = 'Remove canvas';
            removeBtn.onclick = (e) => {
                e.stopPropagation();
                removeCanvas(node.id);
            };
            controls.appendChild(removeBtn);
        }
        
        panel.appendChild(content);
        panel.appendChild(controls);
        
        container.appendChild(panel);
    } else if (node.type === 'split') {
        const splitContainer = document.createElement('div');
        splitContainer.className = `split-container split-${node.direction}`;
        splitContainer.style.flex = '1';
        
        for (let i = 0; i < node.children.length; i++) {
            const child = node.children[i];
            const childContainer = document.createElement('div');
            childContainer.style.flex = `${node.sizes[i] || 50}`;
            childContainer.style.display = 'flex';
            
            renderNode(child, childContainer);
            splitContainer.appendChild(childContainer);
            
            // Add divider after each child except the last one
            if (i < node.children.length - 1) {
                const divider = document.createElement('div');
                divider.className = `divider ${node.direction}-divider`;
                
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
                    
                    const leftChild = splitContainer.children[i * 2]; // Every other child (skip dividers)
                    const rightChild = splitContainer.children[(i + 1) * 2];
                    
                    const onMouseMove = (e) => {
                        e.preventDefault();
                        requestAnimationFrame(() => {
                            const currentPos = node.direction === 'horizontal' ? e.clientX : e.clientY;
                            const delta = currentPos - startPos;
                            const containerSize = node.direction === 'horizontal' ? 
                                splitContainer.offsetWidth : splitContainer.offsetHeight;
                            const deltaPercent = (delta / containerSize) * 100;
                            
                            const newSize1 = Math.max(5, Math.min(95, startSizes[i] + deltaPercent));
                            const newSize2 = Math.max(5, Math.min(95, startSizes[i + 1] - deltaPercent));
                            
                            // Update flex values directly for smooth resizing
                            leftChild.style.flex = newSize1;
                            rightChild.style.flex = newSize2;
                        });
                    };
                    
                    const onMouseUp = (finalEvent) => {
                        divider.classList.remove('dragging');
                        document.body.classList.remove('dragging', 'dragging-vertical');
                        
                        // Update the data model with final sizes
                        const currentPos = node.direction === 'horizontal' ? 
                            (finalEvent?.clientX || startPos) : (finalEvent?.clientY || startPos);
                        const delta = currentPos - startPos;
                        const containerSize = node.direction === 'horizontal' ? 
                            splitContainer.offsetWidth : splitContainer.offsetHeight;
                        const deltaPercent = (delta / containerSize) * 100;
                        
                        node.sizes[i] = Math.max(5, Math.min(95, startSizes[i] + deltaPercent));
                        node.sizes[i + 1] = Math.max(5, Math.min(95, startSizes[i + 1] - deltaPercent));
                        
                        document.removeEventListener('mousemove', onMouseMove);
                        document.removeEventListener('mouseup', onMouseUp);
                    };
                    
                    document.addEventListener('mousemove', onMouseMove);
                    document.addEventListener('mouseup', onMouseUp);
                };
                
                splitContainer.appendChild(divider);
            }
        }
        
        container.appendChild(splitContainer);
    }
}

function render() {
    const canvasArea = document.getElementById('canvasArea');
    canvasArea.innerHTML = '';
    renderNode(layoutTree, canvasArea);
}

// Initial render
render();

// Auto-hide UI after inactivity
let hideUITimer = null;
const HIDE_DELAY = 2500; // 2.5 seconds

function showUI() {
    document.body.classList.remove('hide-ui');
    clearTimeout(hideUITimer);
    hideUITimer = setTimeout(() => {
        document.body.classList.add('hide-ui');
    }, HIDE_DELAY);
}

function hideUI() {
    clearTimeout(hideUITimer);
    document.body.classList.add('hide-ui');
}

// Track mouse movement
document.addEventListener('mousemove', showUI);
document.addEventListener('mousedown', showUI);
document.addEventListener('keydown', showUI);

// Start the timer immediately
showUI();