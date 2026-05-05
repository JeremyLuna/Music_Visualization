import { DynamicCanvasModel } from './DynamicCanvasModel.js';
import { DynamicCanvasView } from './DynamicCanvasView.js';

export class DynamicCanvas extends EventTarget {
    constructor(options = {}) {
        super();
        this.model = new DynamicCanvasModel();
        this.view = new DynamicCanvasView(this.model, this, options);
        // Dispatch initial canvas event
        this.dispatchEvent(new CustomEvent('canvasChanged', { detail: { action: 'init' } }));
    }

    splitCanvas(id, direction) {
        this.model.splitCanvas(id, direction);
        this.view.render();
        this.dispatchEvent(new CustomEvent('canvasChanged', { detail: { action: 'split', id, direction } }));
    }

    removeCanvas(id) {
        this.model.removeCanvas(id);
        this.view.render();
        this.dispatchEvent(new CustomEvent('canvasChanged', { detail: { action: 'remove', id } }));
    }

    get layoutTree() {
        return this.model.layoutTree;
    }
}
