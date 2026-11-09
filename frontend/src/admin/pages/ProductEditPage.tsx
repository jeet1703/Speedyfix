import { useEffect, useRef, useState, type DragEvent } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { Icon } from '../../components/Icons';
import { adminApi, errorMessage } from '../adminApi';
import { AdminLayout } from '../AdminLayout';
import { ConfidencePill } from '../components';
import { EbayPaste } from '../EbayPaste';
import { VehiclePicker } from '../VehiclePicker';
import type { CategoryOption, Condition, Fitment, ProductDetail, ProductStatus, Shipping } from '../types';
import { CardsSkeleton } from '../../components/Skeleton';
import { ListingHtml } from '../../components/ListingHtml';
import { buildDescription, EMPTY_FIELDS, madeFromFields, readFields, type DescriptionFields, type ListingFields } from '../listingDescription';

const CONDITIONS: { value: Condition; label: string }[] = [
  { value: 'USED', label: 'Used, Tested' },
  { value: 'REMANUFACTURED', label: 'Remanufactured' },
  { value: 'NEW', label: 'New' }
];
const GRADES = [
  { value: '', label: 'No Grade' },
  { value: 'A', label: 'A' },
  { value: 'B', label: 'B' },
  { value: 'C', label: 'C' }
];
const SHIPPING: { value: Shipping; label: string }[] = [
  { value: 'PARCEL', label: 'Tracked Parcel' },
  { value: 'PALLET', label: 'Pallet (Engines, Gearboxes)' }
];
/** As many as eBay allows on a listing (the server's limit too). */
const MAX_PHOTOS = 24;
/** The Source of rows Gemini suggested (AdminVehicleController); they're added unticked. */
const AI_SOURCE = 'AI suggestion';

/** The server's limit on vehicles per part (ProductForm.MAX_VEHICLES). */
const MAX_VEHICLES = 500;
const MAX_REFINEMENTS = 5;
/** The brand list's "Add a new brand…" choice. */
const NEW_BRAND = '__new-brand__';
const PHOTO_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
/** Photos are turned into WebP at most this many pixels on the longest side before upload, so many fit in one save. */
const PHOTO_MAX_SIDE = 1600;
const gbp = new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP' });

/** Digits, at most one decimal point and two decimals (pounds and pence, or kilograms like 7.25). */
const decimalInput = (value: string) => value.replace(/[^\d.]/g, '').replace(/(\..*)\./g, '$1').replace(/(\.\d{2})\d+/, '$1');

/** The description's own lines, in the template's order: field, label, example. */
const DESCRIPTION_INPUTS: [Exclude<keyof DescriptionFields, 'notes'>, string, string][] = [
  ['part', 'Part', 'e.g. Alternator Generator'],
  ['colour', 'Colour', 'e.g. Black'],
  ['year', 'Year', 'e.g. 2014-2019'],
  ['miles', 'Miles', 'e.g. 49K Miles'],
  ['make', 'Make', 'e.g. Land Rover'],
  ['model', 'Model', 'e.g. Discovery Sport'],
  ['yearRange', 'Year range', 'e.g. 2014-2019'],
  ['engine', 'Engine type', 'e.g. 2.0L Diesel']
];

const listingFields = (title: string, brand: string, partNumber: string, condition: Condition): ListingFields => ({
  title, brand, partNumber, condition: CONDITIONS.find((c) => c.value === condition)?.label.replace(',', '') ?? ''
});

/** Encodes the canvas, or null when the browser can't make that type (older Safari gives a PNG instead of WebP). */
const encode = (canvas: HTMLCanvasElement, type: string, quality: number) =>
  new Promise<Blob | null>((resolve) => canvas.toBlob((b) => resolve(b && b.type === type ? b : null), type, quality));

/**
 * Every photo as WebP, at most PHOTO_MAX_SIDE pixels across: a 4–8 MB phone photo usually comes out under 400 KB,
 * and WebP loads faster for shoppers. Where the browser can't make WebP, a JPEG instead. A photo that's already a
 * smaller WebP, or one the browser can't redraw, is sent as it is.
 */
async function shrink(file: File): Promise<File> {
  try {
    const bitmap = await createImageBitmap(file);
    const scale = Math.min(1, PHOTO_MAX_SIDE / Math.max(bitmap.width, bitmap.height));
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(bitmap.width * scale);
    canvas.height = Math.round(bitmap.height * scale);
    canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    bitmap.close();
    const webp = await encode(canvas, 'image/webp', 0.82);
    const blob = webp ?? await encode(canvas, 'image/jpeg', 0.85);
    if (!blob || (file.type === blob.type && blob.size >= file.size)) return file;
    return new File([blob], file.name.replace(/\.\w+$/, '') + (webp ? '.webp' : '.jpg'), { type: blob.type });
  } catch {
    return file;
  }
}

interface FitRow {
  fit: Fitment;
  ticked: boolean;
}

interface NewPhoto {
  file: File;
  url: string;
}

