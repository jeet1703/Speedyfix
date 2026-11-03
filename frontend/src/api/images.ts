/**
 * The shop's own stock photos (slides, category tiles and fallbacks) live in public/img/stock as WebP, named
 * "<photo>-<width>x<height>.webp". The server sends them as "/img/stock/…"; these helpers add the site's base path
 * (/Speedyfix/ on GitHub Pages) and offer the smaller copies to phones. Any other address is left as it is.
 */
const STOCK = /^\/img\/stock\/(.+)-(\d+)x(\d+)\.webp$/;

/** The smaller copies kept for each full size. */
const SIZES: Record<string, string[]> = {
  '1920x900': ['828x900', '1280x600', '1920x900'],
  '900x1140': ['450x570', '900x1140']
};

/** The address to load: our own stock photos get the site's base path. */
export function siteImage(url: string): string {
  return STOCK.test(url) ? import.meta.env.BASE_URL + url.slice(1) : url;
}

/** A srcset of the stock photo's copies, or undefined for any other image. */
export function siteSrcSet(url: string): string | undefined {
  const m = STOCK.exec(url);
  const sizes = m && SIZES[`${m[2]}x${m[3]}`];
  if (!m || !sizes) return undefined;
  return sizes.map((s) => `${import.meta.env.BASE_URL}img/stock/${m[1]}-${s}.webp ${s.split('x')[0]}w`).join(', ');
}
