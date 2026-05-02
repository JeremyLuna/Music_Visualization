# Music Visualization - ClojureScript Implementation

A ClojureScript port of the Music Visualization app using **Shadow-cljs** and **Reagent** for a reactive, immutable-state-driven approach to audio visualization.

## Project Structure

```
clojure/
├── deps.edn                 # Clojure/ClojureScript dependencies
├── shadow-cljs.edn         # Shadow-cljs build configuration
├── public/                 # Static assets (compiled JS, HTML, etc)
│   ├── index.html         # Entry point
│   └── js/                # Shadow-cljs output (generated)
└── src/
    ├── app/               # Root component and state management
    │   ├── core.cljs     # Root Reagent component
    │   └── state.cljs    # Central app-state atom + dispatch
    ├── audio/             # Audio pipeline (recording, playback)
    │   ├── interop.cljs  # Web Audio API + Canvas interop
    │   ├── player.cljs   # Audio playback (TODO)
    │   └── sample_puller.cljs  # Sample capture (TODO)
    ├── canvas/            # Dynamic canvas layout management
    │   ├── model.cljs    # Layout tree operations (TODO)
    │   ├── view.cljs     # Reagent canvas components (TODO)
    │   └── controller.cljs # State transitions (TODO)
    ├── visualizers/       # Audio visualization implementations
    │   ├── protocol.cljs # IVisualizer protocol (TODO)
    │   ├── stft.cljs     # STFT spectrogram (TODO)
    │   ├── waveform.cljs # Time-domain waveform (TODO)
    │   └── registry.cljs # Visualizer factory (TODO)
    └── ui/                # UI components
        ├── control_panel.cljs # Settings panel (TODO)
        └── settings.cljs      # Settings schema (TODO)
```

## Setup

### Prerequisites

- Node.js 14+ (for shadow-cljs and npm packages)
- Java 11+ (for Clojure/ClojureScript compilation)

### Installation

1. Install dependencies:
   ```bash
   npm install
   cd clojure
   clj -X shadow.cljs.cli/watch :builds '[{:id "app"}]'
   ```

   OR using the `deps.edn` directly:
   ```bash
   cd clojure
   clj -Ashadow watch app
   ```

2. In another terminal, start the dev server:
   ```bash
   cd clojure/public
   python3 -m http.server 8000
   ```

   (Or use `live-server`, `http-server`, etc.)

3. Open http://localhost:8000 in your browser

## Development Workflow

### Watch Mode

Shadow-cljs automatically recompiles on file changes:

```bash
cd clojure
clj -Ashadow watch app
```

The browser will hot-reload (via Shadow-cljs REPL).

### REPL

To connect to the Shadow-cljs REPL:

```bash
cd clojure
clj -Ashadow cljs-repl app
```

Then in the REPL:
```clojure
(require '[app.state :as state])
@state/app-state  ;; View current state
```

### Build for Production

```bash
cd clojure
clj -Ashadow release app
```

Output: `public/js/main.js` (optimized & minified)

## Implementation Status

### Phase 1: Project Setup ✅
- [x] Shadow-cljs & deps.edn configuration
- [x] Project directory structure
- [x] JS interop layer (Web Audio API, Canvas 2D)
- [x] Central app-state atom definition
- [x] Root component scaffold

### Phase 2: Audio Pipeline ⏳
- [ ] AudioSamplePuller implementation
- [ ] AudioWorklet processor
- [ ] MusicPlayer wrapper functions

### Phase 3: Canvas & Layout ⏳
- [ ] DynamicCanvasModel (layout tree operations)
- [ ] DynamicCanvasView (Reagent components)
- [ ] DynamicCanvasController (state transitions)

### Phase 4: Visualizers ⏳
- [ ] IVisualizer protocol
- [ ] STFT Visualizer (uses JS FFT library)
- [ ] Waveform Visualizer
- [ ] Visualizer registry

### Phase 5: Control Panel & UI ⏳
- [ ] ControlPanel component
- [ ] Settings schema and form components

### Phase 6: App Integration ⏳
- [ ] Wire all components together
- [ ] Initialization and warmup logic

### Phase 7: Build & Deployment ⏳
- [ ] Production build optimization
- [ ] GitHub Pages deployment setup

## Key Technologies

- **Shadow-cljs**: ClojureScript build tool and REPL
- **Reagent**: React wrapper for ClojureScript (reactive components)
- **Web Audio API**: Audio playback and sample capture (via JS interop)
- **Canvas 2D**: Real-time visualization drawing
- **FFT Library**: High-performance FFT (via JS interop, e.g., fftjs)

## Architecture Highlights

### State Management

Single centralized atom (`app.state/app-state`) contains all mutable state:
- Audio context and playback state
- Canvas layout tree (immutable nested structure)
- UI settings
- Visualizer instances
- Sample buffers

Pure functions in `app.state/dispatch` handle state updates.

### Components

Reagent components subscribe to portions of `app-state` and re-render reactively on changes.

### Visualizers

Protocols define the `IVisualizer` interface, enabling polymorphic visualization strategies (STFT, Waveform, future visualizers).

### Interop

Web Audio API and Canvas 2D are accessed through carefully designed wrapper functions in `audio.interop`, providing a Clojure-friendly API while hiding JS interop details.

## Next Steps

1. Implement Audio Pipeline (Phase 2)
2. Implement Canvas & Layout Management (Phase 3)
3. Implement Visualizers (Phase 4)
4. Build UI components (Phase 5)
5. Integrate and test

See `/memories/session/plan.md` for the full development plan.
