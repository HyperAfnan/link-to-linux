import { useState, useEffect } from "react";
import { invoke } from "@tauri-apps/api/core";
import { TopBar } from "./components/TopBar";
import { DeviceCard } from "./components/DeviceCard";
import { 
  ScanButton, 
  EmptyState, 
  PermissionBanner, 
  GenericErrorBanner, 
  PairingRequestBanner,
  ContextMenu 
} from "./components/Common";
import { useP2P } from "./hooks/useP2P";
import { useSocket } from "./hooks/useSocket";
import { CheckIcon, SpinnerIcon } from "./components/Icons";

function App() {
  const {
    nearbyDevices,
    previousDevices,
    scanning,
    connectingMac,
    connectedMac,
    error,
    incomingRequest,
    scanPeers,
    connectToPeer,
    respondToPairingRequest,
    removeDevice
  } = useP2P();

  const { lastMessage } = useSocket();
  const [contextMenu, setContextMenu] = useState<{ x: number; y: number; mac: string } | null>(null);

  useEffect(() => {
    if (lastMessage) {
      const ack = {
        type: "ACK",
        sender_id: "linux_pc",
        payload: `ACK for ${lastMessage.type}`,
        timestamp: Date.now(),
      };
      invoke("send_socket_message", { payload: ack }).catch(console.error);
    }
  }, [lastMessage]);

  const isPermissionError = error?.toLowerCase().includes("permission denied");

  return (
    <div className="h-screen w-screen bg-gb-bg flex flex-col border border-gb-bg3 rounded-xl overflow-hidden">
      <TopBar />

      {/* ── Error banners ── */}
      {error && (
        <div className="px-8 pt-5 shrink-0">
          {isPermissionError ? <PermissionBanner /> : <GenericErrorBanner message={error} />}
        </div>
      )}

      {/* ── Incoming Pairing Request ── */}
      {incomingRequest && (
        <PairingRequestBanner
          device={incomingRequest}
          onAccept={() => respondToPairingRequest(true)}
          onReject={() => respondToPairingRequest(false)}
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
                    onContextMenu={(e) => setContextMenu({ x: e.clientX, y: e.clientY, mac: device.mac_address })}
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
          {lastMessage && (
             <span className="text-gb-fg4 font-mono text-[10px] opacity-50 truncate max-w-[200px]">
               Last msg: {lastMessage.type}
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
