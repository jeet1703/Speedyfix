import DOMPurify from 'dompurify';
import { useMemo } from 'react';

// Links in a description open in a new tab, so shoppers don't lose the listing.
DOMPurify.addHook('afterSanitizeAttributes', (node) => {
  if (node.tagName === 'A') {
    node.setAttribute('target', '_blank');
    node.setAttribute('rel', 'noopener noreferrer nofollow');
  }
});

/**
 * A listing description staff wrote as HTML (often pasted from an eBay listing). Scripts, event handlers, forms,
 * frames and <style> blocks are removed; inline styles, headings, lists, tables, images and links stay. The box
 * keeps anything positioned inside it, so a description can't cover the rest of the page.
 */
export function ListingHtml({ html, className = '' }: { html: string; className?: string }) {
  const clean = useMemo(() => DOMPurify.sanitize(html, {
    FORBID_TAGS: ['style', 'form', 'input', 'button', 'textarea', 'select', 'option', 'iframe', 'object', 'embed'],
    FORBID_ATTR: ['id', 'name'],
    ALLOW_DATA_ATTR: false
  }), [html]);
  return <div className={`listing-html ${className}`.trim()} dangerouslySetInnerHTML={{ __html: clean }} />;
}
