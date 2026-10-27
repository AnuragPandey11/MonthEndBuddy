package dev.monthendbuddy.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextSanitizerTest {

	@Test
	void singleLineReplacesControlAndFormatCharactersAndCollapsesWhitespace() {
		assertThat(TextSanitizer.singleLine("  Cafe\tCoffee\r\nDay\u0000 ")).isEqualTo("Cafe Coffee Day");
		assertThat(TextSanitizer.singleLine("Swiggy​‮Order")).isEqualTo("Swiggy Order");
		assertThat(TextSanitizer.singleLine("₹ नमस्ते 👋")).isEqualTo("₹ नमस्ते 👋");
	}

	@Test
	void blankBecomesNull() {
		assertThat(TextSanitizer.singleLine(null)).isNull();
		assertThat(TextSanitizer.singleLine(" \t\n")).isNull();
		assertThat(TextSanitizer.multiLine("\u0007")).isNull();
	}

	@Test
	void multiLineKeepsLineBreaksOnly() {
		assertThat(TextSanitizer.multiLine("line one\r\nline\ttwo\u0000")).isEqualTo("line one\nline two");
	}

}
