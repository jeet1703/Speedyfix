import { useCallback, useEffect, useState } from 'react';
import { api } from '../api/client';
import type { HomeData } from '../api/types';
import { AboutShort } from '../components/AboutShort';
import { CategoryBanners } from '../components/CategoryBanners';
import { Features } from '../components/Features';
import { HeroSlider } from '../components/HeroSlider';
import { Layout } from '../components/Layout';
import { PlateFinder } from '../components/PlateFinder';
import { ProductCarousel } from '../components/ProductCarousel';
import { Reviews } from '../components/Reviews';
import { HomeSkeleton } from '../components/Skeleton';
import { useStore } from '../state/StoreContext';

export function HomePage() {
  const { vehicle } = useStore();
  const [data, setData] = useState<HomeData | null>(null);
  const [error, setError] = useState('');

  const load = useCallback((vehicleId?: string | null) => {
    api.home(vehicleId)
      .then((d) => { setData(d); setError(''); })
      .catch((e: Error) => setError(e.message));
  }, []);

  // Reload whenever the saved vehicle changes so every card shows the right fit.
  useEffect(() => load(vehicle?.id), [vehicle?.id, load]);

  return (
    <Layout>
      {error && !data && (
        <div className="state">
          <h1 className="h-display">The Homepage Didn’t Load</h1>
          <p>{error}</p>
          <button type="button" className="btn btn--primary" onClick={() => load(vehicle?.id)}>Try Again</button>
        </div>
      )}
      {!data && !error && <HomeSkeleton />}
      {data && (
        <>
          <HeroSlider slides={data.slides} />
          <PlateFinder />
          <Features items={data.features} />
          <AboutShort site={data.site} />
          <CategoryBanners categories={data.popularCategories} />
          <ProductCarousel id="best-selling" title="Best Selling Products" products={data.bestSelling} />
          <ProductCarousel id="latest" title="Latest Products" products={data.latest} />
          <Reviews reviews={data.reviews} />
        </>
      )}
    </Layout>
  );
}
