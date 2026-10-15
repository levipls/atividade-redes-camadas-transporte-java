package br.edu.redes.simulador.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** UDP checksum: one's-complement sum over pseudo-header, header and payload. */
public final class InternetChecksum {
    private InternetChecksum() { }

    public static int udpChecksum(int sourceIp, int destinationIp, int sourcePort, int destinationPort, byte[] payload) {
        byte[] bytes = udpBytes(sourceIp, destinationIp, sourcePort, destinationPort, payload, 0);
        int checksum = (~fold(sumWords(bytes))) & 0xffff;
        return checksum == 0 ? 0xffff : checksum;
    }

    public static boolean verifyUdp(int sourceIp, int destinationIp, int sourcePort, int destinationPort,
                                    byte[] payload, int checksum) {
        return fold(sumWords(udpBytes(sourceIp, destinationIp, sourcePort, destinationPort, payload, checksum))) == 0xffff;
    }

    private static byte[] udpBytes(int sourceIp, int destinationIp, int sourcePort, int destinationPort,
                                   byte[] payload, int checksum) {
        int udpLength = 8 + payload.length;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(12 + udpLength);
        putInt(bytes, sourceIp);
        putInt(bytes, destinationIp);
        bytes.write(0); bytes.write(17); putShort(bytes, udpLength);
        putShort(bytes, sourcePort); putShort(bytes, destinationPort); putShort(bytes, udpLength); putShort(bytes, checksum);
        bytes.writeBytes(payload);
        return bytes.toByteArray();
    }

    private static long sumWords(byte[] bytes) {
        long sum = 0;
        for (int i = 0; i < bytes.length; i += 2) {
            int high = Byte.toUnsignedInt(bytes[i]);
            int low = i + 1 < bytes.length ? Byte.toUnsignedInt(bytes[i + 1]) : 0;
            sum += (high << 8) | low;
        }
        return sum;
    }

    private static int fold(long sum) {
        while ((sum >>> 16) != 0) sum = (sum & 0xffff) + (sum >>> 16);
        return (int) sum & 0xffff;
    }

    private static void putInt(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xff); out.write((value >>> 16) & 0xff);
        out.write((value >>> 8) & 0xff); out.write(value & 0xff);
    }
    private static void putShort(ByteArrayOutputStream out, int value) {
        out.write((value >>> 8) & 0xff); out.write(value & 0xff);
    }

    public static byte[] utf8(String value) { return value.getBytes(StandardCharsets.UTF_8); }
}
