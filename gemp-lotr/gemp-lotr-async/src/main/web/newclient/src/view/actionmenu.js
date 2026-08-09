/**
 * Choosing between several abilities on one card.
 *
 * Shared, because a card action can be offered on more than one surface: cards
 * in a band, and SITES in the path window. A site with an ability -- "Use
 * Rivendell Terrace", every sanctuary -- is a perfectly ordinary
 * CARD_ACTION_CHOICE target, and until this was shared those actions could not
 * be taken at all. Found by the differential harness against the old client.
 */

export function openActionMenu(root, list, event, pick) {
  const doc = root.ownerDocument;
  closeActionMenu(root);

  const menu = doc.createElement("div");
  menu.className = "actionmenu";
  for (const action of list) {
    const item = doc.createElement("button");
    item.type = "button";
    item.className = "actionitem";
    item.textContent = action.text;
    item.addEventListener("click", (e) => { e.stopPropagation(); pick(action.actionId); });
    menu.appendChild(item);
  }
  root.appendChild(menu);

  // Measured after it is in the DOM: its size depends on the action texts.
  const box = root.getBoundingClientRect();
  const size = menu.getBoundingClientRect();
  const x = Math.min(Math.max(4, (event?.clientX ?? 0) - box.left), box.width - size.width - 4);
  const y = Math.min(Math.max(4, (event?.clientY ?? 0) - box.top), box.height - size.height - 4);
  menu.style.left = x + "px";
  menu.style.top = y + "px";

  const dismiss = (e) => {
    if (menu.contains(e.target)) return;
    closeActionMenu(root);
    doc.removeEventListener("pointerdown", dismiss, true);
  };
  doc.addEventListener("pointerdown", dismiss, true);
  return menu;
}

export function closeActionMenu(root) {
  root.querySelector(".actionmenu")?.remove();
}

/**
 * Wire one node as a card-action target: resolve immediately when the card
 * offers a single action, and put up the menu when it offers several -- picking
 * the card is not the same as picking the action.
 */
export function wireActions(node, list, menuRoot, answer) {
  node.addEventListener("click", (e) => {
    if (list.length === 1) answer(list[0].actionId);
    else openActionMenu(menuRoot, list, e, answer);
  });
}
