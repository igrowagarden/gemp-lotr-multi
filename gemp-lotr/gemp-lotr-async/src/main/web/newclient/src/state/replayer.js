/**
 * Playing a recording: position, pacing, and the state at any point in it.
 *
 * This lived in `replay.html` alongside its DOM wiring, which is the same
 * problem `view/session.js` was extracted for -- nothing imports an HTML file,
 * so seek, play/pause and the speed model were outside every suite. They are
 * also the parts that are easy to get subtly wrong in ways a person watching
 * would not notice: an off-by-one in `seek` shows the board one event early
 * for the whole replay.
 *
 * REBUILDING FROM EVENT ZERO IS THE DESIGN, not a shortcut. The reducer is
 * pure, and a whole game is ~1700 events, so replaying the prefix costs a
 * fraction of a millisecond -- which makes stepping BACKWARD free. Trying to
 * invert events instead would need every reducer case to have an undo, and
 * getting one of those subtly wrong corrupts the board in a way nothing would
 * catch. The reference client cannot step back at all: its state is the DOM,
 * and `playNextReplayEvent` only ever walks forward.
 *
 * TIMERS ARE INJECTED so a test can drive playback without waiting. Real
 * `setInterval` is the default; a suite passes fakes and pumps them.
 */

import { initialState, reduce } from "./reduce.js";
import { slower, faster, intervalFor, speedLabel, SPEED_DEFAULT }
  from "../model/replayctl.js";

/**
 * @param store      replaced wholesale on every seek -- `store.replace`, not
 *                   dispatch, because the state IS the prefix and not an
 *                   increment on what was showing.
 * @param viewerId   a replay is ONE SEAT'S view of the game, hidden information
 *                   included, so the reducer must be seeded with that seat.
 * @param onPosition `({at, length, playing, speed, label})` after any change.
 *                   The page renders its scrubber and counter from this rather
 *                   than reaching in.
 */
export function createReplayer({
  store, viewerId, onPosition = () => {},
  timers = { set: setInterval, clear: clearInterval }
}) {
  let events = [];
  let at = 0;
  let timer = null;
  let speed = SPEED_DEFAULT;

  const announce = () => onPosition({
    at, length: events.length, playing: timer !== null,
    speed, label: speedLabel(speed)
  });

  function seek(n) {
    // Clamped at BOTH ends. `length` is a valid position -- it means "after the
    // last event", which is where a finished playback stops.
    at = Math.max(0, Math.min(Math.trunc(n) || 0, events.length));
    let state = initialState(viewerId);
    for (let i = 0; i < at; i++) state = reduce(state, events[i]);
    store.replace(state);
    announce();
    return at;
  }

  function pause() {
    if (timer !== null) { timers.clear(timer); timer = null; }
    announce();
  }

  function play() {
    // Restart rather than adjust: an interval's period is fixed once set, so a
    // speed change during playback would otherwise not take effect until it was
    // stopped and started by hand.
    if (timer !== null) timers.clear(timer);
    timer = timers.set(() => {
      if (at >= events.length) return pause();
      seek(at + 1);
    }, intervalFor(speed));
    announce();
  }

  return {
    /** Load a recording. Resets to the start; playback does not survive it. */
    load(next) {
      pause();
      events = next ?? [];
      seek(0);
    },

    seek,
    /** Stepping is a deliberate act, so it stops playback first. */
    step(delta) { pause(); return seek(at + delta); },
    play,
    pause,
    toggle() { timer === null ? play() : pause(); },

    /**
     * Slower DOUBLES the multiplier and faster HALVES it -- `replaySpeed` is a
     * duration multiplier, not a rate. See model/replayctl.js; built from the
     * button labels alone it comes out backwards.
     */
    slower() { speed = slower(speed); if (timer !== null) play(); else announce(); },
    faster() { speed = faster(speed); if (timer !== null) play(); else announce(); },

    get at() { return at; },
    get length() { return events.length; },
    get playing() { return timer !== null; },
    get speed() { return speed; }
  };
}
