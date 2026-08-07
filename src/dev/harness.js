/**
 * The scaffolding every dev page was carrying its own copy of.
 *
 * Twenty-three pages live in this directory and the same four things were
 * pasted into all of them: a way to wait for the old client's iframe, a way to
 * print a pass/fail line, a deep-equality helper, and the RESULT line the shell
 * runners grep for. Counted before extracting: `ready` in 11 files, `say` in 12,
 * `OLD.boot({` in 12, `eq` in 8.
 *
 * Duplication that size stops being duplication and becomes DRIFT. The RESULT
 * line is the clearest case -- every runner greps `^RESULT:` and four different
 * wordings had grown (`FAILED`, `DIFF`, `PROBLEM`, `MISMATCH`), each with its
 * own arithmetic for the total. One of them counting wrong would look exactly
 * like a passing run.
 *
 * TWO REPORTER STYLES, because the pages genuinely do two different things:
 *
 *   ok(label, cond)   an ASSERTION with a known right answer. The check suites.
 *   say(text, cls)    a LINE, for pages whose output is a comparison rather
 *                     than a verdict -- the differentials.
 *
 * Both feed the same counters and the same RESULT line, so a page can use
 * either or both.
 *
 * WHY textContent AND NOT innerHTML. The old `say` accumulated strings and
 * assigned `out.innerHTML`, which meant any `<` in a diff -- a card's text, a
 * JSON dump, a log message under test -- was parsed as markup. logfuzz compares
 * lines containing `<div class='cardHint'>`; printing one would have silently
 * rendered it instead of showing it.
 */

/** Deep equality by serialisation. Enough for the plain data these pages compare. */
export const eq = (a, b) => JSON.stringify(a) === JSON.stringify(b);

/** The page's query string, which is how every runner passes its options. */
export const query = () => new URLSearchParams(location.search);

/**
 * A reporter: counters, output lines, and the RESULT line at the end.
 *
 * @param out       the element to append to. Defaults to `#out`.
 * @param failWord  the word in `RESULT: n <word> of total`. The runners only
 *                  match `^RESULT:` and `ALL PASS`, so this is for the reader --
 *                  a differential says DIFF because a difference is not
 *                  necessarily a defect, and an assertion suite says FAILED
 *                  because it is.
 */
export function createReporter({ out = document.getElementById("out"),
                                 failWord = "FAILED" } = {}) {
  let pass = 0, fail = 0, skipped = 0;

  /**
   * A line, and it must be a REAL newline in the text.
   *
   * The shell runners strip tags with `sed 's/<[^>]*>//g'` -- DELETING them,
   * not turning them into newlines -- and then grep `^RESULT:`. Appending bare
   * `<div>`s makes every line run together once the tags go, so the RESULT line
   * is never at the start of one and every run reports NO RESULT. The div
   * carries the colour; the text node after it carries the line break.
   */
  const line = (text, cls) => {
    const e = document.createElement("div");
    if (cls) e.className = cls;
    e.textContent = text;
    out.appendChild(e);
    out.appendChild(document.createTextNode("\n"));
    return e;
  };

  const api = {
    /** An assertion. `detail` is shown only on failure, where it is the evidence. */
    ok(label, cond, detail) {
      cond ? pass++ : fail++;
      line((cond ? "PASS  " : "FAIL  ") + label +
           (cond || !detail ? "" : "  -> " + detail), cond ? "pass" : "fail");
      return !!cond;
    },

    /** A free line. `cls` is one of pass / fail / note / warn / bad / ok. */
    say(text, cls) { line(text, cls); },

    /** An indented aside. Never counted -- it is context, not a verdict. */
    note(text) { line("      " + text, "note"); },

    /**
     * A measurement that could not be taken. Counted SEPARATELY and reported in
     * the RESULT line, because a skipped check and a passing one must never
     * look alike -- that is the failure this project keeps re-learning.
     */
    skip(label, why) { skipped++; line("SKIP  " + label + "  -> " + why, "note"); },

    /** A difference found by a comparison. Counts as a failure. */
    diff(text, detail) {
      fail++;
      line("  DIFF     " + text + (detail ? ": " + detail : ""), "bad");
    },

    /**
     * Emit the RESULT line. Returns the failure count so a caller can exit on
     * it. Safe to call once; calling twice prints two RESULT lines and the
     * runners take the first, so do not.
     */
    result(extra = "") {
      const total = pass + fail;
      const text = fail === 0
        ? `RESULT: ALL PASS (${total}${skipped ? `, ${skipped} skipped` : ""})${extra}`
        : `RESULT: ${fail} ${failWord} of ${total}${extra}`;
      const e = line(text, fail === 0 ? "pass" : "fail");
      e.id = "result";
      return fail;
    },

    get pass() { return pass; },
    get fail() { return fail; },
    get skipped() { return skipped; }
  };
  return api;
}

/**
 * Bring up the old client in an iframe and boot it.
 *
 * `before` runs after the harness is reachable but BEFORE `boot()`, which is
 * the only window in which the opt-in flags can be set -- `setRealChatBox` and
 * `setReplayMode` are both read during boot, and setting either afterwards
 * leaves the default in place while the page carries on as though it had taken.
 * Both have their own preflight in the pages that use them for that reason.
 *
 * @returns { OLD, ui, $, frame } -- `ui` and `$` after boot, so they are the
 *   booted instance and not a stale one.
 */
export async function bootOracle({ frameId = "old", gameId = "1",
                                   participantId = "asdf", board = null,
                                   before = null } = {}) {
  const frame = document.getElementById(frameId);
  let OLD = null;
  for (let i = 0; i < 300 && !OLD; i++) {
    OLD = frame.contentWindow?.OLD ?? null;
    if (!OLD) await new Promise((r) => setTimeout(r, 50));
  }
  if (!OLD) throw new Error("old harness never came up");

  before?.(OLD);
  OLD.boot({ gameId, participantId });
  if (board) OLD.feedBatch(OLD.parseEvents(board));

  return { OLD, ui: OLD.ui, $: frame.contentWindow.$, frame };
}
