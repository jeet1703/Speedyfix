export function SectionHeading({ id, title, children }: { id: string; title: string; children?: React.ReactNode }) {
  return (
    <div className="section-heading">
      <h2 id={id} className="h-display">{title}</h2>
      <span className="section-heading__bar" aria-hidden="true" />
      {children}
    </div>
  );
}
