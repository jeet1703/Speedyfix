import { useEffect, useRef, useState, type DragEvent, type FormEvent } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import type { Vehicle } from '../../api/types';
import { Icon } from '../../components/Icons';
import { adminApi, errorMessage } from '../adminApi';
import { AdminLayout } from '../AdminLayout';
import { ConfidencePill } from '../components';
import type { CategoryOption, Condition, Fitment, ProductDetail, ProductStatus, Shipping } from '../types';
import { CardsSkeleton } from '../../components/Skeleton';

const CONDITIONS: { value: Condition; label: string }[] = [
  { value: 'USED', label: 'Used, tested' },
  { value: 'REMANUFACTURED', label: 'Remanufactured' },
  { value: 'NEW', label: 'New' }
];
const GRADES = [
  { value: '', label: 'No grade' },
  { value: 'A', label: 'A' },
  { value: 'B', label: 'B' },
  { value: 'C', label: 'C' }
];
const SHIPPING: { value: Shipping; label: string }[] = [
  { value: 'PARCEL', label: 'Tracked parcel' },
  { value: 'PALLET', label: 'Pallet (engines, gearboxes)' }
];
const MAX_PHOTOS = 4;
const MAX_REFINEMENTS = 5;
const PHOTO_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const gbp = new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP' });

