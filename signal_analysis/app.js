import { ControlPanel } from './controlPanel/controlPanel.js';
import { DynamicCanvas } from './dynamic_canvas/dynamicCanvas.js';
import { AudioSamplePuller, MusicPlayer } from './audioPlayer/audioPlayer.js';

// TODO:
// make favicon with https://favicon.io/favicon-converter/
// register file input

// make array for input registration
const chunk_audio_inputs = [];
const stream_audio_input = [];

// make array of inputs
const plugins = [];

// import control panel
const control_panel = new ControlPanel();

// import dynamic canvas
const dynamic_canvas = new DynamicCanvas();

// make panel elements
const [audioDetails, audioContent] = control_panel.createDetails("Audio File Input");
// put them on the panel
control_panel.tabContent.appendChild(audioDetails);
// put the file player in the panel
const audioPlayer = new MusicPlayer(
  audioContent,
  './audioplayer/SampleProcessor.js'
);

