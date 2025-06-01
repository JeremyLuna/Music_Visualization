// This file must be served alongside your main script.
// It defines a simple AudioWorkletProcessor that forwards every input block (per channel) to the main thread.

class SampleProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
  }

  process(inputs, outputs, parameters) {
    // inputs[0] is an array of Float32Array—one per channel.
    const input = inputs[0];
    if (input.length > 0) {
      // Send the array of Float32Arrays to the main thread
      this.port.postMessage(input);
    }
    return true;
  }
}

registerProcessor('sample-processor', SampleProcessor);