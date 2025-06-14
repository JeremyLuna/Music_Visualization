import { DynamicCanvasModel } from './DynamicCanvasModel.js';
import { DynamicCanvasView } from './DynamicCanvasView.js';

export class DynamicCanvas {
    constructor(options = {}) {
        this.model = new DynamicCanvasModel();
        this.view = new DynamicCanvasView(this.model, this, options);
    }

    splitCanvas(id, direction) {
        this.model.splitCanvas(id, direction);
        this.view.render();
    }

    removeCanvas(id) {
        this.model.removeCanvas(id);
        this.view.render();
    }

    get layoutTree() {
        return this.model.layoutTree;
    }
}
