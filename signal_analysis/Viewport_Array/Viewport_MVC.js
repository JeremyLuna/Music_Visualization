export class Viewport_View {
    constructor(parent_div, is_removable){
        this.parent_div = parent_div;
        this.is_removable = is_removable;
        
        this.canvas_space = document.createElement("div");
        this.canvas_space.classList.add("canvas_space");
        this.style.flex = '1';
        this.style.position = 'relative';
        this.style.backgroundColor = this._getRandomColor();
        
        parent_div.appendChild(this.canvas_space);

        this._make_controls(this.is_removable);
    }

    _make_controls(is_removable){
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
    }

    _getRandomColor() {
        const colors = [
            '#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4', '#FFEAA7',
            '#DDA0DD', '#98D8C8', '#F7DC6F', '#BB8FCE', '#85C1E9',
            '#F8C471', '#82E0AA', '#F1948A', '#85C1E9', '#D2B4DE'
        ];
        return colors[Math.floor(Math.random() * colors.length)];
    }
}