import { useState, useEffect } from "react";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { getCurrentWebviewWindow } from "@tauri-apps/api/webviewWindow";
import "./App.css";

const appWindow = getCurrentWebviewWindow();

// ─── Types ───────────────────────────────────────────────────────────────────

interface Device {
  name: string;
  mac_address: string;
}

type ErrorKind = "permission" | "generic" | null;

// ─── Helpers ─────────────────────────────────────────────────────────────────

function classifyError(err: string): ErrorKind {
  if (
    err.toLowerCase().includes("permission denied") ||
    err.toLowerCase().includes("cannot open")
  ) {
    return "permission";
  }
  return "generic";
}

// ─── Icons ───────────────────────────────────────────────────────────────────

function PhoneIcon({ className = "w-5 h-5" }: { className?: string }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
        d="M12 18h.01M8 21h8a2 2 0 002-2V5a2 2 0 00-2-2H8a2 2 0 00-2 2v14a2 2 0 002 2z" />
    </svg>
  );
}

function ReloadIcon({ className = "w-4 h-4" }: { className?: string }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
    </svg>
  );
}

function SpinnerIcon({ className = "w-4 h-4" }: { className?: string }) {
  return (
    <svg className={`animate-spin ${className}`} xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
      <path className="opacity-75" fill="currentColor"
        d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
    </svg>
  );
}

function CheckIcon({ className = "w-4 h-4" }: { className?: string }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M5 13l4 4L19 7" />
    </svg>
  );
}

function TrashIcon({ className = "w-4 h-4" }: { className?: string }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
    </svg>
  );
}

function ContextMenu({
  x,
  y,
  onClose,
  onRemove,
}: {
  x: number;
  y: number;
  onClose: () => void;
  onRemove: () => void;
}) {
  useEffect(() => {
    const handleClick = () => onClose();
    window.addEventListener("click", handleClick);
    return () => window.removeEventListener("click", handleClick);
  }, [onClose]);

  return (
    <div
      className="fixed z-50 bg-gb-bg1 border border-gb-bg3 rounded shadow-xl overflow-hidden min-w-[140px]"
      style={{ top: y, left: x }}
    >
      <button
        onClick={onRemove}
        className="w-full flex items-center gap-2 px-3 py-2 text-xs font-mono text-gb-red-br hover:bg-gb-red/10 transition-colors"
      >
        <TrashIcon className="w-3.5 h-3.5" />
        Remove Device
      </button>
    </div>
  );
}

// ─── Sub-components ───────────────────────────────────────────────────────────

function ScanButton({ scanning, onClick }: { scanning: boolean; onClick: () => void }) {
  return (
    <button
      id="scan-btn"
      onClick={onClick}
      disabled={scanning}
      title="Scan for nearby Wi-Fi Direct devices"
      className={[
        "w-8 h-8 flex items-center justify-center rounded",
        "text-gb-bg bg-gb-yellow transition-all duration-200",
        scanning
          ? "opacity-80 cursor-not-allowed scan-pulse"
          : "hover:bg-gb-yellow-br active:scale-95 cursor-pointer",
      ].join(" ")}
    >
      <ReloadIcon className={["w-4 h-4", scanning ? "animate-spin" : ""].join(" ")} />
    </button>
  );
}

function DeviceCard({
  device,
  connecting,
  connected,
  disabled,
  onClick,
  onContextMenu,
}: {
  device: Device;
  connecting: boolean;
  connected: boolean;
  disabled: boolean;
  onClick: () => void;
  onContextMenu?: (e: React.MouseEvent) => void;
}) {
  return (
    <button
      onClick={onClick}
      onContextMenu={(e) => {
        if (onContextMenu) {
          e.preventDefault();
          onContextMenu(e);
        }
      }}
      disabled={disabled || connected}
      className={[
        "w-full flex items-center gap-3 px-4 py-3 rounded-lg",
        "border text-left transition-all duration-150",
        connected
          ? "border-gb-green bg-gb-bg-hard text-gb-green-br cursor-default"
          : connecting
          ? "border-gb-yellow bg-gb-bg-hard text-gb-fg connecting-ring cursor-wait"
          : disabled
          ? "border-gb-bg2 bg-gb-bg-hard text-gb-fg4 cursor-not-allowed opacity-60"
          : "border-gb-bg2 bg-gb-bg-hard text-gb-fg hover:border-gb-yellow hover:text-gb-yellow-br active:scale-[0.98] cursor-pointer",
      ].join(" ")}
    >
      <span className={connected ? "text-gb-green-br" : connecting ? "text-gb-yellow" : "text-gb-fg4"}>
        {connected ? <CheckIcon /> : connecting ? <SpinnerIcon /> : <PhoneIcon />}
      </span>
      <span className="font-mono text-sm font-medium truncate flex-1">{device.name}</span>
      <span className="text-xs font-mono opacity-40 shrink-0 hidden sm:block">{device.mac_address}</span>
    </button>
  );
}

