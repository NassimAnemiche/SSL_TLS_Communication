import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

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

    public MessageType getType() { return type; }
    public String getSender() { return sender; }
    public String getRecipient() { return recipient; }
    public String getRoom() { return room; }
    public String getContent() { return content; }

    /* ---------------- JSON MINIMALISTE ---------------- */

    public String toJSON() {
        return "{\"type\":\"" + type +
                "\",\"version\":" + version +
                ",\"timestamp\":" + timestamp +
                ",\"sender\":" + quote(sender) +
                ",\"recipient\":" + quote(recipient) +
                ",\"room\":" + quote(room) +
                ",\"content\":" + quote(content) +
                "}";
    }

    private static String quote(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /* ---------------- PARSE JSON ---------------- */

    public static ChatMessage fromJSON(String json) {
        try {
            String type = extract(json, "\"type\":\"", "\"");
            String sender = unquote(extract(json, "\"sender\":", ","));
            String recipient = unquote(extract(json, "\"recipient\":", ","));
            String room = unquote(extract(json, "\"room\":", ","));

            String contentField = json.substring(json.indexOf("\"content\":") + 10);
            String content = unquote(contentField.replace("}", "").trim());

            return new ChatMessage(
                    MessageType.valueOf(type),
                    sender,
                    recipient,
                    room,
                    content
            );

        } catch (Exception e) {
            return null;
        }
    }

    private static String extract(String json, String startToken, String endToken) {
        int start = json.indexOf(startToken);
        if (start == -1) return null;
        start += startToken.length();
        int end = json.indexOf(endToken, start);
        if (end == -1) return null;
        return json.substring(start, end);
    }

    private static String unquote(String s) {
        if (s == null || s.equals("null")) return null;
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return s;
    }

    /* ---------------- SERIALIZATION ---------------- */

    public byte[] toBytes() {
        try {
            String json = toJSON();
            byte[] body = json.getBytes(StandardCharsets.UTF_8);

            int length = body.length;

            long checksum = 0;
            for (byte b : body) checksum += (b & 0xFF);

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // length
            out.write((length >> 24) & 0xFF);
            out.write((length >> 16) & 0xFF);
            out.write((length >> 8) & 0xFF);
            out.write(length & 0xFF);

            // checksum
            out.write((int)((checksum >> 24) & 0xFF));
            out.write((int)((checksum >> 16) & 0xFF));
            out.write((int)((checksum >> 8) & 0xFF));
            out.write((int)(checksum & 0xFF));

            // body
            out.write(body);

            return out.toByteArray();

        } catch (Exception e) {
            return null;
        }
    }

    public static ChatMessage fromBytes(byte[] data) {
        try {
            int length = ((data[0] & 0xFF) << 24) |
                         ((data[1] & 0xFF) << 16) |
                         ((data[2] & 0xFF) << 8) |
                         (data[3] & 0xFF);

            long expected = ((data[4] & 0xFFL) << 24) |
                            ((data[5] & 0xFFL) << 16) |
                            ((data[6] & 0xFFL) << 8) |
                            (data[7] & 0xFFL);

            byte[] body = Arrays.copyOfRange(data, 8, 8 + length);

            long actual = 0;
            for (byte b : body) actual += (b & 0xFF);

            if (actual != expected) return null;

            return fromJSON(new String(body, StandardCharsets.UTF_8));

        } catch (Exception e) {
            return null;
        }
    }
}
