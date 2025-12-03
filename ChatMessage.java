import java.time.Instant;
import java.io.ByteArrayOutputStream;
import org.json.JSONObject;

public class ChatMessage {

    private MessageType type;
    private int version;
    private long timestamp;
    private String sender;
    private String recipient;
    private String room;
    private String content;

    public ChatMessage(MessageType type, String sender, String recipient, String room, String content) {
        this.type = type;
        this.version = 1;
        this.timestamp = Instant.now().toEpochMilli();
        this.sender = sender;
        this.recipient = recipient;
        this.room = room;
        this.content = content;
    }

    // ====== JSON SERIALIZATION ======
    public String toJSON() {
        JSONObject obj = new JSONObject();
        obj.put("type", type.toString());
        obj.put("version", version);
        obj.put("timestamp", timestamp);
        obj.put("sender", sender);
        obj.put("recipient", recipient);
        obj.put("room", room);
        obj.put("content", content);
        return obj.toString();
    }

    public static ChatMessage fromJSON(String json) {
        JSONObject obj = new JSONObject(json);

        MessageType type = MessageType.valueOf(obj.getString("type"));
        String sender = obj.optString("sender", null);
        String recipient = obj.optString("recipient", null);
        String room = obj.optString("room", null);
        String content = obj.optString("content", null);

        ChatMessage msg = new ChatMessage(type, sender, recipient, room, content);
        msg.version = obj.optInt("version", 1);
        msg.timestamp = obj.optLong("timestamp", Instant.now().toEpochMilli());
        return msg;
    }

    // ====== BINARY SERIALIZATION (header + JSON body) ======
    public byte[] toBytes() {
        try {
            String json = toJSON();
            byte[] body = json.getBytes("UTF-8");

            int length = body.length;

            long checksum = 0;
            for (byte b : body) checksum += (b & 0xFF);

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // 4 bytes: length
            out.write((length >> 24) & 0xFF);
            out.write((length >> 16) & 0xFF);
            out.write((length >> 8) & 0xFF);
            out.write(length & 0xFF);

            // 4 bytes: checksum
            out.write((int)((checksum >> 24) & 0xFF));
            out.write((int)((checksum >> 16) & 0xFF));
            out.write((int)((checksum >> 8) & 0xFF));
            out.write((int)(checksum & 0xFF));

            // JSON body
            out.write(body);

            return out.toByteArray();

        } catch (Exception e) {
            return null;
        }
    }

    // ====== BINARY DESERIALIZATION ======
    public static ChatMessage fromBytes(byte[] data) {
        try {
            // Read length
            int length = ((data[0] & 0xFF) << 24) |
                         ((data[1] & 0xFF) << 16) |
                         ((data[2] & 0xFF) << 8) |
                         (data[3] & 0xFF);

            // Read checksum
            long expectedChecksum = ((data[4] & 0xFFL) << 24) |
                                    ((data[5] & 0xFFL) << 16) |
                                    ((data[6] & 0xFFL) << 8) |
                                    (data[7] & 0xFFL);

            // Extract body
            byte[] body = new byte[length];
            System.arraycopy(data, 8, body, 0, length);

            // Calculate checksum
            long actualChecksum = 0;
            for (byte b : body) actualChecksum += (b & 0xFF);

            // Validate checksum
            if (actualChecksum != expectedChecksum) return null;

            String json = new String(body, "UTF-8");

            return fromJSON(json);

        } catch (Exception e) {
            return null;
        }
    }

    // ====== GETTERS ======
    public MessageType getType() { return type; }
    public String getSender() { return sender; }
    public String getRecipient() { return recipient; }
    public String getRoom() { return room; }
    public String getContent() { return content; }
    public long getTimestamp() { return timestamp; }
}
