use tauri::{Manager, Emitter};

pub mod models;
pub mod p2p;
pub mod network;
pub mod commands;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .setup(|app| {
            let (tx_monitor, mut rx_monitor) = tokio::sync::mpsc::channel(100);
            p2p::wifi_manager::start_wpa_monitor(tx_monitor);

            let app_handle = app.handle().clone();
            tauri::async_runtime::spawn(async move {
                while let Some(device) = rx_monitor.recv().await {
                    let _ = app_handle.emit("pairing-request", device);
                }
            });

            let (tx_socket, _rx_socket) = tokio::sync::broadcast::channel(16);
            app.manage(tx_socket.clone());
            network::socket_server::start_socket_server(app.handle().clone(), tx_socket);

            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            commands::get_previous_devices,
            commands::scan_peers,
            commands::connect_to_peer,
            commands::remove_device,
            commands::respond_to_pairing_request,
            commands::send_socket_message
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
