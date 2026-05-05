export class DynamicCanvasModel {
    constructor() {
        this.layoutTree = {
            id: 'root',
            type: 'canvas',
            color: this.getRandomColor(),
            canvasEl: null
        };
    }

    splitCanvas(canvasId, direction) {
        const node = this.findNodeById(this.layoutTree, canvasId);
        if (!node || node.type !== 'canvas') return;

        const newLeaf = {
            id: this.generateId(),
            type: 'canvas',
            color: this.getRandomColor(),
            canvasEl: null
        };

        const newContainer = {
            id: this.generateId(),
            type: 'split',
            direction,
            sizes: [50, 50],
            children: [
                { ...node, id: node.id },
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
    }

    removeCanvas(canvasId) {
        if (canvasId === this.layoutTree.id && this.layoutTree.type === 'canvas') return;

        const parent = this.findParentById(this.layoutTree, canvasId);
        if (!parent) return;
        const idx = parent.children.findIndex(c => c.id === canvasId);
        if (idx === -1) return;

        const sibling = parent.children[1 - idx];
        if (parent.id === this.layoutTree.id) {
            this.layoutTree = sibling;
        } else {
            const grandParent = this.findParentById(this.layoutTree, parent.id);
            if (!grandParent) return;
            const parentIdx = grandParent.children.findIndex(c => c.id === parent.id);
            grandParent.children[parentIdx] = sibling;
        }
    }

    findNodeById(tree, id) {
        if (tree.id === id) return tree;
        if (tree.type === 'split') {
            for (const child of tree.children) {
                const found = this.findNodeById(child, id);
                if (found) return found;
            }
        }
        return null;
    }

    findParentById(tree, id, parent = null) {
        if (tree.id === id) return parent;
        if (tree.type === 'split') {
            for (const child of tree.children) {
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
}
