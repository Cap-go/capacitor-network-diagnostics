import { CapacitorUpdater } from '@capgo/capacitor-updater';
import { Capacitor } from '@capacitor/core';
import './style.css';
import { NetworkDiagnostics } from '@capgo/capacitor-network-diagnostics';

const output = document.getElementById('plugin-output');
const urlInput = document.getElementById('url-value');
const hostInput = document.getElementById('host-value');
const portInput = document.getElementById('port-value');
const websocketInput = document.getElementById('websocket-value');
const statusButton = document.getElementById('get-status');
const diagnosticsButton = document.getElementById('run-diagnostics');
const versionButton = document.getElementById('get-version');

const setOutput = (value) => {
  output.textContent = typeof value === 'string' ? value : JSON.stringify(value, null, 2);
};

statusButton.addEventListener('click', async () => {
  try {
    const result = await NetworkDiagnostics.getNetworkStatus();
    setOutput(result);
  } catch (error) {
    setOutput(`Error: ${error?.message ?? error}`);
  }
});

diagnosticsButton.addEventListener('click', async () => {
  try {
    const host = hostInput.value.trim();
    const port = Number.parseInt(portInput.value, 10);
    const websocket = websocketInput.value.trim();
    const url = urlInput.value.trim();

    const result = await NetworkDiagnostics.runDiagnostics({
      download: url ? { maxBytes: 1024 * 1024, url } : undefined,
      packetLoss: host && port ? { count: 5, host, mode: 'tcp', port } : undefined,
      ports: host && port ? [{ host, port }] : [],
      urls: url ? [{ method: 'HEAD', url }] : [],
      websockets: websocket ? [{ url: websocket }] : [],
    });

    setOutput(result);
  } catch (error) {
    setOutput(`Error: ${error?.message ?? error}`);
  }
});

versionButton.addEventListener('click', async () => {
  try {
    const result = await NetworkDiagnostics.getPluginVersion();
    setOutput(result);
  } catch (error) {
    setOutput(`Error: ${error?.message ?? error}`);
  }
});

if (Capacitor.isNativePlatform()) {
  CapacitorUpdater.notifyAppReady().catch((error) => {
    console.error('Capgo notifyAppReady failed', error);
  });
}