function EmptyState({ message, sub }: { message: string; sub: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-10 text-gb-fg4">
      <PhoneIcon className="w-10 h-10 mb-3 opacity-30" />
      <p className="text-sm font-medium">{message}</p>
      <p className="text-xs mt-1 opacity-60">{sub}</p>
    </div>
  );
}

function PermissionBanner() {
  return (
    <div className="mb-6 rounded-lg border border-gb-yellow bg-gb-bg-hard px-5 py-4">
      <p className="text-gb-yellow font-mono text-xs font-semibold uppercase tracking-widest mb-2">
        ⚠ Permission Denied — Setup Required
      </p>
      <p className="text-gb-fg3 text-xs mb-3">
        <code className="text-gb-yellow-br">wpa_cli</code> cannot access{" "}
        <code className="text-gb-fg">/run/wpa_supplicant</code>. Run once in your terminal:
      </p>
      <pre className="bg-gb-bg rounded px-3 py-2 text-xs font-mono text-gb-green-br whitespace-pre-wrap mb-2">{`sudo mkdir -p /etc/systemd/system/wpa_supplicant.service.d
sudo tee /etc/systemd/system/wpa_supplicant.service.d/ctrl-interface-group.conf << 'EOF'
[Service]
ExecStart=
ExecStart=/usr/bin/wpa_supplicant -u -s -O /run/wpa_supplicant -G wheel
EOF
sudo systemctl daemon-reload && sudo systemctl restart wpa_supplicant
sudo chgrp wheel /run/wpa_supplicant && sudo chmod g+x /run/wpa_supplicant`}</pre>
      <p className="text-gb-fg4 text-xs">No logout required — you are already in the <code className="text-gb-fg">wheel</code> group.</p>
    </div>
  );
}

function PairingRequestBanner({
  device,
  onAccept,
  onReject,
}: {
  device: Device;
  onAccept: () => void;
  onReject: () => void;
}) {
  return (
    <div className="mx-8 mt-4 p-4 bg-gb-bg-hard border-2 border-gb-yellow rounded-xl flex items-center justify-between animate-bounce">
      <div className="flex items-center gap-4">
        <div className="w-10 h-10 rounded-full bg-gb-yellow/20 flex items-center justify-center text-gb-yellow">
          <PhoneIcon className="w-5 h-5" />
        </div>
        <div>
          <p className="text-gb-fg font-mono text-sm font-bold uppercase tracking-tight">Incoming Request</p>
          <p className="text-gb-fg4 text-xs font-mono">{device.mac_address}</p>
        </div>
      </div>
      <div className="flex items-center gap-2">
        <button
          onClick={onReject}
          className="px-4 py-2 text-xs font-mono font-bold text-gb-red-br hover:bg-gb-red/10 rounded transition-colors"
        >
          REJECT
        </button>
        <button
          onClick={onAccept}
          className="px-6 py-2 bg-gb-yellow text-gb-bg text-xs font-mono font-bold rounded hover:bg-gb-yellow-br transition-all active:scale-95"
        >
          ACCEPT
        </button>
      </div>
    </div>
  );
}

function GenericErrorBanner({ message }: { message: string }) {
  return (
    <div className="mb-6 rounded-lg border border-gb-red bg-gb-bg-hard px-5 py-3">
      <p className="text-gb-red-br font-mono text-xs font-semibold uppercase tracking-widest mb-1">Error</p>
      <p className="text-gb-fg3 text-xs font-mono">{message}</p>
    </div>
  );
}

