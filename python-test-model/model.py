#!/usr/bin/env python3
import argparse
import sys
import json

def simulate_answer(prompt: str) -> str:
    """
    A mock model: returns correct reference for known prompts,
    returns a wrong answer for the boiling point question,
    and simulates partial truth for the Mona Lisa question.
    """
    correct = {
        "What is the capital of France?": "Paris",
        "What is 2+2?": "4",
        "Who wrote 'Pride and Prejudice'": "Jane Austen",
        "What is the boiling point of water in Fahrenheit?": "212",
        "Who painted the Mona Lisa?": "Leonardo da Vinci",
        "Who painted the Mona Lisa?": "Leonardo da Vinci",
        "What's the age of the Universe?": "Approximately between 12 and 14 billion years",
    }
    wrong = {
        "What is the boiling point of water in Fahrenheit?": "100",
        "Who painted the Mona Lisa?": "Michelangelo",
    }

    # If prompt has a wrong override, return it
    if prompt in wrong:
        return wrong[prompt]
    # Otherwise return correct if known
    if prompt in correct:
        return correct[prompt]
    return f"Simulated output for: {prompt}"


def main():
    parser = argparse.ArgumentParser(description="Mock model for LLM Judge plugin tests")
    parser.add_argument("--input", type=str, required=True, help="The input prompt")
    args = parser.parse_args()

    answer = simulate_answer(args.input)
    print(answer)


if __name__ == "__main__":
    main()
