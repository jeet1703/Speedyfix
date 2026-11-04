/**
 * Countries the shop delivers to, by ISO code, with English names from the browser. The UK comes first. Countries
 * under UK sanctions or FedEx embargo aren't listed (the server refuses them too).
 */
const CODES = ('AD AE AF AG AI AL AM AO AR AS AT AU AW AX AZ BA BB BD BE BF BG BH BI BJ BL BM BN BO BQ BR BS BT BW BZ CA CD CF '
  + 'CG CH CI CK CL CM CN CO CR CV CW CY CZ DE DJ DK DM DO DZ EC EE EG ER ES ET FI FJ FK FM FO FR GA GD GE GF GG GH GI GL GM '
  + 'GN GP GQ GR GT GU GW GY HK HN HR HT HU ID IE IL IM IN IQ IS IT JE JM JO JP KE KG KH KI KM KN KR KW KY KZ LA LB LC LI LK '
  + 'LR LS LT LU LV LY MA MC MD ME MF MG MH MK ML MM MN MO MP MQ MR MS MT MU MV MW MX MY MZ NA NC NE NG NI NL NO NP NR NZ OM '
  + 'PA PE PF PG PH PK PL PM PR PS PT PW PY QA RE RO RS RW SA SB SC SD SE SG SI SK SL SM SN SO SR SS ST SV SX SZ TC TD TG TH '
  + 'TJ TL TM TN TO TR TT TV TW TZ UA UG US UY UZ VA VC VE VG VI VN VU WS XK YE YT ZA ZM ZW').split(' ');

const names = new Intl.DisplayNames(['en-GB'], { type: 'region' });

/** "India" for "IN". */
export function countryName(code: string | null | undefined) {
  const c = (code || 'GB').toUpperCase();
  try {
    return names.of(c) ?? c;
  } catch {
    return c;
  }
}

/** Every country but the UK, sorted by name; the UK goes first in the list. */
export const COUNTRIES: { code: string; name: string }[] = [
  { code: 'GB', name: 'United Kingdom' },
  ...CODES.map((code) => ({ code, name: countryName(code) })).sort((a, b) => a.name.localeCompare(b.name, 'en-GB'))
];

/** True inside the UK VAT area (the UK and the Isle of Man): prices include 20% VAT. Elsewhere it's a zero-rated export. */
export function ukVat(code: string | null | undefined) {
  const c = (code || 'GB').toUpperCase();
  return c === 'GB' || c === 'IM';
}
