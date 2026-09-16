import React, { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { Canvas } from '@react-three/fiber';
import { Float, Stars, Sparkles, useTexture } from '@react-three/drei';
import './SplashScreen.css';

const ACCENT = '#22c55e';

/* ------------------------------------------------------------------ */
/* 3D SCENE                                                            */
/* ------------------------------------------------------------------ */

const LogoCoin: React.FC = () => {
  const texture = useTexture('/mascots/Pie.png');
  return (
    /* the "slightly above centre" lift lives OUTSIDE the Z-rotated group —
       inside it, +Y would be rotated 90° into a sideways X offset */
    <group position={[0, 0.75, 0]}>
      <Float speed={2} rotationIntensity={0.15} floatIntensity={0.9}>
        {/* outer group spins the tilted coin face 90° CCW (left) in screen space
            so the texture reads upright — Z must be applied AFTER the X tilt,
            hence a parent group instead of adding Z to the mesh's Euler XYZ */}
        <group rotation={[0, 0, Math.PI / 2]}>
          {/* cylinder = coin: full texture circularly cropped on both faces */}
          <mesh rotation={[Math.PI / 2 - 0.28, 0, 0]}>
            <cylinderGeometry args={[1.15, 1.15, 0.1, 64]} />
            {/* edge */}
            <meshBasicMaterial attach="material-0" color="#0b3d24" />
            {/* top / bottom faces */}
            <meshBasicMaterial attach="material-1" map={texture} />
            <meshBasicMaterial attach="material-2" map={texture} />
          </mesh>
        </group>
      </Float>
    </group>
  );
};

const SplashScene: React.FC = () => {
  return (
    <>
      <color attach="background" args={['#050f0a']} />
      <ambientLight intensity={0.7} />
      <spotLight position={[10, 10, 10]} angle={0.25} penumbra={1} intensity={1.3} />
      <pointLight position={[-6, -3, 4]} color={ACCENT} intensity={0.9} />
      <Stars radius={60} depth={40} count={2600} factor={4} saturation={0} fade speed={1.4} />
      <Sparkles count={90} scale={[9, 6, 6]} size={2.4} speed={0.35} color={ACCENT} opacity={0.6} />
      <LogoCoin />
    </>
  );
};

/* ------------------------------------------------------------------ */
/* PROCEDURAL BOOT AUDIO (Web Audio API — no external files needed)    */
/* ------------------------------------------------------------------ */

type ToneEvent =
  | { kind: 'sweep'; from: number; to: number; duration: number }
  | { kind: 'tone'; freq: number; duration: number }
  | { kind: 'chime' };

function useBootAudio() {
  const ctxRef = useRef<AudioContext | null>(null);

  const getCtx = useCallback((): AudioContext | null => {
    if (typeof window === 'undefined') return null;
    if (!ctxRef.current) {
      const Ctor =
        window.AudioContext ??
        (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
      if (!Ctor) return null;
      try {
        ctxRef.current = new Ctor();
      } catch {
        return null;
      }
    }
    return ctxRef.current;
  }, []);

  const playPing = useCallback(
    (ctx: AudioContext, freq: number, start: number, duration: number, peak: number, type: OscillatorType = 'sine') => {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = type;
      osc.frequency.setValueAtTime(freq, start);
      gain.gain.setValueAtTime(0, start);
      gain.gain.linearRampToValueAtTime(peak, start + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.0001, start + duration);
      osc.connect(gain).connect(ctx.destination);
      osc.start(start);
      osc.stop(start + duration + 0.05);
    },
    []
  );

  const play = useCallback(
    (event: ToneEvent) => {
      const ctx = getCtx();
      if (!ctx) return;
      if (ctx.state === 'suspended') {
        ctx.resume().catch(() => {});
      }
      const now = ctx.currentTime;
      try {
        if (event.kind === 'sweep') {
          const osc = ctx.createOscillator();
          const gain = ctx.createGain();
          osc.type = 'sine';
          osc.frequency.setValueAtTime(event.from, now);
          osc.frequency.exponentialRampToValueAtTime(event.to, now + event.duration);
          gain.gain.setValueAtTime(0, now);
          gain.gain.linearRampToValueAtTime(0.045, now + 0.06);
          gain.gain.exponentialRampToValueAtTime(0.0001, now + event.duration);
          osc.connect(gain).connect(ctx.destination);
          osc.start(now);
          osc.stop(now + event.duration + 0.05);
        } else if (event.kind === 'tone') {
          playPing(ctx, event.freq, now, event.duration, 0.05, 'square');
        } else if (event.kind === 'chime') {
          playPing(ctx, 660, now, 0.16, 0.05);
          playPing(ctx, 880, now + 0.1, 0.22, 0.05);
        }
      } catch {
        /* ignore audio failures (autoplay policy, unsupported browser, etc.) */
      }
    },
    [getCtx, playPing]
  );

  const resume = useCallback(() => {
    const ctx = getCtx();
    if (ctx && ctx.state === 'suspended') {
      ctx.resume().catch(() => {});
    }
  }, [getCtx]);

  return { play, resume };
}

/* ------------------------------------------------------------------ */
/* BOOT SEQUENCE LOG                                                   */
/* ------------------------------------------------------------------ */

interface BootStage {
  at: number;
  text: string;
  tone: ToneEvent;
}

const TOTAL_DURATION = 3400;
const FADE_DELAY = 3450;
const COMPLETE_DELAY = 4000;

const BOOT_STAGES: BootStage[] = [
  { at: 0, text: 'BOOTING PIE OS v2.0', tone: { kind: 'sweep', from: 220, to: 720, duration: 0.4 } },
  { at: 600, text: 'INITIALIZING NEURAL EXTRACTION ENGINE', tone: { kind: 'tone', freq: 480, duration: 0.1 } },
  { at: 1350, text: 'CALIBRATING AGENT SWARM // 4 MASCOTS DETECTED', tone: { kind: 'tone', freq: 540, duration: 0.1 } },
  { at: 2150, text: 'LINKING BPMN 2.0 RUNTIME', tone: { kind: 'tone', freq: 600, duration: 0.1 } },
  { at: 2950, text: 'SYSTEM READY', tone: { kind: 'chime' } },
];

export const SplashScreen: React.FC<{ onComplete: () => void }> = ({ onComplete }) => {
  const [fading, setFading] = useState(false);
  const [stageIndex, setStageIndex] = useState(-1);
  const [progress, setProgress] = useState(0);
  const [muted, setMuted] = useState(false);
  const mutedRef = useRef(muted);
  const skippingRef = useRef(false);
  const { play, resume } = useBootAudio();

  useEffect(() => {
    mutedRef.current = muted;
  }, [muted]);

  const finish = useCallback(() => {
    if (skippingRef.current) return;
    skippingRef.current = true;
    setFading(true);
    window.setTimeout(onComplete, 420);
  }, [onComplete]);

  /* Try to unlock the AudioContext on the first user gesture (autoplay policy). */
  useEffect(() => {
    const unlock = () => resume();
    window.addEventListener('pointerdown', unlock, { once: true });
    window.addEventListener('keydown', unlock, { once: true });
    return () => {
      window.removeEventListener('pointerdown', unlock);
      window.removeEventListener('keydown', unlock);
    };
  }, [resume]);

  useEffect(() => {
    const timers: number[] = [];

    BOOT_STAGES.forEach((stage, idx) => {
      timers.push(
        window.setTimeout(() => {
          setStageIndex(idx);
          if (!mutedRef.current) play(stage.tone);
        }, stage.at)
      );
    });

    const start = performance.now();
    const progressInterval = window.setInterval(() => {
      const elapsed = performance.now() - start;
      setProgress(Math.min(100, Math.round((elapsed / TOTAL_DURATION) * 100)));
    }, 40);

    const fadeTimer = window.setTimeout(() => setFading(true), FADE_DELAY);
    const completeTimer = window.setTimeout(() => {
      skippingRef.current = true;
      onComplete();
    }, COMPLETE_DELAY);

    return () => {
      timers.forEach(clearTimeout);
      clearInterval(progressInterval);
      clearTimeout(fadeTimer);
      clearTimeout(completeTimer);
    };
  }, [onComplete, play]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' || e.key === 'Enter') finish();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [finish]);

  const visibleStages = useMemo(() => BOOT_STAGES.slice(0, stageIndex + 1), [stageIndex]);

  return (
    <div className={`splash-screen ${fading ? 'fade-out' : ''}`}>
      <Canvas camera={{ position: [0, 0, 6], fov: 45 }}>
        <SplashScene />
      </Canvas>

      {/* HUD chrome */}
      <div className="splash-vignette" />
      <div className="splash-scanline" />
      <div className="splash-corner tl" />
      <div className="splash-corner tr" />
      <div className="splash-corner bl" />
      <div className="splash-corner br" />

      <button
        type="button"
        className="splash-mute-toggle"
        onClick={() => setMuted((m) => !m)}
        aria-label={muted ? 'Unmute boot sound' : 'Mute boot sound'}
      >
        {muted ? '🔇' : '🔊'}
      </button>

      <button type="button" className="splash-skip" onClick={finish}>
        SKIP <span>»</span>
      </button>

      <div className="splash-overlay">
        <h1 className="splash-title">PIE</h1>

        <div className="splash-loading-bar">
          <div className="splash-progress" style={{ width: `${progress}%` }} />
        </div>
        <div className="splash-progress-readout">
          <span>{String(progress).padStart(3, '0')}%</span>
        </div>

        <div className="splash-boot-log" aria-live="polite">
          {visibleStages.map((stage, idx) => {
            const isLast = idx === visibleStages.length - 1;
            return (
              <div key={stage.text} className={`splash-boot-line ${isLast ? 'active' : 'done'}`}>
                <span className="splash-boot-marker">{isLast ? '▸' : '✓'}</span>
                <span className="splash-boot-text">{stage.text}</span>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};




