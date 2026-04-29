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
    const unlisten = listen<Device>("pairing-request", (event) => {
      setIncomingRequest(event.payload);
    });

    return () => {
      unlisten.then((f) => f());
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
      setConnectedMac(mac);
      const updated = await invoke<Device[]>("get_previous_devices");
      setPreviousDevices(updated);
    } catch (err) {
      setError(err as string);
    } finally {
      setConnectingMac(null);
    }
  };

  const respondToPairingRequest = async (accept: boolean) => {
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
      setError(err as string);
    } finally {
      setConnectingMac(null);
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
