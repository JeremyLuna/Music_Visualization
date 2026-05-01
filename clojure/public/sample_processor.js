// AudioWorkletProcessor for capturing audio samples in real-time
// This runs on the audio thread and sends samples back to the main thread

class SampleProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
    // Port to communicate with main thread
    this.port.onmessage = (event) => {
      if (event.data.type === 'INIT') {
        // Initialize if needed
      }
    };
  }

  process(inputs, outputs, parameters) {
    // Inputs is a 2D array: inputs[inputIndex][channelIndex] = Float32Array of samples
    const input = inputs[0]; // First input (microphone/audio source)
    
    if (input && input.length > 0) {
      // Send all channel data to main thread
      const channelData = [];
      for (let ch = 0; ch < input.length; ch++) {
        // Copy the samples to avoid sharing across renders
        channelData.push(new Float32Array(input[ch]));
      }
      
      this.port.postMessage({
        type: 'SAMPLES',
        channels: channelData,
        timestamp: currentTime
      });
    }
    
    // Return false to auto-process; return true to keep alive
    return true;
  }
}

registerProcessor('sample-processor', SampleProcessor);
