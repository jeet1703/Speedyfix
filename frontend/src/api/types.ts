export type FitStatus = 'FITS' | 'DOES_NOT_FIT' | 'UNCONFIRMED' | 'NO_VEHICLE';

export interface Vehicle {
  id: string;
  registration: string | null;
  make: string;
  model: string;
  engine: string;
  engineCode: string | null;
  year: number | null;
}

export interface Product {
  id: string;
  name: string;
  categoryId: string;
  categoryName: string;
  condition: string;
  grade: string | null;
  price: number;
  reference: string | null;
  imageUrl: string;
  shipping: 'PARCEL' | 'PALLET';
  stock: number;
  inStock: boolean;
  fit: FitStatus;
}

export interface Category {
  id: string;
  name: string;
  imageUrl: string;
  popular: boolean;
  sortOrder: number;
}

export interface Slide {
  id: string;
  kicker: string;
  title: string;
  text: string;
  imageUrl: string;
  primaryLabel: string;
  primaryHref: string;
  secondaryLabel: string;
  secondaryHref: string;
}

export interface Feature {
  icon: 'wrench' | 'truck' | 'shield' | string;
  title: string;
  detail: string;
}

export interface MenuGroup {
  title: string;
  links: { label: string; href: string }[];
}

export interface Review {
  author: string;
  location: string;
  rating: number;
  text: string;
}

export interface Site {
  name: string;
  tagline: string;
  phone: string;
  whatsapp: string;
  email: string;
  companyName: string;
  address: string;
  videoUrl: string;
  warranty: string;
  shipsFrom: string;
}

export interface SiteData {
  site: Site;
  menu: MenuGroup[];
}

export type ShopSort = 'best-selling' | 'newest' | 'price-low' | 'price-high';

export interface ShopResult {
  items: Product[];
  total: number;
  page: number;
  pageSize: number;
  pageCount: number;
  categories: { id: string; name: string; count: number }[];
}

export interface ProductDetail {
  product: Product;
  oeNumber: string | null;
  engineCode: string | null;
  priceExVat: number;
  description: string;
  fitsVehicles: string[];
  related: Product[];
}

export interface OrderForm {
  name: string;
  email: string;
  phone: string;
  line1: string;
  line2: string;
  town: string;
  postcode: string;
  notes: string;
}

export interface Order {
  reference: string;
  placedAt: string;
  status: 'AWAITING_PAYMENT';
  customer: { name: string; email: string; phone: string };
  address: { line1: string; line2: string | null; town: string; postcode: string };
  notes: string | null;
  lines: BasketLine[];
  delivery: Basket['delivery'];
  subtotal: number;
  total: number;
  vatIncluded: number;
}

export type ReplyBy = 'WHATSAPP' | 'PHONE' | 'EMAIL';

export interface PartRequestReceived {
  reference: string;
  vehicle: string | null;
  replyBy: ReplyBy;
}

export interface HomeData {
  site: Site;
  menu: MenuGroup[];
  slides: Slide[];
  features: Feature[];
  popularCategories: Category[];
  bestSelling: Product[];
  latest: Product[];
  reviews: Review[];
  vehicle: Vehicle | null;
}

export interface BasketLine {
  productId: string;
  name: string;
  reference: string | null;
  imageUrl: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
  stock: number;
}

export interface Basket {
  id: string;
  lines: BasketLine[];
  itemCount: number;
  subtotal: number;
  delivery: { method: 'NONE' | 'STANDARD' | 'PALLET'; label: string | null; price: number };
  total: number;
  vatIncluded: number;
}

export interface LookupResult {
  vehicle: Vehicle;
  fittingParts: number;
}
