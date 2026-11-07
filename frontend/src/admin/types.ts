import type { Order, OrderStatus, ReplyBy, Vehicle } from '../api/types';

export type { Order, OrderStatus };

export type ProductStatus = 'DRAFT' | 'LIVE' | 'ENDED';
export type WorkStatus = 'NEEDS_FITMENT' | 'MISSING_PHOTO' | 'DRAFT' | 'LIVE';
export type Condition = 'USED' | 'REMANUFACTURED' | 'NEW';
export type Shipping = 'PARCEL' | 'PALLET';
export type Confidence = 'HIGH' | 'MEDIUM';

export type StaffRole = 'MASTER';

export interface StaffSession {
  email: string;
  name: string;
  role: StaffRole;
  expiresAt: string;
}

export interface AdminSession extends StaffSession {
  token: string;
}

export interface ProductRow {
  id: string;
  name: string;
  categoryName: string;
  partNumber: string | null;
  engineCode: string | null;
  brand: string | null;
  subType: string | null;
  price: number | null;
  stock: number;
  imageUrl: string | null;
  hasPhoto: boolean;
  status: ProductStatus;
  workStatus: WorkStatus;
  vehicles: number;
  addedOn: string;
  /** Percentage off during a sale, or null; sellingPrice is the price after it. */
  salePercent: number | null;
  sellingPrice: number | null;
}

export interface Fitment {
  vehicleId: string;
  vehicle: string;
  engine: string;
  engineCode: string | null;
  year: number | null;
  /** Set for a row picked from eBay's vehicle list: year is then its first year, yearTo its last. */
  yearTo: number | null;
  source: string;
  confidence: Confidence;
}

export interface ProductDetail {
  id: string;
  name: string;
  categoryId: string;
  condition: Condition;
  grade: string | null;
  price: number | null;
  partNumber: string | null;
  engineCode: string | null;
  brand: string | null;
  subType: string | null;
  refinements: string[];
  shipping: Shipping;
  stock: number;
  bestSeller: boolean;
  status: ProductStatus;
  workStatus: WorkStatus;
  imageUrl: string | null;
  /** imageUrl is the demo catalogue's stock photo, not one staff uploaded. */
  samplePhoto: boolean;
  photos: { id: string; url: string }[];
  /** Photos the part came with from its eBay listing, shown after the uploaded ones. */
  importedPhotos: { id: string; url: string }[];
  vehicles: Fitment[];
  addedOn: string;
  descriptionHtml: string | null;
  salePercent: number | null;
  sellingPrice: number | null;
  /** The packed weight staff set for FedEx's prices, or null to use the category's typical one (defaultWeightKg). */
  weightKg: number | null;
  defaultWeightKg: number;
}

export interface ProductFormData {
  name: string;
  categoryId: string;
  condition: Condition;
  grade: string;
  price: number | null;
  partNumber: string;
  engineCode: string;
  brand: string;
  subType: string;
  refinements: string[];
  shipping: Shipping;
  stock: number;
  bestSeller: boolean;
  vehicleIds: string[];
  keepPhotoIds: string[];
  /** Photos from the part's eBay listing to take off. */
  removePhotoIds: string[];
  status: ProductStatus;
  /** Blank removes the description. */
  descriptionHtml: string;
  /** Percentage off the price, or null for no sale. */
  salePercent: number | null;
  /** Packed weight in kg for FedEx's delivery prices; null uses the category's typical weight. */
  weightKg: number | null;
}

export interface Dashboard {
  tasks: {
    awaitingPayment: number;
    awaitingDispatch: number;
    newPartRequests: number;
    needFitment: number;
    missingPhotos: number;
    outOfStock: number;
  };
  /** change: % up or down on the period before; null when that period took nothing. */
  sales: { period: string; orders: number; total: number; change: number | null }[];
  /** The last 31 UK days, oldest first; date is YYYY-MM-DD. */
  dailySales: { date: string; orders: number; total: number }[];
  orders: { all: number; awaitingPayment: number; awaitingDispatch: number; dispatched: number; cancelled: number };
  customers: { total: number; newThisWeek: number };
  listings: { active: number; drafts: number; outOfStock: number };
  needFitment: number;
  missingPhotos: number;
  noMatchSearches: number;
  lookupsThisMonth: number;
  buyingTeamEmail: string;
  workQueue: ProductRow[];
  /** vehicle is null for plates the data source didn't recognise. */
  noMatch: { vehicle: string | null; searches: number }[];
}

export interface PlateCheck {
  vehicle: Vehicle;
  fitment: Fitment;
  confirmed: ProductRow[];
  sameEngineCode: ProductRow[];
}

export type PartRequestStatus = 'NEW' | 'QUOTED' | 'CLOSED';

export interface PartRequest {
  reference: string;
  receivedAt: string;
  maskedRegistration: string;
  vehicle: string | null;
  part: string;
  notes: string | null;
  name: string;
  phone: string | null;
  email: string | null;
  replyBy: ReplyBy;
  status: PartRequestStatus;
  photoCount: number;
}

export interface SettingsValues {
  buyingTeamEmail: string;
}

export interface SettingsView {
  values: SettingsValues;
  staffName: string;
  staffEmail: string;
  sessionHours: number;
  demoVehicles: number;
  demoPlates: boolean;
}

export interface CustomerRow {
  id: string;
  name: string;
  email: string;
  pictureUrl: string | null;
  joinedAt: string;
  lastSignInAt: string;
  signIns: number;
  recentSearches: string[];
}

/** An engine from eBay's vehicle list: its eBay name, and what staff read ("1.5L diesel, 110HP"). */
export interface EngineChoice {
  name: string;
  label: string;
  fuel: string | null;
  engineCode: string | null;
}

/** A category to list a part in, with its group in the shop's Products menu. */
export interface CategoryOption {
  id: string;
  name: string;
  group: string;
}

/** An order's VAT invoice (null until paid) and the emails it has sent or tried to send. */
export interface InvoiceStatus {
  invoiceNumber: string | null;
  issuedAt: string | null;
  emailOn: boolean;
  emails: {
    id: number;
    kind: 'CUSTOMER_INVOICE' | 'STAFF_NEW_ORDER' | 'CUSTOMER_CANCELLED' | 'STAFF_PAYMENT_CHECK';
    recipient: string;
    status: 'PENDING' | 'SENT' | 'FAILED';
    attempts: number;
    lastError: string | null;
    createdAt: string;
    nextAttemptAt: string;
    sentAt: string | null;
    details: string | null;
  }[];
}


/** The eBay sync: whether it can run on this server, whether it's running, and the last result since start-up. */
export interface EbaySyncStatus {
  available: boolean;
  running: boolean;
  startedAt: string | null;
  last: {
    at: string;
    listingsOnEbay: number;
    added: number;
    repriced: number;
    ended: number;
    relisted: number;
    endingSkipped: boolean;
    problems: string[];
  } | null;
  lastError: string | null;
}
