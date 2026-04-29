use tokio::io::AsyncBufReadExt;
use std::time::{SystemTime, UNIX_EPOCH};
use tauri::{AppHandle, Emitter};
use crate::models::SocketMessage;

pub fn start_socket_server(app: AppHandle) {
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

                    tauri::async_runtime::spawn(async move {
                        let (reader, mut writer) = socket.split();
                        let mut lines = tokio::io::BufReader::new(reader).lines();

                        while let Ok(Some(line)) = lines.next_line().await {
                            if let Ok(msg) = serde_json::from_str::<SocketMessage>(&line) {
                                // Emit to frontend
                                let _ = app_handle.emit("on_message", msg.clone());

                                // Auto ACK
                                let now = SystemTime::now()
                                    .duration_since(UNIX_EPOCH)
                                    .unwrap_or_default()
                                    .as_secs() as i64;

                                let ack = SocketMessage {
                                    r#type: "ACK".to_string(),
                                    sender_id: "linux_pc".to_string(),
                                    payload: format!("ACK for {}", msg.r#type),
                                    timestamp: now,
                                };

                                if let Ok(ack_json) = serde_json::to_string(&ack) {
                                    use tokio::io::AsyncWriteExt;
                                    let _ = writer.write_all(format!("{}\n", ack_json).as_bytes()).await;
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
