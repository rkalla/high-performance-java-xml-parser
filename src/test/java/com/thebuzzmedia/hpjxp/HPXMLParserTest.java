package com.thebuzzmedia.hpjxp;

import org.junit.Test;

import com.thebuzzmedia.hpjxp.HPXMLParser.EventType;

public class HPXMLParserTest {
	public static char[] INDENTS = { ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ',
			' ', ' ' };

	@Test
	public void testParser() {
		try {
			HPXMLParser parser = new HPXMLParser();
			parser.setInput(this.getClass().getResourceAsStream(
					"resources/basic.xml"));

			EventType evt = null;
			int indent = 0;

			while ((evt = parser.next()) != EventType.END_DOCUMENT) {
				// System.out.println("EVT: " + evt);
				switch (evt) {
				case START_TAG:
					System.out.println(new String(INDENTS, 0, indent++) + "<"
							+ parser.getTagName().decodeToString() + ">");
					break;
				case END_TAG:
					System.out.println(new String(INDENTS, 0, --indent) + "</"
							+ parser.getTagName().decodeToString() + ">");
					break;
				// case TEXT:
				// System.out.println("\t\""
				// + parser.getText().decodeToString() + "\"");
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}