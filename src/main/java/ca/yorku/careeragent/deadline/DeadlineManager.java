package ca.yorku.careeragent.deadline;

import ca.yorku.careeragent.domain.Application;
import ca.yorku.careeragent.domain.Job;
import ca.yorku.careeragent.persistence.Repository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

/** Observer subject. Dates are inclusive calendar deadlines in the clock's timezone. */
public final class DeadlineManager {
    private final Repository repository;
    private final Clock clock;
    private final Set<DeadlineObserver> observers = new CopyOnWriteArraySet<>();
    private final Set<String> notifiedToday = new HashSet<>();
    private LocalDate lastCheck;

    public DeadlineManager(Repository repository) {
        this(repository, Clock.system(ZoneId.of("America/Toronto")));
    }

    public DeadlineManager(Repository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }

    public void subscribe(DeadlineObserver observer) { observers.add(Objects.requireNonNull(observer)); }
    public void unsubscribe(DeadlineObserver observer) { observers.remove(observer); }

    public List<DeadlineReminder> listDeadlines() {
        LocalDate today = LocalDate.now(clock);
        Map<String, Application.Status> statuses = repository.listApplications().stream()
                .collect(Collectors.toMap(Application::getJobId, Application::getStatus));
        return repository.listJobs().stream()
                // A submission deadline has been fulfilled once the application is sent.
                .filter(job -> !statuses.containsKey(job.getId()) || statuses.get(job.getId()) == Application.Status.SAVED)
                .sorted(Comparator.comparing(Job::getDeadline, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Job::getId))
                .map(job -> reminder(job, today)).toList();
    }

    private DeadlineReminder reminder(Job job, LocalDate today) {
        LocalDate deadline = job.getDeadline();
        long days = deadline == null ? 0 : ChronoUnit.DAYS.between(today, deadline);
        DeadlineReminder.State state = deadline == null ? DeadlineReminder.State.UNKNOWN
                : days < 0 ? DeadlineReminder.State.OVERDUE
                : days == 0 ? DeadlineReminder.State.TODAY : DeadlineReminder.State.UPCOMING;
        return new DeadlineReminder(job.getId(), job.getTitle(), job.getCompany(), deadline, state, days, clock.getZone());
    }

    /** Notify once per job/date per active process, only for due dates within seven days. */
    public synchronized List<DeadlineReminder> checkDeadlines() {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(lastCheck)) {
            notifiedToday.clear();
            lastCheck = today;
        }
        List<DeadlineReminder> due = listDeadlines().stream()
                .filter(r -> r.deadline() != null && r.daysRemaining() >= 0 && r.daysRemaining() <= 7).toList();
        for (DeadlineReminder reminder : due) {
            String key = reminder.jobId() + ":" + reminder.deadline();
            if (observers.isEmpty() || notifiedToday.contains(key)) continue;
            boolean delivered = false;
            for (DeadlineObserver observer : observers) {
                try { observer.onDeadline(reminder); delivered = true; }
                catch (RuntimeException ignored) { /* A failed view must not prevent delivery to other views. */ }
            }
            if (delivered) notifiedToday.add(key);
        }
        return due;
    }
}
