import { Link } from 'react-router-dom';
import type { Site } from '../api/types';

/** The homepage's "About us, in short": who the shop is, four facts, and a link to the full About page. */
export function AboutShort({ site }: { site: Site }) {
  const town = site.shipsFrom.replace(/, UK$/, '');
  return (
    <section className="section home-about" aria-labelledby="home-about-title">
      <div className="container home-about__inner">
        <div className="home-about__copy">
          <p className="eyebrow">About {site.name}</p>
          <h2 id="home-about-title" className="h-display">Engines, Gearboxes and Parts, Checked Before They Ship</h2>
          <p>
            We supply used, remanufactured and new car parts from our unit in {town}: engines, gearboxes, turbos,
            injectors, ECUs, body panels and more, for garages and drivers across the UK. Every listing shows the part you’ll
            receive, and we’ll check it fits your car before you buy.
          </p>
          <Link className="btn btn--outline" to="/about">More About Us</Link>
        </div>
        <dl className="home-about__facts">
          <div><dt>Warranty</dt><dd>{site.warranty}</dd><span>on every part, from delivery</span></div>
          <div><dt>Ships from</dt><dd>{town}</dd><span>securely packed and tracked</span></div>
          <div><dt>Fit check</dt><dd>By reg</dd><span>or send us your VIN</span></div>
          <div><dt>Pay by</dt><dd>PayPal</dd><span>or debit or credit card</span></div>
        </dl>
      </div>
    </section>
  );
}
