package com.thebuzzmedia.hpjxp;

import java.io.IOException;
import java.io.InputStream;

import com.thebuzzmedia.hpjxp.buffer.DefaultByteSource;
import com.thebuzzmedia.hpjxp.buffer.IByteSource;
import com.thebuzzmedia.hpjxp.util.ScannerUtil;

// TODO: Add support for namespace awareness

public class HPXMLParser {
	// TODO: reset back to Boolean.getBoolean("hpjxp.debug");
	public static final Boolean DEBUG = true;
	public static final Integer BUFFER_SIZE = Integer.getInteger(
			"hpjxp.bufferSize", 131072); // 1024*128 = 131072 (128k)

	public static final String LOG_PREFIX = "[hpjxp] ";

	public static enum EventType {
		/**
		 * An event type representing the parsing of a start-tag (e.g.
		 * &lt;hello&gt;).
		 */
		START_TAG,
		/**
		 * An event type representing the parsing of text between a start and
		 * end tag.
		 */
		TEXT,
		/**
		 * An event type representing the parsing of an end-tag (e.g.
		 * &lt;/hello&gt;).
		 */
		END_TAG,
		/**
		 * An event type representing the end of the document being hit and no
		 * more data left to parse.
		 */
		END_DOCUMENT;
	}

	private int idx = 0;
	private int gIdx = 0;
	private int sIdx = Constants.INVALID;
	private int eIdx = Constants.INVALID;

	private long elapsedTime = 0;

	private EventType eventType;

	private int bufferLength;
	private byte[] buffer;

	private InputStream input;

	public HPXMLParser() {
		buffer = new byte[BUFFER_SIZE];
	}

	@Override
	public String toString() {
		return this.getClass().getName() + "[idx=" + idx + ", sIdx=" + sIdx
				+ ", eIdx=" + eIdx + ", bufferLength=" + bufferLength
				+ ", eventType=" + eventType + ", elapsedTime=" + elapsedTime
				+ "]";
	}

	public void setInput(InputStream input) throws IOException {
		reset();
		this.input = input;

		/*
		 * We do the initial buffer fill here because otherwise we would have to
		 * detect it inside of next OR mark and that condition would be run
		 * EVERY time those methods were called even though it was only the
		 * first time we needed it to initiate the fill. So instead, we are
		 * overly aggressive and do the initial fill now, and allow next/mark to
		 * run faster.
		 */
		fillBuffer();
	}

