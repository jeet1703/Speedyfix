import { useEffect, useRef, useState } from 'react';
import type { Product } from '../api/types';
import { Icon } from './Icons';
import { ProductCard } from './ProductCard';
import { SectionHeading } from './SectionHeading';

export function ProductCarousel({ id, title, products }: { id: string; title: string; products: Product[] }) {
  const track = useRef<HTMLUListElement>(null);
  const [edges, setEdges] = useState({ start: true, end: false });

  const update = () => {
    const el = track.current;
    if (!el) return;
    setEdges({ start: el.scrollLeft <= 2, end: el.scrollLeft + el.clientWidth >= el.scrollWidth - 2 });
  };

  useEffect(() => {
    update();
    window.addEventListener('resize', update);
    return () => window.removeEventListener('resize', update);
  }, [products]);

  const scroll = (dir: 1 | -1) => track.current?.scrollBy({ left: dir * track.current.clientWidth * 0.9, behavior: 'smooth' });

  return (
    <section className="section" aria-labelledby={id}>
      <div className="container">
        <SectionHeading id={id} title={title} />
        <div className="carousel">
          <button type="button" className="carousel__arrow" aria-label={`Previous ${title.toLowerCase()}`} onClick={() => scroll(-1)} disabled={edges.start}>
            <Icon name="chevronLeft" />
          </button>
          <ul className="carousel__track" ref={track} onScroll={update}>
            {products.map((p) => <li key={p.id}><ProductCard product={p} /></li>)}
          </ul>
          <button type="button" className="carousel__arrow" aria-label={`Next ${title.toLowerCase()}`} onClick={() => scroll(1)} disabled={edges.end}>
            <Icon name="chevronRight" />
          </button>
        </div>
      </div>
    </section>
  );
}
