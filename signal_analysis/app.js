import { ControlPanel } from './controlPanel/controlPanel.js';
import { DynamicCanvas } from './dynamic_canvas/dynamicCanvas.js';
import { AudioSamplePuller, MusicPlayer } from './audioPlayer/audioPlayer.js';
import { STFTVisualizer } from './STFTVisualizer/STFTVisualizer.js';

// TODO:
// make favicon with https://favicon.io/favicon-converter/
// don't rebuild canvases
// handle canvas registration
// dont think we need canvas-content class, it only holds a canvas

// make array for input registration
const chunk_audio_inputs = [];
const stream_audio_input = []; // has a pullAllSamples() function

// make array of availible plugins
const plugins = [];

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
  './audioplayer/SampleProcessor.js'
);
// register audioplayer as streamable input
stream_audio_input.push(audioPlayer);

// make STFT plugin
// make panel elements
const [STFTDetails, STFTContent] = control_panel.createDetails("STFT");
// put them on the panel
control_panel.tabContent.appendChild(STFTDetails);
// put the file player in the panel
const stftVisualizer = new STFTVisualizer(
  audioPlayer,
  dynamic_canvas.getCanvas(dynamic_canvas.getAvailableCanvases()[0].id).element,
  STFTDetails
);
// register audioplayer as streamable input
stream_audio_input.push(audioPlayer);