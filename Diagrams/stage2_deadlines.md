# F15 — Implemented Observer lifecycle

```mermaid
sequenceDiagram
    participant View as JobSearchGUI / JobSearchCLI
    participant Controller as AgentController
    participant Facade as CareerFacade
    participant Subject as DeadlineManager
    participant Store as Repository
    View->>Controller: subscribeDeadlines(this)
    Controller->>Facade: subscribeDeadlines(observer)
    Facade->>Subject: subscribe(observer: DeadlineObserver)
    loop GUI every minute / CLI at each prompt
        View->>Controller: submitRequest("deadlines.check", {})
        Controller->>Facade: checkDeadlines()
        Facade->>Subject: checkDeadlines()
        Subject->>Subject: Resolve local date from Clock (America/Toronto)
        Subject->>Store: listApplications(); listJobs()
        Subject->>Subject: Retain untracked / SAVED jobs
        alt Deadline unknown, past, more than 7 days away, or application already submitted/closed
            Subject->>Subject: No notification (overdue/unknown still listed if outstanding)
        else Due within 0–7 days and not already delivered today
            Subject->>View: onDeadline(reminder: DeadlineReminder)
            View->>View: CLI prints; GUI queues display on Swing EDT
            Subject->>Subject: Record successful in-process delivery
        end
        Subject-->>Controller: Current due entries / count
    end
    View->>Controller: unsubscribeDeadlines(this) on close
    Controller->>Facade: unsubscribeDeadlines(observer)
    Facade->>Subject: unsubscribe(observer: DeadlineObserver)
    Note over View,Subject: GUI stops its Timer; CLI unregisters in finally
```

`listDeadlines()` returns sorted `DeadlineReminder` values with OVERDUE, TODAY,
UPCOMING or UNKNOWN state. `checkDeadlines()` publishes only eligible upcoming
reminders. The subject does not depend on Swing, stdin, email or OS notifications.
