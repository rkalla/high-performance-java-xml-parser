package com.thebuzzmedia.hpjxp;

import java.io.IOException;
import java.io.InputStream;

import com.thebuzzmedia.hpjxp.buffer.DefaultByteSource;
import com.thebuzzmedia.hpjxp.buffer.IByteSource;
import com.thebuzzmedia.hpjxp.util.ArrayUtil;
import com.thebuzzmedia.hpjxp.util.ScannerUtil;

// TODO: Add support for namespace awareness

/*
 * TODO: Need to add support for setting encoding. Make the UTIL class take a 
 * hashset approach where the key is the encoding and.
 * 
 * Alternatively, an instance of this parser class could create it's own 
 * encoder/decoder and pass them to the ByteSource to use internally when created.
 */

public class HPXMLParser {
	// TODO: reset back to Boolean.getBoolean("hpjxp.debug");
	public static final Boolean DEBUG = false;
	public static final Integer BUFFER_SIZE = Integer.getInteger(
			"hpjxp.bufferSize", 131072); // 1024*128 = 131072 (128k)

	public static final String LOG_PREFIX = "[hpjxp] ";

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

	private boolean isCDATA = false;
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
	 * data in the underlying stream by updating the <code>idx</code> (buffer
	 * position) <code>sIdx</code> (interesting element start index) and
	 * <code>eIdx</code> (interesting element end index) values.
	 * <p/>
	 * The parser considers anything that might cause a change in {@link State}
	 * "interesting", so encountering another tag or text block for example.
	 * <p/>
	 * No data is copied out of the stream or processed until the caller calls
	 * one of the appropriate <code>getXXX</code> methods.
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
		// Mandatory resets
		isCDATA = false;

