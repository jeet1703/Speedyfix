import { Link } from 'react-router-dom';
import { siteImage, siteSrcSet } from '../api/images';
import type { Category } from '../api/types';
import { SectionHeading } from './SectionHeading';

export function CategoryBanners({ categories }: { categories: Category[] }) {
  return (
    <section className="section" aria-labelledby="categories-title">
      <div className="container">
        <SectionHeading id="categories-title" title="Shop Popular Categories" />
        <ul className="banners">
          {categories.map((c) => (
            <li key={c.id}>
              <Link className="banner" to={`/products?category=${c.id}`}>
                <img src={siteImage(c.imageUrl)} srcSet={siteSrcSet(c.imageUrl)} sizes="(max-width: 700px) 50vw, 300px" alt="" loading="lazy" />
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
