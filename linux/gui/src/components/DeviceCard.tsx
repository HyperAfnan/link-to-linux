import { Device } from "../types";
import { PhoneIcon, CheckIcon, SpinnerIcon } from "./Icons";

interface DeviceCardProps {
  device: Device;
  connecting: boolean;
  connected: boolean;
  disabled: boolean;
  onClick: () => void;
  onContextMenu?: (e: React.MouseEvent) => void;
}

export function DeviceCard({
  device,
  connecting,
  connected,
  disabled,
  onClick,
  onContextMenu,
}: DeviceCardProps) {
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
