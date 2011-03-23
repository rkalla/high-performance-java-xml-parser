package com.thebuzzmedia.hpjxp.util;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

public class CodingUtil {
	public static final int MAX_ENCODE_BUFFER_SIZE = Integer.getInteger(
			"hpjxp.util.maxEncodeBufferSize", 2048);
	public static final int MAX_DECODE_BUFFER_SIZE = Integer.getInteger(
			"hpjxp.util.maxDecodeBufferSize", 2048);

	public static final Charset UTF8_CHARSET = Charset.forName("UTF-8");

	private static final ThreadLocal<CharsetEncoder> THREAD_ENCODER = new ThreadLocal<CharsetEncoder>() {
		@Override
		protected CharsetEncoder initialValue() {
			return UTF8_CHARSET.newEncoder();
		}
	};

	private static final ThreadLocal<CharsetDecoder> THREAD_DECODER = new ThreadLocal<CharsetDecoder>() {
		@Override
		protected CharsetDecoder initialValue() {
			return UTF8_CHARSET.newDecoder();
		}
	};

	public static byte[] encode(char[] chars) {
		return (chars == null ? null : encode(CharBuffer.wrap(chars)));
	}

	public static byte[] encode(int index, int length, char[] chars) {
		return (chars == null ? null : encode(CharBuffer.wrap(chars, index,
				length)));
	}

	public static byte[] encode(CharSequence chars) {
		return (chars == null ? null : encode(CharBuffer.wrap(chars)));
	}

	public static byte[] encode(CharBuffer in) {
		byte[] result = null;

		if (in != null) {
			CharsetEncoder encoder = THREAD_ENCODER.get();

			int size = Math.round(encoder.averageBytesPerChar()
					* (float) in.remaining());
			ByteBuffer buffer = ByteBuffer
					.allocate(size < MAX_ENCODE_BUFFER_SIZE ? size
							: MAX_ENCODE_BUFFER_SIZE);
			result = new byte[size];

			// Reset the encoder
			int bytesRead = 0;
			encoder.reset();

			while (in.hasRemaining()) {
				/*
				 * Encode the first buffer.capacity chars, passing 'false' to
				 * indicate that we aren't sure if we are done with the encode
				 * operation yet.
				 */
				encoder.encode(in, buffer, false);

				// Append what we successfully encoded to our tally
				buffer.flip();
				int bytesEncoded = buffer.remaining();

				// Ensure we have enough room in result to add our encoded bits
				ensureCapacity(bytesRead + bytesEncoded, result);
				buffer.get(result, bytesRead, bytesEncoded);
				bytesRead += bytesEncoded;

				// If there is no more to encode, go through finalization
				if (!in.hasRemaining()) {
					buffer.clear();

					/*
					 * Per the CharsetEncoder Javadocs, encoders must be given
					 * an opportunity to "finalize" their internal state and
					 * flush out any pending operations once we know we've hit
					 * the end of the bytes to encode.
					 */
					encoder.encode(in, buffer, true);
					encoder.flush(buffer);

					buffer.flip();
					bytesEncoded = buffer.remaining();

					// If any finalized bytes were written, append them.
					if (bytesEncoded > 0) {
						// Ensure we have enough room in result to add our
						// encoded bits
						ensureCapacity(bytesRead + bytesEncoded, result);
						buffer.get(result, bytesRead, bytesEncoded);
						bytesRead += bytesEncoded;
					}
				}
			}

			// See if we need to trim the result
			if (bytesRead < result.length) {
				System.out.println("TRIMMING byte[] RESULT");
				byte[] newResult = new byte[bytesRead];
				System.arraycopy(result, 0, newResult, 0, bytesRead);
				result = newResult;
			}
		}

		return result;
	}

	public static char[] decode(byte[] bytes) {
		return (bytes == null ? null : decode(0, bytes.length, bytes));
	}

	public static char[] decode(int index, int length, byte[] bytes) {
		return (bytes == null ? null : decode(ByteBuffer.wrap(bytes, index,
				length)));
	}

	public static char[] decode(ByteBuffer in) {
		char[] result = null;

		if (in != null) {
			CharsetDecoder decoder = THREAD_DECODER.get();

			int size = Math.round(decoder.averageCharsPerByte()
					* (float) in.remaining());
			CharBuffer buffer = CharBuffer
					.allocate(size < MAX_DECODE_BUFFER_SIZE ? size
							: MAX_DECODE_BUFFER_SIZE);
			result = new char[size];

			// Reset the decoder
			int charsRead = 0;
			decoder.reset();

			while (in.hasRemaining()) {
				/*
				 * Decode the first buffer.capacity chars, passing 'false' to
				 * indicate that we aren't sure if we are done with the decode
				 * operation yet.
				 */
				decoder.decode(in, buffer, false);

				// Append what we successfully decoded to our tally
				buffer.flip();
				int charsDecoded = buffer.remaining();

				// Ensure we have enough room in result to add our decoded bits
				ensureCapacity(charsRead + charsDecoded, result);
				buffer.get(result, charsRead, charsDecoded);
				charsRead += charsDecoded;

				// If there is no more to decode, go through finalization
				if (!in.hasRemaining()) {
					buffer.clear();

					/*
					 * Per the CharsetDecoder Javadocs, encoders must be given
					 * an opportunity to "finalize" their internal state and
					 * flush out any pending operations once we know we've hit
					 * the end of the chars to decode.
					 */
					decoder.decode(in, buffer, true);
					decoder.flush(buffer);

					buffer.flip();
					charsDecoded = buffer.remaining();

					// If any finalized chars were written, append them.
					if (charsDecoded > 0) {
						// Ensure we have enough room in result to add our
						// decoded bits
						ensureCapacity(charsRead + charsDecoded, result);
						buffer.get(result, charsRead, charsDecoded);
						charsRead += charsDecoded;
					}
				}
			}

			// See if we need to trim the result
			if (charsRead < result.length) {
				System.out.println("TRIMMING char[] RESULT");
				char[] newResult = new char[charsRead];
				System.arraycopy(result, 0, newResult, 0, charsRead);
				result = newResult;
			}
		}

		return result;
	}

	protected static byte[] ensureCapacity(int capacity, byte[] array) {
		if (capacity < array.length)
			return array;

		byte[] newArray = new byte[capacity];
		System.arraycopy(array, 0, newArray, 0, array.length);
		return newArray;
	}

	protected static char[] ensureCapacity(int capacity, char[] array) {
		if (capacity < array.length)
			return array;

		char[] newArray = new char[capacity];
		System.arraycopy(array, 0, newArray, 0, array.length);
		return newArray;
	}
}