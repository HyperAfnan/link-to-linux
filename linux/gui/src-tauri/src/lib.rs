pub mod models;
pub mod p2p;
pub mod network;
pub mod commands;


#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .setup(|app| {
            p2p::wifi_manager::start_wpa_monitor(app.handle().clone());
            network::socket_server::start_socket_server(app.handle().clone());
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            commands::get_previous_devices,
            commands::scan_peers,
            commands::connect_to_peer,
            commands::remove_device,
            commands::respond_to_pairing_request
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
