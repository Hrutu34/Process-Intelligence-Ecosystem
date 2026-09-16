export interface WaapiDef {
  selector: string;
  keyframes: Keyframe[] | PropertyIndexedKeyframes;
  duration: number;
  baseDelay?: number;
  stagger?: number;
  easing?: string;
}

export function playLoopingAnimations(
  root: Element | null,
  definitions: WaapiDef[]
): () => void {
  if (!root || typeof root.querySelectorAll !== "function") return () => {};
  const prefersReducedMotion =
    typeof window !== "undefined" &&
    window.matchMedia &&
    window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const supportsWaapi = typeof Element !== "undefined" && "animate" in Element.prototype;
  const animations: Animation[] = [];
  if (!prefersReducedMotion && supportsWaapi) {
    definitions.forEach(({ selector, keyframes, duration, baseDelay = 0, stagger = 0, easing = "ease-in-out" }) => {
      const els = root.querySelectorAll(selector);
      els.forEach((el, i) => {
        try {
          const anim = (el as Element).animate(keyframes, {
            duration,
            delay: baseDelay + stagger * i,
            iterations: Infinity,
            easing,
            fill: "both",
          });
          animations.push(anim);
        } catch (err) {}
      });
    });
  }
  return function cleanup() {
    animations.forEach((a) => {
      try { a.cancel(); } catch (err) {}
    });
  };
}
