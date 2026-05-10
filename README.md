# Music Visualization

https://jeremyluna.github.io/Music_Visualization/

A single-page music visualization app. Upload an audio file, play it in the browser, split the canvas layout, and choose or customize visualizers for each canvas.

The current implementation lives in `clojure/` and is built with ClojureScript, Shadow-cljs, Reagent, the Web Audio API, and canvas rendering. The older vanilla JavaScript version is archived under `archived/vanilla_js/`.

## Quickstart

### Prerequisites

- Node.js 20.18.1
- npm 10.8.2
- Java 11+

The Node/npm toolchain is pinned in `clojure/.nvmrc`, `clojure/.node-version`, `clojure/.npmrc`, and `clojure/package.json`. If you use `nvm`, run:

```bash
cd clojure
nvm install
nvm use
```

Check versions with:

```bash
node --version
npm --version
java -version
```

### Install

```bash
cd clojure
npm ci
```

Use `npm ci` for normal setup and builds. It installs exactly from `package-lock.json`, so development and release builds use the same dependency tree on each machine.

### Run Locally

Start the Shadow-cljs watcher:

```bash
cd clojure
nvm use
npm run dev
```

In another terminal, serve `public/`:

```bash
cd clojure
npm run serve:dev
```

Open http://localhost:8000.

Shadow-cljs also starts its own development server at http://127.0.0.1:3000, but the current npm serve script uses the static Python server on port 8000.

## Features

- Reagent single-page app mounted from `app.init`
- Central `app.state/app-state` atom with dispatch-based updates
- Browser audio playback through the Web Audio API
- Audio file loading, play, pause, stop, seek, duration, current time, and volume controls
- AudioWorklet-backed sample capture through `public/sample_processor.js`
- Dynamic canvas layout tree with horizontal and vertical splits
- Canvas registration and resize-aware canvas backing dimensions
- Per-canvas visualizer selection and settings
- Runtime visualizer engine driven by `requestAnimationFrame`
- `IVisualizer` protocol with waveform and STFT visualizer implementations
- Visualizer registry for adding additional visualizer types

## Project Structure

```text
clojure/
|-- deps.edn                    # Clojure/ClojureScript dependencies and aliases
|-- .nvmrc                      # Preferred Node.js version for nvm
|-- .node-version               # Preferred Node.js version for asdf/mise/fnm
|-- .npmrc                      # Enforces Node/npm engine checks during install
|-- package.json                # npm scripts and JS dependencies
|-- package-lock.json           # Locked npm dependency tree for reproducible installs
|-- shadow-cljs.edn             # Shadow-cljs browser build configuration
|-- public/
|   |-- index.html              # Browser entry point
|   |-- sample_processor.js     # AudioWorklet processor for sample capture
|   `-- js/                     # Shadow-cljs output (generated)
`-- src/
    |-- app/
    |   |-- core.cljs           # Root Reagent component
    |   |-- init.cljs           # App startup, audio init, and render loop startup
    |   `-- state.cljs          # Central app-state atom and dispatch actions
    |-- audio/
    |   |-- interop.cljs        # Web Audio, Canvas 2D, FileReader, and FFT wrappers
    |   |-- player.cljs         # AudioPlayer record and playback/file APIs
    |   `-- sample_puller.cljs  # AudioWorklet sample buffering
    |-- canvas/
    |   |-- model.cljs          # Pure layout tree operations
    |   |-- view.cljs           # Reagent canvas/split layout components
    |   `-- controller.cljs     # Convenience dispatch wrappers
    |-- ui/
    |   `-- control_panel.cljs  # File, playback, volume, and visualizer settings UI
    `-- visualizers/
        |-- protocol.cljs       # IVisualizer protocol
        |-- engine.cljs         # Render loop and visualizer instance lifecycle
        |-- registry.cljs       # Visualizer factory and metadata registry
        |-- stft.cljs           # FFT/STFT-style frequency visualizer
        `-- waveform.cljs       # Time-domain waveform visualizer
```

## Script Reference

- `npm run dev`: Watch and recompile the browser build.
- `npm run release`: Build optimized production output into `public/js`.
- `npm run serve:dev`: Serve `public/` on port 8000.
- `npm run serve:release`: Build release output, then serve it.
- `npm run serve`: Alias for `serve:dev`.

## Development

### Browser REPL

Start the watcher first:

```bash
cd clojure
npm run dev
```

Then connect to the app REPL:

```bash
npx shadow-cljs cljs-repl app
```

Useful REPL snippets:

```clojure
(require '[app.state :as state])
@state/app-state
(state/dispatch :toggle-control-panel)
```

### Production Build

```bash
cd clojure
nvm use
npm run release
```

The compiled output is written to `public/js`. To serve the release bundle locally:

```bash
npm run serve:release
```

