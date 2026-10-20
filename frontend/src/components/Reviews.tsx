import type { Review } from '../api/types';
import { SectionHeading } from './SectionHeading';

/** Shows verified reviews from the API. Until a review platform is connected, shows clearly marked placeholders. */
export function Reviews({ reviews }: { reviews: Review[] }) {
  const placeholder = reviews.length === 0;
  const items = placeholder
    ? Array.from({ length: 3 }, () => ({ author: '[Customer name]', location: '[Town]', rating: 0, text: '[Verified review text from the review platform]' }))
    : reviews;
  return (
    <section className="section reviews" aria-labelledby="reviews-title">
      <div className="container">
        <SectionHeading id="reviews-title" title="What our customers say">
          {placeholder && <p className="section-heading__note">Placeholders: connect a review platform to show real, verified reviews here.</p>}
        </SectionHeading>
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