// ─── Main App ─────────────────────────────────────────────────────────────────

function App() {
  const [previousDevices, setPreviousDevices] = useState<Device[]>([]);
  const [nearbyDevices, setNearbyDevices] = useState<Device[]>([]);
  const [scanning, setScanning] = useState(false);
  const [connectingMac, setConnectingMac] = useState<string | null>(null);
  const [connectedMac, setConnectedMac] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState("");
  const [errorKind, setErrorKind] = useState<ErrorKind>(null);

  const [contextMenu, setContextMenu] = useState<{ x: number; y: number; mac: string } | null>(null);
  const [incomingRequest, setIncomingRequest] = useState<Device | null>(null);

  // Load previously connected devices from disk on mount
  useEffect(() => {
    invoke<Device[]>("get_previous_devices")
      .then(setPreviousDevices)
      .catch(console.error);

    // Listen for incoming pairing requests from wpa_supplicant
    const unlisten = listen<Device>("pairing-request", (event) => {
      setIncomingRequest(event.payload);
    });

    return () => {
      unlisten.then((f) => f());
    };
  }, []);

  function handleError(err: unknown) {
    const msg = err as string;
    setErrorMsg(msg);
    setErrorKind(classifyError(msg));
  }

  async function handlePairingResponse(accept: boolean) {
    if (!incomingRequest) return;
    const mac = incomingRequest.mac_address;
    setIncomingRequest(null);
    if (accept) setConnectingMac(mac);

    try {
      const success = await invoke<boolean>("respond_to_pairing_request", { mac, accept });
      if (accept && success) {
        setConnectedMac(mac);
        const updated = await invoke<Device[]>("get_previous_devices");
        setPreviousDevices(updated);
      }
    } catch (err) {
      handleError(err);
    } finally {
      setConnectingMac(null);
    }
  }

  function handleContextMenu(e: React.MouseEvent, mac: string) {
    setContextMenu({ x: e.clientX, y: e.clientY, mac });
  }

  async function removeDevice(mac: string) {
    try {
      const updated = await invoke<Device[]>("remove_device", { mac });
      setPreviousDevices(updated);
      if (connectedMac === mac) setConnectedMac(null);
    } catch (err) {
      handleError(err);
    }
  }

  async function scanPeers() {
    setScanning(true);
    setNearbyDevices([]);
    setErrorMsg("");
    setErrorKind(null);
    setConnectedMac(null);
    try {
      const found = await invoke<Device[]>("scan_peers");
      setNearbyDevices(found);
    } catch (err) {
      handleError(err);
    } finally {
      setScanning(false);
    }
  }

  async function connectToPeer(mac: string, name: string) {
    if (connectingMac || connectedMac === mac) return;
    setConnectingMac(mac);
    setErrorMsg("");
    setErrorKind(null);
    try {
      await invoke<boolean>("connect_to_peer", { mac, name });
      setConnectedMac(mac);
      // Refresh previous devices list since the backend persisted this device
      const updated = await invoke<Device[]>("get_previous_devices");
      setPreviousDevices(updated);
    } catch (err) {
      handleError(err);
    } finally {
      setConnectingMac(null);
    }
  }

  return (
    <div className="h-screen w-screen bg-gb-bg flex flex-col border border-gb-bg3 rounded-xl overflow-hidden">
      {/* ── Header / Custom Title Bar ── */}
      <div 
        data-tauri-drag-region 
        className="px-8 pt-7 pb-5 border-b border-gb-bg2 shrink-0 flex items-center justify-between select-none"
      >
        <div className="w-24"></div> {/* Spacer for symmetry */}
        
        <h1 className="font-mono text-2xl font-bold tracking-tight text-gb-fg pointer-events-none">
          Link To Linux
        </h1>

        <div className="flex items-center gap-2 w-24 justify-end">
          <button 
            onClick={() => appWindow.minimize()}
            className="w-8 h-8 flex items-center justify-center rounded hover:bg-gb-bg2 transition-colors text-gb-fg4 hover:text-gb-fg"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M20 12H4" />
            </svg>
          </button>
          <button 
            onClick={() => appWindow.toggleMaximize()}
            className="w-8 h-8 flex items-center justify-center rounded hover:bg-gb-bg2 transition-colors text-gb-fg4 hover:text-gb-fg"
          >
            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <rect x="3" y="3" width="18" height="18" rx="2" strokeWidth={2} />
            </svg>
          </button>
          <button 
            onClick={() => appWindow.close()}
            className="w-8 h-8 flex items-center justify-center rounded hover:bg-gb-red/20 text-gb-fg4 hover:text-gb-red-br transition-colors"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
      </div>

      {/* ── Error banners ── */}
      {errorKind && (
        <div className="px-8 pt-5 shrink-0">
          {errorKind === "permission" ? <PermissionBanner /> : <GenericErrorBanner message={errorMsg} />}
        </div>
      )}

      {/* ── Incoming Pairing Request ── */}
      {incomingRequest && (
        <PairingRequestBanner
          device={incomingRequest}
          onAccept={() => handlePairingResponse(true)}
          onReject={() => handlePairingResponse(false)}
        />
      )}

      {/* ── Two-column content ── */}
      <div className="flex-1 grid grid-cols-2 divide-x divide-gb-bg2 overflow-hidden">
        {/* ── Left: Previous Devices ── */}
        <div className="px-6 py-6 flex flex-col overflow-hidden">
          <h2 className="text-gb-yellow font-mono text-xs font-semibold uppercase tracking-widest mb-4 shrink-0">
            Previous Devices
          </h2>
          <div className="flex-1 overflow-y-auto pr-1">
            {previousDevices.length === 0 ? (
              <EmptyState message="No history yet" sub="Connect to a device to see it here" />
            ) : (
              <div className="flex flex-col gap-2">
                {previousDevices.map((device) => (
                  <DeviceCard
                    key={device.mac_address}
                    device={device}
                    connecting={connectingMac === device.mac_address}
                    connected={connectedMac === device.mac_address}
                    disabled={connectingMac !== null && connectingMac !== device.mac_address}
                    onClick={() => connectToPeer(device.mac_address, device.name)}
                    onContextMenu={(e) => handleContextMenu(e, device.mac_address)}
                  />
                ))}
              </div>
            )}
          </div>
        </div>

        {/* ── Right: Nearby Devices ── */}
        <div className="px-6 py-6 flex flex-col overflow-hidden">
          <div className="flex items-center justify-between mb-4 shrink-0">
            <h2 className="text-gb-yellow font-mono text-xs font-semibold uppercase tracking-widest">
              Nearby Devices
            </h2>
            <ScanButton scanning={scanning} onClick={scanPeers} />
          </div>
          <div className="flex-1 overflow-y-auto pr-1">
            {scanning ? (
              <EmptyState message="Scanning for peers…" sub="This takes about 10 seconds" />
            ) : nearbyDevices.length === 0 ? (
              <EmptyState message="No devices found" sub="Press the scan button to discover peers" />
            ) : (
              <div className="flex flex-col gap-2">
                {nearbyDevices.map((device) => (
                  <DeviceCard
                    key={device.mac_address}
                    device={device}
                    connecting={connectingMac === device.mac_address}
                    connected={connectedMac === device.mac_address}
                    disabled={connectingMac !== null && connectingMac !== device.mac_address}
                    onClick={() => connectToPeer(device.mac_address, device.name)}
                  />
                ))}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* ── Footer ── */}
      <div className="px-8 py-3 border-t border-gb-bg2 flex items-center justify-between shrink-0">
        <span className="text-gb-bg4 font-mono text-xs">Wi-Fi Direct · P2P</span>
        <div className="flex items-center gap-4">
          {connectedMac && (
            <span className="text-gb-green-br font-mono text-xs flex items-center gap-1.5">
              <CheckIcon className="w-3.5 h-3.5" />
              Connected
            </span>
          )}
          {scanning && (
            <span className="text-gb-yellow font-mono text-xs flex items-center gap-1.5">
              <SpinnerIcon className="w-3 h-3 text-gb-yellow" />
              Scanning…
            </span>
          )}
        </div>
      </div>

      {contextMenu && (
        <ContextMenu
          x={contextMenu.x}
          y={contextMenu.y}
          onClose={() => setContextMenu(null)}
          onRemove={() => removeDevice(contextMenu.mac)}
        />
      )}
    </div>
  );
}

export default App;
