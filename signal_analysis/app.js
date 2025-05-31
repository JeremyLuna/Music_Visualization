import { ControlPanel } from './control_panel/control_panel.js';
import { DynamicCanvas } from './dynamic_canvas/dynamic_canvas.js';

// make favicon with https://favicon.io/favicon-converter/

// make array for input registration
const chunk_audio_inputs = [];
const stream_audio_input = [];

// make array of inputs
const plugins = [];

// import control panel
const control_panel = new ControlPanel();

// import dynamic canvas
const dynamic_canvas = new DynamicCanvas();