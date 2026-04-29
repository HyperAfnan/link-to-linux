use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct Device {
    pub name: String,
    pub mac_address: String,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct SocketMessage {
    pub r#type: String,
    pub sender_id: String,
    pub payload: String,
    pub timestamp: i64,
}
