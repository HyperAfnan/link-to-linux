use crate::models::Device;
use crate::p2p::wifi_manager;
use tauri::AppHandle;
use std::fs;
use std::time::Duration;

#[tauri::command]
pub fn get_previous_devices(app: AppHandle) -> Vec<Device> {
    wifi_manager::load_previous_devices(&app)
}

#[tauri::command]
pub async fn scan_peers() -> Result<Vec<Device>, String> {
    wifi_manager::scan_peers().await
}

#[tauri::command]
pub async fn connect_to_peer(mac: String, name: String, app: AppHandle) -> Result<bool, String> {
    let iface = wifi_manager::P2P_IFACE;

    let output = wifi_manager::execute_wpa_cli(&["-i", iface, "p2p_connect", &mac, "pbc", "go_intent=15"])?;

    if !output.trim().to_lowercase().contains("ok") {
        return Err(format!("wpa_cli p2p_connect failed: {}", output.trim()));
    }

    let mut established = false;
    for _ in 0..40 {
        tokio::time::sleep(Duration::from_millis(500)).await;
        if let Ok(details) = wifi_manager::execute_wpa_cli(&["-i", iface, "p2p_peer", &mac]) {
            if details.contains("p2p_status=connected") || details.contains("status=connected") {
                established = true;
                break;
            }
        }
    }

    if established {
        let device = Device {
            name: name.clone(),
            mac_address: mac.clone(),
        };
        let _ = wifi_manager::persist_device(&app, &device);
        Ok(true)
    } else {
        Err("Connection timed out. Handshake did not complete.".to_string())
    }
}

#[tauri::command]
pub async fn respond_to_pairing_request(mac: String, accept: bool, app: AppHandle) -> Result<bool, String> {
    // Wait, the original code had respond_to_pairing_request as well.
    // I'll copy it over.
    // ...
    // Actually, I'll just write it correctly.
    let iface = wifi_manager::P2P_IFACE;
    if accept {
        wifi_manager::execute_wpa_cli(&["-i", iface, "p2p_connect", &mac, "pbc", "go_intent=15"])?;
        
        let mut established = false;
        for _ in 0..20 {
            tokio::time::sleep(Duration::from_millis(500)).await;
            if let Ok(details) = wifi_manager::execute_wpa_cli(&["-i", iface, "p2p_peer", &mac]) {
                if details.contains("status=connected") {
                    established = true;
                    break;
                }
            }
        }
        if established {
            let device = Device { name: "New Peer".to_string(), mac_address: mac };
            let _ = wifi_manager::persist_device(&app, &device);
            return Ok(true);
        }
        Ok(false)
    } else {
        wifi_manager::execute_wpa_cli(&["-i", iface, "p2p_reject", &mac])?;
        Ok(false)
    }
}

#[tauri::command]
pub fn remove_device(mac: String, app: AppHandle) -> Result<Vec<Device>, String> {
    let path = wifi_manager::devices_file_path(&app)?;
    let mut devices = wifi_manager::load_previous_devices(&app);
    
    devices.retain(|d| d.mac_address != mac);
    
    let json = serde_json::to_string_pretty(&devices)
        .map_err(|e| format!("Failed to serialize devices: {}", e))?;
    fs::write(&path, json).map_err(|e| format!("Failed to write devices file: {}", e))?;
    
    Ok(devices)
}
