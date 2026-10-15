package br.edu.redes.simulador.core;

import br.edu.redes.simulador.protocol.UdpDatagram;

public record NetworkPacket(String protocol, String type, String from, String to,
                            int sequence, int acknowledgement, String payload,
                            boolean retransmission, boolean corrupted, UdpDatagram datagram) {
    public static NetworkPacket tcp(String type, String from, String to, int sequence, int acknowledgement,
                                    String payload, boolean retransmission) {
        return new NetworkPacket("TCP", type, from, to, sequence, acknowledgement, payload,
                retransmission, false, null);
    }

    public static NetworkPacket udp(String from, String to, int sequence, UdpDatagram datagram) {
        return new NetworkPacket("UDP", "DATAGRAM", from, to, sequence, 0, datagram.payloadAsString(),
                false, false, datagram);
    }

    public String label() {
        return switch (type) {
            case "SYN_ACK" -> "SYN-ACK";
            case "DATA" -> "DATA SEQ=" + sequence;
            case "DATA_ACK" -> "ACK=" + acknowledgement;
            case "FIN_ACK" -> "ACK do FIN";
            case "DATAGRAM" -> "UDP #" + sequence;
            default -> type;
        };
    }

    public NetworkPacket asCorrupted() {
        return new NetworkPacket(protocol, type, from, to, sequence, acknowledgement, payload,
                retransmission, true, datagram == null ? null : datagram.corruptPayload());
    }

    public NetworkPacket asRetransmission() {
        return new NetworkPacket(protocol, type, from, to, sequence, acknowledgement, payload,
                true, corrupted, datagram);
    }
}
