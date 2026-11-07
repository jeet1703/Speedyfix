/**
 * Listing descriptions in the shop's eBay template: the same header, shipping, "why buy" and terms on every part, with
 * only the part's own lines filled in. Staff fill fields; empty ones are left out. The fields are kept in a comment at
 * the top of the HTML (removed before shoppers see it), so the listing opens with them filled in next time.
 */

/** The lines only the description has. Title, brand, part number and condition come from the listing itself. */
export interface DescriptionFields {
  part: string;
  colour: string;
  year: string;
  miles: string;
  make: string;
  model: string;
  yearRange: string;
  engine: string;
  notes: string;
}

/** The listing's own fields the description shows. */
export interface ListingFields {
  title: string;
  brand: string;
  partNumber: string;
  condition: string;
}

export const EMPTY_FIELDS: DescriptionFields = { part: '', colour: '', year: '', miles: '', make: '', model: '', yearRange: '', engine: '', notes: '' };

const MARKER = /^\s*<!--speedyfix-fields:(.*?)-->/s;

/** Labels in the template's lists, as the eBay listings write them ("Brand : Land Rover", "Make: Land Rover"). */
const LABELS: [string, keyof DescriptionFields][] = [
  ['part', 'part'], ['colour', 'colour'], ['color', 'colour'], ['year', 'year'], ['miles', 'miles'], ['mileage', 'miles'],
  ['make', 'make'], ['model', 'model'], ['year range', 'yearRange'], ['engine type', 'engine'], ['engine', 'engine']
];

/** "N/A" and "-" say nothing, so they count as empty. */
const value = (s: string) => {
  const v = s.replace(/\s+/g, ' ').trim();
  return /^(n\/?a|-+|none)$/i.test(v) ? '' : v;
};

/**
 * The description's own fields from saved HTML: from the comment when the shop made it, otherwise read from an eBay
 * listing in the same template. Anything else gives empty fields.
 */
export function readFields(html: string | null): DescriptionFields {
  if (!html) return { ...EMPTY_FIELDS };
  const marked = MARKER.exec(html);
  if (marked) {
    try {
      return { ...EMPTY_FIELDS, ...JSON.parse(decodeURIComponent(marked[1])) };
    } catch {
      /* a damaged comment: read the HTML instead */
    }
  }
  const doc = new DOMParser().parseFromString(html, 'text/html');
  const fields = { ...EMPTY_FIELDS };
  doc.querySelectorAll('li').forEach((li) => {
    const m = /^\s*([A-Za-z ]+?)\s*:\s*([\s\S]*)$/.exec(li.textContent ?? '');
    const key = m && LABELS.find(([label]) => label === m[1].toLowerCase())?.[1];
    if (key && !fields[key]) fields[key] = value(m![2]);
  });
  // Notes: paragraphs in the compatibility section beyond its two standard lines (engines' "not exchange" notes).
  const compat = Array.from(doc.querySelectorAll('h3')).find((h) => /vehicle compatibility/i.test(h.textContent ?? ''));
  if (compat?.parentElement) {
    fields.notes = Array.from(compat.parentElement.querySelectorAll('p'))
      .map((p) => value(p.textContent ?? ''))
      .filter((t) => t && !/^please verify fitment/i.test(t) && !/^important:/i.test(t))
      .join('\n\n');
  }
  return fields;
}

/** Whether saved HTML is one the shop made from fields (rather than pasted or imported from eBay). */
export const madeFromFields = (html: string | null) => !!html && MARKER.test(html);

const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

const FONT = 'color: #333; font-family: Arial, Helvetica, sans-serif;';
const BOX = `${FONT} padding: 25px; border: 1px solid #ddd; border-top: none;`;
const H3 = 'border-bottom: 2px solid #111; padding-bottom: 5px;';

/** One "Label : value" line, or nothing when the value is empty. */
const line = (label: string, v: string, sep = ' : ') => (value(v) ? `<li><strong>${label}${sep}</strong>${esc(value(v))}</li>` : '');

