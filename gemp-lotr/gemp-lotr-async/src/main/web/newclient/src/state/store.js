/**
 * The store. Subscribe, emit, snapshot.
 *
 * Deliberately small. Its whole job is to be the one place the state lives so
 * that views are pure readers of it -- including views in other windows, which
 * is why detaching a board costs nothing. The old client had no equivalent: the
 * DOM was the state, so anything that wanted to know a card's zone went looking
 * for a DOM node.
 *
 * A detached window subscribes exactly like an in-page view. It never opens a
 * connection: LotroGameMediator throws SubscriptionConflictException if a second
 * client claims a player's channel, which reaches the browser as HTTP 409.
 */

import { initialState, reduce } from "./reduce.js";

export function createStore(viewerId, options = {}) {
  let state = initialState(viewerId, options);
  const listeners = new Set();
  let depth = 0;

  function emit() {
    // Copy first: a listener may unsubscribe (or a detached window may close)
    // during the walk, and mutating the set mid-iteration would skip listeners.
    for (const listener of [...listeners]) {
      try {
        listener(state);
      } catch (err) {
        // One broken view must not stop the others repainting. A closed child
        // window throws on DOM access and simply drops out here.
        listeners.delete(listener);
        if (options.onListenerError) options.onListenerError(err);
      }
    }
  }

  return {
    getState: () => state,

    subscribe(listener, { immediate = true } = {}) {
      listeners.add(listener);
      if (immediate) {
        try { listener(state); } catch (err) {
          listeners.delete(listener);
          if (options.onListenerError) options.onListenerError(err);
          return () => {};
        }
      }
      return () => listeners.delete(listener);
    },

    /** Apply one decoded event. */
    dispatch(event) {
      state = reduce(state, event);
      if (depth === 0) emit();
      return state;
    },

    /**
     * Apply a whole batch and repaint once. A single poll can carry dozens of
     * events; repainting per event would be both slow and visibly wrong, since
     * mid-batch states are not states the game was ever in.
     */
    dispatchAll(events) {
      depth++;
      try {
        for (const event of events) state = reduce(state, event);
      } finally {
        depth--;
      }
      if (depth === 0) emit();
      return state;
    },

    /** For tests and for a hard resync after a dropped connection. */
    replace(next) {
      state = next;
      emit();
      return state;
    },

    get listenerCount() {
      return listeners.size;
    }
  };
}
