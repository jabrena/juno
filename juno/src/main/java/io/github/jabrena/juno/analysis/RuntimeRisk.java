package io.github.jabrena.juno.analysis;

import io.github.jabrena.juno.classfile.MethodRef;

/** One stable, machine-readable runtime-risk finding. */
public record RuntimeRisk(String code, RiskSeverity severity, MethodRef method, String message) {
}
