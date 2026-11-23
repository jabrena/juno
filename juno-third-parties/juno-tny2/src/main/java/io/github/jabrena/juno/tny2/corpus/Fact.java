package io.github.jabrena.juno.tny2.corpus;

import java.util.List;

/**
 * One fact: every way to ask it and every acceptable answer. The first answer is the one the model is taught most
 * often, so it is the one a greedy answer converges on.
 *
 * @param questions the phrasings, at least one
 * @param answers the acceptable answers, at least one
 */
public record Fact(List<String> questions, List<String> answers) {

    public Fact {
        questions = List.copyOf(questions);
        answers = List.copyOf(answers);
        if (questions.isEmpty() || answers.isEmpty()) {
            throw new IllegalArgumentException("a fact needs a question and an answer");
        }
        for (String question : questions) {
            for (String answer : answers) {
                // Fails fast on a pair that cannot be trained, instead of on a later random draw.
                new Example(question, answer);
            }
        }
    }

    public String primaryQuestion() {
        return questions.getFirst();
    }

    public String primaryAnswer() {
        return answers.getFirst();
    }

    public boolean accepts(String answer) {
        return answers.contains(answer);
    }
}
