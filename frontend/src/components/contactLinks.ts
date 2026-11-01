/** "tel:" address for a phone number as written ("+44 0770 774977" becomes "tel:+440770774977"). */
export const telHref = (phone: string) => `tel:${phone.replace(/[^+\d]/g, '')}`;

/** WhatsApp chat link: wa.me wants the number in international form without "+", spaces or the UK trunk 0. */
export const whatsAppHref = (phone: string) => {
  let digits = phone.replace(/\D/g, '');
  if (digits.startsWith('440')) digits = '44' + digits.slice(3);
  else if (digits.startsWith('0')) digits = '44' + digits.slice(1);
  return `https://wa.me/${digits}`;
};
