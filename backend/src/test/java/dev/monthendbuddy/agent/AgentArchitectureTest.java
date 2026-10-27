package dev.monthendbuddy.agent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import dev.monthendbuddy.agent.infrastructure.SpringAiLlmGateway;

/** Architecture §10.2 and ADR 0001: the model provider is reachable only through the gateway port. */
class AgentArchitectureTest {

	private final JavaClasses classes = new ClassFileImporter()
		.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
		.importPackages("dev.monthendbuddy");

	@Test
	void onlyTheGatewayUsesSpringAi() {
		noClasses().that()
			.doNotHaveFullyQualifiedName(SpringAiLlmGateway.class.getName())
			.and()
			.doNotHaveFullyQualifiedName(SpringAiLlmGateway.class.getName() + "$DefinitionOnlyTool")
			.should()
			.dependOnClassesThat()
			.resideInAnyPackage("org.springframework.ai..", "com.openai..")
			.check(classes);
	}

	@Test
	void chatClientIsNeverUsed() {
		// ChatClient's advisor executes tools itself; the orchestrator must run the loop (architecture §10.3).
		noClasses().should()
			.dependOnClassesThat()
			.resideInAPackage("org.springframework.ai.chat.client..")
			.check(classes);
	}

}
