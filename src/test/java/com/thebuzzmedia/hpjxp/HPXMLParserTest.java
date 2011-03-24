package com.thebuzzmedia.hpjxp;

import org.junit.Test;

import com.thebuzzmedia.hpjxp.HPXMLParser.State;

public class HPXMLParserTest {
	public static char[] INDENTS = { ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ',
			' ', ' ' };

	@Test
	public void testParserBasic() {
		try {
			HPXMLParser parser = new HPXMLParser();
			parser.setInput(this.getClass().getResourceAsStream(
					"resources/basic.xml"));

			State evt = null;
			int indent = 0;

			while ((evt = parser.nextState()) != State.END_DOCUMENT) {
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

	@Test
	public void testParserBasic2() {
		try {
			HPXMLParser parser = new HPXMLParser();
			parser.setInput(this.getClass().getResourceAsStream(
					"resources/basic2.xml"));

			State evt = null;
			int indent = 0;

			while ((evt = parser.nextState()) != State.END_DOCUMENT) {
				System.out.println("EVT: " + evt);
				switch (evt) {
				case START_TAG:
					System.out.println(new String(INDENTS, 0, indent++) + "<"
							+ parser.getTagName().decodeToString() + ">");
					break;
				case END_TAG:
					System.out.println(new String(INDENTS, 0, --indent) + "<"
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

	@Test
	public void testParserBasicCData() {
		try {
			HPXMLParser parser = new HPXMLParser();
			parser.setInput(this.getClass().getResourceAsStream(
					"resources/basic_cdata.xml"));

			State evt = null;
			int indent = 0;

			while ((evt = parser.nextState()) != State.END_DOCUMENT) {
				System.out.println("EVT: " + evt);
				switch (evt) {
				case START_TAG:
					System.out.println(new String(INDENTS, 0, indent++) + "<"
							+ parser.getTagName().decodeToString() + ">");
					break;
				case END_TAG:
					System.out.println(new String(INDENTS, 0, --indent) + "<"
							+ parser.getTagName().decodeToString() + ">");
					break;
				case TEXT:
					System.out.println("\t\""
							+ parser.getText().decodeToString() + "\"");
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testParser2() {
		try {
			HPXMLParser parser = new HPXMLParser();
			parser.setInput(this.getClass().getResourceAsStream(
					"resources/simple.xml"));

			State evt = null;
			int indent = 0;

			while ((evt = parser.nextState()) != State.END_DOCUMENT) {
				 System.out.println("EVT: " + evt);
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