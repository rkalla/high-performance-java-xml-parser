package com.thebuzzmedia.hpjxp;

import java.io.IOException;
import java.io.InputStream;

import com.thebuzzmedia.hpjxp.buffer.DefaultByteSource;
import com.thebuzzmedia.hpjxp.buffer.IByteSource;
import com.thebuzzmedia.hpjxp.util.ArrayUtil;
import com.thebuzzmedia.hpjxp.util.ScannerUtil;

// TODO: Add support for namespace awareness

public class HPXMLParser {
	// TODO: reset back to Boolean.getBoolean("hpjxp.debug");
	public static final Boolean DEBUG = false;
	public static final Integer BUFFER_SIZE = Integer.getInteger(
			"hpjxp.bufferSize", 131072); // 1024*128 = 131072 (128k)

	public static final String LOG_PREFIX = "[hpjxp] ";

	/*
	 * TODO: rename to STATE, makes a bit more sense intuitively...
	 * "I am currently looking at a TAG, or text, etc." and then the getter
	 * methods make more sense.
	 * 
	 * TODO: Maybe provide accessor methods on the state itself?
	 */
	public static enum State {
		/**
		 * Used to describe the state the parser is in once it has found and
		 * marked the bounds of a start tag (e.g. &lt;hello&gt; or
		 * &lt;hello/&gt;).
		 * <p/>
		 * <h3>Valid Operations</h3> When the parser is in this state, the
		 * following data-retrieval operations are valid:
		 * {@link HPXMLParser#getTagName()}.
		 * <p/>
		 * <h3>Empty Elements</h3> When an empty element (e.g. &lt;hello/&gt;)
		 * is encountered, the first call to {@link HPXMLParser#nextState()}
		 * will return a {@link #START_TAG} state and the second call will
		 * return a symmetrical {@link #END_TAG} state. This was done to make
		 * handling different tag types transparent to the caller; they can just
		 * code START/END handlers and the parser will do the right thing.
		 */
		START_TAG,
		/**
		 * Used to describe the state the parser is in once it has found and
		 * marked the bounds of a full run of character data (text between &gt;
		 * and &lt; chars of separate tags).
		 * <p/>
		 * <h3>Valid Operations</h3> When the parser is in this state, the
		 * following data-retrieval operations are valid:
		 * {@link HPXMLParser#getText()}.
		 */
		TEXT,
		/**
		 * Used to describe the state the parser is in once it has found and
		 * marked the bounds of an end tag (e.g. &lt;/hello&gt; or the second
		 * call to {@link HPXMLParser#nextState()} for an empty element like
		 * &lt;hello/&gt;).
		 * <p/>
		 * <h3>Valid Operations</h3> When the parser is in this state, the
		 * following data-retrieval operations are valid:
		 * {@link HPXMLParser#getTagName()}.
		 * <p/>
		 * <h3>Empty Elements</h3> When an empty element (e.g. &lt;hello/&gt;)
		 * is encountered, the first call to {@link HPXMLParser#nextState()}
		 * will return a {@link #START_TAG} state and the second call will
		 * return a symmetrical {@link #END_TAG} state. This was done to make
		 * handling different tag types transparent to the caller; they can just
		 * code START/END handlers and the parser will do the right thing.
		 */
		END_TAG,
		/**
		 * Used to describe the state the parser is in once it has scanned all
		 * characters in its internal <code>byte[]</code> buffer and the given
		 * input source ({@link HPXMLParser#setInput(InputStream)}) returns
		 * <code>-1</code> indicating no more data is available.
		 * <p/>
		 * <h3>Valid Operations</h3> When the parser is in this state, the
		 * following data-retrieval operations are valid: <em>none</em>.
		 */
		END_DOCUMENT;
	}

	private boolean isEmptyElement = false;

	private int idx = 0;
	private int gIdx = 0;
	private int sIdx = Constants.INVALID;
	private int eIdx = Constants.INVALID;

