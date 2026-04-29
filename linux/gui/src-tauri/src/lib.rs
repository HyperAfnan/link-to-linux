use serde::{Deserialize, Serialize};
use std::fs;
use std::process::Command;
use std::thread;
use std::time::Duration;
use tauri::{Emitter, Manager};

const P2P_IFACE: &str = "p2p-dev-wlan0";

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct Device {
    name: String,
    mac_address: String,
}

fn execute_wpa_cli(args: &[&str]) -> Result<String, String> {
    let output = Command::new("wpa_cli")
        .arg("-p")
        .arg("/run/wpa_supplicant")
        .args(args)
        .output()
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

fn detect_p2p_iface() -> Result<String, String> {
    let candidates = ["wlan0", P2P_IFACE, "wlan1"];
    for iface in &candidates {
        if Command::new("wpa_cli")
            .args(["-p", "/run/wpa_supplicant", "-i", iface, "status"])
            .output()
            .map(|o| o.status.success())
            .unwrap_or(false)
        {
            return Ok(iface.to_string());
        }
    }
    Err(format!(
        "No usable wpa_supplicant interface found (tried: {}). \
        Ensure wpa_supplicant is running and your user can access /run/wpa_supplicant.",
        candidates.join(", ")
    ))
}

// ─── Persistence helpers ──────────────────────────────────────────────────────

fn devices_file_path(app: &tauri::AppHandle) -> Result<std::path::PathBuf, String> {
    let config_dir = app
        .path()
        .app_config_dir()
        .map_err(|e| format!("Could not resolve config directory: {}", e))?;
    Ok(config_dir.join("devices.json"))
}

fn load_previous_devices(app: &tauri::AppHandle) -> Vec<Device> {
    devices_file_path(app)
        .ok()
        .and_then(|p| fs::read_to_string(p).ok())
        .and_then(|s| serde_json::from_str(&s).ok())
        .unwrap_or_default()
}

fn persist_device(app: &tauri::AppHandle, device: &Device) -> Result<(), String> {
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

#[tauri::command]
fn remove_device(mac: String, app: tauri::AppHandle) -> Result<Vec<Device>, String> {
    let path = devices_file_path(&app)?;
    let mut devices = load_previous_devices(&app);
    
    devices.retain(|d| d.mac_address != mac);
    
    let json = serde_json::to_string_pretty(&devices)
        .map_err(|e| format!("Failed to serialize devices: {}", e))?;
    fs::write(&path, json).map_err(|e| format!("Failed to write devices file: {}", e))?;
    
    Ok(devices)
}

// ─── Tauri commands ───────────────────────────────────────────────────────────

#[tauri::command]
fn get_previous_devices(app: tauri::AppHandle) -> Vec<Device> {
    load_previous_devices(&app)
}

#[tauri::command]
async fn scan_peers() -> Result<Vec<Device>, String> {
    // Force p2p-dev-wlan0 for commands to avoid conflict with wlan0 monitor
    let iface = "p2p-dev-wlan0";

    execute_wpa_cli(&["-i", iface, "p2p_find"])?;
    // Increase to 10 seconds for better discovery reliability
    tokio::time::sleep(Duration::from_secs(10)).await;
    let _ = execute_wpa_cli(&["-i", iface, "p2p_stop_find"]);

    let peers_output = execute_wpa_cli(&["-i", iface, "p2p_peers"])?;
    let mut devices = Vec::new();

    for mac in peers_output.lines() {
        let mac = mac.trim();
        if mac.is_empty() || mac == "OK" || mac == "FAIL" {
            continue;
        }

        let name = execute_wpa_cli(&["-i", iface, "p2p_peer", mac])
            .ok()
            .and_then(|details| {
                details
                    .lines()
                    .find(|l| l.starts_with("device_name="))
                    .map(|l| l.replace("device_name=", "").trim().to_string())
            })
            .filter(|n| !n.is_empty())
            .unwrap_or_else(|| "Unknown Device".to_string());

        devices.push(Device {
            name,
            mac_address: mac.to_string(),
        });
    }

    Ok(devices)
}

#[tauri::command]
async fn connect_to_peer(mac: String, name: String, app: tauri::AppHandle) -> Result<bool, String> {
    let iface = "p2p-dev-wlan0";

    let output = execute_wpa_cli(&["-i", iface, "p2p_connect", &mac, "pbc", "go_intent=15"])?;

    if !output.trim().to_lowercase().contains("ok") {
        return Err(format!("wpa_cli p2p_connect failed: {}", output.trim()));
    }

    let mut established = false;
    for _ in 0..40 {
        tokio::time::sleep(Duration::from_millis(500)).await;
        if let Ok(details) = execute_wpa_cli(&["-i", iface, "p2p_peer", &mac]) {
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
        let _ = persist_device(&app, &device);
        Ok(true)
    } else {
        Err("Connection timed out. Handshake did not complete.".to_string())
    }
}

#[tauri::command]
async fn respond_to_pairing_request(mac: String, accept: bool, app: tauri::AppHandle) -> Result<bool, String> {
    let iface = "p2p-dev-wlan0";
    if accept {
        execute_wpa_cli(&["-i", iface, "p2p_connect", &mac, "pbc", "go_intent=15"])?;
        
        let mut established = false;
        for _ in 0..20 {
            tokio::time::sleep(Duration::from_millis(500)).await;
            if let Ok(details) = execute_wpa_cli(&["-i", iface, "p2p_peer", &mac]) {
                if details.contains("status=connected") {
                    established = true;
                    break;
                }
            }
        }
        if established {
            let device = Device { name: "New Peer".to_string(), mac_address: mac };
            let _ = persist_device(&app, &device);
            return Ok(true);
        }
        Ok(false)
    } else {
        execute_wpa_cli(&["-i", iface, "p2p_reject", &mac])?;
        Ok(false)
    }
}

// ─── Event Monitor ───────────────────────────────────────────────────────────

fn start_wpa_monitor(app: tauri::AppHandle) {
    use std::io::{BufRead, BufReader};
    use std::process::Stdio;

    thread::spawn(move || {
        // Monitor wlan0 while commands use p2p-dev-wlan0
        let iface = "wlan0";

        let mut child = Command::new("wpa_cli")
            .args(["-p", "/run/wpa_supplicant", "-i", iface])
            .stdout(Stdio::piped())
            .spawn()
            .expect("Failed to start wpa_cli interactive monitor");

        let stdout = child.stdout.take().unwrap();
        let reader = BufReader::new(stdout);

        for line in reader.lines() {
            if let Ok(line) = line {
                if line.contains("P2P-PROV-DISC-PBC-REQ") || line.contains("P2P-GO-NEG-REQUEST") {
                    let parts: Vec<&str> = line.split_whitespace().collect();
                    for part in parts {
                        let clean = part.trim_matches(|c: char| !c.is_alphanumeric() && c != ':');
                        if clean.len() == 17 && clean.contains(':') {
                             // Bonus: Try to resolve the device name
                             let name = execute_wpa_cli(&["-i", &iface, "p2p_peer", clean])
                                .ok()
                                .and_then(|details| {
                                    details.lines()
                                        .find(|l| l.starts_with("device_name="))
                                        .map(|l| l.replace("device_name=", "").trim().to_string())
                                })
                                .filter(|n| !n.is_empty())
                                .unwrap_or_else(|| "Incoming Request".to_string());

                             let _ = app.emit("pairing-request", Device {
                                name,
                                mac_address: clean.to_string(),
                            });
                             break;
                        }
                    }
                }
            }
        }
    });
}

// ─── Entry point ─────────────────────────────────────────────────────────────

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .setup(|app| {
            start_wpa_monitor(app.handle().clone());
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            get_previous_devices,
            scan_peers,
            connect_to_peer,
            remove_device,
            respond_to_pairing_request
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