	/**
	 * Used to progress the parsing of the underlying XML input source forward
	 * by processing the <code>byte</code>-stream until the next
	 * {@link EventType} of interest is encountered. <h3>Performance</h3> This
	 * method merely detects and marks the bounds of interesting bits of
	 * <code>byte</code> data in the underlying stream; no data is copied out of
	 * the stream or processed until the caller calls one of the appropriate
	 * <code>getXXX</code> methods.
	 * <p/>
	 * This method behaves like a lexer, running as fast as possible through the
	 * <code>byte</code>-stream, marking off indices of TAG and TEXT data every
	 * time this method is called.
	 * 
	 * @return an value used to signal the type of data that was encountered
	 *         during parsing. Keying off of this value the caller can then call
	 *         any of the appropriate <code>getXXX</code> methods to retrieve
	 *         the data the parser has encountered.
	 * 
	 * @throws IOException
	 *             if any error occurs reading data from the underlying input
	 *             source ({@link #setInput(InputStream)}).
	 * @throws XMLParseException
	 *             if the XML content is sufficiently malformed to the point the
	 *             parser cannot make heads or tales of it OR if the XML file
	 *             contains TEXT or TAG constructs so large, that they do not
	 *             fit inside of the internal buffer. In this case, increasing
	 *             the value of {@link #BUFFER_SIZE} is required. This should
	 *             only occur with oddly enormous files though.
	 */
	public EventType next() throws IOException, XMLParseException {
		long startTime = System.currentTimeMillis();

		/*
		 * First, move the buffer index to point at the byte right after
		 * whatever the end of the last thing we marked was. Even on the first
		 * run when eIdx is -1, this puts us comfortable at index 0 to begin.
		 */
		idx = eIdx + 1;

		// Find the next (or first) '<', mark will refill the buffer as-needed.
		sIdx = mark(Constants.LT);

		// Check for EOF
		if (bufferLength == -1) {
			if (DEBUG) {
				elapsedTime += (System.currentTimeMillis() - startTime);
				System.out
						.println(LOG_PREFIX
								+ "Parse complete, total elapsed time spent inside of HPJXP: "
								+ elapsedTime + "ms.");
			}

			return (eventType = EventType.END_DOCUMENT);
		}

		/*
		 * Check if we are processing a TAG (enclosed in <>) or TEXT (everything
		 * between >< chars). If idx == sIdx (where we found '<') then we are
		 * just entertain a tag. If sIdx is > idx, then that means we were
		 * already inside of TEXT data and sIdx now signals the end of that TEXT
		 * data (the beginning of the next tag right after it).
		 */
		if (idx == sIdx) {
			// Processing a TAG, so find the end of it ('>')
			eIdx = mark(Constants.GT);

			/*
			 * Mark will continually replace stale data from the buffer in an
			 * attempt to find the given value, if it couldn't find it that
			 * means either the XML is malformed and didn't contain that char,
			 * or our BUFFER_SIZE wasn't big enough to hold the run of bytes
			 * representing everything from the start to the end in memory at
			 * once.
			 */
			if (eIdx == Constants.INVALID)
				throw new XMLParseException(
						"Unable to find closing '>' for tag beginning at position "
								+ gIdx
								+ " in the XML document. Either the XML is malformed or contains TAG/TEXT constructs so long that BUFFER_SIZE will need to be increased to hold it in memory at one time.");

			/*
			 * Check if we are dealing with a START or END tag based on the '/'
			 * char immediately following the '<' char.
			 */
			if (buffer[sIdx + 1] == Constants.FS)
				eventType = EventType.END_TAG;
			else
				eventType = EventType.START_TAG;
		} else {
			/*
			 * Processing TEXT, so the '<' we found is actually 1 after the
			 * ending index of the run of character data and idx is our
			 * beginning index.
			 */
			eIdx = sIdx - 1;
			sIdx = idx;
			eventType = EventType.TEXT;
		}

		if (DEBUG) {
			elapsedTime += (System.currentTimeMillis() - startTime);
			System.out.println(LOG_PREFIX + "[idx=" + idx + ",eventType="
					+ eventType + ", sIdx=" + sIdx + ", eIdx=" + eIdx
					+ ", length=" + (eIdx - sIdx + 1) + "]");
		}

		return eventType;
	}

	public IByteSource getTagName() throws IllegalStateException {
		if (eventType != EventType.START_TAG && eventType != EventType.END_TAG)
			throw new IllegalStateException(
					"getTagName() can only be called immediately after a START_TAG or END_TAG event, but this parser is currently at event: "
							+ eventType);

		int wsIdx = ScannerUtil.indexOf(Constants.WS, sIdx, (eIdx - sIdx + 1),
				buffer);

		// No whitespace means a plain tag, e.g. <hello>.
		if (wsIdx == Constants.INVALID)
			wsIdx = eIdx;

		return copyAndCreateByteSource(
				(eventType == EventType.START_TAG ? sIdx + 1 : sIdx + 2),
				(wsIdx - sIdx - (eventType == EventType.START_TAG ? 1 : 2)),
				buffer);
	}

	public IByteSource getText() throws IllegalStateException {
		if (eventType != EventType.TEXT)
			throw new IllegalStateException(
					"getText() can only be called immediately after a TEXT event, but this parser is currently at event: "
							+ eventType);

		return copyAndCreateByteSource(sIdx, (eIdx - sIdx), buffer);
	}

	protected void reset() {
		idx = 0;
		gIdx = 0;
		sIdx = Constants.INVALID;
		eIdx = Constants.INVALID;

		elapsedTime = 0;
		bufferLength = 0;

		eventType = null;
		input = null;
	}

