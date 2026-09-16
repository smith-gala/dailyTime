import { attachPlayPhrasePage, connectToBrowser, evaluate } from "../automation/playphrase/cdp.mjs";

const connection = await connectToBrowser(
  process.env.OPENCLAW_CDP_URL || "http://127.0.0.1:18800",
);

try {
  const { sessionId } = await attachPlayPhrasePage(connection);
  if (process.argv.includes('--dialog')) {
    const point = await evaluate(connection, sessionId, `(() => {
      const element = document.querySelector('.download-current-video-button');
      if (!element) return null;
      const rect = element.getBoundingClientRect();
      return {x: rect.x + rect.width / 2, y: rect.y + rect.height / 2};
    })()`);
    if (!point) throw new Error('找不到下载按钮');
    await connection.call('Input.dispatchMouseEvent', {type: 'mouseMoved', ...point}, {sessionId});
    await connection.call('Input.dispatchMouseEvent', {type: 'mousePressed', ...point, button: 'left', buttons: 1, clickCount: 1}, {sessionId});
    await connection.call('Input.dispatchMouseEvent', {type: 'mouseReleased', ...point, button: 'left', buttons: 0, clickCount: 1}, {sessionId});
    await new Promise((resolve) => setTimeout(resolve, 500));
    const {nodes = []} = await connection.call('Accessibility.getFullAXTree', {}, {sessionId});
    const relevant = nodes.filter((node) => /Download|Audio only|稍后再说|以后再说|解锁|登录|已订阅/i.test(String(node.name?.value || '')))
      .map((node) => ({role: node.role?.value, name: node.name?.value,
        backendDOMNodeId: node.backendDOMNodeId, ignored: node.ignored, properties: node.properties}));
    console.log(JSON.stringify({accessibility: relevant}, null, 2));
    process.exit(0);
  }
  const details = await evaluate(connection, sessionId, `(() => {
    const visible = (element) => {
      const rect = element.getBoundingClientRect();
      const style = getComputedStyle(element);
      return rect.width > 0 && rect.height > 0 && style.display !== 'none' && style.visibility !== 'hidden';
    };
    const describe = (element) => {
      const rect = element.getBoundingClientRect();
      const ancestors = [];
      for (let node = element; node && ancestors.length < 5; node = node.parentElement) {
        ancestors.push({tag: node.tagName, id: node.id, className: String(node.className || '')});
      }
      return {
        tag: element.tagName,
        text: (element.textContent || '').trim(),
        id: element.id,
        className: String(element.className || ''),
        rect: {x: rect.x, y: rect.y, width: rect.width, height: rect.height},
        ancestors,
      };
    };
    const counters = [...document.querySelectorAll('body *')]
      .filter((element) => /^\\d[\\d,]*\\s*\\/\\s*\\d[\\d,]*$/.test((element.textContent || '').trim()))
      .filter(visible)
      .filter((element) => ![...element.children].some((child) => /^\\d[\\d,]*\\s*\\/\\s*\\d[\\d,]*$/.test((child.textContent || '').trim())))
      .map(describe);
    const download = document.querySelector('.download-current-video-button');
    const inputs = [...document.querySelectorAll('input, textarea, [contenteditable="true"]')]
      .filter(visible)
      .map((element) => ({...describe(element), placeholder: element.placeholder, ariaLabel: element.getAttribute('aria-label'), value: element.value}));
    const videos = [...document.querySelectorAll('video')].map((element, index) => {
      const style = getComputedStyle(element);
      return {
        ...describe(element), index, paused: element.paused, ended: element.ended,
        readyState: element.readyState, currentTime: element.currentTime, currentSrc: element.currentSrc,
        style: {display: style.display, visibility: style.visibility, opacity: style.opacity,
          zIndex: style.zIndex, transform: style.transform},
        parentClass: String(element.parentElement?.className || ''),
      };
    });
    const videoRect = document.querySelector('video')?.getBoundingClientRect();
    const videoStack = videoRect ? document.elementsFromPoint(
      videoRect.x + videoRect.width / 2, videoRect.y + videoRect.height / 2,
    ).filter((element) => element.tagName === 'VIDEO').map((element) => [...document.querySelectorAll('video')].indexOf(element)) : [];
    const forward = [...document.querySelectorAll('.overlay-controls-icons .controls img')]
      .find((element) => (element.getAttribute('src') || '').endsWith('/forward.svg'));
    return {href: location.href, title: document.title, counters, download: download ? describe(download) : null,
      inputs, videos, videoStack, forward: forward ? describe(forward) : null};
  })()`);
  console.log(JSON.stringify(details, null, 2));
} finally {
  connection.close();
}
