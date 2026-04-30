import { ControlPanel } from './controlPanel/controlPanel.js';
import { DynamicCanvas } from './dynamic_canvas/dynamicCanvas.js';
import { AudioSamplePuller, MusicPlayer } from './audioPlayer/audioPlayer.js';
import { STFTVisualizer } from './STFTVisualizer/STFTVisualizer.js';
import { WaveformVisualizer } from './WaveformVisualizer/WaveformVisualizer.js';

// Apply basic global styling without a stylesheet
document.body.style.margin = '0';
document.body.style.padding = '0';
document.body.style.boxSizing = 'border-box';
document.body.style.fontFamily = "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif";
document.body.style.height = '100vh';
document.body.style.overflow = 'hidden';
document.body.style.background = '#1a1a1a';
document.body.style.color = 'white';

// TODO:
// make favicon with https://favicon.io/favicon-converter/
// don't rebuild canvases
// handle canvas registration
// dont think we need canvas-content class, it only holds a canvas

// make array for input registration
const chunk_audio_inputs = [];
const stream_audio_input = []; // has a pullAllSamples() function

// make array of available plugins
const plugins = [];

// Visualizer registry
const visualizerClasses = {
    'STFT': STFTVisualizer,
    'Oscilloscope': WaveformVisualizer
};

// Canvas panels map: canvasId -> { details, content, select, settingsDiv, visualizer }
const canvasPanels = new Map();

// Function to get all canvas nodes from the layout tree
function getAllCanvasNodes(tree) {
    const canvases = [];
    function traverse(node) {
        if (node.type === 'canvas') {
            canvases.push(node);
        } else if (node.type === 'split') {
            node.children.forEach(traverse);
        }
    }
    traverse(tree);
    return canvases;
}

// Function to update canvas panels based on current canvases
function updateCanvasPanels() {
    const currentCanvases = getAllCanvasNodes(dynamic_canvas.layoutTree);

    // Remove panels for removed canvases
    for (const [id, panel] of canvasPanels) {
        if (!currentCanvases.find(c => c.id === id)) {
            control_panel.tabContent.removeChild(panel.details);
            if (panel.visualizer) panel.visualizer._stop();
            canvasPanels.delete(id);
        }
    }

    // Add panels for new canvases
    for (const canvasNode of currentCanvases) {
        if (!canvasPanels.has(canvasNode.id)) {
            const [details, content] = control_panel.createDetails(`Canvas ${canvasNode.id}`);
            control_panel.tabContent.appendChild(details);

            const select = document.createElement('select');
            const noneOption = document.createElement('option');
            noneOption.value = 'None';
            noneOption.textContent = 'None';
            select.appendChild(noneOption);
            for (const name in visualizerClasses) {
                const option = document.createElement('option');
                option.value = name;
                option.textContent = name;
                select.appendChild(option);
            }
            select.value = 'None';

            const settingsDiv = document.createElement('div');
            content.appendChild(select);
            content.appendChild(settingsDiv);

            canvasPanels.set(canvasNode.id, { details, content, select, settingsDiv, visualizer: null });

            select.addEventListener('change', () => {
                const selected = select.value;
                const panel = canvasPanels.get(canvasNode.id);
                if (panel.visualizer) {
                    panel.visualizer._stop();
                    panel.visualizer = null;
                }
                if (selected !== 'None') {
                    const VisClass = visualizerClasses[selected];
                    panel.visualizer = new VisClass(audioPlayer, canvasNode.canvasEl, panel.settingsDiv);
                    panel.visualizer._start();
                }
                // Settings div is cleared and repopulated by the visualizer constructor
            });
        }
    }
}

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

// Listen to canvas changes and update panels
dynamic_canvas.addEventListener('canvasChanged', updateCanvasPanels);

// Initial update of canvas panels
updateCanvasPanels();