# Quick Start Guide - ClojureScript Music Visualization

Get the app running in 5 minutes!

## Prerequisites

Make sure you have installed:
- **Node.js 14+** (includes npm)
- **Java 11+** (for ClojureScript compilation)

Check versions:
```bash
node --version
java -version
```

## Setup (First Time Only)

1. **Navigate to the clojure directory:**
   ```bash
   cd clojure
   ```

2. **Install npm dependencies:**
   ```bash
   npm install
   ```

   This installs:
   - shadow-cljs (build tool)
   - Reagent (React wrapper)
   - fftjs (FFT library)

## Running the App

### Option 1: Single Command (Recommended)

```bash
cd clojure
npm run dev
```

Then in a new terminal:
```bash
npm run serve
```

Open http://localhost:8000 in your browser.

**Note:** The `npm run dev` command watches for file changes and auto-recompiles. Your browser will hot-reload.

### Option 2: Step by Step

**Terminal 1 - Build/Watch:**
```bash
cd clojure
npx shadow-cljs watch app
```

You should see output like:
```
shadow-cljs - HTTP server available at http://localhost:3000
shadow-cljs - server version: 2.27.6 compiled at: 2024-06-01
shadow-cljs - watching build :app
...
```

**Terminal 2 - Dev Server:**
```bash
cd clojure/public
python3 -m http.server 8000
```

Then open http://localhost:8000

## Development Tips

### Hot Reload
- Edit a `.cljs` file and save
- Shadow-cljs automatically recompiles
- Browser page reloads automatically
- Your app state persists (if using Reagent atoms carefully)

### Browser Console
- Open browser DevTools (F12 or Cmd+Option+I)
- Look for log messages like "✓ Audio player initialized"
- Check for errors if app doesn't load

### REPL (ClojureScript Interactive Shell)

In a third terminal:
```bash
cd clojure
npx shadow-cljs cljs-repl app
```

Then in the REPL, you can run:
```clojure
(require '[app.state :as state])
@state/app-state  ;; View current state
(state/dispatch :toggle-control-panel)  ;; Test dispatch
```

## Building for Production

```bash
cd clojure
npm run release
```

Output: `public/js/app.js` (optimized & minified, ~50-100KB)

## Deployment to GitHub Pages

### Automatic (CI/CD)
The `.github/workflows/deploy.yml` file automatically:
1. Builds the app when you push to `main`
2. Deploys to GitHub Pages under `/music-viz`

Just push to GitHub and check "Actions" tab for build status.

### Manual Deployment

1. Build the app:
   ```bash
   cd clojure
   npm run release
   ```

2. Copy `public/` to your GitHub Pages hosting location

3. Update GitHub repo settings:
   - Go to Settings → Pages
   - Set source to `gh-pages` branch

## Troubleshooting

### App doesn't load or shows "Loading app..."

1. **Check browser console (F12)** for errors
   - Look for "FFT library not loaded" → fftjs import failed
   - Look for "App container (#app) not found" → index.html issue

2. **Check server is running:**
   ```bash
   curl http://localhost:8000
   ```

3. **Verify files were compiled:**
   - Check `clojure/public/js/app.js` exists and has content

### "Cannot GET /js/app.js"

- The dev server isn't running, or
- You're serving from wrong directory
- Run `npm run serve` from `clojure/` directory

### REPL connection fails

- Kill any existing `watch` processes: `ps aux | grep shadow`
- Start fresh watch in new terminal: `npx shadow-cljs watch app`

## Project Structure

```
clojure/
├── public/
│   ├── index.html           # Entry point
│   ├── js/app.js            # Compiled output (generated)
│   └── sample_processor.js  # AudioWorklet code
├── src/
│   ├── app/
│   │   ├── core.cljs        # Root React component
│   │   ├── init.cljs        # Initialization logic
│   │   └── state.cljs       # Central app state
│   ├── audio/
│   │   ├── interop.cljs     # Web Audio API wrappers
│   │   ├── player.cljs      # Playback functions
│   │   └── sample_puller.cljs  # Sample capture
│   ├── canvas/
│   │   ├── model.cljs       # Layout tree logic
│   │   ├── view.cljs        # Reagent components
│   │   └── controller.cljs  # Event handlers
│   ├── visualizers/
│   │   ├── protocol.cljs    # IVisualizer protocol
│   │   ├── stft.cljs        # Spectrogram visualizer
│   │   ├── waveform.cljs    # Waveform visualizer
│   │   └── registry.cljs    # Visualizer factory
│   └── ui/
│       └── control_panel.cljs  # Settings UI
├── shadow-cljs.edn          # Build config
├── deps.edn                 # Clojure deps
├── package.json             # npm config
└── README.md                # Full docs
```

## What's Next?

1. **Upload an audio file** - Use the file input in the control panel
2. **Play music** - Hit play and see the waveform visualizer
3. **Split canvases** - Click "Split H" or "Split V" to add more visualizers
4. **Change visualizers** - Select different visualizers for each canvas

## Common Tasks

### Adding a new module
1. Create file in `src/category/module.cljs`
2. Shadow-cljs watches and recompiles automatically

### Debugging state changes
Uncomment the debug watch in `app/init.cljs` to see all state updates in console.

### Testing a component in isolation
1. Open REPL: `npx shadow-cljs cljs-repl app`
2. Require the module: `(require '[visualizers.stft :as stft])`
3. Test: `(stft/create-stft-visualizer :fft-size 256)`

## Support

- **Reagent docs:** https://reagent-project.github.io/
- **Shadow-cljs docs:** https://shadow-cljs.github.io/
- **Web Audio API:** https://developer.mozilla.org/en-US/docs/Web/API/Web_Audio_API
- **ClojureScript guide:** https://clojurescript.io/

Happy coding! 🎵
