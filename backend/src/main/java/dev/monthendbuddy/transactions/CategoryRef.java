package dev.monthendbuddy.transactions;

import java.util.UUID;

/** A category as shown next to a transaction. */
public record CategoryRef(UUID id, String name) {

}
