import { useState, useEffect } from "react";
import { listen } from "@tauri-apps/api/event";
import { SocketMessage } from "../types";

export function useSocket() {
  const [lastMessage, setLastMessage] = useState<SocketMessage | null>(null);

  useEffect(() => {
    const unlisten = listen<SocketMessage>("on_message", (event) => {
      setLastMessage(event.payload);
    });

    return () => {
      unlisten.then((f) => f());
    };
  }, []);

  return {
    lastMessage,
  };
}
