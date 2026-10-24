import type { Order, OrderStatus, ReplyBy, Vehicle } from '../api/types';

export type { Order, OrderStatus };

export type ProductStatus = 'DRAFT' | 'LIVE';
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
}

export interface Fitment {
  vehicleId: string;
  vehicle: string;
  engine: string;
  engineCode: string | null;
  year: number | null;
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
  vehicles: Fitment[];
  addedOn: string;
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
  status: ProductStatus;
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
  lookupSpend: number;
  lookupBudget: number;
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
  lookupCost: number;
  lookupBudget: number;
  buyingTeamEmail: string;
}

export interface SettingsView {
  values: SettingsValues;
  staffName: string;
  staffEmail: string;
  sessionHours: number;
  demoVehicles: number;
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

export interface CategoryOption {
  id: string;
  name: string;
}
