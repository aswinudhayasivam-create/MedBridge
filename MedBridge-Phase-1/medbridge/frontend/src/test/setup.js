import '@testing-library/jest-dom/vitest';
import { vi } from 'vitest';
if (!window.scrollTo) window.scrollTo = vi.fn();
window.scrollTo = vi.fn();
Element.prototype.scrollIntoView = vi.fn();
