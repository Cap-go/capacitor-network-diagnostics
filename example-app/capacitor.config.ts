import type { CapacitorConfig } from '@capacitor/cli';

import pkg from './package.json';

const config: CapacitorConfig = {
  "appId": "app.capgo.networkdiagnostics.example",
  "appName": "Network Diagnostics Example",
  "webDir": "dist",
  "plugins": {
    "CapacitorUpdater": {
      "appId": "app.capgo.networkdiagnostics.example",
      "autoUpdate": true,
      "autoSplashscreen": true,
      "directUpdate": "always",
      "defaultChannel": "production",
      "version": pkg.version
    }
  }
};

export default config;
