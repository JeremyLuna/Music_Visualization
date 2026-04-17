import { ControlPanel } from './controlPanel/controlPanel.js';
import { DynamicCanvas } from './dynamic_canvas/dynamicCanvas.js';
import { AudioSamplePuller, MusicPlayer } from './audioPlayer/audioPlayer.js';
import { STFTVisualizer } from './STFTVisualizer/STFTVisualizer.js';
import { EventEmitter } from './EventEmitter.js';
import { registryInstance as visualizerRegistry } from './VisualizerRegistry.js';
import { STFTVisualizerAdapter } from './visualizers/STFTVisualizerAdapter.js';

// Create shared event emitter for component communication
const eventEmitter = new EventEmitter();

// Register available visualizers
visualizerRegistry.register('stft', STFTVisualizerAdapter);

// TODO:
// make favicon with https://favicon.io/favicon-converter/
// don't rebuild canvases
// handle canvas registration
// dont think we need canvas-content class, it only holds a canvas

// import control panel
const control_panel = new ControlPanel();

// import dynamic canvas
const dynamic_canvas = new DynamicCanvas();

// make file input
// make panel elements
const [audioDetails, audioContent] = control_panel.createDetails("Audio File Input");
// put them on the panel
control_panel.tabContent.appendChild(audioDetails);
// put the file player in the panel
const audioPlayer = new MusicPlayer(
  audioContent,
  './audioplayer/SampleProcessor.js',
  eventEmitter
);

// make STFT plugin via registry
// make panel elements
const [STFTDetails, STFTContent] = control_panel.createDetails("STFT");
// put them on the panel
control_panel.tabContent.appendChild(STFTDetails);
// create visualizer via registry
const stftVisualizer = visualizerRegistry.create(
  'stft',
  dynamic_canvas.layoutTree.canvasEl,
  STFTContent,
  eventEmitter,
  audioPlayer
);

stftVisualizer.start();