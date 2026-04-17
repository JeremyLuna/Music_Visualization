/**
 * EventEmitter.js
 * Simple pub/sub event system for decoupling components.
 */

export class EventEmitter {
  constructor() {
    this.listeners = {};
  }

  /**
   * Subscribe to an event.
   * @param {string} eventName - The event name
   * @param {function} callback - Function to call when event fires
   */
  on(eventName, callback) {
    if (!this.listeners[eventName]) {
      this.listeners[eventName] = [];
    }
    this.listeners[eventName].push(callback);
  }

  /**
   * Unsubscribe from an event.
   * @param {string} eventName - The event name
   * @param {function} callback - The callback to remove
   */
  off(eventName, callback) {
    if (!this.listeners[eventName]) return;
    this.listeners[eventName] = this.listeners[eventName].filter(cb => cb !== callback);
  }

  /**
   * Emit an event to all listeners.
   * @param {string} eventName - The event name
   * @param {*} data - Data to pass to listeners
   */
  emit(eventName, data) {
    if (!this.listeners[eventName]) return;
    this.listeners[eventName].forEach(callback => {
      try {
        callback(data);
      } catch (e) {
        console.error(`Error in listener for event '${eventName}':`, e);
      }
    });
  }
}