/** Create listing (/admin/products/new) and Edit listing (/admin/products/:id). Remounted per id (see AdminApp). */
export function ProductEditPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const savedNotice = (useLocation().state as { saved?: string } | null)?.saved ?? '';

  const [detail, setDetail] = useState<ProductDetail | null>(null);
  const [loadError, setLoadError] = useState('');
  const [categories, setCategories] = useState<CategoryOption[]>([]);

  const [partNumber, setPartNumber] = useState('');
  const [rows, setRows] = useState<FitRow[]>([]);
  const [fitNote, setFitNote] = useState('');
  const [fitError, setFitError] = useState('');
  const [suggesting, setSuggesting] = useState(false);
  // AI suggestions that didn't match a vehicle in the list, shown so staff can add them by hand.
  const [aiMissed, setAiMissed] = useState<string[]>([]);

  const [name, setName] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [condition, setCondition] = useState<Condition>('USED');
  const [grade, setGrade] = useState('A');
  const [price, setPrice] = useState('');
  const [sale, setSale] = useState('');
  const [weight, setWeight] = useState('');
  const [typicalWeight, setTypicalWeight] = useState<number | null>(null);
  const [stock, setStock] = useState('1');
  const [shipping, setShipping] = useState<Shipping>('PARCEL');
  const [engineCode, setEngineCode] = useState('');
  const [brand, setBrand] = useState('');
  const [brands, setBrands] = useState<string[]>([]);
  // Typing a brand that isn't in the list yet, instead of picking one.
  const [newBrand, setNewBrand] = useState(false);
  const [subType, setSubType] = useState('');
  const [refinements, setRefinements] = useState<string[]>([]);
  const [descriptionHtml, setDescriptionHtml] = useState('');
  const [descFields, setDescFields] = useState<DescriptionFields>(EMPTY_FIELDS);
  // The saved description as built from what the listing had when it loaded (see descriptionToSave).
  const [loadedBuild, setLoadedBuild] = useState('');
  const [newRefinement, setNewRefinement] = useState<string | null>(null);
  const [bestSeller, setBestSeller] = useState(false);

  const [kept, setKept] = useState<{ id: string; url: string }[]>([]);
  // Photos from the part's eBay listing that are staying; any removed are sent as removePhotoIds.
  const [imported, setImported] = useState<{ id: string; url: string }[]>([]);
  // The saved photos' order (uploaded and from eBay together), and whether staff changed it.
  const [photoOrder, setPhotoOrder] = useState<string[]>([]);
  const [orderChanged, setOrderChanged] = useState(false);
  const [added, setAdded] = useState<NewPhoto[]>([]);
  const [photoError, setPhotoError] = useState('');
  const [dragging, setDragging] = useState(false);

  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState('');
  const [notice, setNotice] = useState(savedNotice);
  const errorRef = useRef<HTMLParagraphElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  // Object URLs for new photos are released when they're removed, saved, or the page closes.
  const addedRef = useRef(added);
  addedRef.current = added;
  useEffect(() => () => addedRef.current.forEach((p) => URL.revokeObjectURL(p.url)), []);

  useEffect(() => {
    adminApi.categories().then((c) => {
      setCategories(c);
    }).catch(() => setLoadError('The categories didn’t load. Reload the page.'));
    adminApi.brands().then(setBrands).catch(() => setNewBrand(true));
    if (id) {
      adminApi.product(id).then(fill).catch((err) => setLoadError(errorMessage(err, 'This part didn’t load. Try again.')));
    }
  }, [id]);

  useEffect(() => {
    if (saveError) errorRef.current?.focus();
  }, [saveError]);

  function fill(d: ProductDetail) {
    setDetail(d);
    setPartNumber(d.partNumber ?? '');
    setBrand(d.brand ?? '');
    setSubType(d.subType ?? '');
    setRefinements(d.refinements);
    setDescriptionHtml(d.descriptionHtml ?? '');
    const read = readFields(d.descriptionHtml);
    setDescFields(read);
    setLoadedBuild(buildDescription(listingFields(d.name, d.brand ?? '', d.partNumber ?? '', d.condition), read));
    setRows(d.vehicles.map((fit) => ({ fit, ticked: true })));
    setName(d.name);
    setCategoryId(d.categoryId);
    setCondition(d.condition);
    setGrade(d.grade ?? '');
    setPrice(d.price === null ? '' : d.price.toFixed(2));
    setSale(d.salePercent ? String(d.salePercent) : '');
    setWeight(d.weightKg != null ? String(d.weightKg) : '');
    setTypicalWeight(d.defaultWeightKg);
    setStock(String(d.stock));
    setShipping(d.shipping);
    setEngineCode(d.engineCode ?? '');
    setBestSeller(d.bestSeller);
    setKept(d.photos);
    setImported(d.importedPhotos ?? []);
    setPhotoOrder(d.photoOrder ?? [...d.photos, ...(d.importedPhotos ?? [])].map((p) => p.id));
    setOrderChanged(false);
    setAdded((prev) => {
      prev.forEach((p) => URL.revokeObjectURL(p.url));
      return [];
    });
  }

  /** Adds vehicles not already listed. tick decides whether each starts ticked. */
  function merge(found: Fitment[], tick: (f: Fitment) => boolean) {
    setRows((prev) => {
      const have = new Set(prev.map((r) => r.fit.vehicleId));
      return [...prev, ...found.filter((f) => !have.has(f.vehicleId)).map((fit) => ({ fit, ticked: tick(fit) }))];
    });
  }

  /** Gemini's suggestions, matched to the vehicle list on the server; added unticked, since AI can be wrong. */
  async function suggestWithAi() {
    setFitNote('');
    setFitError('');
    setAiMissed([]);
    setSuggesting(true);
    try {
      const d = descFields;
      const notes = [d.part && `Part: ${d.part}`, d.make && `Make: ${d.make}`, d.model && `Model: ${d.model}`,
        d.yearRange && `Years: ${d.yearRange}`, d.engine && `Engine: ${d.engine}`, d.notes].filter(Boolean).join('\n');
      const found = await adminApi.suggestVehicles({
        title: name.trim(), partNumber: partNumber.trim(), engineCode: engineCode.trim(), brand: brand.trim(),
        category: categories.find((c) => c.id === categoryId)?.name ?? '', notes
      });
      const before = new Set(rows.map((r) => r.fit.vehicleId));
      merge(found.vehicles, () => false);
      setAiMissed(found.notMatched);
      const added = found.vehicles.filter((f) => !before.has(f.vehicleId)).length;
      setFitNote(found.vehicles.length
        ? `AI suggested ${found.vehicles.length} ${found.vehicles.length === 1 ? 'vehicle' : 'vehicles'}` +
          (added < found.vehicles.length ? ` (${found.vehicles.length - added} already listed)` : '') +
          '. They’re unticked: check each one, then tick the ones that are right.'
        : 'AI couldn’t suggest any vehicles from these details. Add the part number or engine code and try again.');
    } catch (err) {
      setFitError(errorMessage(err, 'The AI suggestion didn’t work. Try again.'));
    } finally {
      setSuggesting(false);
    }
  }


  function addRefinement() {
    const value = (newRefinement ?? '').trim();
    if (value && !refinements.some((r) => r.toLowerCase() === value.toLowerCase()) && refinements.length < MAX_REFINEMENTS) {
      setRefinements([...refinements, value]);
    }
    setNewRefinement(null);
  }

  async function addFiles(files: File[]) {
    setPhotoError('');
    const images = files.filter((f) => PHOTO_TYPES.includes(f.type));
    if (images.length < files.length) setPhotoError('Photos must be JPG, PNG or WebP images.');
    const room = MAX_PHOTOS - kept.length - imported.length - added.length;
    if (images.length > room) setPhotoError(`A listing can have up to ${MAX_PHOTOS} photos.`);
    const take = await Promise.all(images.slice(0, Math.max(0, room)).map(shrink));
    setAdded((prev) => [...prev, ...take.map((file) => ({ file, url: URL.createObjectURL(file) }))]);
  }

  function removeAdded(photo: NewPhoto) {
    URL.revokeObjectURL(photo.url);
    setAdded((prev) => prev.filter((p) => p !== photo));
  }

  function onDrop(e: DragEvent) {
    e.preventDefault();
    setDragging(false);
    addFiles(Array.from(e.dataTransfer.files));
  }

  const ticked = rows.filter((r) => r.ticked);
  const highTicked = ticked.filter((r) => r.fit.confidence === 'HIGH').length;
  const aiUnticked = rows.filter((r) => !r.ticked && r.fit.source === AI_SOURCE).length;
  const priceValue = price.trim() === '' ? null : Number(price);
  const priceOk = priceValue !== null && Number.isFinite(priceValue) && priceValue > 0;
  const saleValue = sale.trim() === '' ? null : Number(sale);
  const weightValue = weight.trim() === '' ? null : Number(weight);
  const weightOk = weightValue === null || (weightValue >= 0.1 && weightValue < 1000);
  const saleOk = saleValue === null || (Number.isInteger(saleValue) && saleValue >= 1 && saleValue <= 90);
  // Same rounding as the server: to the penny, half a penny up.
  const salePrice = priceOk && saleValue && saleOk ? Math.round(priceValue! * (100 - saleValue)) / 100 : null;
  const photoCount = kept.length + imported.length + added.length;
  // Saved photos as the shop shows them; the first is the main image.
  const at = (photoId: string) => { const i = photoOrder.indexOf(photoId); return i < 0 ? Number.MAX_SAFE_INTEGER : i; };
  const savedPhotos = [...kept.map((p) => ({ ...p, imported: false })), ...imported.map((p) => ({ ...p, imported: true }))]
    .sort((a, b) => at(a.id) - at(b.id));

  /** Moves a saved photo to position `to` (0 = main photo). */
  function movePhoto(photoId: string, to: number) {
    const ids = savedPhotos.map((p) => p.id).filter((x) => x !== photoId);
    ids.splice(Math.max(0, Math.min(to, ids.length)), 0, photoId);
    setPhotoOrder(ids);
    setOrderChanged(true);
  }

  function removeSaved(photo: { id: string; imported: boolean }) {
    if (photo.imported) setImported(imported.filter((x) => x.id !== photo.id));
    else setKept(kept.filter((x) => x.id !== photo.id));
  }
  const usesSample = photoCount === 0 && !!detail?.samplePhoto;
  // A listing whose photo was never downloaded still shows it from its link; "photo coming soon" isn't a photo.
  const usesListingPhoto = photoCount === 0 && !detail?.samplePhoto && !!detail?.imageUrl && !detail.imageUrl.endsWith('/placeholder.svg')
    && !(detail.importedPhotos?.length);
  const photoOk = photoCount > 0 || usesSample || usesListingPhoto;
  const refOk = !!(partNumber.trim() || engineCode.trim());
  const blocker = !photoOk ? 'add a photo first' : !priceOk ? 'add a price first' : !refOk ? 'add a part number first' : '';
  const isLive = detail?.status === 'LIVE';

  const builtDescription = buildDescription(listingFields(name, brand, partNumber, condition), descFields);
  // A description imported from eBay is kept exactly as it was until something it shows changes; then it's rebuilt
  // from the fields like any other.
  const descriptionToSave = !descriptionHtml.trim() || madeFromFields(descriptionHtml) || builtDescription !== loadedBuild
    ? builtDescription : descriptionHtml;
  const setDesc = (key: keyof DescriptionFields) => (e: { target: { value: string } }) => setDescFields((prev) => ({ ...prev, [key]: e.target.value }));

  async function save(status: ProductStatus) {
    setSaveError('');
    setNotice('');
    const stockValue = Number(stock);
    if (!name.trim()) return setSaveError('Enter the title shoppers will see.');
    if (!categoryId) return setSaveError('Choose a category.');
    if (ticked.length > MAX_VEHICLES) return setSaveError(`A part can list up to ${MAX_VEHICLES} vehicles. Untick some, or add fewer years or engines.`);
    if (priceValue !== null && !priceOk) return setSaveError('Enter a price above £0, like 149.00.');
    if (!saleOk) return setSaveError('Enter a sale between 1% and 90% off, or leave it empty for no sale.');
    if (!weightOk) return setSaveError('Enter a weight between 0.1 and 999 kg, or leave it empty for the typical weight.');
    if (stock.trim() === '' || !Number.isInteger(stockValue) || stockValue < 0 || stockValue > 999) return setSaveError('Enter a stock quantity from 0 to 999.');
    setSaving(true);
    try {
      const saved = await adminApi.saveProduct(id ?? null, {
        name: name.trim(), categoryId, condition, grade, price: priceValue, partNumber: partNumber.trim(),
        engineCode: engineCode.trim(), brand: brand.trim(), subType: subType.trim(), refinements, shipping, stock: stockValue, bestSeller,
        vehicleIds: ticked.map((r) => r.fit.vehicleId), keepPhotoIds: kept.map((p) => p.id),
        removePhotoIds: (detail?.importedPhotos ?? []).filter((p) => !imported.some((i) => i.id === p.id)).map((p) => p.id),
        photoOrder: orderChanged ? savedPhotos.map((p) => p.id) : null, status, descriptionHtml: descriptionToSave, salePercent: saleValue, weightKg: weightValue
      }, added.map((p) => p.file));
      const message = status === 'LIVE'
        ? (isLive ? 'Changes saved. The listing is live in the shop.' : 'Listed. The part is now in the shop.')
        : (isLive ? 'Listing ended. The part is saved as a draft.' : 'Saved as a draft. Shoppers can’t see it yet.');
      // Back to the listings, on the tab the part is now in, with a link to open it again.
      const tab = saved.status === 'DRAFT' ? 'drafts' : saved.status === 'ENDED' ? 'ended' : saved.stock > 0 ? '' : 'out-of-stock';
      navigate(`/admin/listings${tab ? `?tab=${tab}` : ''}`, { state: { saved: message, id: saved.id, name: saved.name } });
    } catch (err) {
      setSaveError(errorMessage(err, 'We couldn’t save the listing. Try again.'));
    } finally {
      setSaving(false);
    }
  }

  // Listings brought over from eBay keep the eBay item number at the end of their id (EbayImportService.productId).
  const ebayItem = id?.match(/-(\d{12})$/)?.[1];
  const ebayUrl = ebayItem ? `https://www.ebay.co.uk/itm/${ebayItem}` : null;
  const ebayLink = ebayUrl && <a className="btn btn--outline" href={ebayUrl} target="_blank" rel="noreferrer">Open on eBay</a>;

  const title = id ? (detail ? 'Edit Listing' : 'Loading Listing…') : 'Create Listing';
  const actions = (id && !detail) ? null : isLive ? (
    <>
      {ebayLink}
      <a className="btn btn--outline" href={`${import.meta.env.BASE_URL}products/${encodeURIComponent(id!)}`} target="_blank" rel="noreferrer">View in Shop</a>
      <button type="button" className="btn btn--outline" disabled={saving} onClick={() => void save('DRAFT')}>End Listing</button>
      <button type="button" className="btn btn--primary" disabled={saving || !!blocker} onClick={() => void save('LIVE')}>
        {blocker ? `Save Changes (${blocker})` : saving ? 'Saving…' : 'Save Changes'}
      </button>
    </>
  ) : (
    <>
      {ebayLink}
      <button type="button" className="btn btn--outline" disabled={saving} onClick={() => void save('DRAFT')}>{saving ? 'Saving…' : 'Save Draft'}</button>
      <button type="button" className="btn btn--primary" disabled={saving || !!blocker} onClick={() => void save('LIVE')}>
        {blocker ? `List It (${blocker})` : saving ? 'Listing…' : 'List It'}
      </button>
    </>
  );

  if (loadError && id && !detail) {
    return (
      <AdminLayout title="Edit Listing" crumbs={[{ label: 'Listings', to: '/admin/listings' }, { label: 'Edit Listing' }]}>
        <p className="notice notice--error" role="alert">{loadError}</p>
      </AdminLayout>
    );
  }

  const previewImage = (orderChanged ? savedPhotos[0]?.url : undefined) ?? added[0]?.url ?? savedPhotos[0]?.url ?? (usesSample || usesListingPhoto ? detail?.imageUrl : null);
  const reference = partNumber.trim() ? `Part no. ${partNumber.trim()}` : engineCode.trim() ? `Engine code ${engineCode.trim()}` : '';

  return (
    <AdminLayout title={title} crumbs={[{ label: 'Listings', to: '/admin/listings' }, { label: id ? 'Edit Listing' : 'Create Listing' }]} actions={actions}>
      {id && !detail ? <CardsSkeleton cards={3} /> : (
        <div className="edit-cols">
          <div className="edit-main">
            {notice && <p className="notice notice--ok" role="status">{notice}</p>}
            {saveError && <p ref={errorRef} className="notice notice--error" role="alert" tabIndex={-1}>{saveError}</p>}
            {loadError && <p className="notice notice--error" role="alert">{loadError}</p>}

            <section className="card" aria-labelledby="step-photos">
              <div className="card__head">
                <h2 id="step-photos" className="card__title">1. Photos</h2>
                <p className="admin-muted"><strong>{photoCount}</strong> of {MAX_PHOTOS} photos</p>
              </div>
              {usesSample && <p className="notice">This part is using a sample photo. Upload real photos of this unit to replace it.</p>}
              {usesListingPhoto && (
                <div className="notice">
                  <img src={detail!.imageUrl!} alt="" width={64} height={64} style={{ objectFit: 'cover', borderRadius: 4, float: 'left', marginRight: 12 }} />
                  <p>This listing already has a photo. You can save changes without uploading any; photos you upload here go first.</p>
                </div>
              )}
              <div className="photos">
                {photoCount < MAX_PHOTOS && (
                  <div className={'dropzone' + (dragging ? ' dropzone--over' : '')}
                    onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
                    onDragLeave={() => setDragging(false)} onDrop={onDrop}>
                    <p>Drop photos here or <button type="button" className="link-btn" onClick={() => fileRef.current?.click()}>browse</button></p>
                    <p className="field__hint">Add up to {MAX_PHOTOS} real photos of this unit, at least one. JPG, PNG or WebP; each is turned into a WebP for the shop.</p>
                    <input ref={fileRef} type="file" accept={PHOTO_TYPES.join(',')} multiple hidden
                      onChange={(e) => { addFiles(Array.from(e.target.files ?? [])); e.target.value = ''; }} />
                  </div>
                )}
                {savedPhotos.map((p, i) => (
                  <figure key={p.id} className="photo-tile">
                    <img src={p.url} alt={`Photo ${i + 1}`} />
                    <figcaption>{i === 0 ? 'Main photo' : `Photo ${i + 1}`}</figcaption>
                    <button type="button" className="photo-tile__remove" aria-label={`Remove photo ${i + 1}`} onClick={() => removeSaved(p)}><Icon name="x" size={16} /></button>
                    <div className="photo-tile__move">
                      <button type="button" disabled={i === 0} aria-label={`Move photo ${i + 1} earlier`} onClick={() => movePhoto(p.id, i - 1)}>←</button>
                      {i > 0 && <button type="button" onClick={() => movePhoto(p.id, 0)}>Make Main</button>}
                      <button type="button" disabled={i === savedPhotos.length - 1} aria-label={`Move photo ${i + 1} later`} onClick={() => movePhoto(p.id, i + 1)}>→</button>
                    </div>
                  </figure>
                ))}
                {added.map((p, i) => (
                  <figure key={p.url} className="photo-tile">
                    <img src={p.url} alt={`New photo ${i + 1}`} />
                    <figcaption>Not saved yet</figcaption>
                    <button type="button" className="photo-tile__remove" aria-label={`Remove new photo ${i + 1}`} onClick={() => removeAdded(p)}><Icon name="x" size={16} /></button>
                  </figure>
                ))}
              </div>
              {photoError && <p className="field-error" role="alert">{photoError}</p>}
            </section>

            <section className="card" aria-labelledby="step-details">
              <h2 id="step-details" className="card__title">2. Part Details</h2>
              <div className="field">
                <label htmlFor="title">Title shown to shoppers</label>
                <input id="title" className="input" value={name} onChange={(e) => setName(e.target.value)} maxLength={200} />
              </div>
              <div className="field-row">
                <div className="field">
                  <label htmlFor="category">Category</label>
                  <select id="category" className="input" value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
                    {!categoryId && <option value="">Choose a category</option>}
                    {[...new Set(categories.map((c) => c.group))].map((group) => (
                      <optgroup key={group} label={group}>
                        {categories.filter((c) => c.group === group).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                      </optgroup>
                    ))}
                  </select>
                </div>
                <div className="field">
                  <label htmlFor="condition">Condition</label>
                  <select id="condition" className="input" value={condition} onChange={(e) => setCondition(e.target.value as Condition)}>
                    {CONDITIONS.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
                  </select>
                </div>
                <div className="field">
                  <label htmlFor="grade">Grade</label>
                  <select id="grade" className="input" value={grade} onChange={(e) => setGrade(e.target.value)}>
                    {GRADES.map((g) => <option key={g.value} value={g.value}>{g.label}</option>)}
                  </select>
                </div>
                <div className="field">
                  <label htmlFor="price">Price (£, inc. VAT)</label>
                  <input id="price" className="input" inputMode="decimal" placeholder="0.00" value={price} onChange={(e) => setPrice(decimalInput(e.target.value))} />
                </div>
                <div className="field">
                  <label htmlFor="sale">Sale (% off)<span className="field__optional"> (optional)</span></label>
                  <input id="sale" className="input" inputMode="numeric" placeholder="e.g. 20" value={sale} aria-describedby="sale-hint"
                    onChange={(e) => setSale(e.target.value.replace(/\D/g, '').slice(0, 2))} />
                  <p id="sale-hint" className={'field__hint' + (saleOk ? '' : ' field-error')}>
                    {!saleOk ? 'Between 1 and 90.' : salePrice !== null ? `Shoppers pay ${gbp.format(salePrice)} (was ${gbp.format(priceValue!)}).` : 'Leave empty for no sale.'}
                  </p>
                </div>
                <div className="field">
                  <label htmlFor="stock">Stock quantity</label>
                  <input id="stock" className="input" inputMode="numeric" value={stock} onChange={(e) => setStock(e.target.value.replace(/\D/g, '').slice(0, 3))} />
                </div>
                <div className="field">
                  <label htmlFor="shipping">Ships as</label>
                  <select id="shipping" className="input" value={shipping} onChange={(e) => setShipping(e.target.value as Shipping)}>
                    {SHIPPING.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
                  </select>
                </div>
                {shipping === 'PARCEL' && (
                  <div className="field">
                    <label htmlFor="weight">Packed weight (kg)<span className="field__optional"> (optional)</span></label>
                    <input id="weight" className="input" inputMode="decimal" placeholder={typicalWeight != null ? String(typicalWeight) : 'e.g. 7.5'} value={weight}
                      aria-describedby="weight-hint" onChange={(e) => setWeight(decimalInput(e.target.value).slice(0, 6))} />
                    <p id="weight-hint" className={'field__hint' + (weightOk ? '' : ' field-error')}>
                      {!weightOk ? 'Between 0.1 and 999 kg.' : 'For FedEx’s delivery price. Leave empty to use the category’s typical weight' + (typicalWeight != null ? ` (${typicalWeight} kg).` : '.')}
                    </p>
                  </div>
                )}
                <div className="field">
                  <label htmlFor="part-number">Part number</label>
                  <input id="part-number" className="input mono" value={partNumber} onChange={(e) => setPartNumber(e.target.value)} maxLength={40} autoComplete="off" spellCheck={false} aria-describedby="part-number-hint" />
                  <p id="part-number-hint" className="field__hint">Shoppers can search by it with or without spaces and dashes.</p>
                </div>
                <div className="field">
                  <label htmlFor="engine-code">Engine code<span className="field__optional"> (optional)</span></label>
                  <input id="engine-code" className="input mono" value={engineCode} onChange={(e) => setEngineCode(e.target.value)} maxLength={20} spellCheck={false} />
                </div>
                <div className="field">
                  <label htmlFor="brand">Brand<span className="field__optional"> (optional)</span></label>
                  {newBrand ? (
                    <>
                      <input id="brand" className="input" value={brand} onChange={(e) => setBrand(e.target.value)} maxLength={60} placeholder="e.g. Nissan, Bosch" autoFocus />
                      <p className="field__hint">
                        A brand not listed yet. Shoppers filter by it, so spell it as the maker does.{' '}
                        {brands.length > 0 && <button type="button" className="link-btn" onClick={() => { setNewBrand(false); setBrand(''); }}>Pick from the list</button>}
                      </p>
                    </>
                  ) : (
                    <select id="brand" className="input" value={brand}
                      onChange={(e) => (e.target.value === NEW_BRAND ? (setNewBrand(true), setBrand('')) : setBrand(e.target.value))}>
                      <option value="">No brand</option>
                      {/* A listing's brand from before the list was tidied still shows, so saving doesn't drop it. */}
                      {[...brands, ...(brand && !brands.includes(brand) ? [brand] : [])].map((b) => <option key={b} value={b}>{b}</option>)}
                      <option value={NEW_BRAND}>Add a new brand…</option>
                    </select>
                  )}
                </div>
                <div className="field">
                  <label htmlFor="sub-type">Sub type<span className="field__optional"> (optional)</span></label>
                  <input id="sub-type" className="input" value={subType} onChange={(e) => setSubType(e.target.value)} maxLength={60} placeholder="e.g. Complete engine, Manual gearbox" />
                </div>
                <div className="field field--check">
                  <label className="check">
                    <input type="checkbox" checked={bestSeller} onChange={(e) => setBestSeller(e.target.checked)} />
                    Feature in Best selling (engines and gearboxes only)
                  </label>
                </div>
              </div>
              <div className="field">
                <span className="field__label" id="refinements-label">Featured refinements<span className="field__optional"> (optional, up to {MAX_REFINEMENTS})</span></span>
                <p className="field__hint">Popular shortcuts shoppers tick in the shop’s filters, like “Nissan Navara engine” or “Diesel engine”.</p>
                <div className="refs" role="group" aria-labelledby="refinements-label">
                  {refinements.length === 0 && newRefinement === null && <span className="admin-muted">none</span>}
                  {refinements.map((r) => (
                    <span key={r} className="ref-chip">
                      {r}
                      <button type="button" aria-label={`Remove featured refinement ${r}`} onClick={() => setRefinements(refinements.filter((x) => x !== r))}><Icon name="x" size={14} /></button>
                    </span>
                  ))}
                  {newRefinement !== null ? (
                    <span className="inline-row inline-row--sm">
                      <label className="sr-only" htmlFor="new-refinement">Featured refinement</label>
                      <input id="new-refinement" className="input input--sm" value={newRefinement} maxLength={60} autoFocus
                        onChange={(e) => setNewRefinement(e.target.value)}
                        onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); addRefinement(); } if (e.key === 'Escape') setNewRefinement(null); }} />
                      <button type="button" className="link-btn" onClick={addRefinement}>Add</button>
                    </span>
                  ) : refinements.length < MAX_REFINEMENTS && (
                    <button type="button" className="link-btn" onClick={() => setNewRefinement('')}>Add One</button>
                  )}
                </div>
              </div>
            </section>

            <section className="card" aria-labelledby="step-vehicles">
              <div className="card__head">
                <h2 id="step-vehicles" className="card__title">3. Confirm the Vehicles It Fits</h2>
                {rows.length > 0 && (
                  <p className="admin-muted">
                    <strong>{ticked.length}</strong> of {rows.length} ticked · {highTicked} high confidence
                    {ticked.length < rows.length && <> · <button type="button" className="link-btn" onClick={() => setRows(rows.filter((r) => r.ticked))}>Remove Unticked</button></>}
                  </p>
                )}
              </div>
              <div aria-live="polite">
                {fitError && <p className="field-error">{fitError}</p>}
                {fitNote && <p className="admin-muted">{fitNote}</p>}
              </div>
              {rows.length === 0 ? (
                <p className="admin-muted">No vehicles yet (optional). Add them with AI, from the vehicle list below, or paste them from eBay.</p>
              ) : (
                <div className="table-wrap">
                  <table className="table table--fit">
                    <thead>
                      <tr><th scope="col">
                        <label className="fit-all">
                          <input type="checkbox" className="tick" checked={ticked.length === rows.length}
                            ref={(el) => { if (el) el.indeterminate = ticked.length > 0 && ticked.length < rows.length; }}
                            onChange={(e) => setRows(rows.map((r) => ({ ...r, ticked: e.target.checked })))}
                            aria-label={ticked.length === rows.length ? 'Untick all vehicles' : 'Tick all vehicles'} />
                          Fits
                        </label>
                      </th><th scope="col">Vehicle</th><th scope="col">Engine</th><th scope="col">Year</th><th scope="col">Source</th><th scope="col">Confidence</th><th scope="col"><span className="sr-only">Remove</span></th></tr>
                    </thead>
                    <tbody>
                      {rows.map((r) => (
                        <tr key={r.fit.vehicleId} className={r.ticked ? undefined : 'is-unticked'}>
                          <td>
                            <input type="checkbox" className="tick" checked={r.ticked} aria-label={`Fits ${r.fit.vehicle}`}
                              onChange={(e) => setRows(rows.map((x) => (x === r ? { ...x, ticked: e.target.checked } : x)))} />
                          </td>
                          <td>{r.fit.vehicle}<span className="table__sub">{r.fit.engine}</span></td>
                          <td className="mono">{r.fit.engineCode ?? '—'}</td>
                          <td>{r.fit.year == null ? '—' : r.fit.yearTo != null && r.fit.yearTo !== r.fit.year ? `${r.fit.year}–${r.fit.yearTo}` : r.fit.year}</td>
                          <td>{r.fit.source}</td>
                          <td><ConfidencePill confidence={r.fit.confidence} /></td>
                          <td>
                            <button type="button" className="icon-btn" aria-label={`Remove ${r.fit.vehicle}, ${r.fit.engine}`} title="Remove from the list"
                              onClick={() => setRows(rows.filter((x) => x !== r))}><Icon name="x" size={16} /></button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              <div className="ai-suggest">
                <div className="ai-suggest__head">
                  <button type="button" className="btn btn--outline" disabled={suggesting || !name.trim()} onClick={() => void suggestWithAi()}>
                    {suggesting ? 'Asking AI…' : 'Suggest Vehicles With AI'}
                  </button>
                  {aiUnticked > 0 && (
                    <button type="button" className="btn btn--primary" onClick={() => setRows(rows.map((r) => (r.fit.source === AI_SOURCE ? { ...r, ticked: true } : r)))}>
                      Tick All {aiUnticked} AI {aiUnticked === 1 ? 'Suggestion' : 'Suggestions'}
                    </button>
                  )}
                  <p className="field__hint">
                    Gemini reads the title, part number, engine code, brand and description lines above and suggests the
                    vehicles it thinks this part fits. <strong>AI can make mistakes:</strong> suggestions are added unticked,
                    so check each one before you tick it. It can take up to a minute.
                  </p>
                </div>
                {aiMissed.length > 0 && (
                  <p className="admin-muted">Also suggested, but not found in the vehicle list (add them by hand if right): {aiMissed.join('; ')}.</p>
                )}
              </div>
              <VehiclePicker onAdd={(found) => merge(found, () => true)} />
              <EbayPaste ebayUrl={ebayUrl} onAdd={(found) => merge(found, () => true)} />
            </section>

            <section className="card" aria-labelledby="step-description">
              <h2 id="step-description" className="card__title">4. Description</h2>
              <p className="field__hint">
                Built from the shop’s listing template. The title, brand, part number and condition come from above; fill
                in whichever of these lines apply. Empty ones are left out.
              </p>
              <div className="field-row">
                {DESCRIPTION_INPUTS.map(([key, label, placeholder]) => (
                  <div className="field" key={key}>
                    <label htmlFor={`desc-${key}`}>{label}<span className="field__optional"> (optional)</span></label>
                    <input id={`desc-${key}`} className="input" value={descFields[key]} onChange={setDesc(key)} placeholder={placeholder} maxLength={200} />
                  </div>
                ))}
              </div>
              <div className="field">
                <label htmlFor="desc-notes">Notes<span className="field__optional"> (optional)</span></label>
                <textarea id="desc-notes" className="input" rows={3} value={descFields.notes} onChange={setDesc('notes')} maxLength={2000}
                  placeholder="Anything else shoppers should know, like what doesn’t come with an engine. A blank line starts a new paragraph." />
              </div>
              <p className="field__label">Preview</p>
              {descriptionToSave === descriptionHtml && descriptionHtml.trim() && !madeFromFields(descriptionHtml) && (
                <p className="field__hint">This is the listing’s original description. It’s kept as it is until you change a line it shows.</p>
              )}
              <ListingHtml html={descriptionToSave} className="listing-html--preview" />
            </section>
          </div>

          <aside className="edit-side">
            <section className="card" aria-labelledby="ready-title">
              <h2 id="ready-title" className="card__title">Ready to List?</h2>
              <ul className="checklist">
                <Check ok={refOk} label={partNumber.trim() ? 'Part number entered' : refOk ? 'Engine code entered' : 'Part number or engine code'} />
                <Check ok={ticked.length > 0} optional label={ticked.length > 0 ? `Compatibility (${ticked.length} ${ticked.length === 1 ? 'vehicle' : 'vehicles'})` : 'Compatibility (optional)'} />
                <Check ok={photoOk} label={usesSample ? 'Photo (sample only)' : usesListingPhoto ? 'Photo already on the listing' : 'At least one photo'} />
                <Check ok={priceOk} label="Price Set" />
              </ul>
              {detail && <p className="admin-muted">Currently {detail.status === 'LIVE' ? 'live in the shop' : detail.status === 'ENDED' ? 'ended (not in the shop)' : 'a draft'}.</p>}
            </section>

            <section className="card" aria-labelledby="preview-title">
              <h2 id="preview-title" className="card__title">Shopper Preview</h2>
              <p className="admin-muted">How this part appears to someone who searched a matching plate.</p>
              <div className="preview">
                <div className="preview__photo">{previewImage ? <img src={previewImage} alt="" /> : <span>Photo</span>}</div>
                <div className="preview__body">
                  {ticked.length > 0 && <span className="pill pill--live">Fits your vehicle</span>}
                  <p className="preview__name">{name.trim() || 'Title shown to shoppers'}</p>
                  {reference && <p className="mono preview__ref">{reference}</p>}
                  <p className="preview__price">
                    {salePrice !== null ? <>{gbp.format(salePrice)} <s className="admin-muted">{gbp.format(priceValue!)}</s> <span className="pill pill--sale">-{saleValue}%</span></> : priceOk ? gbp.format(priceValue!) : '£—'}
                  </p>
                </div>
              </div>
            </section>
            {id && <p className="admin-muted">Need the full list? <Link to="/admin/listings">Back to Listings</Link></p>}
          </aside>
        </div>
      )}
    </AdminLayout>
  );
}

/** optional: not needed to list, so it shows as a dash rather than a warning until it's done. */
function Check({ ok, label, optional = false }: { ok: boolean; label: string; optional?: boolean }) {
  return (
    <li className={ok ? 'is-ok' : optional ? 'is-optional' : 'is-todo'}>
      <span className="checklist__mark" aria-hidden="true">{ok ? '✓' : optional ? '–' : '!'}</span>
      {label}<span className="sr-only">{ok ? ': done' : optional ? ': optional' : ': to do'}</span>
    </li>
  );
}
