# Security Policy

Chassis loads the code of every jar in a host's folder and decides which classes of the host each extension sees. It is no sandbox: an extension runs with the permissions of the host's process, so install only extensions you trust. A flaw in Chassis can still matter, for example when it loads a jar the host did not ask for, or lets an extension replace a class the host exports.

## Supported versions

Security fixes go into the next release built from `dev`. Only the latest release receives them; update to it before reporting.

## Reporting a vulnerability

Report vulnerabilities privately through [GitHub's private vulnerability reporting](https://github.com/whereareiam/chassis/security/advisories/new). Do not open a public issue, pull request, or Discord message with the details.

Include:

- the Chassis version, the host it runs in, and the platform
- what an extension or an attacker can do, and what they need for it
- steps to reproduce

You receive an answer on the report. The fix is released before the details are published, and the release notes name it as a security fix.