/** Digits and at most one decimal point (as on the shop's price filter). */
const priceInput = (value: string) => value.replace(/[^\d.]/g, '').replace(/(\..*)\./g, '$1');

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
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);

  const [partNumber, setPartNumber] = useState('');
  const [rows, setRows] = useState<FitRow[]>([]);
  const [manualId, setManualId] = useState('');
  const [fitNote, setFitNote] = useState('');
  const [fitError, setFitError] = useState('');
  const [finding, setFinding] = useState(false);

  const [name, setName] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [condition, setCondition] = useState<Condition>('USED');
  const [grade, setGrade] = useState('A');
  const [price, setPrice] = useState('');
  const [stock, setStock] = useState('1');
  const [shipping, setShipping] = useState<Shipping>('PARCEL');
  const [engineCode, setEngineCode] = useState('');
  const [brand, setBrand] = useState('');
  const [subType, setSubType] = useState('');
  const [refinements, setRefinements] = useState<string[]>([]);
  const [newRefinement, setNewRefinement] = useState<string | null>(null);
  const [bestSeller, setBestSeller] = useState(false);

  const [kept, setKept] = useState<{ id: string; url: string }[]>([]);
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
      setCategoryId((current) => current || c[0]?.id || '');
    }).catch(() => setLoadError('The categories didn’t load. Reload the page.'));
    adminApi.vehicles().then(setVehicles).catch(() => setVehicles([]));
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
    setRows(d.vehicles.map((fit) => ({ fit, ticked: true })));
    setName(d.name);
    setCategoryId(d.categoryId);
    setCondition(d.condition);
    setGrade(d.grade ?? '');
    setPrice(d.price === null ? '' : d.price.toFixed(2));
    setStock(String(d.stock));
    setShipping(d.shipping);
    setEngineCode(d.engineCode ?? '');
    setBestSeller(d.bestSeller);
    setKept(d.photos);
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

  async function findVehicles(e: FormEvent) {
    e.preventDefault();
    setFitNote('');
    setFitError('');
    if (!partNumber.trim()) {
      setFitError('Enter the part number first.');
      return;
    }
    setFinding(true);
    try {
      const found = await adminApi.fitment(partNumber.trim(), '', id ?? null);
      merge(found, (f) => f.confidence === 'HIGH');
      setFitNote(found.length
        ? `Found ${found.length} ${found.length === 1 ? 'vehicle' : 'vehicles'} from parts with this part number.`
        : 'No vehicles are confirmed for this part number yet. Apply the engine-code rule or add vehicles by hand.');
    } catch (err) {
      setFitError(errorMessage(err, 'We couldn’t search for vehicles. Try again.'));
    } finally {
      setFinding(false);
    }
  }

  async function applyEngineRule() {
    setFitNote('');
    setFitError('');
    if (!engineCode.trim()) {
      setFitError('Enter the engine code under Part details first.');
      return;
    }
    setFinding(true);
    try {
      const found = await adminApi.fitment('', engineCode.trim(), id ?? null);
      merge(found, () => false);
      setFitNote(found.length
        ? `Found ${found.length} ${found.length === 1 ? 'vehicle' : 'vehicles'} with engine ${engineCode.trim()}. Tick the ones you’ve checked.`
        : `No vehicles in the vehicle data have engine ${engineCode.trim()}.`);
    } catch (err) {
      setFitError(errorMessage(err, 'We couldn’t apply the engine-code rule. Try again.'));
    } finally {
      setFinding(false);
    }
  }

  function addManual() {
    const v = vehicles.find((x) => x.id === manualId);
    if (!v) return;
    merge([{ vehicleId: v.id, vehicle: `${v.make} ${v.model}`, engine: v.engine, engineCode: v.engineCode, year: v.year, source: 'Added by hand', confidence: 'HIGH' }], () => true);
    setManualId('');
  }

  function addRefinement() {
    const value = (newRefinement ?? '').trim();
    if (value && !refinements.some((r) => r.toLowerCase() === value.toLowerCase()) && refinements.length < MAX_REFINEMENTS) {
      setRefinements([...refinements, value]);
    }
    setNewRefinement(null);
  }

  function addFiles(files: File[]) {
    setPhotoError('');
    const images = files.filter((f) => PHOTO_TYPES.includes(f.type));
    if (images.length < files.length) setPhotoError('Photos must be JPG, PNG or WebP images.');
    const room = MAX_PHOTOS - kept.length - added.length;
    if (images.length > room) setPhotoError(`Add up to ${MAX_PHOTOS} photos per part.`);
    const take = images.slice(0, Math.max(0, room));
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
  const priceValue = price.trim() === '' ? null : Number(price);
  const priceOk = priceValue !== null && Number.isFinite(priceValue) && priceValue > 0;
  const photoCount = kept.length + added.length;
  const usesSample = photoCount === 0 && !!detail?.samplePhoto;
  const photoOk = photoCount > 0 || usesSample;
  const refOk = !!(partNumber.trim() || engineCode.trim());
  const blocker = !photoOk ? 'add a photo first' : !priceOk ? 'add a price first' : !refOk ? 'add a part number first' : '';
  const isLive = detail?.status === 'LIVE';

  async function save(status: ProductStatus) {
    setSaveError('');
    setNotice('');
    const stockValue = Number(stock);
    if (!name.trim()) return setSaveError('Enter the title shoppers will see.');
    if (!categoryId) return setSaveError('Choose a category.');
    if (priceValue !== null && !priceOk) return setSaveError('Enter a price above £0, like 149.00.');
    if (stock.trim() === '' || !Number.isInteger(stockValue) || stockValue < 0 || stockValue > 999) return setSaveError('Enter a stock quantity from 0 to 999.');
    setSaving(true);
    try {
      const saved = await adminApi.saveProduct(id ?? null, {
        name: name.trim(), categoryId, condition, grade, price: priceValue, partNumber: partNumber.trim(),
        engineCode: engineCode.trim(), brand: brand.trim(), subType: subType.trim(), refinements, shipping, stock: stockValue, bestSeller,
        vehicleIds: ticked.map((r) => r.fit.vehicleId), keepPhotoIds: kept.map((p) => p.id), status
      }, added.map((p) => p.file));
      const message = status === 'LIVE'
        ? (isLive ? 'Changes saved. The listing is live in the shop.' : 'Listed. The part is now in the shop.')
        : (isLive ? 'Listing ended. The part is saved as a draft.' : 'Saved as a draft. Shoppers can’t see it yet.');
      if (!id) {
        navigate(`/admin/products/${encodeURIComponent(saved.id)}`, { replace: true, state: { saved: message } });
      } else {
        fill(saved);
        setNotice(message);
        window.scrollTo({ top: 0, behavior: 'smooth' });
      }
    } catch (err) {
      setSaveError(errorMessage(err, 'We couldn’t save the listing. Try again.'));
    } finally {
      setSaving(false);
    }
  }

  const title = id ? (detail ? 'Edit listing' : 'Loading listing…') : 'Create listing';
  const actions = (id && !detail) ? null : isLive ? (
    <>
      <a className="btn btn--outline" href={`${import.meta.env.BASE_URL}products/${encodeURIComponent(id!)}`} target="_blank" rel="noreferrer">View in shop</a>
      <button type="button" className="btn btn--outline" disabled={saving} onClick={() => void save('DRAFT')}>End listing</button>
      <button type="button" className="btn btn--primary" disabled={saving || !!blocker} onClick={() => void save('LIVE')}>
        {blocker ? `Save changes (${blocker})` : saving ? 'Saving…' : 'Save changes'}
      </button>
    </>
  ) : (
    <>
      <button type="button" className="btn btn--outline" disabled={saving} onClick={() => void save('DRAFT')}>{saving ? 'Saving…' : 'Save draft'}</button>
      <button type="button" className="btn btn--primary" disabled={saving || !!blocker} onClick={() => void save('LIVE')}>
        {blocker ? `List it (${blocker})` : 'List it'}
      </button>
    </>
  );

  if (loadError && id && !detail) {
    return (
      <AdminLayout title="Edit listing" crumbs={[{ label: 'Listings', to: '/admin/listings' }, { label: 'Edit listing' }]}>
        <p className="notice notice--error" role="alert">{loadError}</p>
      </AdminLayout>
    );
  }

  const previewImage = added[0]?.url ?? kept[0]?.url ?? (usesSample ? detail?.imageUrl : null);
  const reference = partNumber.trim() ? `Part no. ${partNumber.trim()}` : engineCode.trim() ? `Engine code ${engineCode.trim()}` : '';
  const manualOptions = vehicles.filter((v) => !rows.some((r) => r.fit.vehicleId === v.id));

  return (
    <AdminLayout title={title} crumbs={[{ label: 'Listings', to: '/admin/listings' }, { label: id ? 'Edit listing' : 'Create listing' }]} actions={actions}>
      {id && !detail ? <CardsSkeleton cards={3} /> : (
        <div className="edit-cols">
          <div className="edit-main">
            {notice && <p className="notice notice--ok" role="status">{notice}</p>}
            {saveError && <p ref={errorRef} className="notice notice--error" role="alert" tabIndex={-1}>{saveError}</p>}
            {loadError && <p className="notice notice--error" role="alert">{loadError}</p>}

            <section className="card" aria-labelledby="step-1">
              <h2 id="step-1" className="card__title">1. Identify the part</h2>
              <p className="admin-muted">Enter the part number to find vehicles already confirmed for other parts with the same number.</p>
              <form className="field" onSubmit={findVehicles}>
                <label htmlFor="part-number">Part number</label>
                <div className="inline-row">
                  <input id="part-number" className="input mono" value={partNumber} onChange={(e) => setPartNumber(e.target.value)} maxLength={40} autoComplete="off" spellCheck={false} />
                  <button type="submit" className="btn btn--dark" disabled={finding}>Find vehicles</button>
                </div>
                <p className="field__hint">Shoppers can search by it with or without spaces and dashes.</p>
              </form>
            </section>

            <section className="card" aria-labelledby="step-2">
              <div className="card__head">
                <h2 id="step-2" className="card__title">2. Confirm the vehicles it fits</h2>
                {rows.length > 0 && <p className="admin-muted"><strong>{ticked.length}</strong> of {rows.length} ticked · {highTicked} high confidence</p>}
              </div>
              <div aria-live="polite">
                {fitError && <p className="field-error">{fitError}</p>}
                {fitNote && <p className="admin-muted">{fitNote}</p>}
              </div>
              {rows.length === 0 ? (
                <p className="admin-muted">No vehicles yet. Find them by part number, apply the engine-code rule, or add them by hand.</p>
              ) : (
                <div className="table-wrap">
                  <table className="table table--fit">
                    <thead>
                      <tr><th scope="col">Fits</th><th scope="col">Vehicle</th><th scope="col">Engine</th><th scope="col">Year</th><th scope="col">Source</th><th scope="col">Confidence</th></tr>
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
                          <td>{r.fit.year ?? '—'}</td>
                          <td>{r.fit.source}</td>
                          <td><ConfidencePill confidence={r.fit.confidence} /></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              <div className="fit-actions">
                <div className="inline-row inline-row--sm">
                  <label className="sr-only" htmlFor="manual-vehicle">Vehicle to add</label>
                  <select id="manual-vehicle" className="input input--sm" value={manualId} onChange={(e) => setManualId(e.target.value)}>
                    <option value="">Add vehicle manually…</option>
                    {manualOptions.map((v) => <option key={v.id} value={v.id}>{v.make} {v.model}, {v.engine}{v.engineCode ? ` (${v.engineCode})` : ''}</option>)}
                  </select>
                  <button type="button" className="link-btn" disabled={!manualId} onClick={addManual}>Add</button>
                </div>
                <button type="button" className="link-btn" disabled={finding} onClick={() => void applyEngineRule()}>Apply engine-code rule</button>
              </div>
            </section>

            <section className="card" aria-labelledby="step-3">
              <h2 id="step-3" className="card__title">3. Part details</h2>
              <div className="field">
                <label htmlFor="title">Title shown to shoppers</label>
                <input id="title" className="input" value={name} onChange={(e) => setName(e.target.value)} maxLength={200} />
              </div>
              <div className="field-row">
                <div className="field">
                  <label htmlFor="category">Category</label>
                  <select id="category" className="input" value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
                    {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
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
                  <input id="price" className="input" inputMode="decimal" placeholder="0.00" value={price} onChange={(e) => setPrice(priceInput(e.target.value))} />
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
                <div className="field">
                  <label htmlFor="engine-code">Engine code<span className="field__optional"> (optional)</span></label>
                  <input id="engine-code" className="input mono" value={engineCode} onChange={(e) => setEngineCode(e.target.value)} maxLength={20} spellCheck={false} />
                </div>
                <div className="field">
                  <label htmlFor="brand">Brand<span className="field__optional"> (optional)</span></label>
                  <input id="brand" className="input" value={brand} onChange={(e) => setBrand(e.target.value)} maxLength={60} placeholder="e.g. Nissan, Bosch" />
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
                    <button type="button" className="link-btn" onClick={() => setNewRefinement('')}>Add one</button>
                  )}
                </div>
              </div>
            </section>

            <section className="card" aria-labelledby="step-4">
              <h2 id="step-4" className="card__title">4. Photos</h2>
              {usesSample && <p className="notice">This part is using a sample photo. Upload real photos of this unit to replace it.</p>}
              <div className="photos">
                {photoCount < MAX_PHOTOS && (
                  <div className={'dropzone' + (dragging ? ' dropzone--over' : '')}
                    onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
                    onDragLeave={() => setDragging(false)} onDrop={onDrop}>
                    <p>Drop photos here or <button type="button" className="link-btn" onClick={() => fileRef.current?.click()}>browse</button></p>
                    <p className="field__hint">Real photos of this unit, at least one required. JPG, PNG or WebP, up to {MAX_PHOTOS}.</p>
                    <input ref={fileRef} type="file" accept={PHOTO_TYPES.join(',')} multiple hidden
                      onChange={(e) => { addFiles(Array.from(e.target.files ?? [])); e.target.value = ''; }} />
                  </div>
                )}
                {kept.map((p, i) => (
                  <figure key={p.id} className="photo-tile">
                    <img src={p.url} alt={`Photo ${i + 1}`} />
                    <button type="button" className="photo-tile__remove" aria-label={`Remove photo ${i + 1}`} onClick={() => setKept(kept.filter((x) => x !== p))}><Icon name="x" size={16} /></button>
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
          </div>

          <aside className="edit-side">
            <section className="card" aria-labelledby="ready-title">
              <h2 id="ready-title" className="card__title">Ready to list?</h2>
              <ul className="checklist">
                <Check ok={refOk} label={partNumber.trim() ? 'Part number entered' : refOk ? 'Engine code entered' : 'Part number or engine code'} />
                <Check ok={highTicked > 0} label={highTicked > 0 ? `High-confidence fitment (${highTicked})` : 'High-confidence fitment (recommended)'} />
                <Check ok={photoOk} label={usesSample ? 'Photo (sample only)' : 'At least one photo'} />
                <Check ok={priceOk} label="Price set" />
              </ul>
              {detail && <p className="admin-muted">Currently {detail.status === 'LIVE' ? 'live in the shop' : detail.status === 'ENDED' ? 'ended (not in the shop)' : 'a draft'}.</p>}
            </section>

            <section className="card" aria-labelledby="preview-title">
              <h2 id="preview-title" className="card__title">Shopper preview</h2>
              <p className="admin-muted">How this part appears to someone who searched a matching plate.</p>
              <div className="preview">
                <div className="preview__photo">{previewImage ? <img src={previewImage} alt="" /> : <span>Photo</span>}</div>
                <div className="preview__body">
                  {ticked.length > 0 && <span className="pill pill--live">Fits your vehicle</span>}
                  <p className="preview__name">{name.trim() || 'Title shown to shoppers'}</p>
                  {reference && <p className="mono preview__ref">{reference}</p>}
                  <p className="preview__price">{priceOk ? gbp.format(priceValue!) : '£—'}</p>
                </div>
              </div>
            </section>
            {id && <p className="admin-muted">Need the full list? <Link to="/admin/listings">Back to listings</Link></p>}
          </aside>
        </div>
      )}
    </AdminLayout>
  );
}

function Check({ ok, label }: { ok: boolean; label: string }) {
  return (
    <li className={ok ? 'is-ok' : 'is-todo'}>
      <span className="checklist__mark" aria-hidden="true">{ok ? '✓' : '!'}</span>
      {label}<span className="sr-only">{ok ? ': done' : ': to do'}</span>
    </li>
  );
}
