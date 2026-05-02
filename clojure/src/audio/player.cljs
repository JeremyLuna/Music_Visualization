(ns audio.player
  "Audio playback and file loading via Web Audio API."
  (:require [audio.interop :as interop]
            [audio.sample-puller :as puller]
            [app.state :as state]))

;; ============================================================================
;; Audio Player - High-level audio playback interface
;; ============================================================================

(defrecord AudioPlayer
  [^js audio-context               ;; AudioContext instance
   ^js audio-element               ;; HTMLAudioElement for playback
   ^js media-source                ;; MediaElementAudioSourceNode
   ^js gain-node                   ;; GainNode for volume control
   ^js worklet-node                ;; AudioWorkletNode for sample capture
   sample-puller])                 ;; SamplePuller instance

(defn ^:async create-audio-player
  "Create a new AudioPlayer with Web Audio API graph.
   
   Returns a promise that resolves with the AudioPlayer instance."
  []
  (js/Promise.
   (fn [resolve reject]
     (try
       ;; Create AudioContext
       (let [audio-context (interop/create-audio-context)
             
             ;; Create audio element for playback
             audio-element (js/document.createElement "audio")
             _ (set! (.-crossOrigin audio-element) "anonymous")
             
             ;; Create audio graph: audio-element -> media-source -> gain -> destination
             media-source (interop/create-media-element-source audio-context audio-element)
             gain-node (interop/create-gain-node audio-context)
             
             ;; Initialize gain to 1.0 (full volume)
             _ (interop/set-gain gain-node 1.0)
             
             ;; Connect: media-source -> gain -> destination
             _ (interop/connect-nodes media-source gain-node)
             _ (interop/connect-nodes gain-node (interop/get-audio-context-destination audio-context))]
         
         ;; Now load the AudioWorklet for sample capture
         (-> (interop/create-audio-worklet audio-context "/sample_processor.js" "sample-processor")
             (.then (fn [worklet-node]
                      ;; Connect worklet to gain node (for monitoring/analysis)
                      (interop/connect-nodes gain-node worklet-node)
                      
                      ;; Create sample puller
                      (let [sp (puller/create-sample-puller worklet-node 2 :max-buffer-size 8192)
                            player (->AudioPlayer audio-context audio-element media-source gain-node worklet-node sp)]
                        
                        ;; Hide audio element (we just use it for playback control)
                        (set! (.-hidden audio-element) true)
                        (js/document.body.appendChild audio-element)
                        
                        ;; Update app state with audio context
                        (state/dispatch :init-audio audio-context)
                        (state/dispatch :set-sample-rate (interop/get-sample-rate audio-context))
                        
                        (resolve player))))
             (.catch reject)))
       
       (catch :default e
         (reject e))))))

(defn load-audio-file
  "Load an audio file into the player.
   
   Args:
   - player: AudioPlayer instance
   - file: File object from file input
   
   Returns a promise that resolves when audio is loaded."
  [^AudioPlayer player file]
  (js/Promise.
   (fn [resolve reject]
     ;; Read file as array buffer
     (-> (interop/file-reader-read-array-buffer file)
         (.then (fn [array-buffer]
                  ;; Decode audio data
                  (interop/decode-audio-data (:audio-context player) array-buffer)))
         (.then (fn [audio-buffer]
                  ;; Create a blob URL and set as audio source
                  (let [blob-url (js/URL.createObjectURL file)
                        audio-element (:audio-element player)]
                    (set! (.-src audio-element) blob-url)
                    
                    ;; Listen for loadedmetadata to get duration
                    (set! (.-onloadedmetadata audio-element)
                          (fn []
                            (let [duration (interop/get-audio-element-duration audio-element)]
                              (state/dispatch :set-duration duration))
                            (remove-property audio-element "onloadedmetadata")
                            (resolve audio-element)))
                    
                    ;; Handle load errors
                    (set! (.-onerror audio-element)
                          (fn [error]
                            (reject error))))))
         (.catch reject)))))

(defn play
  "Start playback.
   
   Args:
   - player: AudioPlayer instance"
  [^AudioPlayer player]
  (let [audio-element (:audio-element player)]
    (-> (interop/play-audio-element audio-element)
        (.then (fn []
                 (state/dispatch :set-playing true)))
        (.catch (fn [e]
                  (.warn js/console "Play error:" e))))))

(defn pause
  "Pause playback.
   
   Args:
   - player: AudioPlayer instance"
  [^AudioPlayer player]
  (interop/pause-audio-element (:audio-element player))
  (state/dispatch :set-playing false))

(defn stop
  "Stop playback and reset to beginning.
   
   Args:
   - player: AudioPlayer instance"
  [^AudioPlayer player]
  (pause player)
  (seek player 0))

(defn seek
  "Seek to a specific time.
   
   Args:
   - player: AudioPlayer instance
   - time: Time in seconds"
  [^AudioPlayer player time]
  (interop/set-audio-element-current-time (:audio-element player) time))

(defn get-current-time
  "Get the current playback position.
   
   Args:
   - player: AudioPlayer instance
   
   Returns: Time in seconds"
  [^AudioPlayer player]
  (interop/get-audio-element-current-time (:audio-element player)))

(defn get-duration
  "Get the total duration of the loaded audio.
   
   Args:
   - player: AudioPlayer instance
   
   Returns: Time in seconds"
  [^AudioPlayer player]
  (interop/get-audio-element-duration (:audio-element player)))

(defn set-volume
  "Set the playback volume.
   
   Args:
   - player: AudioPlayer instance
   - volume: 0.0 to 1.0"
  [^AudioPlayer player volume]
  (interop/set-gain (:gain-node player) volume))

(defn get-volume
  "Get the current playback volume.
   
   Args:
   - player: AudioPlayer instance
   
   Returns: 0.0 to 1.0"
  [^AudioPlayer player]
  (get-in player [:gain-node :gain :value]))

(defn get-sample-puller
  "Get the SamplePuller for accessing audio samples.
   
   Args:
   - player: AudioPlayer instance
   
   Returns: SamplePuller instance"
  [^AudioPlayer player]
  (:sample-puller player))
