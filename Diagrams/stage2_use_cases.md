# Stage 2 implemented use cases — November 7

```mermaid
flowchart LR
    Candidate([Candidate]) --> GUI[JobSearchGUI]
    Candidate --> CLI[JobSearchCLI]
    GUI --> C[Shared AgentController / CareerFacade]
    CLI --> C
    C --> F01[F01 Candidate profile]
    C --> F02[F02 Resume import and confirmation]
    C --> F03[F03 Evidence-backed skill extraction]
    C --> F04[F04 Job import and updates]
    C --> F05[F05 Job analysis and review]
    C --> F06[F06 Skill gaps and explanation]
    C --> F07[F07 Strategy matching and explanation]
    C --> F08[F08 Job comparison]
    C --> F09[F09 Resume suggestions]
    C --> F10[F10 Cover letter]
    C --> F11[F11 Interview questions and rubric]
    C --> F12[F12 Persistent mock interview]
    C --> F13[F13 Answer feedback]
    C --> F14[F14 Application tracking]
    C --> F15[F15 Deadline tracking]
    C --> F16[F16 Company notes and summary]
    C --> F17[F17 Budgeted career plan]
    F03 --> L[ConfiguredLLMClient / GroqAdapter or OpenAIAdapter]
    F05 --> L
    F06 --> L
    F07 --> L
    F08 --> L
    F09 --> L
    F10 --> L
    F11 --> L
    F13 --> L
    F16 --> L
    F17 --> L
    F12 --> Q[Saved questions, answers and original rubric]
    F17 --> M[Profile, reviewed gaps and prior reviewed advice]
```

These are connected code workflows. AI paths have automated injected-provider
verification; live provider verification and all feature demonstration links are
still required before submission. The app does not send applications, letters,
messages or external notifications, or browse research websites automatically.
