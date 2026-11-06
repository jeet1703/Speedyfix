import type { Review } from '../api/types';
import { SectionHeading } from './SectionHeading';

/** Shows verified reviews from the API; nothing until a review platform is connected and has reviews. */
export function Reviews({ reviews }: { reviews: Review[] }) {
  if (reviews.length === 0) return null;
  const items = reviews;
  return (
    <section className="section reviews" aria-labelledby="reviews-title">
      <div className="container">
        <SectionHeading id="reviews-title" title="What Our Customers Say" />
        <ul className="reviews__list">
          {items.map((r, i) => (
            <li key={i} className="review">
              <span className="review__mark" aria-hidden="true">“</span>
              <p className="review__text">{r.text}</p>
              <p className="review__who"><strong>{r.author}</strong> {r.location}{r.rating ? `, ${r.rating} out of 5` : ''}</p>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
