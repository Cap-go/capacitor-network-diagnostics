import { registerPlugin } from '@capacitor/core';

import type { NetworkDiagnosticsPlugin } from './definitions';

const NetworkDiagnostics = registerPlugin<NetworkDiagnosticsPlugin>('NetworkDiagnostics', {
  web: () => import('./web').then((m) => new m.NetworkDiagnosticsWeb()),
});

export * from './definitions';
export { NetworkDiagnostics };
