# Keyboard-Aware Suggester

This document outlines the functionality of the new keyboard-aware suggester and how to customize it.

## Feature Overview

The keyboard-aware suggester is a powerful new feature that provides intelligent corrections for typos based on the physical layout of the keyboard. It is designed to be both fast and accurate, handling a wide variety of common typing errors.

### How it Works

The suggester uses a hybrid search approach to find the best corrections for a given word:

1.  **Full Substitution Search:** This search finds words that can be formed by swapping characters in the original word with their neighbors on the keyboard. This is very effective for typos where you hit a key next to the one you intended.
2.  **Edit Distance 1 Search:** This search finds words that are one "edit" away from the original word. An edit can be:
    *   An **insertion** (an extra character).
    *   A **deletion** (a missing character).
    *   A **transposition** (two adjacent characters swapped).

Both of these searches are performed in parallel across all three dictionary sources (`custom.txt`, `common.txt`, and `wordlist.txt`) to ensure maximum speed. The results are then combined and prioritized, with words from `custom.txt` always appearing first.

### Sentence-Level Correction

The suggester also includes a sentence-level correction module that can handle space-merged words. For example, if you type "iamgoingtoschool", the suggester will attempt to segment it into "i am going to school".

## Customization

The keyboard-aware suggester can be customized by editing the `res/xml/surroundings.xml` file. This file defines the neighbor data for each key on the keyboard.

### `surroundings.xml` Format

The `surroundings.xml` file contains a series of `<char>` tags, each with two attributes:

*   `value`: The character for which you are defining neighbors.
*   `neighbors`: A string of characters that are considered neighbors of the `value` character.

**Example:**

```xml
<char value="a" neighbors="qwsz" />
```

This line defines that the neighbors of the character "a" are "q", "w", "s", and "z".

### How to Edit

You can edit this file to add or remove neighbors for any character. For example, to add the special character `!` as a neighbor of `q`, you would change the line to:

```xml
<char value="q" neighbors="aws12!" />
```

This allows you to fine-tune the suggestion logic to your specific typing style and preferences.
