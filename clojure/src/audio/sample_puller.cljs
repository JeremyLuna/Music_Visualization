(ns audio.sample-puller
  "Audio sample capture and buffering via AudioWorklet.
   
   Captures real-time audio samples from the audio pipeline without affecting playback."
  (:require [audio.interop :as interop]
            [goog.object :as gobj]))

;; ============================================================================
;; Sample Puller - Captures audio samples on demand
;; ============================================================================

(defrecord SamplePuller
  [^js worklet-node              ;; AudioWorkletNode instance
   channel-buffers              ;; Vector of atom-wrapped sample buffers (one per channel)
   num-channels                 ;; Number of audio channels
   max-buffer-size])            ;; Maximum samples to keep per channel

(defn- copy-samples!
  [channel-buffer write-index max-buffer-size samples]
  (let [write-idx @write-index
        buffer @channel-buffer
        samples-len (.-length samples)
        available (- max-buffer-size write-idx)]
    (if (>= available samples-len)
      (do
        (.set buffer samples write-idx)
        (reset! write-index (+ write-idx samples-len)))
      (do
        (.set buffer (js/Float32Array. samples 0 available) write-idx)
        (.set buffer (js/Float32Array. samples available) 0)
        (reset! write-index (- samples-len available))))))

(defn create-sample-puller
  "Create a new SamplePuller that captures samples from an audio worklet.
   
   Args:
   - worklet-node: AudioWorkletNode created from the sample processor
   - num-channels: Number of audio channels (typically 1 or 2)
   - max-buffer-size: Maximum samples to buffer per channel (default 8192)
   
   Returns: SamplePuller instance"
  [worklet-node num-channels & {:keys [max-buffer-size] :or {max-buffer-size 8192}}]
  (let [channel-buffers (mapv (fn [_] (atom (js/Float32Array. max-buffer-size)))
                              (range num-channels))
        write-indices (mapv (fn [_] (atom 0)) (range num-channels))
        port (.-port worklet-node)
        puller (->SamplePuller worklet-node channel-buffers num-channels max-buffer-size)]
    (gobj/set port "onmessage"
              (fn [event]
                (let [data (.-data event)
                      msg-type (.-type data)]
                  (when (= msg-type "SAMPLES")
                    (let [channels (.-channels data)]
                      (doseq [ch (range num-channels)]
                        (when-let [samples (aget channels ch)]
                          (copy-samples! (get channel-buffers ch)
                                         (get write-indices ch)
                                         max-buffer-size
                                         samples))))))))
    puller))

(defn pull-all-samples
  "Extract all buffered samples from the puller.
   
   Returns: {:channels [{:data Float32Array :length int} ...]}
   
   Note: This returns views of the internal buffers. Copy if you need to persist."
  [^SamplePuller puller]
  {:channels
   (mapv (fn [buffer]
           (let [data @buffer]
             {:data data
              :length (.-length data)}))
         (:channel-buffers puller))})

(defn pull-channel-samples
  "Extract samples from a specific channel.
   
   Args:
   - puller: SamplePuller instance
   - channel-index: Which channel (0 for mono, 0-1 for stereo, etc.)
   - max-samples: Maximum number of samples to return (default: all)
   
   Returns: Float32Array of samples"
  [^SamplePuller puller channel-index & {:keys [max-samples]}]
  (let [buffer @(get (:channel-buffers puller) channel-index)
        len (.-length buffer)
        count (min (or max-samples len) len)]
    (js/Float32Array. buffer 0 count)))

(defn get-channel-count
  "Get the number of audio channels being captured."
  [^SamplePuller puller]
  (:num-channels puller))

(defn reset-buffers!
  "Clear all sample buffers."
  [^SamplePuller puller]
  (doseq [buffer (:channel-buffers puller)]
    (reset! buffer (js/Float32Array. (:max-buffer-size puller)))))
