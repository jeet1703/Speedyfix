import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { siteImage, siteSrcSet } from '../api/images';
import type { Slide } from '../api/types';
import { Icon } from './Icons';

const INTERVAL = 7000;

export function HeroSlider({ slides }: { slides: Slide[] }) {
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  const count = slides.length;
  const go = useCallback((i: number) => setIndex(((i % count) + count) % count), [count]);

  // The current slide's photo loads first; the next one starts once it's in, so they never compete.
  const [wanted, setWanted] = useState<Set<number>>(() => new Set([0]));
  const want = useCallback((i: number) => setWanted((w) => (w.has(i) ? w : new Set([...w, i]))), []);
  useEffect(() => want(index), [index, want]);

  useEffect(() => {
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (paused || reduce || count < 2) return;
    const t = window.setTimeout(() => go(index + 1), INTERVAL);
    return () => window.clearTimeout(t);
  }, [index, paused, count, go]);

  if (!count) return null;

  return (
    <section className="hero" aria-roledescription="carousel" aria-label="Featured categories"
      onMouseEnter={() => setPaused(true)} onMouseLeave={() => setPaused(false)}
      onFocus={() => setPaused(true)} onBlur={() => setPaused(false)}>
      {slides.map((s, i) => (
        <div key={s.id} className={'hero__slide' + (i === index ? ' is-active' : '')} role="group"
          aria-roledescription="slide" aria-label={`${i + 1} of ${count}: ${s.title}`} aria-hidden={i !== index}>
          {wanted.has(i) && (
            <img className="hero__img" src={siteImage(s.imageUrl)} srcSet={siteSrcSet(s.imageUrl)} sizes="100vw" alt=""
              onLoad={i === index ? () => want((i + 1) % count) : undefined} />
          )}
          <div className="container hero__content">
            <p className="hero__kicker">{s.kicker}</p>
            <h1 className="hero__title">{s.title}</h1>
            <p className="hero__text">{s.text}</p>
            <div className="hero__actions">
              <Link className="btn btn--primary" to={s.primaryHref} tabIndex={i === index ? 0 : -1}>{s.primaryLabel}</Link>
              <Link className="btn btn--outline-light" to={s.secondaryHref} tabIndex={i === index ? 0 : -1}>{s.secondaryLabel}</Link>
            </div>
          </div>
        </div>
      ))}
      {count > 1 && (
        <>
          <button type="button" className="hero__arrow hero__arrow--prev" aria-label="Previous slide" onClick={() => go(index - 1)}>
            <Icon name="chevronLeft" />
          </button>
          <button type="button" className="hero__arrow hero__arrow--next" aria-label="Next slide" onClick={() => go(index + 1)}>
            <Icon name="chevronRight" />
          </button>
          <div className="hero__dots">
            {slides.map((s, i) => (
              <button key={s.id} type="button" className={'hero__dot' + (i === index ? ' is-active' : '')}
                aria-label={`Show slide ${i + 1}: ${s.title}`} aria-current={i === index} onClick={() => go(i)} />
            ))}
          </div>
        </>
      )}
    </section>
  );
}
