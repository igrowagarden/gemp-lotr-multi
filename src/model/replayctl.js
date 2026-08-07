/**
 * Replay pacing.
 *
 * The reference's replay panel is three buttons (gameUi.js:153-180): slower,
 * faster, and one play/pause toggle. There is no scrubber, no step-back and no
 * seek -- `playNextReplayEvent` walks `replayGameEventNextIndex` forward and
 * only forward. This client has all three of those extras, which is a superset
 * and not a disagreement; what it did NOT have was any way to change the speed
 * at all, which is the one control the reference has and we lacked.
 *
 * The speed model is worth reading carefully, because it is inverted:
 *
 *     slower:  replaySpeed = Math.min(16,     replaySpeed * 2)
 *     faster:  replaySpeed = Math.max(0.0625, replaySpeed / 2)
 *
 * `replaySpeed` is a **duration multiplier**, not a rate --
 * `gameAnimations.js:17` returns `origValue * this.replaySpeed`. So the number
 * going UP means the replay goes SLOWER, and "faster" is the one that divides.
 * Anyone implementing this from the button labels alone gets it backwards.
 *
 * The bounds are 1/16 and 16, four halvings and four doublings from 1.
 *
 * Where this client differs, and it is a difference of model rather than of
 * value: the reference scales ANIMATION DURATIONS, because it replays by
 * playing each event's animation. This client advances one event per interval,
 * so the same multiplier scales the INTERVAL. Same control, same bounds, same
 * inversion, applied to the thing each client actually paces on.
 */

export const SPEED_MIN = 0.0625;   // four halvings from 1
export const SPEED_MAX = 16;       // four doublings from 1
export const SPEED_DEFAULT = 1;

/** The base interval, at speed 1. The reference has no equivalent constant. */
export const BASE_INTERVAL_MS = 220;

/** Slower DOUBLES the multiplier. Not a typo -- see the note above. */
export const slower = (speed) => Math.min(SPEED_MAX, speed * 2);

/** Faster HALVES it. */
export const faster = (speed) => Math.max(SPEED_MIN, speed / 2);

/** Milliseconds between events at this speed. */
export const intervalFor = (speed, base = BASE_INTERVAL_MS) => base * speed;

/**
 * How the speed reads to a person. The reference shows no number at all -- its
 * two buttons give no feedback whatsoever, so you cannot tell how many times
 * you have pressed one or whether you are at the limit. Shown here as a rate
 * (x2 is twice as fast) rather than as the multiplier, because the multiplier
 * being inverted is exactly the confusion this label should not pass on.
 */
export function speedLabel(speed) {
  const rate = 1 / speed;
  if (rate >= 1) return `×${Number.isInteger(rate) ? rate : rate.toFixed(2)}`;
  return `×1/${Number.isInteger(1 / rate) ? 1 / rate : (1 / rate).toFixed(2)}`;
}