	private State state;

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
				+ ", state=" + state + "]";
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
	 * Used to get the current state which will be equivalent to the last value
	 * returned by {@link #nextState()}.
	 * <p/>
	 * This method will not advance the parser through the data stream at all
	 * and is a simple accessor to the underlying current state.
	 * 
	 * @return the current state of the parser.
	 */
	public State getState() {
		return state;
	}

	/**
	 * Used to advance the parser forward through the underlying data stream
	 * until the next {@link State} of interest is encountered.
	 * <p/>
	 * <h3>Performance</h3>
	 * This method merely detects and marks the bounds of interesting bits of
	 * data in the underlying stream (matching the different {@link State}s this
	 * parser can be in). No data is copied out of the stream or processed until
	 * the caller calls one of the appropriate <code>getXXX</code> methods.
	 * <p/>
	 * This method behaves like a lexer, running as fast as possible through the
	 * <code>byte</code>-stream until a point of interest is encountered, then
	 * stopping, returning the new state to the caller, giving them a chance to
	 * retrieve the value before invoking this method again and moving the
	 * parsing forward again.
	 * 
	 * @return an value used to signal the type of data that was encountered
	 *         during parsing. The caller is meant to use the return type to
	 *         determine which <code>getXXX</code> methods are appropriate to
	 *         call to retrieve the data the parser has encountered.
	 * 
	 * @throws IOException
	 *             if any error occurs reading data from the underlying input
	 *             source ({@link #setInput(InputStream)}).
	 * @throws XMLParseException
	 *             if the XML content is sufficiently malformed to the point the
	 *             parser cannot make heads or tales of it OR if the XML file
	 *             contains TEXT or TAG constructs so large, that a single TAG
	 *             or TEXT construct does not fit inside of the internal buffer
	 *             at one time. In this case, increasing the value of
	 *             {@link #BUFFER_SIZE} is required. This should only occur with
	 *             oddly enormous files though.
	 */
	public State nextState() throws IOException, XMLParseException {
		/*
		 * Before we do anything, if we were processing an empty element
		 * (<hello/>) we need to finish "processing" it by flipping out
		 * empty-element flag and returning the END_TAG event to match the
		 * previous START_TAG event for this element.
		 * 
		 * Only on the following call to next() will we move forward with
		 * parsing again.
		 */
		if (isEmptyElement) {
			isEmptyElement = false;
			return (state = State.END_TAG);
		}

		/*
		 * First, move the buffer index to point at the byte right after
		 * whatever the end of the last thing we marked was. Even on the first
		 * run when eIdx is -1, this puts us comfortably at index 0 to begin.
		 */
		idx = eIdx + 1;

		// Find the next (or first) '<', mark will refill the buffer as-needed.
//		sIdx = mark(Constants.LT);
		sIdx = scan(Constants.LT_A);

		// Check for EOF
		if (bufferLength == -1) {
			return (state = State.END_DOCUMENT);
		}

		/*
		 * TODO: Need to define the possible tag-situations we have here.
		 * 
		 * <blah - normal tag <!-- - comment <![CDATA[- char data <? -
		 * processing instruction
		 */

		/*
		 * Check if we are processing a TAG (enclosed in <>) or TEXT (everything
		 * between >< chars). If idx == sIdx (where we found '<') then we are
		 * just entertain a tag (assuming it isn't a CDATA block, we will check
		 * for that later). If sIdx is > idx, then that means we were already
		 * inside of TEXT data and sIdx now signals the end of that TEXT data
		 * (the beginning of the next tag right after it).
		 */
		if (idx == sIdx) {
			/*
			 * Before we look for the ending '>' of the tag, we need to know if
			 * this is a CDATA block, because it's unique markers will screw up
			 * a regular search for '>' and it means we are in a TEXT state, not
			 * a TAG one as we thought (so far).
			 */
			if (ArrayUtil.equals(Constants.CD_START, sIdx, buffer)) {
				System.out.println("@@@@@@@@@@@@@@ INSIDE CDATA");
				// Adjust sIdx to point beyond CDATA start
				sIdx += Constants.CD_START.length;

				/*
				 * TODO: Need to be able to scan-refill-mark for the ending
				 * CDATA ]]> notation. In order to keep the functionality that
				 * mark performs for us, may need to modify that method to
				 * accept a byte[] which would require defining all the existing
				 * single-char constants as single-byte arrays or something like
				 * that.
				 */
			}

			// Processing a TAG, so find the end of it ('>')
//			eIdx = mark(Constants.GT);
			eIdx = scan(Constants.GT_A);

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
			 * First, if we are dealing with an empty element (<book/>) in which
			 * case we treat it special so it triggers both the START and END
			 * events for the same marks.
			 * 
			 * If this isn't an empty element, then we just need to figure out
			 * if it is a START_TAG (<hello>) or an END_TAG (</hello>).
			 */
			if (buffer[eIdx - 1] == Constants.FS) {
				// Set the empty element flag
				isEmptyElement = true;

				// Adjust eIdx to not include the ending '/'
				eIdx--;

				// First call to next is START, next is END, then we move on.
				state = State.START_TAG;
			} else if (buffer[sIdx + 1] == Constants.FS)
				state = State.END_TAG;
			else
				state = State.START_TAG;
		} else {
			/*
			 * Processing TEXT, so the '<' we just found is actually the
			 * terminating character to the run of characters and the beginning
			 * of the characters is where idx is currently pointing (1 after the
			 * previous end index as adjusted by the top of this method).
			 */
			eIdx = sIdx - 1;
			sIdx = idx;
			state = State.TEXT;
		}

		if (DEBUG)
			System.out.println(LOG_PREFIX + "[idx=" + idx + ",eventType="
					+ state + ", sIdx=" + sIdx + ", eIdx=" + eIdx + ", length="
					+ (eIdx - sIdx + 1) + "]");

		return state;
	}

	/**
	 * Used to get the name of the tag currently marked by the parser before
	 * returning a {@link State#START_TAG} or {@link State#END_TAG} event from
	 * {@link #nextState()} to the caller.
	 * <p/>
	 * <h3>Return Value Warning</h3>
	 * In order to make value-processing as fast as possible, this method
	 * returns an instance of {@link IByteSource} that simply wraps the
	 * underlying, volatile <code>byte[]</code> buffer used by the parser.
	 * <p/>
	 * The index, length and data described by the returned {@link IByteSource}
	 * are guaranteed to be valid until the next call to {@link #nextState()}
	 * (which has the potential to not only shift the internal indices, but also
	 * refill the buffer contents).
	 * <p/>
	 * Because of this design, it is imperative that {@link IByteSource}'s are
	 * never stored directly, but instead a copy of the <code>byte</code> values
	 * is stored or decoding the bytes into a <code>char[]</code> or
	 * {@link String} and storing that value instead.
	 * <p/>
	 * Fortunately the {@link IByteSource} provides a multitude of
	 * conversion/decoding methods to make this process easy.
	 * <p/>
	 * This design was chosen because of the huge win in parsing performance as
	 * well as memory usage; the only usage-requirement being that the returned
	 * value needs to be processed/converted/copied right away and not stored
	 * as-is.
	 * 
	 * @return a wrapper around the underlying <code>byte[]</code> buffer used
	 *         by the parser, marking the bytes that make up the tag name.
	 * 
	 * @throws IllegalStateException
	 *             if current parser state is not {@link State#START_TAG} or
	 *             {@link State#END_TAG}.
	 */
	public IByteSource getTagName() throws IllegalStateException {
		if (state != State.START_TAG && state != State.END_TAG)
			throw new IllegalStateException(
					"getTagName() can only be called when the parser is in a START_TAG or END_TAG state, but this parser is currently in state: "
							+ state);

		int nameStartIdx = sIdx;

		/*
		 * sIdx points at the opening '<' char. For a START_TAG event, sIdx+1 is
		 * always the beginning of the tag name. For an END_TAG event, in the
		 * case of an empty element (<hello/>), sIdx+1 is the start of the tag
		 * name and in the case of a normal closing tag (</hello>), sIdx+2 is
		 * the start of the tag name.
		 */
		if (state == State.START_TAG || buffer[sIdx + 1] != Constants.FS)
			nameStartIdx = sIdx + 1;
		else
			nameStartIdx = sIdx + 2;

		/*
		 * Attempt to find the end of the tag name by searching for whitespace
		 * (e.g. <hello attr="bob"> or tag-terminating constructs (e.g. '>' or
		 * '/') after the calculated start index to the end of our marked
		 * bounds.
		 */
		int nameEndIdx = ScannerUtil.indexOfAny(Constants.TN_TERM,
				nameStartIdx, (eIdx - nameStartIdx + 1), buffer);

		// Return a wrapper around the tag name bits of the buffer.
		return new DefaultByteSource(nameStartIdx, nameEndIdx - nameStartIdx,
				buffer);
	}

	public IByteSource getText() throws IllegalStateException {
		if (state != State.TEXT)
			throw new IllegalStateException(
					"getText() can only be called immediately after a TEXT event, but this parser is currently at event: "
							+ state);

		return new DefaultByteSource(sIdx, (eIdx - sIdx), buffer);
	}

	private void reset() {
		idx = 0;
		gIdx = 0;
		sIdx = Constants.INVALID;
		eIdx = Constants.INVALID;

		bufferLength = 0;

		state = null;
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
	private int fillBuffer() throws IOException {
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

	private int scan(byte[] values) throws IOException {
		/*
		 * If a previous scan operation had exhausted the buffer, attempt to
		 * refill it if we have any data left.
		 * 
		 * The buffer is considered "exhausted" if we have less than
		 * values.length number of bytes left in the buffer to scan against.
		 */
		if (idx + values.length >= bufferLength) {
			fillBuffer();

			/*
			 * If we hit EOF as a result of trying to refill the buffer, then we
			 * have exhausted all data to scan (and all data from the input
			 * source) and must return INVALID to the caller so it can handle
			 * the issue of the file being complete.
			 * 
			 * This typically signals the END_DOCUMENT event.
			 */
			if (bufferLength == -1)
				return Constants.INVALID;
		}

		int index = Constants.INVALID;
		int valuesLength = values.length;

		/*
		 * Begin scanning the buffer for the values given. Stop scanning as soon
		 * as a valid index is found (or we hit the end of our buffer).
		 */
		for (int i = idx; index == Constants.INVALID && i < bufferLength; i++) {
			int j = 0;

			// Increment j every time we match a value to the buffer
			for (; j < valuesLength && values[j] == buffer[i + j]; j++) {
				// no-op, the for-loop header does all the work.
			}

			/*
			 * If j was incremented by the for-loop to valuesLength, that means
			 * we matched every value to the buffer and we have found our
			 * starting index.
			 * 
			 * If j is less than valuesLength though, it means we matched j
			 * number of values and can skip ahead (j+1) indices and try to
			 * match again. We add j down here and the for-loop adds +1 for us
			 * on the next iteration.
			 */
			if (j == valuesLength)
				index = i;
			else
				i += j;
		}

		/*
		 * We couldn't find the values anywhere in the buffer and we need more
		 * bytes to scan. If our idx is > 0, that means there are old byte
		 * values we have already processed that we can replace with new values
		 * from the underlying stream in order to continue our search for the
		 * given value. If idx == 0, that means the entire buffer is already
		 * full and there is no old data that can be expunged and replaced; we
		 * would just have to report to the caller that we couldn't find the
		 * value.
		 */
		if (index == Constants.INVALID && idx > 0) {
			// Replace all old data with new data from input (if available)
			int bytesKept = fillBuffer();

			/*
			 * Try 1 more time to find the given value, starting the scan at the
			 * beginning of the new content we just read in and skipping all the
			 * stuff we already scanned the first time.
			 */
			for (int i = bytesKept; index == Constants.INVALID
					&& i < bufferLength; i++) {
				int j = 0;

				// Increment j every time we match a value to the buffer
				for (; j < valuesLength && values[j] == buffer[i + j]; j++) {
					// no-op, the for-loop header does all the work.
				}

				/*
				 * If j was incremented by the for-loop to valuesLength, that
				 * means we matched every value to the buffer and we have found
				 * our starting index.
				 * 
				 * If j is less than valuesLength though, it means we matched j
				 * number of values and can skip ahead (j+1) indices and try to
				 * match again. We add j down here and the for-loop adds +1 for
				 * us on the next iteration.
				 */
				if (j == valuesLength)
					index = i;
				else
					i += j;
			}
		}

		return index;
	}

	/**
	 * Convenience method used to scan the current buffer contents for a given
	 * <code>byte</code> value, replacing stale data in the buffer with new data
	 * from the input source as-needed to try and find the value.
	 * <p/>
	 * Data located in the buffer before (but not including) the current
	 * <code>idx</code> value, is considered "stale" or "processed" already and
	 * can be safely discarded and replaced with fresh data. When
	 * <code>idx</code> is equal to 0, that means there are no stale bytes that
	 * can be expunged from the buffer and it is considered full.
	 * <p/>
	 * Every time stale data is ejected from the buffer, the remaining data (if
	 * any) is moved to the front of the underlying <code>byte[]</code> buffer
	 * and new data added in after the kept data to the end of the buffer; or as
	 * much as was available from the underlying input source.
	 * <p/>
	 * One design detail about this method is that it always attempts to fill
	 * the buffer with at least 9 bytes of data. The reason for this is because
	 * detecting CDATA sections requires having the entire run of 9-char CDATA
	 * prefix in the buffer at one time; we don't just want to find the
	 * beginning &lt; char, we need to know if that is followed by the remaining
	 * "![CDATA[" chars indicating a CDATA block.
	 * <p/>
	 * To avoid making this method implementation more complex and looking for
	 * <code>byte[]</code> values instead of a single <code>byte</code> value,
	 * we just always make sure the buffer has at least as many bytes in it from
	 * the underlying stream as is necessary to detect the longest single run of
	 * chars that can trigger a state-change in the parser... and that happens
	 * to be a CDATA block which is 9 characters long.
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
	private int markOLD(byte value) throws IOException {
		/*
		 * TODO: Because of needing to find the ends of certain tags, like
		 * CDATA, comments, processing instructions, it might be necessary to
		 * modify this method to accept and match an array of bytes...
		 */

		/*
		 * If a previous mark operation had exhausted the buffer, attempt to
		 * refill it if we have any data left.
		 * 
		 * Always make sure there is at least 9 chars in the buffer so we can
		 * detect the longest run of chars that can cause a State change in the
		 * parser (a CDATA block).
		 */
		if (idx + 9 >= bufferLength) {
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
}