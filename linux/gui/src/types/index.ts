export interface Device {
  name: string;
  mac_address: string;
}

export interface SocketMessage {
  type: string;
  sender_id: string;
  payload: string;
  timestamp: number;
}

export type ErrorKind = "permission" | "generic" | null;
