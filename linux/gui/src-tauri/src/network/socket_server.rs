use tokio::io::{AsyncBufReadExt, AsyncWriteExt};
use tokio::sync::broadcast;
use tauri::{AppHandle, Emitter};
use crate::models::SocketMessage;

pub fn start_socket_server(app: AppHandle, tx_out: broadcast::Sender<SocketMessage>) {
    tauri::async_runtime::spawn(async move {
        let listener = match tokio::net::TcpListener::bind("0.0.0.0:5005").await {
            Ok(l) => l,
            Err(e) => {
                eprintln!("Failed to bind socket server: {}", e);
                return;
            }
        };

        println!("Socket server listening on 0.0.0.0:5005");

        loop {
            match listener.accept().await {
                Ok((mut socket, addr)) => {
                    println!("New client connected: {}", addr);
                    let app_handle = app.clone();
                    let mut rx_out = tx_out.subscribe();

                    tauri::async_runtime::spawn(async move {
                        let (reader, mut writer) = socket.split();
                        let mut lines = tokio::io::BufReader::new(reader).lines();

                        loop {
                            tokio::select! {
                                result = lines.next_line() => {
                                    match result {
                                        Ok(Some(line)) => {
                                            if let Ok(msg) = serde_json::from_str::<SocketMessage>(&line) {
                                                // Emit to frontend
                                                let _ = app_handle.emit("on_message", msg);
                                            }
                                        }
                                        _ => break, // Client disconnected or error
                                    }
                                }
                                Ok(msg) = rx_out.recv() => {
                                    if let Ok(json) = serde_json::to_string(&msg) {
                                        let _ = writer.write_all(format!("{}\n", json).as_bytes()).await;
                                    }
                                }
                            }
                        }
                    });
                }
                Err(e) => eprintln!("Socket accept error: {}", e),
            }
        }
    });
}