## Troubleshooting

### App Does Not Load Or Shows "Loading App..."

1. Check the browser console for errors.
2. Confirm the dev server is running:

   ```bash
   curl http://localhost:8000
   ```

3. Confirm `clojure/public/js/main.js` exists and has content.

### Main Bundle Does Not Load

The dev server is not running, or it is serving from the wrong directory. Run this from `clojure/`:

```bash
npm run serve:dev
```

### REPL Connection Fails

Kill any existing Shadow-cljs watch processes, then start fresh:

```bash
npx shadow-cljs watch app
```

## Architecture

### State

The app keeps browser-facing mutable state in one Reagent atom:

- `:audio`: audio context, player, sample puller, playback state, duration, current time, and volume
- `:layout`: the immutable canvas layout tree and next canvas ID
- `:ui`: control panel state
- `:visualizers`: reserved for visualizer UI state
- `:canvas-elements`: mounted canvas DOM nodes keyed by canvas ID
- `:samples`: sample storage hooks for future expansion

State changes flow through `app.state/dispatch`, while pure layout behavior lives in `canvas.model`.

### Audio

`audio.player/create-audio-player` builds the browser audio graph:

```text
HTMLAudioElement -> MediaElementAudioSourceNode -> GainNode -> speakers
                                                   `--------> AudioWorklet sample capture
```

Loaded audio files are played through the hidden audio element. The worklet posts sample frames to `audio.sample-puller`, which keeps per-channel circular buffers for visualizers.

### Canvas Layout

Canvas layout is represented as a recursive tree of `:canvas` leaves and `:split` containers. Splitting a canvas replaces the selected leaf with a split node containing the original canvas and a new waveform canvas. Removing a canvas promotes its sibling so the layout remains valid.

### Visualizers

Visualizers implement `visualizers.protocol/IVisualizer`:

- `render`: draw the current frame to a canvas
- `update-settings`: apply per-canvas settings
- `get-settings`: expose current settings

The render loop in `visualizers.engine` walks the active layout, creates or reuses visualizer instances from private runtime state, syncs settings only when they change, and renders each mounted canvas on every animation frame.

## Adding a Visualizer

1. Add a new namespace under `clojure/src/visualizers/`.
2. Implement `visualizers.protocol/IVisualizer`.
3. Define a `theme-settings` function if the visualizer should derive default colors from the active theme.
4. Add the visualizer metadata, factory, and theme settings function to `visualizers.registry/visualizer-registry`.
5. Add settings controls in `ui.control-panel/visualizer-settings` if the visualizer has configurable options.

## Todo List

- Add swapping behavior for dividers.
- Analytic signal:
    - is either starting or ending at corners?
- Add adjustable range for spectrogram.
- Add constant Q transform.
    - Route to multi-oscilloscope.
    - Route to multi-analytic signal.
- Add levels bars / plot.
- Add spiral levels visualization.
- Deploy to pages.
- Add a "load demo" button with a copyright-free song.
- Add live Audio feature extraction
- Add spotify input
- add spotify audio features
- add mic input
- Add audio feature non-technical visualizers
    - add night drive visualizer
    - add band playing visualizer, where you can pick character models
    - add rain and lightning visualizer
    - add dj dance floor visualizer
        - can reuse character models for dance floor
        - can have screen behind dj that nests a visualizer

## AI Recommended Todo:

Make canvas rendering retina-aware. view.cljs (line 39) sets canvas width/height to CSS pixels. On high-DPI displays, visuals will look softer than they need to. I’d multiply backing dimensions by devicePixelRatio and scale the 2D context.

## Resources

- https://www.mathworks.com/help/wavelet/ref/vmd.html
- https://arxiv.org/pdf/2501.09174
- https://ww3.math.ucla.edu/camreport/cam13-22.pdf
- https://vamsivk1995.medium.com/introduction-to-variational-mode-decomposition-vmd-d7100210a56a
- https://vamsivk1995.medium.com/variational-mode-decomposition-part-2-the-maths-4a81a8e05076
- https://www.faberacoustical.com/apps/signalscope/signalscope_x/subs/pro.html
- https://pitchreader.com/
- https://minimeters.app/
- https://oxfordwaveresearch.com/products/spectrumviewapp/
- https://vimeo.com/196216785
- https://delu.medium.com/a-perceptually-meaningful-audio-visualizer-ee72051781bc
- https://github.com/MTG/sms-tools-materials
- https://github.com/ohollo/chord-extractor
- https://vizzy.io/
- https://app.onemaker.io/music-visualizer
- https://www.sonicvisualiser.org/
- https://www.videoproc.com/resource/spotify-visualizer.htm
- https://hydra.ojack.xyz/?sketch_id=ritchse_4
- https://butterchurnviz.com/
