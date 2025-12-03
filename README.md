# SSL_TLS_Communication

Minimal SSL/TLS chat demo with a simple framed protocol.

## What’s Included
- `SecureChatServer.java`: SSL server using keystore, handling login, room text, private messages.
- `SSLClient.java`: SSL client sending framed messages and printing server replies.
- `SSLTCPServer.java`: Basic echo SSL server (legacy/simple).
- `ChatMessage.java`, `MessageType.java`, `SecureChatClient.java`: earlier variants (optional).

## Requirements
- Java 21+ (javac/java on PATH)
- A PKCS12 keystore file (e.g., `server.p12`) with password `password123` and CN `localhost` for local testing.

Create keystore (if you don’t have one):
```
keytool -genkeypair -alias myserver -keyalg RSA -keysize 2048 -validity 365 -keystore server.p12 -storetype PKCS12 -storepass password123
```

## Run
In two terminals, from the project folder:

1) Start the server (default port 9000):
```
javac SecureChatServer.java
java SecureChatServer 9000 server.p12 password123
```

2) Run the client:
```
javac SSLClient.java
java SSLClient
```

You should see replies like:
- `OK:login-success`
- `MSG:[General] alice: Hello from alice`
- `ERROR:user-offline`
- `ERROR:unknown-type`

## Protocol (simple)
- Frame = `int length` (big-endian) + payload bytes
- Payload = first byte `type` + UTF-8 body
	- `1` Login: body = `username`
	- `2` Text: body = `room\nmessage`
	- `3` Private: body = `target\nmessage`
