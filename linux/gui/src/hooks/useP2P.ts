import { useState, useEffect } from "react";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { Device } from "../types";

export function useP2P() {
  const [nearbyDevices, setNearbyDevices] = useState<Device[]>([]);
  const [previousDevices, setPreviousDevices] = useState<Device[]>([]);
  const [scanning, setScanning] = useState(false);
  const [connectingMac, setConnectingMac] = useState<string | null>(null);
  const [connectedMac, setConnectedMac] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [incomingRequest, setIncomingRequest] = useState<Device | null>(null);

  useEffect(() => {
    // Load initial devices
    invoke<Device[]>("get_previous_devices")
      .then(setPreviousDevices)
      .catch((err) => setError(err as string));

    // Listen for incoming pairing requests
    const unlistenPairing = listen<Device>("pairing-request", (event) => {
      setIncomingRequest(event.payload);
    });

    const unlistenConnection = listen<{ status: string; mac_address: string }>("connection_status", async (event) => {
      setConnectingMac(null);
      if (event.payload.status === "success") {
        setConnectedMac(event.payload.mac_address);
        // Refresh previous devices list since a new connection likely wrote to disk
        try {
          const updated = await invoke<Device[]>("get_previous_devices");
          setPreviousDevices(updated);
        } catch (err) {
          console.error("Failed to load previous devices", err);
        }
      } else {
        setError(`Failed to connect to ${event.payload.mac_address}`);
      }
    });

    return () => {
      unlistenPairing.then((f) => f());
      unlistenConnection.then((f) => f());
    };
  }, []);

  const scanPeers = async () => {
    setScanning(true);
    setError(null);
    try {
      const found = await invoke<Device[]>("scan_peers");
      setNearbyDevices(found);
    } catch (err) {
      setError(err as string);
    } finally {
      setScanning(false);
    }
  };

  const connectToPeer = async (mac: string, name: string) => {
    if (connectingMac || connectedMac === mac) return;
    setConnectingMac(mac);
    setError(null);
    try {
      await invoke("connect_to_peer", { mac, name });
    } catch (err) {
      setError(err as string);
      setConnectingMac(null);
    }
  };

  const respondToPairingRequest = async (accept: boolean) => {
    if (!incomingRequest) return;
    const mac = incomingRequest.mac_address;
    setIncomingRequest(null);
    
    if (accept) {
      setConnectingMac(mac);
      setError(null);
    }

    try {
      await invoke("respond_to_pairing_request", { mac, accept });
    } catch (err) {
      setError(err as string);
      if (accept) setConnectingMac(null);
    }
  };

  const removeDevice = async (mac: string) => {
    try {
      const updated = await invoke<Device[]>("remove_device", { mac });
      setPreviousDevices(updated);
      if (connectedMac === mac) setConnectedMac(null);
    } catch (err) {
      setError(err as string);
    }
  };

  return {
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
    removeDevice,
    setIncomingRequest
  };
}
