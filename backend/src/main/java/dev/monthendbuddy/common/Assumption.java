package dev.monthendbuddy.common;

/**
 * One input of a calculation and where it came from (architecture §10.4), so an estimate can never be presented as
 * a fact: the UI shows the label as a chip and the agent must state it.
 */
public record Assumption(String name, String value, AssumptionLabel label) {

}
