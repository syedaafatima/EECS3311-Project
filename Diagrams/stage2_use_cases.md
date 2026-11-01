# Currently implemented interface workflows

```mermaid
flowchart LR
    Candidate([Candidate]) --> GUI[JobSearchGUI]
    Candidate --> CLI[JobSearchCLI]
    GUI --> Shared[Shared AgentController / CareerFacade]
    CLI --> Shared
    Shared --> F01[F01 Manage candidate profile]
    Shared --> F02[F02 Import and review resume]
    Shared --> F03[F03 Extract and approve resume skills]
    Shared --> F04[F04 Import or update job posting]
    Shared --> F05[F05 Analyze and review job requirements]
    Shared --> F06[F06 Deterministic skill gaps - partial]
    Shared --> F07[F07 Strategy matching - partial]
    Shared --> F14[F14 Track applications and status history]
    Shared --> F15[F15 List deadlines and receive active-view reminders]
    F03 --> Provider[Configured OpenAI provider]
    F05 --> Provider
    F06 --> Reviewed[Reviewed profile skills and job requirements]
    F07 --> Reviewed
```

This is the implemented subset, not a claim that all 17 Stage 1 use cases are done.
`docs/stage2-progress.md` accounts for every original feature and remaining work.
The Stage 1 use-case diagram remains the planned full-system scope.
