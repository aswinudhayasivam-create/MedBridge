import { useState } from 'react';
export default function DateEntry({ value, onChange, time = false }) {
  const [manual, setManual] = useState(false);
  return <span className="demo-date-entry"><input required type={manual ? 'text' : time ? 'datetime-local' : 'date'}
    value={value} onChange={onChange} placeholder={time ? 'YYYY-MM-DDTHH:mm' : 'YYYY-MM-DD'}
    {...(manual ? { pattern: time ? '[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}' : '[0-9]{4}-[0-9]{2}-[0-9]{2}', maxLength: time ? 16 : 10 } : {})}/>
    <button type="button" onClick={() => setManual(!manual)}>{manual ? 'Use date picker' : 'Enter date manually'}</button></span>;
}
