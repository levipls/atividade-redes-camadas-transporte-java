package br.edu.redes.simulador.protocol;

import java.nio.charset.StandardCharsets;

public final class UdpDatagram {
    private final int sourceIp;
    private final int destinationIp;
    private final int sourcePort;
    private final int destinationPort;
    private final byte[] payload;
    private final int checksum;

    private UdpDatagram(int sourceIp, int destinationIp, int sourcePort, int destinationPort, byte[] payload, int checksum) {
        this.sourceIp = sourceIp; this.destinationIp = destinationIp;
        this.sourcePort = sourcePort; this.destinationPort = destinationPort;
        this.payload = payload.clone(); this.checksum = checksum;
    }

    public static UdpDatagram create(int sourceIp, int destinationIp, int sourcePort, int destinationPort, String message) {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        int checksum = InternetChecksum.udpChecksum(sourceIp, destinationIp, sourcePort, destinationPort, bytes);
        return new UdpDatagram(sourceIp, destinationIp, sourcePort, destinationPort, bytes, checksum);
    }

    public UdpDatagram corruptPayload() {
        byte[] changed = payload.clone();
        if (changed.length > 0) changed[0] ^= 0x01;
        return new UdpDatagram(sourceIp, destinationIp, sourcePort, destinationPort, changed, checksum);
    }

    public boolean checksumValid() {
        return InternetChecksum.verifyUdp(sourceIp, destinationIp, sourcePort, destinationPort, payload, checksum);
    }
    public int sourceIp() { return sourceIp; }
    public int destinationIp() { return destinationIp; }
    public int sourcePort() { return sourcePort; }
    public int destinationPort() { return destinationPort; }
    public int length() { return 8 + payload.length; }
    public int checksum() { return checksum; }
    public byte[] payload() { return payload.clone(); }
    public String payloadAsString() { return new String(payload, StandardCharsets.UTF_8); }
}
