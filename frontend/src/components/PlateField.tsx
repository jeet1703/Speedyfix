/** The yellow UK number-plate input, in a smaller size for forms. Pair it with a visible or sr-only label. */
export function PlateField({ id, value, onChange, invalid, describedBy, required, size = 'md' }: {
  id: string;
  value: string;
  onChange: (value: string) => void;
  invalid?: boolean;
  describedBy?: string;
  required?: boolean;
  size?: 'sm' | 'md';
}) {
  return (
    <div className={`plate-input plate-input--${size}`}>
      <span className="plate-input__band" aria-hidden="true">UK</span>
      <input id={id} name="registration" value={value} onChange={(e) => onChange(e.target.value)} placeholder="AB12 CDE"
        maxLength={9} autoComplete="off" spellCheck={false} aria-invalid={invalid || undefined}
        aria-describedby={describedBy} required={required} />
    </div>
  );
}
