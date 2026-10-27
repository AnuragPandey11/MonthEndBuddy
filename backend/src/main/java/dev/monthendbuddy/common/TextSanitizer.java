package dev.monthendbuddy.common;

/**
 * Cleans free text that users or CSV files supply: control characters become spaces, runs of whitespace collapse
 * and the result is trimmed. Descriptions later reach the LLM as untrusted data (architecture §10.5), so they must
 * not carry hidden control sequences.
 */
public final class TextSanitizer {

	private TextSanitizer() {
	}

	/** Single-line text; {@code null} or blank input gives {@code null}. */
	public static String singleLine(String text) {
		if (text == null) {
			return null;
		}
		String cleaned = text.codePoints()
			.map(cp -> Character.isISOControl(cp) || Character.getType(cp) == Character.FORMAT ? ' ' : cp)
			.collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
			.toString()
			.replaceAll("\\s+", " ")
			.strip();
		return cleaned.isEmpty() ? null : cleaned;
	}

	/** Multi-line text (notes): keeps line breaks, removes other control characters. */
	public static String multiLine(String text) {
		if (text == null) {
			return null;
		}
		String cleaned = text.replace("\r\n", "\n")
			.codePoints()
			.map(cp -> cp == '\n' ? cp : (Character.isISOControl(cp) || Character.getType(cp) == Character.FORMAT ? ' '
					: cp))
			.collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
			.toString()
			.strip();
		return cleaned.isEmpty() ? null : cleaned;
	}

}
