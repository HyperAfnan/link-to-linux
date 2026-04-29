import { getCurrentWebviewWindow } from "@tauri-apps/api/webviewWindow";

const appWindow = getCurrentWebviewWindow();

export function TopBar() {
  return (
    <div 
      data-tauri-drag-region 
      className="px-8 pt-7 pb-5 border-b border-gb-bg2 shrink-0 flex items-center justify-between select-none"
    >
      <div className="w-24"></div>
      
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
  );
}
