import '@testing-library/jest-dom/vitest';

const store: Record<string, string> = {};
Object.defineProperty(window, 'localStorage', {
  value: {
    getItem: (key: string) => store[key] ?? null,
    setItem: (key: string, value: string) => { store[key] = value; },
    removeItem: (key: string) => { delete store[key]; },
    clear: () => { for (const k of Object.keys(store)) delete store[k]; },
    get length() { return Object.keys(store).length; },
    key: (index: number) => Object.keys(store)[index] ?? null,
  },
});

const sessionStore: Record<string, string> = {};
Object.defineProperty(window, 'sessionStorage', {
  value: {
    getItem: (key: string) => sessionStore[key] ?? null,
    setItem: (key: string, value: string) => { sessionStore[key] = value; },
    removeItem: (key: string) => { delete sessionStore[key]; },
    clear: () => { for (const k of Object.keys(sessionStore)) delete sessionStore[k]; },
    get length() { return Object.keys(sessionStore).length; },
    key: (index: number) => Object.keys(sessionStore)[index] ?? null,
  },
});

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }),
});

const noop = () => {};
const createEmptyStyle = () => ({
  getPropertyValue: () => '',
  getPropertyPriority: () => '',
  item: () => '',
  get length() { return 0; },
  setProperty: noop,
  removeProperty: () => '',
} as unknown as CSSStyleDeclaration);

window.getComputedStyle = (elt: Element, _pseudoElt?: string | null) => createEmptyStyle();
