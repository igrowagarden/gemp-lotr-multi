/**
 * Telling you it is your turn when you are not looking at the tab.
 *
 * The reference client does two things our client did not do at all: it plays a
 * short sound on every decision (`PlaySound("awaitAction")`, seven call sites in
 * gameUi.js) and it animates the browser tab title between the game name and a
 * "your turn" line (`startAnimatingTitle` / `setDecisionTitle`). Without either,
 * a backgrounded tab gives the player nothing, and in a five-player game the
 * wait between your turns is long enough that nobody watches continuously.
 *
 * Deliberate choices:
 *
 *  - The sound is SYNTHESISED, with WebAudio, rather than shipped as an asset.
 *    Two short notes need no file, no fetch, and no licence question.
 *  - Browsers refuse to start audio before a gesture. Rather than let that throw
 *    on every decision, the context is created lazily and a failure is swallowed
 *    -- the title alert still works, and audio starts working the moment the
 *    player interacts with the page.
 *  - Nothing fires while the tab is visible: an alert for something already on
 *    screen is just noise. That also means the sound cannot fire before the
 *    player has ever touched the page, which is the case browsers block anyway.
 */

const TITLE_MS = 900;

export function createAlerts({
  doc = document,
  win = window,
  title = doc.title,
  sound = true
} = {}) {
  let audio = null;
  let flashing = false;
  let timer = null;
  let armed = null;        // the decision we have already alerted for

  function beep() {
    if (!sound) return;
    try {
      const Ctx = win.AudioContext ?? win.webkitAudioContext;
      if (!Ctx) return;
      audio ??= new Ctx();
      if (audio.state === "suspended") audio.resume?.();
      const now = audio.currentTime;
      for (const [at, hz] of [[0, 660], [0.13, 880]]) {
        const osc = audio.createOscillator();
        const gain = audio.createGain();
        osc.type = "sine";
        osc.frequency.value = hz;
        gain.gain.setValueAtTime(0.0001, now + at);
        gain.gain.exponentialRampToValueAtTime(0.09, now + at + 0.02);
        gain.gain.exponentialRampToValueAtTime(0.0001, now + at + 0.12);
        osc.connect(gain).connect(audio.destination);
        osc.start(now + at);
        osc.stop(now + at + 0.14);
      }
    } catch {
      /* audio is a courtesy; never let it break the board */
    }
  }

  function stopFlashing() {
    flashing = false;
    if (timer) { clearInterval(timer); timer = null; }
    doc.title = title;
  }

  function startFlashing(text) {
    if (flashing) return;
    flashing = true;
    let on = false;
    timer = setInterval(() => {
      on = !on;
      doc.title = on ? text : title;
    }, TITLE_MS);
    doc.title = text;
  }

  const visible = () => doc.visibilityState !== "hidden";
  doc.addEventListener("visibilitychange", () => { if (visible()) stopFlashing(); });

  return {
    /**
     * Called on every state change. `decision` is the one addressed to us, or
     * null. Alerts once per decision, and only while the tab is in the
     * background.
     */
    update(decision) {
      if (!decision) {
        armed = null;
        stopFlashing();
        return;
      }
      // Identity, not id: the engine hardcodes decision ids, so consecutive
      // decisions share them and an id check would alert only once.
      if (decision === armed) return;
      armed = decision;
      if (visible()) return;
      beep();
      startFlashing("● Your turn — " + title);
    },

    stop: stopFlashing
  };
}
