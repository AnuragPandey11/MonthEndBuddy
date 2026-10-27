/**
 * Shared kernel: value objects ({@code Money}, {@code Period}, {@code UserId}), base entities and repositories,
 * eventing ports, the error model, cache and rate-limit seams (LLD §2). Shared with every module and depends on
 * none. It is an open module, so its sub-packages ({@code error}, {@code web}, {@code persistence}, ...) are
 * visible to all modules.
 */
@ApplicationModule(displayName = "Common", type = ApplicationModule.Type.OPEN, allowedDependencies = {})
package dev.monthendbuddy.common;

import org.springframework.modulith.ApplicationModule;