/** The listing's description as HTML, in the template, with the fields in a comment at the top. */
export function buildDescription(listing: ListingFields, f: DescriptionFields): string {
  const product = [
    line('Brand', listing.brand), line('Part', f.part), line('Part Number', listing.partNumber),
    line('Condition', listing.condition), line('Colour', f.colour), line('Year', f.year), line('Miles', f.miles)
  ].join('');
  const vehicle = [line('Make', f.make, ': '), line('Model', f.model, ': '), line('Year Range', f.yearRange, ': '), line('Engine Type', f.engine, ': ')].join('');
  const notes = f.notes.split(/\n\s*\n/).map((p) => p.trim()).filter(Boolean)
    .map((p) => `<p>${esc(p).replace(/\n/g, '<br>')}</p>`).join('');
  const kept = Object.fromEntries(Object.entries(f).filter(([, v]) => v.trim()));

  return `<!--speedyfix-fields:${encodeURIComponent(JSON.stringify(kept))}-->
<div style="max-width: 1000px; margin: 0 auto; line-height: 1.6;">
<div style="color: #fff; font-family: Arial, Helvetica, sans-serif; background: #111; padding: 25px; text-align: center; border-radius: 6px 6px 0 0;">
<h1 style="margin: 0; font-size: 28px; letter-spacing: 1px;">SPEEDYFIX AUTO SALES LTD</h1>
<p style="margin: 5px 0 0; font-size: 14px;">Quality OEM &amp; Aftermarket Auto Parts Specialists</p>
</div>
${listing.title.trim() ? `<div style="${FONT} background: #f4f4f4; padding: 20px; border-left: 1px solid #ddd; border-right: 1px solid #ddd;"><h2 style="margin: 0; font-size: 22px; color: #000;">${esc(listing.title.trim())}</h2></div>` : ''}
${product ? `<div style="${BOX}"><h3 style="${H3}">Product Description</h3><ul>${product}</ul></div>` : ''}
<div style="${BOX} background: #fafafa;">
<h3 style="${H3}">Vehicle Compatibility</h3>
<p>Please verify fitment before purchasing.</p>
${vehicle ? `<ul>${vehicle}</ul>` : ''}
<p><strong>Important:</strong> If unsure, message us with your VIN or Reg and we will confirm compatibility.</p>
${notes}
</div>
<div style="${BOX}"><h3 style="${H3}">Shipping Information</h3><ul><li>Secure packaging</li><li>Tracking provided</li><li>International shipping available</li></ul></div>
<div style="${BOX}"><h3 style="${H3}">Why Buy From Us?</h3><ul><li>Trusted seller</li><li>Fast response time</li><li>Competitive pricing</li><li>100% customer satisfaction focus</li></ul></div>
<div style="color: #fff; font-family: Arial, Helvetica, sans-serif; background: #111; text-align: center; padding: 15px; border-radius: 0 0 6px 6px;"><p style="margin: 0; font-size: 24px;"><strong>Terms &amp; Conditions</strong></p></div>
<div style="${FONT} padding: 0 4px;">
<p><strong>Warranty:</strong><br>All parts are supplied with a <strong>28-day warranty</strong> from the date of delivery. Warranty covers the part only and does not include labour or fitting costs.</p>
<p><strong>Returns:</strong><br>Returns accepted within 30 days. A <strong>30% handling/restocking fee</strong> will apply if the item has been used for testing or ordered by mistake.<br>Original postage costs will be deducted from any refund. Items must be returned in the same condition as supplied.</p>
<p><strong>UK Delivery:</strong><br>UK delivery within <strong>7 working days</strong> (estimated). Delivery times may vary due to courier delays.</p>
<p><strong>International Delivery:</strong><br>We ship via <strong>UK Pallets or FedEx</strong>. Standard Economy or Express Priority available (extra charges apply).<br>Estimated <strong>3 to 10 working days after dispatch</strong>.<br>Buyers are responsible for customs clearance and any import VAT or duties, as the UK is no longer part of the EU.</p>
<p style="text-align: center;"><strong>Have A Good Day</strong></p>
</div>
</div>`;
}
