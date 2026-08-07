# Contributing to P.I.E. — Process Intelligence Ecosystem

This document provides a set of guidelines for contributing to the project. These are mostly guidelines, not rules. Use your best judgment, and feel free to propose changes to this document in a pull request.

## How Can I Contribute?

### Reporting Bugs

This section guides you through submitting a bug report for P.I.E. Following these guidelines helps maintainers and the community understand your report, reproduce the behavior, and find related reports.

- **Ensure the bug was not already reported** by searching on GitHub under [Issues](https://github.com/prompt-cartel/pie-process-intelligence-ecosystem/issues).
- If you're unable to find an open issue addressing the problem, [open a new one](https://github.com/prompt-cartel/pie-process-intelligence-ecosystem/issues/new). Be sure to include a **title and clear description**, as much relevant information as possible, and a **code sample** or an **executable test case** demonstrating the expected behavior that is not occurring.

### Suggesting Enhancements

This section guides you through submitting an enhancement suggestion for P.I.E., including completely new features and minor improvements to existing functionality.

- **Perform a cursory search** to see if the enhancement has already been suggested. If it has, add a comment to the existing issue instead of opening a new one.
- If you're unable to find an open issue addressing the enhancement, [open a new one](https://github.com/prompt-cartel/pie-process-intelligence-ecosystem/issues/new). Be sure to include a **title and clear description**, as much relevant information as possible, and a **code sample** or an **executable test case** demonstrating the expected behavior that is not occurring.

### Your First Code Contribution

Unsure where to begin contributing to P.I.E.? You can start by looking through these good first issue and help wanted issues:

- [Good first issues](https://github.com/prompt-cartel/pie-process-intelligence-ecosystem/labels/good%20first%20issue) - issues which should only require a few lines of code, and a test or two.
- [Help wanted issues](https://github.com/prompt-cartel/pie-process-intelligence-ecosystem/labels/help%20wanted) - issues which should be a bit more involved than good first issue issues.

### Pull Requests

The process described here has several goals:

- Maintain P.I.E.'s quality
- Fix problems that are important to users
- Engage the community in working toward the best possible P.I.E.
- Enable a sustainable system for P.I.E.'s maintainers to review contributions

Please follow these steps to have your contribution considered by the maintainers:

1. Follow all instructions in [the template](PULL_REQUEST_TEMPLATE.md)
2. Follow the [style guides](#style-guides)
3. After you submit your pull request, verify that all [status checks](https://help.github.com/articles/about-status-checks/) are passing
4. If a maintainer asks you to "rebase" your PR, they're saying that a lot of code has changed, and that you need to update your branch so it's easier to merge.

## Style Guides

### Git Commit Messages

- Use the present tense ("Add feature" not "Added feature")
- Use the imperative mood ("Move cursor to..." not "Moves cursor to...")
- Limit the first line to 72 characters or less
- Reference issues and pull requests liberally after the first line
- When only changing documentation, include [ci skip] in the commit title
- Consider starting the commit message with an applicable emoji:
    - :art: when improving the format/structure of the code
    - :racehorse: when improving performance
    - :non-potable_water: when plugging memory leaks
    - :memo: when writing docs
    - :penguin: when fixing something on Linux
    - :apple: when fixing something on macOS
    - :checkered_flag: when fixing something on Windows
    - :bug: when fixing a bug
    - :fire: when removing code or files
    - :green_heart: when fixing the CI build
    - :white_check_mark: when adding tests
    - :lock: when dealing with security
    - :arrow_up: when upgrading dependencies
    - :arrow_down: when downgrading dependencies
    - :shirt: when removing linter warnings

### Code Style

- Follow the style you see used in the repository.
- We use Prettier for formatting.
