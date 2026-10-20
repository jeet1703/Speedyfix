import { Link } from 'react-router-dom';
import type { Category } from '../api/types';
import { SectionHeading } from './SectionHeading';

export function CategoryBanners({ categories }: { categories: Category[] }) {
  return (
    <section className="section" aria-labelledby="categories-title">
      <div className="container">
        <SectionHeading id="categories-title" title="Shop popular categories" />
        <ul className="banners">
          {categories.map((c) => (
            <li key={c.id}>
              <Link className="banner" to={`/products?category=${c.id}`}>
                <img src={c.imageUrl} alt="" loading="lazy" />
                <span className="banner__text">
                  <strong>{c.name}</strong>
                  <span>View more</span>
                </span>
              </Link>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
