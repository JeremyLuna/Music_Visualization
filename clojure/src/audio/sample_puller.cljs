(ns audio.sample-puller
  "Audio sample capture and buffering via AudioWorklet.
   
   Captures real-time audio samples from the audio pipeline without affecting playback."
  (:require [goog.object :as gobj]))

;; ============================================================================
;; Sample Puller - Captures audio samples on demand
;; ============================================================================

(defrecord SamplePuller
  [^js worklet-node              ;; AudioWorkletNode instance
   channel-buffers              ;; Vector of atom-wrapped sample buffers (one per channel)
   write-indices                ;; Current circular-buffer write positions
   samples-written              ;; Total samples captured per channel
   num-channels                 ;; Number of audio channels
   max-buffer-size])            ;; Maximum samples to keep per channel

(defn- copy-samples!
  [channel-buffer write-index samples-written max-buffer-size samples]
  (let [samples-len (.-length samples)]
    (when (pos? samples-len)
      (let [source (if (> samples-len max-buffer-size)
                     (.subarray samples (- samples-len max-buffer-size) samples-len)
                     samples)
            source-len (.-length source)
            write-idx @write-index
            buffer @channel-buffer
            available (- max-buffer-size write-idx)]
        (if (>= available source-len)
          (.set buffer source write-idx)
          (let [tail (.subarray source 0 available)
                head (.subarray source available source-len)]
            (.set buffer tail write-idx)
            (.set buffer head 0)))
        (reset! write-index (mod (+ write-idx source-len) max-buffer-size))
        (swap! samples-written + source-len)))))

(defn- chronological-window
  [buffer write-index samples-written max-buffer-size requested-count]
  (let [available (min @samples-written max-buffer-size)
        count (min requested-count available)
        output (js/Float32Array. count)]
    (when (pos? count)
      (let [start (mod (- @write-index count) max-buffer-size)
            end (+ start count)]
        (if (<= end max-buffer-size)
          (.set output (.subarray buffer start end) 0)
          (let [tail-count (- max-buffer-size start)
                head-count (- count tail-count)]
            (.set output (.subarray buffer start max-buffer-size) 0)
            (.set output (.subarray buffer 0 head-count) tail-count)))))
    output))

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
        samples-written (mapv (fn [_] (atom 0)) (range num-channels))
        port (.-port worklet-node)
        puller (->SamplePuller worklet-node channel-buffers write-indices samples-written num-channels max-buffer-size)]
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
                                         (get samples-written ch)
                                         max-buffer-size
                                         samples))))))))
    puller))

(defn pull-all-samples
  "Extract all buffered samples from the puller.
   
   Returns: {:channels [{:data Float32Array :length int} ...]}
   
   Note: This returns views of the internal buffers. Copy if you need to persist."
  [^SamplePuller puller]
  {:channels
   (mapv (fn [buffer write-index samples-written]
           (let [data (chronological-window @buffer
                                            write-index
                                            samples-written
                                            (:max-buffer-size puller)
                                            (:max-buffer-size puller))]
             {:data data
              :length (.-length data)}))
         (:channel-buffers puller)
         (:write-indices puller)
         (:samples-written puller))})

(defn pull-channel-samples
  "Extract samples from a specific channel.
   
   Args:
   - puller: SamplePuller instance
   - channel-index: Which channel (0 for mono, 0-1 for stereo, etc.)
   - max-samples: Maximum number of samples to return (default: all)
   
   Returns: Float32Array of samples"
  [^SamplePuller puller channel-index & {:keys [max-samples]}]
  (if (or (neg? channel-index) (>= channel-index (:num-channels puller)))
    (js/Float32Array. 0)
    (let [buffer @(get (:channel-buffers puller) channel-index)
          count (or max-samples (:max-buffer-size puller))]
      (chronological-window buffer
                            (get (:write-indices puller) channel-index)
                            (get (:samples-written puller) channel-index)
                            (:max-buffer-size puller)
                            count))))

(defn get-channel-samples-written
  "Get the total number of samples captured for a channel."
  [^SamplePuller puller channel-index]
  (if (or (neg? channel-index) (>= channel-index (:num-channels puller)))
    0
    @(get (:samples-written puller) channel-index)))

(defn pull-channel-samples-since
  "Extract samples captured for a channel after a previous total count.

   If more samples arrived than the circular buffer can hold, this returns the
   newest max-buffer-size samples."
  [^SamplePuller puller channel-index previous-samples-written]
  (if (or (neg? channel-index) (>= channel-index (:num-channels puller)))
    (js/Float32Array. 0)
    (let [current (get-channel-samples-written puller channel-index)
          previous (max 0 (or previous-samples-written 0))
          delta (max 0 (- current previous))
          count (min delta (:max-buffer-size puller))
          buffer @(get (:channel-buffers puller) channel-index)]
      (chronological-window buffer
                            (get (:write-indices puller) channel-index)
                            (get (:samples-written puller) channel-index)
                            (:max-buffer-size puller)
                            count))))

(defn get-channel-count
  "Get the number of audio channels being captured."
  [^SamplePuller puller]
  (:num-channels puller))

(defn reset-buffers!
  "Clear all sample buffers."
  [^SamplePuller puller]
  (doseq [buffer (:channel-buffers puller)]
    (reset! buffer (js/Float32Array. (:max-buffer-size puller))))
  (doseq [write-index (:write-indices puller)]
    (reset! write-index 0))
  (doseq [samples-written (:samples-written puller)]
    (reset! samples-written 0)))
