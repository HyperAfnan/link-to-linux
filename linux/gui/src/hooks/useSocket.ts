import { useState, useEffect } from "react";
import { listen } from "@tauri-apps/api/event";
import { SocketMessage } from "../types";

export function useSocket() {
  const [messages, setMessages] = useState<SocketMessage[]>([]);
  const [lastMessage, setLastMessage] = useState<SocketMessage | null>(null);

  useEffect(() => {
    const unlisten = listen<SocketMessage>("on_message", (event) => {
      setLastMessage(event.payload);
      setMessages((prev) => [...prev, event.payload]);
    });

    return () => {
      unlisten.then((f) => f());
    };
  }, []);

  return {
    messages,
    lastMessage,
    setMessages
  };
}
