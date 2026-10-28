/**
 * Audio format constants and Opus codec wrappers, so addons don't need a codec dependency of their own.
 *
 * <p>All PCM in the API is one {@link net.elgerelg.cozyvoicechat.api.audio.VoiceAudioFormat 20 ms frame}
 * of 48 kHz mono 16-bit samples. Get codecs from
 * {@link net.elgerelg.cozyvoicechat.api.CozyVoiceApi#createEncoder()} and
 * {@link net.elgerelg.cozyvoicechat.api.CozyVoiceApi#createDecoder()}; they work on both sides
 * and on any thread.</p>
 *
 * @since 1.1.0
 */
package net.elgerelg.cozyvoicechat.api.audio;
