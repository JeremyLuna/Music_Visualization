# Music Visualization - ClojureScript Implementation

A ClojureScript port of the Music Visualization app using **Shadow-cljs** and **Reagent**. The implementation mirrors the vanilla JS version's core ideas while leaning into ClojureScript's strengths: immutable layout data, centralized state transitions, and protocol-based visualizers.

## Current Features

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
├── deps.edn                    # Clojure/ClojureScript dependencies and aliases
├── package.json                # npm scripts and JS dependencies
├── shadow-cljs.edn             # Shadow-cljs browser build configuration
├── public/
│   ├── index.html              # Browser entry point
│   ├── sample_processor.js     # AudioWorklet processor for sample capture
│   └── js/                     # Shadow-cljs output (generated)
└── src/
    ├── app/
    │   ├── core.cljs           # Root Reagent component
    │   ├── init.cljs           # App startup, audio init, and render loop startup
    │   └── state.cljs          # Central app-state atom and dispatch actions
    ├── audio/
    │   ├── interop.cljs        # Web Audio, Canvas 2D, FileReader, and FFT wrappers
    │   ├── player.cljs         # AudioPlayer record and playback/file APIs
    │   └── sample_puller.cljs  # AudioWorklet sample buffering
    ├── canvas/
    │   ├── model.cljs          # Pure layout tree operations
    │   ├── view.cljs           # Reagent canvas/split layout components
    │   └── controller.cljs     # Convenience dispatch wrappers
    ├── ui/
    │   └── control_panel.cljs  # File, playback, volume, and visualizer settings UI
    └── visualizers/
        ├── protocol.cljs       # IVisualizer protocol
        ├── engine.cljs         # Render loop and visualizer instance lifecycle
        ├── registry.cljs       # Visualizer factory and metadata registry
        ├── stft.cljs           # FFT/STFT-style frequency visualizer
        └── waveform.cljs       # Time-domain waveform visualizer
```

## Setup

### Prerequisites

- Node.js 14+
- Java 11+

### Install

```bash
cd clojure
npm install
```

## Running the App

Run the Shadow-cljs watcher:

```bash
cd clojure
npm run dev
```

In another terminal, serve `public/`:

```bash
cd clojure
npm run serve:dev
```

Open http://localhost:8000.

Shadow-cljs also starts its own development server at http://127.0.0.1:3000, but the current npm serve script uses the static Python server on port 8000.

## Script Reference

- `npm run dev`: Watch and recompile the browser build.
- `npm run release`: Build optimized production output into `public/js`.
- `npm run serve:dev`: Serve `public/` on port 8000.
- `npm run serve:release`: Build release output, then serve it.
- `npm run serve`: Alias for `serve:dev`.

## Development Workflow

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
npm run release
```

The compiled output is written to `public/js`.

## Architecture

### State

The app keeps browser-facing mutable state in one Reagent atom:

- `:audio`: audio context, player, sample puller, playback state, duration, current time, and volume
- `:layout`: the immutable canvas layout tree and next canvas ID
- `:ui`: control panel state
- `:visualizers`: active visualizer instances and settings
- `:canvas-elements`: mounted canvas DOM nodes keyed by canvas ID
- `:samples`: sample storage hooks for future expansion

State changes flow through `app.state/dispatch`, while pure layout behavior lives in `canvas.model`.

### Audio

`audio.player/create-audio-player` builds the browser audio graph:

```text
HTMLAudioElement -> MediaElementAudioSourceNode -> GainNode -> speakers
                                                   └---------> AudioWorklet sample capture
```

Loaded audio files are played through the hidden audio element. The worklet posts sample frames to `audio.sample-puller`, which keeps per-channel circular buffers for visualizers.

### Canvas Layout

Canvas layout is represented as a recursive tree of `:canvas` leaves and `:split` containers. Splitting a canvas replaces the selected leaf with a split node containing the original canvas and a new waveform canvas. Removing a canvas promotes its sibling so the layout remains valid.

### Visualizers

Visualizers implement `visualizers.protocol/IVisualizer`:

- `render`: draw the current frame to a canvas
- `update-settings`: apply per-canvas settings
- `get-settings`: expose current settings

The render loop in `visualizers.engine` walks the active layout, creates or reuses visualizer instances, syncs settings, and renders each mounted canvas on every animation frame.

## Todo List

- scroll bar and sliders should be squarable
- Add swapping behavior for dividers
- squareness should be part of theme
- Work on themes
- add analytic signal
- adjustable range for spectrogram
- and constant q transform
    - route to multi-oscilloscope
    - route to multi-analytic signal
- add levels bars / plot
- add spiral levels visualization
- Production deployment is not wired in this directory.
- add "load demo" button with copyright free song?

## Adding a Visualizer

1. Add a new namespace under `src/visualizers/`.
2. Implement `visualizers.protocol/IVisualizer`.
3. Add the visualizer metadata and factory to `visualizers.registry/visualizer-registry`.
4. Add settings controls in `ui.control-panel/visualizer-settings` if the visualizer has configurable options.

## Related Docs

- `QUICKSTART.md`: shorter runbook and troubleshooting notes
- Root `README.md`: overall repo notes and research links