	/**
	 * Used to top-off the internal <code>buffer</code> with new bytes replacing
	 * bytes already processed as indicated by the value of <code>idx</code>.
	 * <p/>
	 * All bytes preceding <code>idx</code> are considered to be "processed" and
	 * are discarded, the remaining bytes (including <code>idx</code> and
	 * extending to the end of the buffer as described by
	 * <code>bufferLength</code>) are moved to the front of the buffer and all
	 * remaining empty slots filled with new data from the underlying input
	 * source (or as much data as is available from the underlying stream).
	 * <p/>
	 * If <code>idx==0</code>, then no new data can be pulled into the buffer as
	 * it is "full" and if <code>idx==buffer.length-1</code> then the entire
	 * buffer is overwritten and refilled with fresh content from the input
	 * source.
	 * 
	 * @return the number of bytes that were kept and moved to the front of the
	 *         <code>buffer</code>.
	 * 
	 * @throws IOException
	 *             if any error occurs reading content from the underlying
	 *             {@link InputStream} provided as the input source for this
	 *             parser.
	 */
	protected int fillBuffer() throws IOException {
		// Calculate how many bytes to keep
		int bytesKept = bufferLength - idx;
		// Keep track of how far these bytes indices are shifted.
		int shiftCount = bufferLength - bytesKept;

		// Nothing to keep? Refill the whole buffer!
		if (bytesKept == 0) {
			bufferLength = input.read(buffer);
		} else {
			// Move all kept bytes to the beginning of the buffer.
			System.arraycopy(buffer, idx, buffer, 0, bytesKept);

			// Fill up-to the remainder of the buffer with new content
			int bytesRead = input.read(buffer, bytesKept, buffer.length
					- bytesKept);

			// If EOF, buffer length is just the remaining kept bytes.
			if (bytesRead == -1)
				bytesRead = 0;

			// Our new buffer length is our old kept length + new read length
			bufferLength = bytesKept + bytesRead;
		}

		// Update the global position index counter
		gIdx += idx;

		// Update buffer index to point back at the front
		idx = 0;

		// Update the existing start/end indices by the shifted position amount.
		sIdx -= shiftCount;
		eIdx -= shiftCount;

		// Make sure the shifts stay in valid ranges.
		if (sIdx < Constants.INVALID)
			sIdx = Constants.INVALID;
		if (eIdx < Constants.INVALID)
			eIdx = Constants.INVALID;

		// Tell the caller know how many bytes we kept.
		return bytesKept;
	}

	/**
	 * Convenience method used to scan the current buffer contents for a given
	 * <code>byte</code> value.
	 * <p/>
	 * This method will replace any stale data in the buffer from the underlying
	 * input source if necessary in order to try and find the given
	 * <code>byte</code> value.
	 * <p/>
	 * Searching begins at the current <code>idx</code> and runs to the
	 * <code>bufferLength</code>.
	 * 
	 * @param value
	 *            The <code>byte</code> value to find the index of.
	 * 
	 * @return the index in <code>buffer</code> where the given value was found
	 *         or {@link Constants#INVALID} if the value could not be found.
	 * 
	 * @throws IOException
	 *             if any error occurs with the underlying input source while
	 *             trying to replace stale data in the <code>buffer</code> with
	 *             new data.
	 */
	protected int mark(byte value) throws IOException {
		/*
		 * If a previous mark operation had exhausted the buffer, attempt to
		 * refill it if we have any data left.
		 */
		if (idx >= bufferLength) {
			fillBuffer();

			/*
			 * If we hit EOF as a result of trying to refill the buffer, then we
			 * have exhausted all data to scan and must return INVALID to the
			 * caller so it can handle the issue of the file being complete.
			 * 
			 * This typically signals the END_DOCUMENT event.
			 */
			if (bufferLength == -1)
				return Constants.INVALID;
		}

		/*
		 * Attempt to find the index of the given byte value. Use a little
		 * short-cut here in case the value we are looking for is at the current
		 * index (a likely scenario if this is being called from a switch-case
		 * statement, switching on the values we are interested in anyway).
		 * 
		 * No reason to spend the extra microseconds calling down into
		 * ScannerUtil if we already have what we want.
		 */
		int index = (buffer[idx] == value ? idx : ScannerUtil.indexOf(value,
				idx, bufferLength - idx, buffer));

		// Could not find the requested value anywhere in the buffer
		if (index == Constants.INVALID) {
			/*
			 * We need more bytes to scan for the given value because we were
			 * unable to find it. If our idx is > 0, that means there are old
			 * byte values we have already processed that we can replace with
			 * new values from the underlying stream in order to continue our
			 * search for the given value. If idx == 0, that means the entire
			 * buffer is already full and there is no old data that can be
			 * expunged and replaced; we would just have to report to the caller
			 * that we couldn't find the value.
			 */
			if (idx > 0) {
				// We had some old data, so replace it with fresh data.
				int bytesKept = fillBuffer();

				/*
				 * Try 1 more time to find the given value, starting the scan at
				 * the beginning of the new content we just read in and skipping
				 * all the stuff we already scanned the first time.
				 */
				index = ScannerUtil.indexOf(value, bytesKept,
						(bufferLength - bytesKept), buffer);
			}
		}

		return index;
	}

