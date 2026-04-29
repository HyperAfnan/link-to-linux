import { PhoneIcon, ReloadIcon, SpinnerIcon, CheckIcon, TrashIcon } from "./Icons";
import { Device, ErrorKind } from "../types";
import { useEffect } from "react";

export function ScanButton({ scanning, onClick }: { scanning: boolean; onClick: () => void }) {
  return (
    <button
      id="scan-btn"
      onClick={onClick}
      disabled={scanning}
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

export function EmptyState({ message, sub }: { message: string; sub: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-10 text-gb-fg4">
      <PhoneIcon className="w-10 h-10 mb-3 opacity-30" />
      <p className="text-sm font-medium">{message}</p>
      <p className="text-xs mt-1 opacity-60">{sub}</p>
    </div>
  );
}

export function PermissionBanner() {
  return (
    <div className="mb-6 rounded-lg border border-gb-yellow bg-gb-bg-hard px-5 py-4">
      <p className="text-gb-yellow font-mono text-xs font-semibold uppercase tracking-widest mb-2">
        ⚠ Permission Denied — Setup Required
      </p>
      <p className="text-gb-fg3 text-xs mb-3">
        wpa_cli cannot access /run/wpa_supplicant. Run the setup commands.
      </p>
    </div>
  );
}

export function GenericErrorBanner({ message }: { message: string }) {
  return (
    <div className="mb-6 rounded-lg border border-gb-red bg-gb-bg-hard px-5 py-3">
      <p className="text-gb-red-br font-mono text-xs font-semibold uppercase tracking-widest mb-1">Error</p>
      <p className="text-gb-fg3 text-xs font-mono">{message}</p>
    </div>
  );
}

export function PairingRequestBanner({
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

export function ContextMenu({
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
