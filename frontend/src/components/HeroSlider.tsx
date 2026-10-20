import { useCallback, useEffect, useState } from 'react';
import type { Slide } from '../api/types';
import { Icon } from './Icons';

const INTERVAL = 7000;

export function HeroSlider({ slides }: { slides: Slide[] }) {
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  const count = slides.length;
  const go = useCallback((i: number) => setIndex(((i % count) + count) % count), [count]);

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
          <img className="hero__img" src={s.imageUrl} alt="" loading={i === 0 ? 'eager' : 'lazy'} />
          <div className="container hero__content">
            <p className="hero__kicker">{s.kicker}</p>
            <h1 className="hero__title">{s.title}</h1>
            <p className="hero__text">{s.text}</p>
            <div className="hero__actions">
              <a className="btn btn--primary" href={s.primaryHref} tabIndex={i === index ? 0 : -1}>{s.primaryLabel}</a>
              <a className="btn btn--outline-light" href={s.secondaryHref} tabIndex={i === index ? 0 : -1}>{s.secondaryLabel}</a>
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
