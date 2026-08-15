# Contributing to Morning Peace ☀️

Thank you for your interest in contributing to **Morning Peace**! We welcome contributions ranging from bug reports and documentation enhancements to new features.

---

## 🛠️ Getting Started

1. **Fork the Repository**: Fork the project on GitHub to your account.
2. **Clone Locally**:
   ```bash
   git clone https://github.com/leo-BWL/MorningPeace.git
   cd MorningPeace
   ```
3. **Open in Android Studio**: Open the root directory in Android Studio (Ladybug or newer recommended).
4. **Build and Run**: Run the app on an Android device or emulator running Android 7.0 (API 24) or higher.

---

## 🌿 Branching & Development Workflow

- Create a new branch for your feature or bug fix:
  ```bash
  git checkout -b feature/my-new-feature
  # or
  git checkout -b fix/issue-description
  ```
- Make focused, atomic commits with clear descriptions:
  ```bash
  git commit -m "feat(lockscreen): add gentle fade transition"
  ```
- Ensure all tests pass before submitting:
  ```bash
  ./gradlew testDebugUnitTest
  ```

---

## 📋 Pull Request Process

1. Push your branch to your fork.
2. Open a Pull Request against the `main` branch.
3. Fill out the PR template with relevant context and testing details.
4. Maintainers will review your PR and provide constructive feedback.

---

## 📜 Code Style Guidelines

- Follow official Kotlin coding conventions.
- Use ViewBinding rather than `findViewById` or synthetic bindings.
- Keep UI strings in `res/values/strings.xml` for internationalization.
- Add unit tests for business logic in `BlockStateManager` and managers.

Thank you for helping build mindful digital experiences! 🕊️
