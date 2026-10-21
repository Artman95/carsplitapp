package com.artman.navisplit;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;

/**
 * Minimal adb client for the box's own adbd on 127.0.0.1:5555. It gives the app a shell-uid
 * command channel (am start/resize/stack remove) without root. The RSA key is generated once;
 * the box asks to authorize it on first use ("always allow").
 */
public class Adb {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 5555;

    private static final int A_CNXN = 0x4e584e43, A_AUTH = 0x48545541, A_OPEN = 0x4e45504f,
            A_OKAY = 0x59414b4f, A_CLSE = 0x45534c43, A_WRTE = 0x45545257;
    private static final int AUTH_TOKEN = 1, AUTH_SIGNATURE = 2, AUTH_RSAPUBLICKEY = 3;
    private static final int VERSION = 0x01000001, MAXDATA = 256 * 1024;
    private static final int READ_TIMEOUT_MS = 15000, AUTH_TIMEOUT_MS = 60000;

    // DER DigestInfo prefix for SHA-1; adb signs the 20-byte token as if it were already a digest
    private static final byte[] SHA1_PREFIX = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e,
            0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};

    private static class Msg {
        int cmd, arg0, arg1;
        byte[] data;
    }

    /** Runs one shell command as the adb (shell) user; returns its output. Blocks; not for the UI thread. */
    static String shell(Context ctx, String cmd) throws Exception {
        try (Socket sock = new Socket()) {
            sock.connect(new InetSocketAddress(HOST, PORT), 3000);
            sock.setSoTimeout(READ_TIMEOUT_MS);
            InputStream in = new DataInputStream(sock.getInputStream());
            OutputStream out = sock.getOutputStream();
            connect(ctx, sock, in, out);

            final int localId = 1;
            send(out, A_OPEN, localId, 0, ("shell:" + cmd + "\0").getBytes("UTF-8"));
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            for (;;) {
                Msg m = read(in);
                if (m.cmd == A_WRTE) {
                    result.write(m.data);
                    send(out, A_OKAY, localId, m.arg0, new byte[0]);
                } else if (m.cmd == A_CLSE) {
                    break;
                } else if (m.cmd != A_OKAY) {
                    throw new IOException("unexpected adb message 0x" + Integer.toHexString(m.cmd));
                }
            }
            return result.toString("UTF-8").replace("\r\n", "\n");
        }
    }

    private static void connect(Context ctx, Socket sock, InputStream in, OutputStream out) throws Exception {
        send(out, A_CNXN, VERSION, MAXDATA, "host::\0".getBytes("UTF-8"));
        KeyPair key = loadOrCreateKey(ctx);
        boolean signed = false;
        for (;;) {
            Msg m = read(in);
            if (m.cmd == A_CNXN) return;
            if (m.cmd != A_AUTH || m.arg0 != AUTH_TOKEN) {
                throw new IOException("unexpected adb message 0x" + Integer.toHexString(m.cmd));
            }
            if (!signed) {
                signed = true;
                Signature s = Signature.getInstance("NONEwithRSA");
                s.initSign(key.getPrivate());
                s.update(SHA1_PREFIX);
                s.update(m.data);
                send(out, A_AUTH, AUTH_SIGNATURE, 0, s.sign());
            } else {
                // the box doesn't know this key yet: offer it, then wait for the on-screen "allow"
                Launcher.toast(ctx, "Подтвердите отладку на экране бокса (всегда разрешать)");
                sock.setSoTimeout(AUTH_TIMEOUT_MS);
                send(out, A_AUTH, AUTH_RSAPUBLICKEY, 0, publicKeyBlob((java.security.interfaces.RSAPublicKey) key.getPublic()));
            }
        }
    }

    // ---- wire format ----

    private static void send(OutputStream out, int cmd, int arg0, int arg1, byte[] data) throws IOException {
        int sum = 0;
        for (byte b : data) sum += b & 0xff;
        ByteBuffer b = ByteBuffer.allocate(24 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(cmd).putInt(arg0).putInt(arg1).putInt(data.length).putInt(sum).putInt(cmd ^ 0xffffffff);
        b.put(data);
        out.write(b.array());
        out.flush();
    }

    private static Msg read(InputStream in) throws IOException {
        byte[] h = new byte[24];
        readFully(in, h);
        ByteBuffer b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
        Msg m = new Msg();
        m.cmd = b.getInt();
        m.arg0 = b.getInt();
        m.arg1 = b.getInt();
        int len = b.getInt();
        if (len < 0 || len > MAXDATA) throw new IOException("bad adb length " + len);
        m.data = new byte[len];
        readFully(in, m.data);
        return m;
    }

    private static void readFully(InputStream in, byte[] buf) throws IOException {
        for (int off = 0; off < buf.length; ) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) throw new IOException("adb connection closed");
            off += n;
        }
    }

    // ---- key handling ----

    private static KeyPair loadOrCreateKey(Context ctx) throws Exception {
        File f = new File(ctx.getFilesDir(), "adbkey.pk8");
        KeyFactory kf = KeyFactory.getInstance("RSA");
        if (f.exists()) {
            byte[] enc = new byte[(int) f.length()];
            try (FileInputStream fi = new FileInputStream(f)) { readFully(fi, enc); }
            PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(enc));
            RSAPrivateCrtKey crt = (RSAPrivateCrtKey) priv;
            return new KeyPair(kf.generatePublic(
                    new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent())), priv);
        }
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        try (FileOutputStream fo = new FileOutputStream(f)) { fo.write(kp.getPrivate().getEncoded()); }
        Log.i("navisplit", "generated adb key");
        return kp;
    }

    /** adb's RSAPublicKey struct (2048-bit: n, n0inv, R^2 mod n, e as little-endian words), base64'd. */
    private static byte[] publicKeyBlob(java.security.interfaces.RSAPublicKey pub) throws IOException {
        final int words = 64;
        BigInteger n = pub.getModulus();
        BigInteger two32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n0inv = two32.subtract(n.mod(two32).modInverse(two32));
        BigInteger rr = BigInteger.ONE.shiftLeft(2048).pow(2).mod(n);
        ByteBuffer b = ByteBuffer.allocate(4 + 4 + 4 * words + 4 * words + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(words).putInt(n0inv.intValue());
        for (int i = 0; i < words; i++) b.putInt(n.shiftRight(32 * i).intValue());
        for (int i = 0; i < words; i++) b.putInt(rr.shiftRight(32 * i).intValue());
        b.putInt(pub.getPublicExponent().intValue());
        byte[] b64 = Base64.encode(b.array(), Base64.NO_WRAP);
        byte[] tail = " navisplit@box\0".getBytes("UTF-8");
        byte[] blob = new byte[b64.length + tail.length];
        System.arraycopy(b64, 0, blob, 0, b64.length);
        System.arraycopy(tail, 0, blob, b64.length, tail.length);
        return blob;
    }
}
