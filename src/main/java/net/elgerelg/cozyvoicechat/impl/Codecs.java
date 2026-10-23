package net.elgerelg.cozyvoicechat.impl;

import io.github.jaredmdobson.concentus.OpusDecoder;
import io.github.jaredmdobson.concentus.OpusEncoder;
import net.elgerelg.cozyvoicechat.CozyVoiceProtocol;
import net.elgerelg.cozyvoicechat.OpusCodec;
import net.elgerelg.cozyvoicechat.api.audio.VoiceDecoder;
import net.elgerelg.cozyvoicechat.api.audio.VoiceEncoder;

import java.util.Arrays;

/** Обёртки Concentus для публичного API. */
final class Codecs {

    private Codecs() {}

    static final class Encoder implements VoiceEncoder {
        private final int bitrate;
        private OpusEncoder enc;
        private final byte[] buf = new byte[1275];
        private boolean closed;

        Encoder(int bitrate) {
            this.bitrate = bitrate;
            this.enc = create();
        }

        private OpusEncoder create() {
            OpusEncoder e = OpusCodec.newEncoder(bitrate);
            if (e == null) throw new IllegalStateException("Opus is not available");
            return e;
        }

        @Override
        public synchronized byte[] encode(short[] pcm) {
            if (closed) throw new IllegalStateException("encoder closed");
            if (pcm == null || pcm.length != CozyVoiceProtocol.FRAME_SAMPLES) {
                throw new IllegalArgumentException("frame must have " + CozyVoiceProtocol.FRAME_SAMPLES + " samples");
            }
            try {
                int n = enc.encode(pcm, 0, CozyVoiceProtocol.FRAME_SAMPLES, buf, 0, buf.length);
                return Arrays.copyOf(buf, Math.max(0, n));
            } catch (Exception e) {
                throw new IllegalStateException("Opus encode failed", e);
            }
        }

        @Override
        public synchronized void reset() {
            if (!closed) enc = create();
        }

        @Override
        public synchronized void close() {
            closed = true;
            enc = null;
        }
    }

    static final class Decoder implements VoiceDecoder {
        private OpusDecoder dec;
        private boolean closed;

        Decoder() {
            this.dec = create();
        }

        private static OpusDecoder create() {
            OpusDecoder d = OpusCodec.newDecoder();
            if (d == null) throw new IllegalStateException("Opus is not available");
            return d;
        }

        @Override
        public synchronized short[] decode(byte[] opus) {
            if (closed) throw new IllegalStateException("decoder closed");
            short[] out = new short[CozyVoiceProtocol.FRAME_SAMPLES];
            try {
                if (opus == null || opus.length == 0) {
                    dec.decode(null, 0, 0, out, 0, CozyVoiceProtocol.FRAME_SAMPLES, false);
                } else {
                    dec.decode(opus, 0, opus.length, out, 0, CozyVoiceProtocol.FRAME_SAMPLES, false);
                }
            } catch (Exception e) {
                throw new IllegalStateException("Opus decode failed", e);
            }
            return out;
        }

        @Override
        public synchronized void reset() {
            if (!closed) dec = create();
        }

        @Override
        public synchronized void close() {
            closed = true;
            dec = null;
        }
    }
}
