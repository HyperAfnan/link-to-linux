use std::fs;
use tauri::{AppHandle, Manager};
use tokio::io::{AsyncBufReadExt, BufReader};
use tokio::process::Command;
use std::process::Stdio;
use std::time::Duration;
use crate::models::Device;

pub const P2P_IFACE: &str = "p2p-dev-wlan0";

pub async fn execute_wpa_cli(args: &[&str]) -> Result<String, String> {
    let output = Command::new("wpa_cli")
        .arg("-p")
        .arg("/run/wpa_supplicant")
        .args(args)
        .output()
        .await
        .map_err(|e| format!("Failed to execute wpa_cli process: {}", e))?;

    let stdout = String::from_utf8_lossy(&output.stdout).to_string();
    let stderr = String::from_utf8_lossy(&output.stderr).to_string();

    if !output.status.success() || stdout.trim() == "FAIL" || stderr.to_lowercase().contains("permission denied") {
        let details = if !stderr.trim().is_empty() {
            stderr.trim().to_string()
        } else if !stdout.trim().is_empty() {
            stdout.trim().to_string()
        } else {
            "Command returned non-zero exit code with no output".to_string()
        };
        return Err(format!("wpa_cli failed (args: {:?}): {}", args, details));
    }

    Ok(stdout)
}

pub async fn get_device_name(iface: &str, mac: &str) -> String {
    execute_wpa_cli(&["-i", iface, "p2p_peer", mac])
        .await
        .ok()
        .and_then(|details| {
            details
                .lines()
                .find(|l| l.starts_with("device_name="))
                .map(|l| l.replace("device_name=", "").trim().to_string())
        })
        .filter(|n| !n.is_empty())
        .unwrap_or_else(|| "Unknown Device".to_string())
}

pub async fn wait_for_connection(iface: &str, mac: &str) -> Result<bool, String> {
    for _ in 0..40 {
        tokio::time::sleep(Duration::from_millis(500)).await;
        if let Ok(details) = execute_wpa_cli(&["-i", iface, "p2p_peer", mac]).await {
            if details.contains("p2p_status=connected") || details.contains("status=connected") {
                return Ok(true);
            }
        }
    }
    Err("Connection timed out. Handshake did not complete.".to_string())
}

pub async fn scan_peers() -> Result<Vec<Device>, String> {
    let iface = P2P_IFACE;

    execute_wpa_cli(&["-i", iface, "p2p_find"]).await?;
    tokio::time::sleep(Duration::from_secs(10)).await;
    let _ = execute_wpa_cli(&["-i", iface, "p2p_stop_find"]).await;

    let peers_output = execute_wpa_cli(&["-i", iface, "p2p_peers"]).await?;
    let mut devices = Vec::new();

    for mac in peers_output.lines() {
        let mac = mac.trim();
        if mac.is_empty() || mac == "OK" || mac == "FAIL" {
            continue;
        }

        let name = get_device_name(iface, mac).await;

        devices.push(Device {
            name,
            mac_address: mac.to_string(),
        });
    }

    Ok(devices)
}

pub fn devices_file_path(app: &AppHandle) -> Result<std::path::PathBuf, String> {
    let config_dir = app
        .path()
        .app_config_dir()
        .map_err(|e| format!("Could not resolve config directory: {}", e))?;
    Ok(config_dir.join("devices.json"))
}

pub fn load_previous_devices(app: &AppHandle) -> Vec<Device> {
    devices_file_path(app)
        .ok()
        .and_then(|p| fs::read_to_string(p).ok())
        .and_then(|s| serde_json::from_str(&s).ok())
        .unwrap_or_default()
}

pub fn persist_device(app: &AppHandle, device: &Device) -> Result<(), String> {
    let path = devices_file_path(app)?;

    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)
            .map_err(|e| format!("Failed to create config directory: {}", e))?;
    }

    let mut devices = load_previous_devices(app);

    if let Some(existing) = devices.iter_mut().find(|d| d.mac_address == device.mac_address) {
        existing.name = device.name.clone();
    } else {
        devices.push(device.clone());
    }

    let json = serde_json::to_string_pretty(&devices)
        .map_err(|e| format!("Failed to serialize devices: {}", e))?;
    fs::write(&path, json).map_err(|e| format!("Failed to write devices file: {}", e))?;

    Ok(())
}

pub fn start_wpa_monitor(tx: tokio::sync::mpsc::Sender<Device>) {
    tauri::async_runtime::spawn(async move {
        let iface = if std::path::Path::new("/run/wpa_supplicant/p2p-dev-wlan0").exists() {
            "p2p-dev-wlan0"
        } else {
            "wlan0"
        };

        let mut child = Command::new("wpa_cli")
            .args(["-p", "/run/wpa_supplicant", "-i", iface])
            .stdout(Stdio::piped())
            .spawn()
            .expect("Failed to start wpa_cli interactive monitor");

        let stdout = child.stdout.take().unwrap();
        let mut reader = BufReader::new(stdout).lines();

        while let Ok(Some(line)) = reader.next_line().await {
            if line.contains("P2P-PROV-DISC-PBC-REQ") || 
               line.contains("P2P-GO-NEG-REQUEST") ||
               line.contains("P2P-PROV-DISC-ENTER-PIN") {
                let parts: Vec<&str> = line.split_whitespace().collect();
                for part in parts {
                    let clean = part.trim_matches(|c: char| !c.is_alphanumeric() && c != ':');
                    if clean.len() == 17 && clean.contains(':') {
                         let name = get_device_name(iface, clean).await;
                         let name = if name == "Unknown Device" {
                             "Incoming Request".to_string()
                         } else {
                             name
                         };

                         let _ = tx.send(Device {
                            name,
                            mac_address: clean.to_string(),
                        }).await;
                         break;
                    }
                }
            }
        }
    });
}
