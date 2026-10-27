package dev.monthendbuddy.transactions.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TransactionQueryRepositoryTest {

	@Test
	void likeWildcardsInSearchTextMatchLiterally() {
		assertThat(TransactionQueryRepository.escapeLike("100%_off\\")).isEqualTo("100\\%\\_off\\\\");
	}

}