	// protected void markTagBounds() throws IOException, XMLParseException {
	// // Remember the number of bytes we kept IF we have to refill and rescan
	// int keepLength = bufferLength - idx;
	//
	// // Attempt to find the next '<' char index.
	// sIdx = (buffer[idx] == Constants.LT ? idx : ScannerUtil.indexOf(
	// Constants.LT, idx, bufferLength - idx, buffer));
	//
	// // Could not find '<'
	// if (sIdx == Constants.INVALID) {
	// /*
	// * Only try and refill/rescan if we have space in the buffer for new
	// * content to scan. If idx == 0 that means we were staring at the
	// * beginning of the buffer and have NO extra space to fill in with
	// * new content; it is all already new content.
	// */
	// if (idx > 0) {
	// /*
	// * Fill the buffer with new content to scan, replacing the
	// * indices of content we've already looked at previously.
	// */
	// fillBuffer();
	//
	// // Try 1 more time to scan for '<' in the new bytes only
	// sIdx = ScannerUtil.indexOf(Constants.LT, keepLength,
	// (bufferLength - keepLength), buffer);
	// }
	//
	// // Still can't find '<', something is wrong.
	// if (sIdx == Constants.INVALID)
	// throw new XMLParseException(
	// lineCount,
	// "Unable to mark tag bounds; no opening '<' char found after scanning "
	// + bufferLength
	// +
	// " bytes and exhausting the buffer contents. Either this XML file is malformed or has very large tag constructs and the BUFFER_SIZE must be increased to parse this file.");
	// }
	//
	// // Update the number of bytes we kept IF we have to refill and rescan
	// keepLength = bufferLength - idx;
	//
	// // Attempt to find the next '>' char index, closing this tag.
	// eIdx = ScannerUtil.indexOf(Constants.GT, sIdx, bufferLength - sIdx,
	// buffer);
	//
	// // Could not find '>'
	// if (eIdx == Constants.INVALID) {
	// /*
	// * Only try and refill/rescan if we have space in the buffer for new
	// * content to scan. If idx == 0 that means we were staring at the
	// * beginning of the buffer and have NO extra space to fill in with
	// * new content; it is all already new content.
	// */
	// if (idx > 0) {
	// /*
	// * Fill the buffer with new content to scan, replacing the
	// * indices of content we've already looked at previously.
	// */
	// fillBuffer();
	//
	// // Try 1 more time to scan for '>' in the new bytes only
	// eIdx = ScannerUtil.indexOf(Constants.GT, keepLength,
	// (bufferLength - keepLength), buffer);
	// }
	//
	// // Still can't find '>', something is wrong.
	// if (eIdx == Constants.INVALID)
	// throw new XMLParseException(
	// lineCount,
	// "Unable to mark tag bounds; no closing '>' char found after scanning "
	// + bufferLength
	// +
	// " bytes and exhausting the buffer contents. Either this XML file is malformed or has very large tag constructs and the BUFFER_SIZE must be increased to parse this file.");
	//
	// }
	//
	// // Determine if we are inside an open <hello> or close </hello> tag
	// if (sIdx < eIdx && buffer[sIdx + 1] == Constants.FS)
	// isOpenTag = false;
	// else
	// isOpenTag = true;
	// }

	protected IByteSource copyAndCreateByteSource(int index, int length,
			byte[] data) {
		byte[] source = new byte[length];
		System.arraycopy(data, index, source, 0, length);
		return new DefaultByteSource(source);
	}

	protected void doStartTag() {

	}
}