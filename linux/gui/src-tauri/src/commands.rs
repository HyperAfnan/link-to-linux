use crate::models::{Device, SocketMessage};
use crate::p2p::wifi_manager;
use tauri::{AppHandle, Emitter, State};
use tokio::sync::broadcast;
use std::fs;

fn is_valid_mac(mac: &str) -> bool {
    let clean = mac.trim();
    if clean.len() != 17 { return false; }
    let parts: Vec<&str> = clean.split(':').collect();
    if parts.len() != 6 { return false; }
    parts.iter().all(|p| p.len() == 2 && p.chars().all(|c| c.is_ascii_hexdigit()))
}

#[derive(serde::Serialize, Clone)]
struct ConnectionStatusPayload {
    status: String,
    mac_address: String,
}

#[tauri::command]
pub fn get_previous_devices(app: AppHandle) -> Vec<Device> {
    wifi_manager::load_previous_devices(&app)
}

#[tauri::command]
pub async fn scan_peers() -> Result<Vec<Device>, String> {
    wifi_manager::scan_peers().await
}

#[tauri::command]
pub async fn connect_to_peer(mac: String, name: String, app: AppHandle) -> Result<(), String> {
    if !is_valid_mac(&mac) { return Err("Invalid MAC address".to_string()); }

    let iface = wifi_manager::P2P_IFACE;
    let output = wifi_manager::execute_wpa_cli(&["-i", iface, "p2p_connect", &mac, "pbc", "go_intent=15"]).await?;

    if !output.trim().to_lowercase().contains("ok") {
        return Err(format!("wpa_cli p2p_connect failed: {}", output.trim()));
    }

    let mac_clone = mac.clone();
    tauri::async_runtime::spawn(async move {
        if let Ok(true) = wifi_manager::wait_for_connection(iface, &mac_clone).await {
            let device = Device { name: name.clone(), mac_address: mac_clone.clone() };
            let _ = wifi_manager::persist_device(&app, &device);
            let _ = app.emit("connection_status", ConnectionStatusPayload { status: "success".to_string(), mac_address: mac_clone });
        } else {
            let _ = app.emit("connection_status", ConnectionStatusPayload { status: "failed".to_string(), mac_address: mac_clone });
        }
    });

    Ok(())
}

#[tauri::command]
pub async fn respond_to_pairing_request(mac: String, accept: bool, app: AppHandle) -> Result<(), String> {
    if !is_valid_mac(&mac) { return Err("Invalid MAC address".to_string()); }

    let iface = wifi_manager::P2P_IFACE;
    if accept {
        wifi_manager::execute_wpa_cli(&["-i", iface, "p2p_connect", &mac, "pbc", "go_intent=15"]).await?;
        
        let mac_clone = mac.clone();
        tauri::async_runtime::spawn(async move {
            if let Ok(true) = wifi_manager::wait_for_connection(iface, &mac_clone).await {
                let device = Device { name: "New Peer".to_string(), mac_address: mac_clone.clone() };
                let _ = wifi_manager::persist_device(&app, &device);
                let _ = app.emit("connection_status", ConnectionStatusPayload { status: "success".to_string(), mac_address: mac_clone });
            } else {
                let _ = app.emit("connection_status", ConnectionStatusPayload { status: "failed".to_string(), mac_address: mac_clone });
            }
        });
    } else {
        wifi_manager::execute_wpa_cli(&["-i", iface, "p2p_reject", &mac]).await?;
    }

    Ok(())
}

#[tauri::command]
pub fn remove_device(mac: String, app: AppHandle) -> Result<Vec<Device>, String> {
    if !is_valid_mac(&mac) { return Err("Invalid MAC address".to_string()); }
    
    let path = wifi_manager::devices_file_path(&app)?;
    let mut devices = wifi_manager::load_previous_devices(&app);
    
    devices.retain(|d| d.mac_address != mac);
    
    let json = serde_json::to_string_pretty(&devices)
        .map_err(|e| format!("Failed to serialize devices: {}", e))?;
    fs::write(&path, json).map_err(|e| format!("Failed to write devices file: {}", e))?;
    
    Ok(devices)
}

#[tauri::command]
pub fn send_socket_message(
    payload: SocketMessage,
    tx: State<'_, broadcast::Sender<SocketMessage>>
) -> Result<(), String> {
    tx.send(payload).map_err(|e| format!("Failed to send: {}", e))?;
    Ok(())
}