		/*
		 * Before we do anything, if we were processing an empty element (e.g.
		 * <hello/>) we need to finish "processing" it by flipping our
		 * empty-element flag and returning the END_TAG event to match the
		 * previous START_TAG event for this element.
		 * 
		 * We don't adjust the sIdx or eIdx as they are still marking the tag
		 * name in case the caller wants that again.
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

		// Find the next '<', scan will refill the buffer as-needed.
		sIdx = scan(Constants.A_LT);

		// Check for EOF
		if (bufferLength == -1) {
			return (state = State.END_DOCUMENT);
		}

		/*
		 * Check if we are processing a TAG (enclosed in <>) or TEXT (everything
		 * between >< chars). If idx == sIdx (where we found '<') then we are
		 * just entertain a tag (assuming it isn't a CDATA block, we will check
		 * for that later). If sIdx is > idx, then that means we were already
		 * inside of TEXT data and sIdx now signals the end of that TEXT data
		 * (the beginning of the next tag right after it) and idx marks the
		 * beginning of it (more specifically, we scanned across all the
		 * character data and slammed into the next tag that terminates it).
		 */
		if (idx == sIdx) {
			// Ok we are inside a tag, figure out what kind (maybe CDATA)
			switch (buffer[sIdx + 1]) {
			// <?, processing instruction
			case Constants.QM:
				state = handlePI();
				break;
			// <!, comment OR CDATA block
			case Constants.EP:
				switch (buffer[sIdx + 2]) {
				// <!-, comment
				case Constants.DA:
					// Fully confirm the comment tag, then handle it.
					if (ArrayUtil.equals(Constants.CMT_PFX, sIdx, buffer))
						state = handleComment();
					break;
				// <![, CDATA
				case Constants.LB:
					// Fully confirm the CDATA block, then handle it.
					if (ArrayUtil.equals(Constants.CDATA_PFX, sIdx, buffer))
						handleCDATA();
					break;
				}
				break;
			/*
			 * A normal tag. This could be an open tag (<bob>), close tag
			 * (</bob>) or an empty-element tag (<bob/>).
			 */
			default:
				handleTag();
				break;
			}
		} else
			handleCharData();

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
		int nameEndIdx = ScannerUtil.indexOfAny(Constants.TAG_NAME_DELIM,
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

		IByteSource text = null;

		if (isCDATA)
			text = new DefaultByteSource(sIdx + Constants.CDATA_PFX.length,
					eIdx - sIdx - Constants.CDATA_PFX.length
							- Constants.CDATA_SFX.length + 1, buffer);
		else
			text = new DefaultByteSource(sIdx, (eIdx - sIdx + 1), buffer);

		return text;
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
	 * it is considered "full" already. If <code>idx==buffer.length-1</code>
	 * then the entire buffer is overwritten and refilled with fresh content
	 * from the input source.
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
			System.out.println("idx: " + idx + ", sIdx: " + sIdx + ", eIdx: "
					+ eIdx + ", bufferLength: " + bufferLength
					+ ", bytesKept: " + bytesKept + ", shiftCount: "
					+ shiftCount);

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
	 * Optimized method used to scan the underlying input stream buffer for the
	 * given <code>byte[]</code> values, replacing stale data in the buffer
	 * as-needed (by calling {@link #fillBuffer()}) in order to try and find the
	 * values.
	 * <p/>
	 * In the name of performance this method duplicates its search code and
	 * avoids calling out into helper libraries so it can complete as fast as
	 * possible.
	 * 
	 * @param value
	 *            The <code>byte</code> value to find the index of.
	 * 
	 * @return the index in <code>buffer</code> where the given values were
	 *         found or {@link Constants#INVALID} if the value could not be
	 *         found.
	 * 
	 * @throws IOException
	 *             (calls {@link #fillBuffer()})
	 */
	private int scan(byte[] values) throws IOException {
		/*
		 * If a previous scan operation had exhausted the buffer, attempt to
		 * refill it if we have any data left.
		 * 
		 * The buffer is considered "exhausted" if we have less than 9 or
		 * values.length number of bytes (whichever is bigger) left in the
		 * buffer to scan against.
		 * 
		 * Because of how nextState matches and detects tag-type when it finds
		 * '<', in the maximum case we need 9 bytes available in the buffer to
		 * check the longest tag type (CDATA prefix). So we make sure whenever a
		 * scan is done that we have at least 9 bytes in our buffer so nextState
		 * can be coded to always assume it has at least sIdx+8 valid indices it
		 * can check without doing a fill itself; it keeps the logic up there
		 * simpler.
		 */
		if (idx + (9 > values.length ? 9 : values.length) >= bufferLength) {
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

	private State handlePI() throws IOException, XMLParseException {
		// Find the end of the processing instruction.
		eIdx = scan(Constants.PI_SFX);

		if (eIdx == Constants.INVALID)
			throw new XMLParseException(
					"Unable to find closing '?>' for the processing-instruction block starting at position "
							+ gIdx
							+ " in the XML document. Either the XML is malformed or contains individual TAG/TEXT constructs so long that BUFFER_SIZE will need to be increased in order to hold it in memory at one time.");

		/*
		 * Effectively skip the PI by recursively executing nextState again,
		 * which will point idx at eIdx+1 and move us beyond it to the next
		 * state.
		 */
		return nextState();
	}

	private State handleComment() throws IOException, XMLParseException {
		// Find the end of the comment.
		eIdx = scan(Constants.CMT_SFX);

		if (eIdx == Constants.INVALID)
			throw new XMLParseException(
					"Unable to find closing '-->' for the comment starting at position "
							+ gIdx
							+ " in the XML document. Either the XML is malformed or contains individual TAG/TEXT constructs so long that BUFFER_SIZE will need to be increased in order to hold it in memory at one time.");

		/*
		 * Effectively skip the comment by recursively executing nextState
		 * again, which will point idx at eIdx+1 and move us beyond it to the
		 * next state.
		 */
		return nextState();
	}

	private void handleTag() throws IOException, XMLParseException {
		// Processing a TAG, so find the end of it ('>')
		eIdx = scan(Constants.A_GT);

		if (eIdx == Constants.INVALID)
			throw new XMLParseException(
					"Unable to find closing '>' for the tag starting at position "
							+ gIdx
							+ " in the XML document. Either the XML is malformed or contains individual TAG/TEXT constructs so long that BUFFER_SIZE will need to be increased in order to hold it in memory at one time.");

		/*
		 * Check if we are dealing with an empty element (<book/>) in which case
		 * we treat it special so it triggers both the START and END events for
		 * the same marks.
		 * 
		 * If this isn't an empty element, then we just need to figure out if it
		 * is a START_TAG (<hello>) or an END_TAG (</hello>).
		 */
		if (buffer[eIdx - 1] == Constants.FS) {
			// Set the empty element flag
			isEmptyElement = true;

			// First call to next is START, next is END, then we move on.
			state = State.START_TAG;
		} else if (buffer[sIdx + 1] == Constants.FS)
			state = State.END_TAG;
		else
			state = State.START_TAG;
	}

	/**
	 * Used to mark a CDATA block.
	 * <p/>
	 * This method assumes that <code>sIdx</code> points at the opening '&lt;'
	 * char beginning the CDATA prefix, it will then find the closing CDATA
	 * suffix. After that the method will adjust <code>sIdx</code> and
	 * <code>eIdx</code> to point at the beginning and ending indices of the
	 * actual character data contained within the CDATA prefix and suffix and
	 * set the current parser state to {@link State#TEXT}.
	 * 
	 * @throws IOException
	 *             (calls {@link #scan(byte[])})
	 * 
	 * @throws XMLParseException
	 *             if the CDATA-terminating suffix ({@link Constants#CD_END})
	 *             cannot be found within the buffer even after expiring old
	 *             data and filling it back up with new data from the underlying
	 *             input source.
	 */
	private void handleCDATA() throws IOException, XMLParseException {
		// Find the end of the CDATA block.
		eIdx = scan(Constants.CDATA_SFX);

		if (eIdx == Constants.INVALID)
			throw new XMLParseException(
					"Unable to find closing ']]>' for the CDATA block starting at position "
							+ gIdx
							+ " in the XML document. Either the XML is malformed or contains individual TAG/TEXT constructs so long that BUFFER_SIZE will need to be increased in order to hold it in memory at one time.");

		// getText is handled differently for CDATA and plain char data.
		isCDATA = true;

		// Adjust eIdx to point at the index of the last CDATA suffix char
		eIdx += Constants.CDATA_SFX.length - 1;

		// Set the current state.
		state = State.TEXT;
	}

	/**
	 * Used to mark the bounds of character data (data found between the close
	 * of one tag and opening of another).
	 * <p/>
	 * This method assumes that <code>sIdx</code> points at the opening '&lt;'
	 * char of the tag that terminates this run of characters; it will then
	 * adjust <code>eIdx</code> to equal 1 index before that and set
	 * <code>sIdx</code> equal to the current <code>idx</code>. Lastly, the
	 * state is set to {@link State#TEXT}.
	 */
	private void handleCharData() {
		/*
		 * TODO: Something is going on here with the end of files, running
		 * simple.xml through parser with test case shows it parse the entire
		 * thing then throw an exception at the last step as eIdx gets
		 * decreamented to -2 from this method. Need to research more.
		 */

		/*
		 * Processing TEXT, so the '<' we just found is actually the terminating
		 * character to the run of characters and the beginning of the
		 * characters is where idx is currently pointing (1 after the previous
		 * end index as adjusted by the top of this method).
		 */
		eIdx = sIdx - 1;
		sIdx = idx;

		// Set the current state.
		state = State.TEXT;
	}
}