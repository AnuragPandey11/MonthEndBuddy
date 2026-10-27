package dev.monthendbuddy.agent.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Structural guard for D2: no tool input may carry a user ID, so the model can never choose whose data a tool reads.
 * The user comes only from {@link ToolContext}.
 */
class ToolInputGuardTest {

	@Test
	void noToolInputRecordDeclaresAUserId() {
		List<Class<?>> inputs = new ArrayList<>();
		for (JavaClass tool : new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages("dev.monthendbuddy.agent")
			.that(com.tngtech.archunit.base.DescribedPredicate.describe("finance tools",
					c -> c.isAssignableTo(FinanceTool.class) && !c.isInterface()))) {
			inputs.add(inputType(tool.reflect()));
		}
		assertThat(inputs).as("finance tools found").hasSizeGreaterThanOrEqualTo(4);
		for (Class<?> input : inputs) {
			assertThat(input.isRecord()).as(input + " is a record").isTrue();
			assertNoUserId(input, input.getSimpleName());
		}
	}

	private static void assertNoUserId(Class<?> type, String path) {
		if (!type.isRecord()) {
			return;
		}
		for (RecordComponent component : type.getRecordComponents()) {
			assertThat(component.getName().toLowerCase()).as(path + "." + component.getName())
				.doesNotContain("userid")
				.isNotEqualTo("user")
				.isNotEqualTo("owner");
			assertNoUserId(component.getType(), path + "." + component.getName());
		}
	}

	private static Class<?> inputType(Class<?> tool) {
		for (Type type : tool.getGenericInterfaces()) {
			if (type instanceof ParameterizedType p && p.getRawType() == FinanceTool.class) {
				return (Class<?>) p.getActualTypeArguments()[0];
			}
		}
		throw new AssertionError(tool + " does not implement FinanceTool<I> directly");
	}

}
