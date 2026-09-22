# SnoozeShare User Guide

## Introduction

SnoozeShare is a desktop app for short-term property rentals, supporting three
roles: Guest, Host, and Support Agent. See the [Product Backlog](ProductBacklog.md)
for the full feature list.

_This guide will be filled in as features are implemented._

## Starting The App

SnoozeShare requires Java 25. Check your version with `java -version` before
running it either way below.

### From Source

Run the app from the project root:

```shell
.\gradlew run
```

### From a Packaged Jar

```shell
.\gradlew shadowJar
java -jar build/libs/SnoozeShare.jar
```

## Registering an Account

Roles are mocked at registration time:

- **Guest** — register with a name and email, no code required.
- **Host** / **Support Agent** — register with a name, email, and the
  role-specific registration code (see the Product Backlog, F0.1.1).

_Screenshots and per-role walkthroughs to follow once the UI is built._
