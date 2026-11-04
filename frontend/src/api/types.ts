/** LIKELY_FITS: not confirmed, but the listing names the vehicle's make and model, with matching years and engine size. */
export type FitStatus = 'FITS' | 'LIKELY_FITS' | 'DOES_NOT_FIT' | 'UNCONFIRMED' | 'NO_VEHICLE';

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
  /** During a sale: the price before it (shown struck through), and the percentage off. price is the sale price. */
  originalPrice: number | null;
  salePercent: number | null;
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
  /** imageUrl: the category's photo for the hover preview; null for links that aren't a category. */
  links: { label: string; href: string; imageUrl: string | null }[];
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
  /** The delivery partners, as a phrase: "FedEx, UPS, Royal Mail, DHL and DPD". */
  couriers: string;
  returns: string;
  vatNumber: string;
  /** Companies House registration number. */
  companyNumber: string;
}

export interface SiteData {
  site: Site;
  menu: MenuGroup[];
  /** Google's OAuth client id for Sign in with Google; empty until it's set up on the server. */
  googleClientId: string;
  /** True until DVSA is connected, while only the sample plates resolve. */
  demoPlates: boolean;
  /** PayPal's public client id for its buttons; empty until PayPal is set up. */
  paypalClientId: string;
  /** How long an unpaid order keeps its parts, in minutes (0: no limit). */
  unpaidHoldMinutes: number;
  /** True when the help chat can answer with AI (Gemini); otherwise it answers by itself. */
  chatAi: boolean;
  /** True when FedEx prices parcel delivery from the postcode at checkout; otherwise the flat prices apply. */
  deliveryQuotes: boolean;
  /** True when shoppers can also sign up with an email address and password (off until the shop's email is set up). */
  emailSignIn: boolean;
  /** True when a paid order emails the buyer their VAT invoice. */
  orderEmails: boolean;
  /** True when only signed-in shoppers can check out. */
  signInToBuy: boolean;
}

/** An AI answer in the help chat, with the parts and vehicle its lookups found. */
export interface ChatReply {
  text: string;
  parts: Product[];
  vehicle: Vehicle | null;
}

export type ShopSort = 'best-selling' | 'newest' | 'price-low' | 'price-high';

export interface Facet {
  value: string;
  count: number;
}

export interface ShopResult {
  items: Product[];
  total: number;
  page: number;
  pageSize: number;
  pageCount: number;
  categories: { id: string; name: string; count: number }[];
  brands: Facet[];
  subTypes: Facet[];
  refinements: Facet[];
}

export interface ProductDetail {
  product: Product;
  partNumber: string | null;
  engineCode: string | null;
  brand: string | null;
  subType: string | null;
  priceExVat: number;
  description: string;
  fitsVehicles: string[];
  related: Product[];
  /** Every photo of the listing, the main one first. */
  photos: { url: string; thumbnailUrl: string }[];
  /** eBay's item specifics, like "Manufacturer Part Number". */
  specifics: { name: string; value: string }[];
  /** The description staff wrote as HTML, or null. Show it through ListingHtml, which cleans it. */
  descriptionHtml: string | null;
}

export interface OrderForm {
  name: string;
  email: string;
  phone: string;
  line1: string;
  line2: string;
  town: string;
  postcode: string;
  /** ISO country code, like GB or IN. */
  country: string;
  notes: string;
}

export interface Order {
  reference: string;
  placedAt: string;
  status: OrderStatus;
  customer: { name: string; email: string; phone: string };
  /** postcode can be null abroad; country is an ISO code (GB for the UK). */
  address: { line1: string; line2: string | null; town: string; postcode: string | null; country: string };
  notes: string | null;
  lines: BasketLine[];
  delivery: Basket['delivery'];
  subtotal: number;
  total: number;
  vatIncluded: number;
  dispatch: { courier: string; trackingNumber: string | null; dispatchedAt: string } | null;
  /** A PayPal payment: captureId and paidAt are set once the money is taken. Null for orders paid by hand. */
  payment: { paypalOrderId: string; captureId: string | null; paidAt: string | null } | null;
}

export type OrderStatus = 'AWAITING_PAYMENT' | 'AWAITING_DISPATCH' | 'DISPATCHED' | 'CANCELLED';

export interface Customer {
  id: string;
  email: string;
  name: string;
  pictureUrl: string | null;
  joinedAt: string;
  /** How the shopper signs in: Google, or an email address and password. */
  signInMethod: 'GOOGLE' | 'EMAIL';
}

export interface AccountSession {
  token: string;
  expiresAt: string;
  customer: Customer;
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
  delivery: Delivery;
  total: number;
  vatIncluded: number;
}

/**
 * QUOTE: FedEx prices it at checkout, once the postcode is in. FEDEX: a FedEx service chosen at checkout.
 * MANUAL: the shopper pays for the parts only and contacts us afterwards for a quote from another courier.
 */
export interface Delivery {
  method: 'NONE' | 'STANDARD' | 'PALLET' | 'FEDEX' | 'QUOTE' | 'MANUAL';
  label: string | null;
  price: number;
}

/** A delivery choice at checkout; send its id with the order. arrives is a date (yyyy-MM-dd), or null. */
export interface DeliveryOption {
  id: string;
  method: Delivery['method'];
  label: string;
  price: number;
  arrives: string | null;
}

export interface LookupResult {
  vehicle: Vehicle;
  fittingParts: number;
}
